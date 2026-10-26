package vn.boxpvp.boxquest.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Lớp nền của mọi màn hình. Kho được tạo một lần; mọi cập nhật dùng {@code inventory.setItem()},
 * không mở lại kho (mở lại làm con trỏ chuột nhảy về giữa).
 */
public abstract class MenuView implements InventoryHolder {

    protected final Inventory inventory;

    protected MenuView(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Khung chung của màn hình xác nhận 27 ô: viền quanh, kính trang trí ở hàng giữa.
     * <pre>
     *  B  B  B  B  B  B  B  B  B
     *  B  F [✔] F [i] F [✘] F  B      ✔ = ô 11, i = ô 13 (thông tin), ✘ = ô 15
     *  B  B  B  B  B  B  B  B  B
     * </pre>
     */
    protected void drawConfirmFrame(IconFactory icons) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            boolean edge = slot < 9 || slot >= 18 || slot == 9 || slot == 17;
            inventory.setItem(slot, edge ? icons.border() : icons.filler());
        }
    }

    /** Vẽ toàn bộ kho. */
    public abstract void render();

    /** Gọi định kỳ khi đang có người xem; mặc định không làm gì. */
    public void refresh() {
    }

    /** Bấm vào một ô của kho trên (sự kiện đã bị huỷ sẵn). */
    public abstract void onClick(Player player, int slot, ClickType click);
}
