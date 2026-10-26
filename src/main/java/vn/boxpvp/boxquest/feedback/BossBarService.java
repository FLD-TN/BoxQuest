package vn.boxpvp.boxquest.feedback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.config.Settings;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.util.Format;

/**
 * Bossbar tiến độ: luôn hiện khi người chơi có nhiệm vụ đang làm và bật tuỳ chỉnh {@code show_bossbar}, kể cả lúc
 * không đào, để nhắc họ đang làm nhiệm vụ nào. Khác action bar, Minecraft giữ bossbar cho tới khi bị ẩn nên không cần
 * gửi lại định kỳ.
 *
 * <p>Đường nóng (mỗi block): chỉ thêm UUID vào {@link #pending}; task mỗi {@code bossbar.update-ticks} mới dựng lại
 * tiêu đề, và bỏ qua nếu tiến độ không đổi. Sự kiện đổi trạng thái (nhận, huỷ, hoàn thành, bật/tắt, vào server)
 * gọi {@link #sync} để cập nhật ngay. Không giữ tham chiếu {@link Player}; xoá khi thoát. Chỉ dùng trên main thread.
 */
public final class BossBarService {

    /** Bossbar đang hiện cho một người chơi, kèm giá trị đã hiển thị để bỏ qua cập nhật thừa. */
    private static final class Shown {
        final BossBar bar;
        String questId;
        int count = -1;
        int amount = -1;

        Shown(BossBar bar) {
            this.bar = bar;
        }
    }

    private final BoxQuestPlugin plugin;
    private final Map<UUID, Shown> bars = new HashMap<>();
    /** Người có tiến độ đổi từ lần cập nhật trước, chờ task xử lý. */
    private final Set<UUID> pending = new HashSet<>();
    private BukkitTask task;

    public BossBarService(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long period = plugin.settings().bossbarUpdateTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::flushPending, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Sau mỗi block được tính: chỉ đánh dấu, task sẽ cập nhật. */
    public void markDirty(UUID uuid) {
        if (bars.containsKey(uuid)) pending.add(uuid);
    }

    /** Hiện, cập nhật hoặc ẩn bossbar theo trạng thái hiện tại, ngay lập tức. */
    public void sync(Player player, ActiveQuest active) {
        UUID uuid = player.getUniqueId();
        pending.remove(uuid);
        Quest quest = active == null || !active.showBossbar() ? null : plugin.quests().current(active);
        if (quest == null) {
            hide(player);
            return;
        }
        Shown shown = bars.get(uuid);
        if (shown == null) {
            Settings s = plugin.settings();
            shown = new Shown(BossBar.bossBar(Component.empty(), 0f,
                    s.bossbarColor(), s.bossbarOverlay()));
            bars.put(uuid, shown);
            update(shown, quest, active);
            player.showBossBar(shown.bar);
        } else {
            update(shown, quest, active);
        }
    }

    /** Ẩn và quên bossbar của người chơi (huỷ nhiệm vụ, tắt tuỳ chỉnh). */
    public void hide(Player player) {
        Shown shown = bars.remove(player.getUniqueId());
        pending.remove(player.getUniqueId());
        if (shown != null) player.hideBossBar(shown.bar);
    }

    /** Thoát server: chỉ xoá khỏi RAM (client tự bỏ bossbar khi ngắt kết nối). */
    public void forget(UUID uuid) {
        bars.remove(uuid);
        pending.remove(uuid);
    }

    /** Ẩn mọi bossbar (tắt plugin, reload). Thiếu bước này bossbar sẽ kẹt trên màn hình người chơi. */
    public void hideAll() {
        for (Map.Entry<UUID, Shown> e : bars.entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player != null) player.hideBossBar(e.getValue().bar);
        }
        bars.clear();
        pending.clear();
    }

    /** Dựng lại toàn bộ cho người đang online: sau reload (màu, kiểu thanh, tiêu đề, nhiệm vụ có thể đã đổi). */
    public void syncAll() {
        hideAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sync(player, plugin.progress().get(player.getUniqueId()));
        }
    }

    private void flushPending() {
        try {
            if (pending.isEmpty()) return;
            UUID[] uuids = pending.toArray(new UUID[0]);
            pending.clear();
            for (UUID uuid : uuids) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    forget(uuid);
                } else {
                    sync(player, plugin.progress().get(uuid));
                }
            }
        } catch (Exception e) {
            plugin.errors().report("bossbar-task", e);
        }
    }

    /** Chỉ dựng lại tiêu đề khi nhiệm vụ hoặc tiến độ đổi. */
    private void update(Shown shown, Quest quest, ActiveQuest active) {
        int count = active.count();
        if (count == shown.count && quest.amount() == shown.amount && Objects.equals(quest.id(), shown.questId)) return;
        shown.questId = quest.id();
        shown.count = count;
        shown.amount = quest.amount();
        Messages m = plugin.messages();
        shown.bar.name(m.text("bossbar.title",
                "quest", quest.display(),
                "count", Format.number(count),
                "amount", Format.number(quest.amount()),
                "percent", String.valueOf(Format.percent(count, quest.amount())),
                "completions", Format.number(active.completions())));
        shown.bar.progress(progress(count, quest.amount()));
    }

    /** Tỉ lệ cho thanh bossbar, luôn trong [0, 1] (Adventure ném lỗi nếu ra ngoài khoảng này). */
    static float progress(int count, int amount) {
        if (amount <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) count / amount));
    }
}
