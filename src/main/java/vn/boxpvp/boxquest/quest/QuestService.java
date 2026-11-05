package vn.boxpvp.boxquest.quest;

import org.bukkit.entity.Player;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.util.ErrorLog;
import vn.boxpvp.boxquest.util.Format;

/** Thay đổi trạng thái nhiệm vụ của người chơi: cộng tiến độ, hoàn thành, nhận / huỷ / đổi. */
public final class QuestService {

    private final BoxQuestPlugin plugin;
    /** Lần cuối ghi log save-failed (main thread). */
    private long lastSaveFailedLog;

    public QuestService(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    /** Quest đang làm, hoặc {@code null} nếu chưa nhận hay quest đã bị gỡ khỏi quests.yml. */
    public Quest current(ActiveQuest active) {
        return plugin.registry().get(active.questId());
    }

    /**
     * Cộng một block. Chỉ thao tác RAM, trừ khi hoàn thành.
     * <ul>
     *   <li>{@code count -= amount} (không đặt về 0): giữ phần block vượt.</li>
     *   <li>{@code while} (không {@code if}): trả đủ số lần khi vượt nhiều lần {@code amount}.</li>
     *   <li>Ghi đĩa (A) TRƯỚC khi trả Xu (B) và chạy lệnh thưởng (C): sập giữa chừng thì mất một lần thưởng, không
     *       bao giờ trả hai lần. (A) thất bại thì không trả gì cả.</li>
     *   <li>(C) luôn chạy dù (B) thất bại: Xu và lệnh là hai phần thưởng độc lập. (C) không bao giờ ném lỗi.</li>
     * </ul>
     */
    public void increment(Player player, ActiveQuest active, Quest quest) {
        active.setCount(active.count() + 1);
        active.markDirty();

        boolean completed = false;
        while (active.count() >= quest.amount()) {
            completed = true;
            // trạng thái trước lần hoàn thành này, để khôi phục nếu ghi (A) thất bại
            String questBefore = active.questId();
            int countBefore = active.count();
            int completionsBefore = active.completions();
            double moneyBefore = active.totalMoney();
            active.setCount(active.count() - quest.amount());
            active.setCompletions(active.completions() + 1);
            // Hệ số boost đọc MỘT lần cho lần hoàn thành này; cùng một hệ số dùng cho tổng, log, Xu, lệnh và tin nhắn
            int tenths = plugin.questMultiplierTenths(player.getUniqueId());
            double reward = boostedMoney(quest.rewardMoney(), tenths);
            active.setTotalMoney(active.totalMoney() + reward);
            if (!active.autoRepeat()) {
                // Kết thúc nhiệm vụ trong cùng lần ghi (A), vòng lặp dừng vì count = 0.
                active.setQuestId(null);
                active.setCount(0);
            }

            if (!plugin.progress().saveNow(active)) { // (A)
                // Không để RAM giữ một lần hoàn thành chưa trả thưởng (lần flush sau sẽ ghi nó). count vẫn >= amount
                // nên block kế tiếp sẽ thử hoàn thành lại; vẫn dirty để tiến độ đào được lưu.
                active.setQuestId(questBefore);
                active.setCount(countBefore);
                active.setCompletions(completionsBefore);
                active.setTotalMoney(moneyBefore);
                active.markDirty();
                logSaveFailed(quest, player, reward);
                return;
            }
            boolean paid = false;
            try {
                paid = plugin.rewards().deposit(player, reward); // (B)
            } finally {
                // (C) kể cả khi (B) trả false hay ném lỗi (lỗi của (B) vẫn lan ra như 0.1.0)
                plugin.rewards().runCommands(player, quest, tenths);
            }
            plugin.feedback().onComplete(player, quest, active, reward, paid);
            // 0.3.2: báo cho plugin khác (BoxPet); lỗi của plugin nghe không được làm hỏng việc đếm
            try {
                org.bukkit.Bukkit.getPluginManager().callEvent(new vn.boxpvp.boxquest.api.QuestCompleteEvent(
                        player, quest.id(), active.completions(), reward));
            } catch (Exception e) {
                plugin.errors().report("quest-complete-event", e);
            }
        }
        if (!completed) plugin.feedback().onProgress(player, quest, active);
    }

    /** Database hỏng thì mỗi block đều thử lại: tối đa một dòng mỗi {@link ErrorLog#COOLDOWN_MILLIS}. */
    private void logSaveFailed(Quest quest, Player player, double reward) {
        long now = System.currentTimeMillis();
        if (lastSaveFailedLog != 0 && now - lastSaveFailedLog < ErrorLog.COOLDOWN_MILLIS) return;
        lastSaveFailedLog = now;
        plugin.getLogger().severe(plugin.messages().log("log.reward.save-failed",
                "quest", quest.id(), "player", player.getName(), "money", Format.money(reward)));
    }

    /**
     * Xu thưởng đã nhân hệ số ({@code tenths} theo phần mười): nhân trước, chia sau, làm tròn 2 chữ số thập phân.
     * Không boost (x1) thì giữ nguyên số trong quests.yml.
     */
    static double boostedMoney(double money, int tenths) {
        if (tenths <= 10) return money;
        return Math.round(money * tenths / 10.0 * 100.0) / 100.0;
    }

    /**
     * Người chơi còn trống để nhận {@code quest}. Không thì báo lý do vào chat người chơi: đang làm đúng nhiệm vụ
     * này, hoặc đang giữ nhiệm vụ khác (kể cả nhiệm vụ đã bị xoá khỏi quests.yml).
     */
    public boolean canAccept(Player player, ActiveQuest active, Quest quest) {
        String held = active.questId();
        if (held == null) return true;
        if (held.equals(quest.id())) {
            player.sendMessage(plugin.messages().text("feedback.accept-same", "quest", quest.display()));
        } else {
            Quest current = plugin.registry().get(held);
            String name = current != null ? current.display()
                    : plugin.messages().raw("menu.missing-quest-name", "id", held);
            player.sendMessage(plugin.messages().text("feedback.accept-busy", "quest", name, "target", quest.display()));
        }
        return false;
    }

    /** Nhận nhiệm vụ: báo trong chat và hiện ngay thanh tiến độ trên action bar. */
    public void accept(Player player, ActiveQuest active, Quest quest) {
        active.setQuestId(quest.id());
        active.setCount(0);
        active.markDirty();
        player.sendMessage(plugin.messages().text("feedback.accept", "quest", quest.display(),
                "amount", Format.number(quest.amount())));
        plugin.feedback().showProgress(player, quest, active);
        plugin.bossbars().sync(player, active);
    }

    public void cancel(ActiveQuest active) {
        active.setQuestId(null);
        active.setCount(0);
        active.markDirty();
    }
}
