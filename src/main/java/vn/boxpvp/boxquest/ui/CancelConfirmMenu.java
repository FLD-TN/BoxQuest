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
 * Xác nhận huỷ (hoặc bỏ nhiệm vụ không còn tồn tại), 27 ô. Hai nút đặt xa nhau (ô 11 và 15);
 * "Quay lại" là mặc định an toàn.
 */
public final class CancelConfirmMenu extends MenuView {

    private static final int CONFIRM_SLOT = 11;
    private static final int INFO_SLOT = 13;
    private static final int BACK_SLOT = 15;

    private final BoxQuestPlugin plugin;
    private final UUID owner;
    private final String questId;
    /** Trang menu {@code /quest} để quay lại. */
    private final int returnPage;

    public CancelConfirmMenu(BoxQuestPlugin plugin, UUID owner, String questId, int returnPage) {
        super(27, plugin.messages().text("cancel-confirm.title"));
        this.plugin = plugin;
        this.owner = owner;
        this.questId = questId;
        this.returnPage = returnPage;
    }

    @Override
    public void render() {
        ActiveQuest active = plugin.progress().get(owner);
        if (active == null) return;
        IconFactory icons = new IconFactory(plugin.messages(), plugin.settings(), owner,
                IconFactory.name(Bukkit.getOfflinePlayer(owner)));
        Quest quest = plugin.registry().get(questId);
        String[] ph = {
                "quest", icons.questName(questId, quest),
                "count", Format.number(active.count())
        };
        drawConfirmFrame(icons);
        inventory.setItem(CONFIRM_SLOT, icons.button("confirm", "cancel-confirm.confirm", ph));
        inventory.setItem(INFO_SLOT, quest != null
                ? icons.button(quest.icon(), "cancel-confirm.info", ph)
                : icons.button("cancel", "cancel-confirm.info", ph));
        inventory.setItem(BACK_SLOT, icons.button("back", "cancel-confirm.back", ph));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot != CONFIRM_SLOT && slot != BACK_SLOT) return;
        ActiveQuest active = plugin.progress().get(owner);
        // Chỉ huỷ nếu người chơi vẫn đang giữ đúng nhiệm vụ lúc mở màn hình này
        if (slot == CONFIRM_SLOT && active != null && questId.equals(active.questId())) {
            plugin.quests().cancel(active);
            plugin.feedback().clear(player);
            plugin.bossbars().hide(player);
        }
        plugin.feedback().click(player);
        plugin.menus().open(player, new QuestMenu(plugin, owner, returnPage));
    }
}
