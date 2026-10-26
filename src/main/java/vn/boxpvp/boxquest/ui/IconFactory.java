package vn.boxpvp.boxquest.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.config.Settings;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.reward.CommandReward;
import vn.boxpvp.boxquest.util.Format;

/** Dựng icon theo trạng thái, dùng chung cho cả ba màn hình. */
public final class IconFactory {

    private final Messages messages;
    private final Settings settings;
    /** Người đang xem menu: {@code {player}} và {@code {uuid}} trong {@code display} của phần thưởng lệnh. */
    private final String viewerName;
    private final String viewerUuid;

    public IconFactory(Messages messages, Settings settings, UUID viewer, String viewerName) {
        this.messages = messages;
        this.settings = settings;
        this.viewerName = viewerName;
        this.viewerUuid = viewer.toString();
    }

    /** Tên người chơi (có thể đang offline), chuỗi rỗng nếu server chưa biết tên. */
    public static String name(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : "";
    }

    public static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        if (glint) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** Ô kính trang trí: không tên, không tooltip khi rê chuột. */
    public static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack border() {
        return pane(settings.border());
    }

    public ItemStack filler() {
        return pane(settings.filler());
    }

    /** Placeholder chung về tiến độ của một quest. */
    public String[] progress(Quest quest, ActiveQuest active) {
        return new String[] {
                "quest", quest.display(),
                "bar", Format.bar(active.count(), quest.amount(), messages),
                "percent", String.valueOf(Format.percent(active.count(), quest.amount())),
                "count", Format.number(active.count()),
                "amount", Format.number(quest.amount()),
                "money", Format.money(quest.rewardMoney()),
                "completions", Format.number(active.completions())
        };
    }

    /** Đầu của người chơi, chưa có tên và lore. Dựng một lần cho mỗi menu rồi cập nhật bằng {@link #updateHead}. */
    public static ItemStack playerHead(OfflinePlayer player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.addItemFlags(ItemFlag.values());
            item.setItemMeta(skull);
        }
        return item;
    }

    /**
     * Ghi tên và lore cho đầu người chơi ở giữa hàng trên cùng: nhiệm vụ đang giữ, số lần hoàn thành và các quy tắc
     * chung. Nhiệm vụ đang giữ đã bị xoá khỏi quests.yml ({@code current == null} mà vẫn có id) thì thêm dòng
     * hướng dẫn huỷ. Chỉ đổi tên và lore, giữ nguyên chủ đầu.
     */
    public void updateHead(ItemStack head, ActiveQuest active, Quest current) {
        String status;
        if (active.questId() == null) {
            status = messages.raw("menu.head.status-none");
        } else if (current == null) {
            status = messages.raw("menu.head.status-missing", "id", active.questId());
        } else {
            status = messages.raw("menu.head.status-active", progress(current, active));
        }
        String[] ph = {
                "player", viewerName,
                "status", status,
                "completions", Format.number(active.completions())
        };
        List<Component> lore = new ArrayList<>(messages.lines("menu.head.lore", ph));
        if (active.questId() != null && current == null) lore.addAll(messages.lines("menu.head.missing-lore", ph));

        ItemMeta meta = head.getItemMeta();
        meta.displayName(messages.line("menu.head.name", ph));
        meta.lore(lore);
        head.setItemMeta(meta);
    }

    /** Nút chuyển trang; {@code target} là số trang (đếm từ 1) sẽ mở khi bấm. */
    public ItemStack pageButton(String key, int target) {
        String[] ph = {"page", String.valueOf(target)};
        return item(settings.icon(key), messages.line("menu.page." + key + ".name", ph),
                messages.lines("menu.page." + key + ".lore", ph), false);
    }

    /** Ô số trang ở giữa hàng dưới; {@code page} đếm từ 1. */
    public ItemStack pageInfo(int page, int pages, int total) {
        String[] ph = {"page", String.valueOf(page), "pages", String.valueOf(pages), "total", String.valueOf(total)};
        return item(settings.icon("page"), messages.line("menu.page.info.name", ph),
                messages.lines("menu.page.info.lore", ph), false);
    }

    /**
     * Danh sách phần thưởng cho lore, theo thứ tự mở rộng: {@code reward} (các {@code display} của phần thưởng lệnh,
     * đã thay placeholder ở chế độ menu) rồi {@code money} (một phần tử nếu Xu &gt; 0, rỗng thì dòng Xu biến mất).
     */
    public Map<String, List<String>> rewardLists(Quest quest) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("reward", rewardLines(quest));
        lists.put("money", quest.rewardMoney() > 0 ? List.of(Format.money(quest.rewardMoney())) : List.of());
        return lists;
    }

    /**
     * {@code display} của các phần thưởng lệnh, thay placeholder TRƯỚC khi đưa vào lore: {@code {boost:N}} hiện N
     * (chưa nhân, giống Xu), {@code {multiplier}} là 1.
     */
    private List<String> rewardLines(Quest quest) {
        List<CommandReward> rewards = quest.commandRewards();
        if (rewards.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(rewards.size());
        for (CommandReward reward : rewards) {
            if (reward.display() != null) out.add(reward.display().resolve(viewerName, viewerUuid, quest.id(), 1, true));
        }
        return out;
    }

    /**
     * Icon quest trong danh sách. Chưa nhận nhiệm vụ nào: đúng block của mine. Đang làm: block của mine + viền
     * enchant. Các nhiệm vụ còn lại khi đang bận: icon khoá ({@code gui.icons.locked}).
     */
    public ItemStack quest(Quest quest, ActiveQuest active, Quest current) {
        Component name = Messages.itemLine(quest.display());
        if (current == null) {
            return item(quest.icon(), name, questLore("available", quest, 0), false);
        }
        if (current.id().equals(quest.id())) {
            return item(quest.icon(), name, questLore("active", quest, active.count()), true);
        }
        return item(settings.icon("locked"), name, questLore("locked", quest, 0), false);
    }

    /** Lore nhiệm vụ trong danh sách, có mô tả nhiều dòng, tiến độ (0 nếu chưa nhận) và phần thưởng. */
    private List<Component> questLore(String state, Quest quest, int count) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("description", quest.description());
        lists.putAll(rewardLists(quest));
        return messages.lines("menu.quest." + state + ".lore", lists,
                "quest", quest.display(),
                "count", Format.number(count),
                "amount", Format.number(quest.amount()),
                "bar", Format.bar(count, quest.amount(), messages),
                "percent", String.valueOf(Format.percent(count, quest.amount())),
                "money", Format.money(quest.rewardMoney()));
    }

    /** Icon quest khi người chơi còn giữ nhiệm vụ không còn tồn tại. */
    public ItemStack blockedQuest(Quest quest) {
        return item(settings.icon("locked"), Messages.itemLine(quest.display()), questLore("blocked", quest, 0), false);
    }

    /** Tên hiển thị của nhiệm vụ đang giữ, kể cả khi nó không còn trong config. */
    public String questName(String questId, Quest quest) {
        return quest != null ? quest.display() : messages.raw("menu.missing-quest-name", "id", questId);
    }

    /** Nút trong màn hình xác nhận; {@code iconKey} là khoá dưới {@code gui.icons}. */
    public ItemStack button(String iconKey, String path, String... placeholders) {
        return button(settings.icon(iconKey), path, placeholders);
    }

    public ItemStack button(Material material, String path, String... placeholders) {
        return item(material, messages.line(path + ".name", placeholders),
                messages.lines(path + ".lore", placeholders), false);
    }

    /** Như {@link #button(Material, String, String...)}, lore có khoá danh sách (xem {@link Messages#lines(String, Map, String...)}). */
    public ItemStack button(Material material, String path, Map<String, List<String>> lists, String... placeholders) {
        return item(material, messages.line(path + ".name", placeholders),
                messages.lines(path + ".lore", lists, placeholders), false);
    }
}
