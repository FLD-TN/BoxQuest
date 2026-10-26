package vn.boxpvp.boxquest.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import vn.boxpvp.boxquest.BoxQuestPlugin;

/**
 * Danh sách người đang mở menu, kèm đúng đối tượng kho BoxQuest đã mở cho họ.
 * Xoá khi đóng kho và khi thoát server. Task cập nhật chỉ tồn tại khi danh sách không rỗng.
 */
public final class MenuManager {

    private static final long REFRESH_TICKS = 10L;

    private final BoxQuestPlugin plugin;
    private final Map<UUID, MenuView> viewers = new HashMap<>();
    private BukkitTask refreshTask;

    public MenuManager(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    /** Mở màn hình ở tick kế tiếp (mở kho ngay trong InventoryClickEvent là không an toàn). */
    public void open(Player player, MenuView view) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline()) return;
                view.render();
                player.openInventory(view.getInventory());
                // Plugin khác có thể huỷ InventoryOpenEvent
                if (player.getOpenInventory().getTopInventory() != view.getInventory()) return;
                viewers.put(player.getUniqueId(), view);
                startTask();
            } catch (Exception e) {
                plugin.errors().report("menu-open", e);
            }
        });
    }

    /**
     * Đóng {@code view} ở tick kế tiếp (cùng lý do với {@link #open}), nếu người chơi vẫn đang mở đúng nó: không đóng
     * nhầm kho khác vừa mở trong tick đó. Sự kiện đóng kho tự xoá người xem.
     */
    public void close(Player player, MenuView view) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == view.getInventory()) {
                player.closeInventory();
            }
        });
    }

    /**
     * Người chơi đang mở ĐÚNG màn hình này: khớp với đối tượng đã đăng ký cho họ và với kho đang mở
     * (so sánh đối tượng, không so tiêu đề).
     */
    public boolean isViewing(Player player, MenuView view) {
        return viewers.get(player.getUniqueId()) == view
                && player.getOpenInventory().getTopInventory() == view.getInventory();
    }

    /** InventoryCloseEvent: chỉ xoá nếu đúng màn hình đang đăng ký (đổi màn hình cũng sinh sự kiện đóng). */
    public void closed(Player player, MenuView view) {
        viewers.remove(player.getUniqueId(), view);
        stopIfIdle();
    }

    public void forget(UUID uuid) {
        viewers.remove(uuid);
        stopIfIdle();
    }

    public void closeAll() {
        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.closeInventory();
        }
        viewers.clear();
        stopIfIdle();
    }

    public int viewerCount() {
        return viewers.size();
    }

    private void startTask() {
        if (refreshTask != null) return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    private void tick() {
        // Dọn phòng hờ trường hợp sự kiện đóng kho không đến (người đã thoát hoặc đã đổi sang kho khác)
        viewers.entrySet().removeIf(e -> {
            Player player = Bukkit.getPlayer(e.getKey());
            return player == null || player.getOpenInventory().getTopInventory() != e.getValue().getInventory();
        });
        if (viewers.isEmpty()) {
            stopIfIdle();
            return;
        }
        for (MenuView view : new ArrayList<>(viewers.values())) {
            try {
                view.refresh();
            } catch (Exception e) {
                plugin.errors().report("menu-refresh", e);
            }
        }
    }

    private void stopIfIdle() {
        if (viewers.isEmpty() && refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
}
