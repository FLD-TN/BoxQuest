package vn.boxpvp.boxquest.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.config.Settings;
import vn.boxpvp.boxquest.progress.ActiveQuest;

/**
 * Bốn nút tuỳ chỉnh dựng theo cùng một mẫu: tên, trạng thái, giải thích ngắn, dòng hành động.
 * Đặt đối xứng ở hai góc hàng dưới: Tự động nhận lại + Âm thanh bên trái, Bossbar + Thanh tiến độ bên phải.
 * Trạng thái bật được báo bằng màu tên và viền enchant trên nút.
 * Thêm tuỳ chỉnh mới = thêm một dòng ở đây (và khoá tương ứng trong messages.yml / config.yml).
 */
public enum ToggleButton {

    AUTO_REPEAT(45, "auto-repeat", ActiveQuest::autoRepeat, ActiveQuest::setAutoRepeat),
    SOUND(46, "sound", ActiveQuest::sound, ActiveQuest::setSound),
    BOSSBAR(52, "bossbar", ActiveQuest::showBossbar, ActiveQuest::setShowBossbar),
    ACTIONBAR(53, "actionbar", ActiveQuest::showActionbar, ActiveQuest::setShowActionbar);

    private final int slot;
    private final String key;
    private final Predicate<ActiveQuest> getter;
    private final BiConsumer<ActiveQuest, Boolean> setter;

    ToggleButton(int slot, String key, Predicate<ActiveQuest> getter, BiConsumer<ActiveQuest, Boolean> setter) {
        this.slot = slot;
        this.key = key;
        this.getter = getter;
        this.setter = setter;
    }

    public int slot() {
        return slot;
    }

    public static ToggleButton bySlot(int slot) {
        for (ToggleButton b : values()) {
            if (b.slot == slot) return b;
        }
        return null;
    }

    /** Đảo cờ và đánh dirty (không cần ghi ngay). */
    public void flip(ActiveQuest active) {
        setter.accept(active, !getter.test(active));
        active.markDirty();
    }

    public ItemStack build(Messages m, Settings s, ActiveQuest active) {
        boolean on = getter.test(active);
        String state = on ? "on" : "off";
        Component name = Messages.itemLine(m.raw("toggle.color-" + state) + m.raw("toggle." + key + ".name"));
        return IconFactory.item(s.icon(key), name, lore(m, state), on);
    }

    public void render(Inventory inventory, Messages m, Settings s, ActiveQuest active) {
        inventory.setItem(slot, build(m, s, active));
    }

    private List<Component> lore(Messages m, String state) {
        List<Component> lore = new ArrayList<>();
        lore.add(m.line("toggle.status-line", "status", m.raw("toggle.status-" + state)));
        lore.add(Component.empty());
        lore.addAll(m.lines("toggle." + key + ".description"));
        lore.add(Component.empty());
        lore.add(m.line("toggle.action-" + state));
        return lore;
    }
}
