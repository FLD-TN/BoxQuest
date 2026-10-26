package vn.boxpvp.boxquest.ui;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.util.Format;

/**
 * Xác nhận nhận nhiệm vụ, 27 ô. Cùng bố cục với màn hình huỷ: ✔ ô 11, thông tin ô 13, ✘ ô 15.
 * Mở từ menu {@code /quest} thì bấm xong quay lại đúng trang đó; mở bằng {@code /quest offer} (NPC) thì đóng lại.
 */
public final class AcceptConfirmMenu extends MenuView {

    private static final int CONFIRM_SLOT = 11;
    private static final int INFO_SLOT = 13;
    private static final int BACK_SLOT = 15;

    private final BoxQuestPlugin plugin;
    private final UUID owner;
    private final String questId;
    /** Trang menu {@code /quest} để quay lại, hoặc {@code null}: đóng màn hình (mở từ NPC). */
    private final Integer returnPage;

    private AcceptConfirmMenu(BoxQuestPlugin plugin, UUID owner, String questId, Integer returnPage) {
        super(27, plugin.messages().text("accept-confirm.title"));
        this.plugin = plugin;
        this.owner = owner;
        this.questId = questId;
        this.returnPage = returnPage;
    }

    /** Từ menu {@code /quest}: bấm xong quay lại trang {@code page}. */
    public static AcceptConfirmMenu fromMenu(BoxQuestPlugin plugin, UUID owner, String questId, int page) {
        return new AcceptConfirmMenu(plugin, owner, questId, page);
    }

    /** Từ {@code /quest offer}: bấm xong đóng màn hình. */
    public static AcceptConfirmMenu offer(BoxQuestPlugin plugin, UUID owner, String questId) {
        return new AcceptConfirmMenu(plugin, owner, questId, null);
    }

    @Override
    public void render() {
        Quest quest = plugin.registry().get(questId);
        if (quest == null) return;
        String[] ph = {
                "quest", quest.display(),
                "amount", Format.number(quest.amount()),
                "money", Format.money(quest.rewardMoney())
        };
        IconFactory icons = new IconFactory(plugin.messages(), plugin.settings(), owner,
                IconFactory.name(Bukkit.getOfflinePlayer(owner)));
        drawConfirmFrame(icons);
        inventory.setItem(CONFIRM_SLOT, icons.button("confirm", "accept-confirm.confirm", ph));
        inventory.setItem(INFO_SLOT, icons.button(quest.icon(), "accept-confirm.info", icons.rewardLists(quest), ph));
        inventory.setItem(BACK_SLOT, icons.button("back", returnPage != null
                ? "accept-confirm.back" : "accept-confirm.close", ph));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot != CONFIRM_SLOT && slot != BACK_SLOT) return;
        ActiveQuest active = plugin.progress().get(owner);
        Quest quest = plugin.registry().get(questId);
        // Kiểm tra lại lúc bấm: người chơi có thể đã nhận nhiệm vụ khác trong lúc màn hình này đang mở
        if (slot == CONFIRM_SLOT && active != null && quest != null && plugin.quests().canAccept(player, active, quest)) {
            plugin.quests().accept(player, active, quest);
        }
        plugin.feedback().click(player);
        if (returnPage != null) {
            plugin.menus().open(player, new QuestMenu(plugin, owner, returnPage));
        } else {
            plugin.menus().close(player, this);
        }
    }
}
