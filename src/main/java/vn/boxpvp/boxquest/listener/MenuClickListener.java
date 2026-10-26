package vn.boxpvp.boxquest.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.ui.MenuView;

/**
 * Huỷ MỌI thao tác khi kho BoxQuest đang mở: click thường, shift-click, click túi đồ dưới, phím số hotbar,
 * kéo thả. Chỉ chuyển cho màn hình số ô người chơi bấm — không bao giờ đọc tên, lore hay NBT của item.
 */
public final class MenuClickListener implements Listener {

    private final BoxQuestPlugin plugin;

    public MenuClickListener(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof MenuView view)) return;
        event.setCancelled(true); // trước mọi thứ khác, kể cả khi phần sau ném ngoại lệ

        try {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            // Đúng đối tượng kho đã mở cho người này, không chỉ trùng tiêu đề
            if (!plugin.menus().isViewing(player, view)) return;
            if (event.getClickedInventory() != top) return;

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            ClickType click = event.getClick();
            // DOUBLE_CLICK đi kèm sau một cú LEFT — bỏ qua để không bật/tắt hai lần
            if (click == ClickType.DOUBLE_CLICK || !(click.isLeftClick() || click.isRightClick())) return;
            view.onClick(player, slot, click);
        } catch (Exception e) {
            plugin.errors().report("menu-click", e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuView) {
            event.setCancelled(true);
        }
    }

    private void showProgress(Player player) {
        ActiveQuest active = plugin.progress().get(player.getUniqueId());
        if (active == null) return;
        Quest quest = plugin.quests().current(active);
        if (quest != null) plugin.feedback().showProgress(player, quest, active);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        try {
            if (event.getInventory().getHolder(false) instanceof MenuView view
                    && event.getPlayer() instanceof Player player) {
                plugin.menus().closed(player, view);
                // Đóng hẳn menu (không phải chuyển sang màn hình khác): hiện lại thanh tiến độ vừa bị kho che
                if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) showProgress(player);
            }
        } catch (Exception e) {
            plugin.errors().report("menu-close", e);
        }
    }
}
