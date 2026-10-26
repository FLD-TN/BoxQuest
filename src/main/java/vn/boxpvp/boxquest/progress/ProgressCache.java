package vn.boxpvp.boxquest.progress;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.config.Settings;
import vn.boxpvp.boxquest.progress.ActiveQuest.Row;

/**
 * Trạng thái người chơi trong RAM + write-behind. Bốn mốc flush:
 * <ol>
 *   <li>Hoàn thành nhiệm vụ: {@link #saveNow} — đồng bộ, chỉ người đó.</li>
 *   <li>Định kỳ: {@link #flushDirtyAsync} — async, chỉ bản ghi {@code dirty}.</li>
 *   <li>Thoát server: {@link #unload} — ghi ngay rồi xoá khỏi RAM.</li>
 *   <li>Tắt plugin: {@link #flushAllSync} — đồng bộ, toàn bộ (scheduler đã dừng, task async sẽ không chạy).</li>
 * </ol>
 * Chỉ dùng trên main thread.
 */
public final class ProgressCache {

    private final BoxQuestPlugin plugin;
    private final ProgressStorage storage;
    private final Map<UUID, ActiveQuest> players = new HashMap<>();
    /** Số lần flush async chưa xử lý xong kết quả trên main thread. 0 thì dọn được mốc phiên bản (xem storage). */
    private int flushesInFlight;
    private BukkitTask flushTask;

    public ProgressCache(BoxQuestPlugin plugin, ProgressStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public ActiveQuest get(UUID uuid) {
        return players.get(uuid);
    }

    /**
     * Nạp từ database. Chưa có dòng nào -> tạo mới theo {@code defaults} (chỉ lúc này defaults mới được áp dụng).
     *
     * @return {@code null} nếu database lỗi; khi đó người chơi không có trong cache nên dữ liệu thật không bị ghi đè
     */
    public ActiveQuest load(UUID uuid) {
        ActiveQuest existing = players.get(uuid);
        if (existing != null) return existing;
        ActiveQuest active;
        try {
            active = storage.load(uuid).map(ActiveQuest::fromRow).orElse(null);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.messages().log("log.database.load-failed", "uuid", uuid.toString()), e);
            return null;
        }
        if (active == null) {
            Settings s = plugin.settings();
            active = new ActiveQuest(uuid, null, 0, 0, 0,
                    s.defaultAutoRepeat(), s.defaultShowActionbar(), s.defaultSound(), s.defaultShowBossbar());
            active.markDirty(); // ghi dòng mới ở lần flush tới, cố định lựa chọn mặc định cho người này
        }
        players.put(uuid, active);
        return active;
    }

    /** Ghi ngay, đồng bộ. Dùng khi hoàn thành nhiệm vụ — phải xong TRƯỚC khi trả Xu. */
    public boolean saveNow(ActiveQuest active) {
        active.markDirty();
        boolean ok = storage.write(List.of(active.snapshot()));
        if (ok) active.clearDirty();
        return ok;
    }

    /** Thoát server: ghi ngay rồi xoá khỏi RAM. */
    public void unload(UUID uuid) {
        ActiveQuest active = players.remove(uuid);
        if (active != null && active.dirty()) saveNow(active);
    }

    public int size() {
        return players.size();
    }

    /** Task định kỳ: dọn bản ghi của người đã offline (phòng hờ), rồi flush async. */
    private void periodic() {
        try {
            pruneOffline();
            if (flushesInFlight == 0) storage.retainVersions(players.keySet());
            flushDirtyAsync();
        } catch (Exception e) {
            plugin.errors().report("flush-task", e);
        }
    }

    /**
     * Số bản ghi trong RAM phải bằng số người online. Nếu lệch (sự kiện thoát bị lỗi ở plugin khác...),
     * ghi rồi xoá bản ghi của người đã offline và cảnh báo.
     */
    private void pruneOffline() {
        if (players.size() <= Bukkit.getOnlinePlayers().size()) return;
        List<UUID> offline = new ArrayList<>();
        for (UUID uuid : players.keySet()) {
            if (Bukkit.getPlayer(uuid) == null) offline.add(uuid);
        }
        if (offline.isEmpty()) return;
        plugin.getLogger().warning(plugin.messages().log("log.database.pruned", "count", String.valueOf(offline.size())));
        offline.forEach(this::unload);
    }

    /** Chụp bản ghi dirty trên main thread, ghi đĩa ở luồng async. */
    public void flushDirtyAsync() {
        List<Row> rows = new ArrayList<>();
        for (ActiveQuest active : players.values()) {
            if (!active.dirty()) continue;
            rows.add(active.snapshot());
            active.clearDirty();
        }
        if (rows.isEmpty()) return;
        flushesInFlight++;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            try {
                ok = storage.write(rows);
            } catch (Exception e) {
                plugin.errors().report("flush-async", e);
                ok = false;
            }
            if (!plugin.isEnabled()) return;
            boolean written = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                flushesInFlight--;
                if (!written) retryFailed(rows);
            });
        });
    }

    /**
     * Main thread, sau một lần flush async lỗi: người còn online được đánh dấu dirty để lần flush sau ghi lại. Người
     * đã thoát trong lúc đó thì {@link #unload} không ghi gì (bản chụp đã xoá cờ dirty), nên ghi lại bản chụp ngay;
     * mốc phiên bản bảo đảm nó không đè lên bản mới hơn đã ghi lúc thoát.
     */
    void retryFailed(List<Row> rows) {
        List<Row> orphans = new ArrayList<>();
        for (Row row : rows) {
            ActiveQuest active = players.get(row.uuid());
            if (active != null) active.redirtyIf(row.version());
            else orphans.add(row);
        }
        if (orphans.isEmpty() || storage.write(orphans)) return;
        List<String> ids = new ArrayList<>(orphans.size());
        for (Row row : orphans) ids.add(row.uuid().toString());
        plugin.getLogger().severe(plugin.messages().log("log.database.lost",
                "count", String.valueOf(orphans.size()), "uuids", String.join(", ", ids)));
    }

    /** Tắt plugin: ghi toàn bộ, đồng bộ. */
    public void flushAllSync() {
        List<Row> rows = new ArrayList<>(players.size());
        for (ActiveQuest active : players.values()) {
            active.markDirty(); // phiên bản mới nhất, vượt mọi bản chụp async còn dở
            rows.add(active.snapshot());
        }
        if (!rows.isEmpty() && storage.write(rows)) {
            players.values().forEach(ActiveQuest::clearDirty);
        }
    }

    public void startFlushTask() {
        stopFlushTask();
        long period = plugin.settings().flushIntervalSeconds() * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::periodic, period, period);
    }

    public void stopFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }
}
