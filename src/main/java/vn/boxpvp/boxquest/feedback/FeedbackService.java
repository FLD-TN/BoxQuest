package vn.boxpvp.boxquest.feedback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.config.Settings;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.util.Format;

/**
 * Phản hồi trong lúc chơi. Nơi DUY NHẤT đọc cờ tuỳ chỉnh để quyết định phản hồi:
 * <ul>
 *   <li>{@code show_actionbar}: thanh tiến độ trên action bar. Mode mining: hiện khi đang đào, ẩn sau
 *       {@code hide-after-seconds} kể từ block cuối; mode always: luôn hiện khi có nhiệm vụ. Khi đang hiện,
 *       cập nhật ngay mỗi block (tối đa một lần mỗi tick) và được gửi lại định kỳ để không tự mờ đi.</li>
 *   <li>{@code sound}: âm thanh khi hoàn thành.</li>
 *   <li>Tin nhắn hoàn thành trong chat: luôn gửi, chỉ người đó thấy.</li>
 * </ul>
 * Bossbar ({@code show_bossbar}) do {@link BossBarService} quản lý; lớp này chỉ báo cho nó khi tiến độ đổi.
 */
public final class FeedbackService {

    private final BoxQuestPlugin plugin;
    /** Mốc phát âm thanh hoàn thành gần nhất theo người chơi, xoá khi thoát. */
    private final Map<UUID, Long> lastSound = new HashMap<>();
    /** Tick gần nhất đã gửi action bar cho người chơi — chặn gửi nhiều lần trong cùng một tick. */
    private final Map<UUID, Integer> lastSentTick = new HashMap<>();
    /** Người có tiến độ đổi sau lần gửi trong tick này: gửi lại số mới nhất một lần ở tick sau. */
    private final Set<UUID> resendNextTick = new HashSet<>();
    private boolean resendScheduled;
    /** mode mining: tick gần nhất người chơi đào được block / vừa nhận nhiệm vụ. Rỗng khi không ai đang đào. */
    private final Map<UUID, Integer> lastActivity = new HashMap<>();
    private BukkitTask keepAliveTask;

    public FeedbackService(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Action bar của Minecraft tự mờ sau khoảng 3 giây. Task này gửi lại thanh tiến độ cho người đang có nhiệm vụ
     * để nó luôn hiện, kể cả khi đứng yên.
     */
    public void startKeepAlive() {
        stopKeepAlive();
        long period = plugin.settings().actionbarRefreshTicks();
        keepAliveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::keepAlive, period, period);
    }

    public void stopKeepAlive() {
        if (keepAliveTask != null) {
            keepAliveTask.cancel();
            keepAliveTask = null;
        }
    }

    private void keepAlive() {
        try {
            if (plugin.settings().actionbarAlways()) {
                for (Player player : Bukkit.getOnlinePlayers()) resend(player);
                return;
            }
            // mode: mining — chỉ những người vừa đào gần đây
            int now = Bukkit.getCurrentTick();
            int hideAfter = plugin.settings().actionbarHideAfterTicks();
            Iterator<Map.Entry<UUID, Integer>> it = lastActivity.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> e = it.next();
                Player player = Bukkit.getPlayer(e.getKey());
                if (player == null) {
                    it.remove();
                } else if (now - e.getValue() >= hideAfter) {
                    it.remove();
                    lastSentTick.remove(e.getKey());
                    player.sendActionBar(Component.empty()); // ngừng đào đủ lâu: ẩn thanh
                } else if (!resend(player)) {
                    it.remove();
                }
            }
        } catch (Exception e) {
            plugin.errors().report("actionbar-task", e);
        }
    }

    /** Gửi lại thanh tiến độ nếu người chơi còn nhiệm vụ và còn bật tuỳ chỉnh. */
    private boolean resend(Player player) {
        ActiveQuest active = plugin.progress().get(player.getUniqueId());
        if (active == null || !active.showActionbar() || active.questId() == null) return false;
        Quest quest = plugin.quests().current(active);
        if (quest == null) return false;
        send(player, quest, active);
        return true;
    }

    /**
     * Hiện thanh tiến độ ngay (đào được block, nhận nhiệm vụ, tự nhận lại, đóng menu) và tính là "đang hoạt động":
     * ở mode mining, thanh sẽ được giữ trong {@code hide-after-seconds} kể từ lần này.
     */
    public void showProgress(Player player, Quest quest, ActiveQuest active) {
        if (!active.showActionbar()) return;
        lastActivity.put(player.getUniqueId(), Bukkit.getCurrentTick());
        send(player, quest, active);
    }

    /** Sau mỗi block được tính (chưa hoàn thành): cập nhật theo thời gian thực. */
    public void onProgress(Player player, Quest quest, ActiveQuest active) {
        showProgress(player, quest, active);
        plugin.bossbars().markDirty(player.getUniqueId());
    }

    /**
     * @param reward số Xu của lần hoàn thành (đã nhân boost) — không gọi lại BoxCore ở đây
     * @param paid   Vault đã cộng {@code reward} thành công ({@code true} cả khi {@code reward} là 0)
     */
    public void onComplete(Player player, Quest quest, ActiveQuest active, double reward, boolean paid) {
        Settings s = plugin.settings();
        Messages m = plugin.messages();

        // Thứ tự trong chat: tin của lệnh thưởng (Gem, đã chạy trước), dòng Xu, rồi tin hoàn thành.
        // Dòng riêng báo số Xu: Vault cộng thẳng nên plugin kinh tế không tự báo. Chỉ khi đã cộng thật; '' để tắt
        String money = Format.money(reward);
        if (paid && reward > 0 && !m.raw("feedback.money").isEmpty()) {
            player.sendMessage(m.text("feedback.money", "money", money, "quest", quest.display()));
        }
        // Chat: luôn gửi, không phụ thuộc tuỳ chỉnh nào
        player.sendMessage(m.text(active.autoRepeat() ? "feedback.complete-repeat" : "feedback.complete-no-repeat",
                "money", money, "quest", quest.display()));

        if (active.sound()) {
            long now = System.currentTimeMillis();
            Long last = lastSound.get(player.getUniqueId());
            if (last == null || now - last >= s.soundCooldownMillis()) {
                player.playSound(player.getLocation(), s.completeSound(), 1f, 1f);
                lastSound.put(player.getUniqueId(), now);
            }
        }

        // Bossbar: về đầu thanh nếu tự nhận lại, ẩn nếu nhiệm vụ đã kết thúc
        plugin.bossbars().sync(player, active);

        if (active.autoRepeat()) {
            // Tự nhận lại: hiện ngay thanh tiến độ của lượt mới (kể cả phần block dư)
            lastSentTick.remove(player.getUniqueId());
            showProgress(player, quest, active);
        } else {
            clear(player);
        }
    }

    /** Hiện hoặc xoá thanh tiến độ theo trạng thái hiện tại (sau khi bật/tắt tuỳ chỉnh, huỷ, đổi nhiệm vụ). */
    public void refresh(Player player, ActiveQuest active) {
        Quest quest = plugin.quests().current(active);
        if (quest != null && active.showActionbar()) {
            lastSentTick.remove(player.getUniqueId());
            showProgress(player, quest, active);
        } else {
            clear(player);
        }
    }

    /** Xoá thanh tiến độ khỏi action bar (hết nhiệm vụ, huỷ, tắt tuỳ chỉnh). */
    public void clear(Player player) {
        lastSentTick.remove(player.getUniqueId());
        lastActivity.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
    }

    /** Tiếng click nút GUI — luôn phát, không phụ thuộc cờ âm thanh. */
    public void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    public void forget(UUID uuid) {
        resendNextTick.remove(uuid);
        lastSound.remove(uuid);
        lastSentTick.remove(uuid);
        lastActivity.remove(uuid);
    }

    /**
     * Gửi action bar, tối đa một lần mỗi tick cho mỗi người (đào 20 block/giây không gửi thừa). Đã gửi trong tick
     * này rồi (nhiều block trong một tick, ví dụ enchant nổ) thì hẹn gửi lại một lần ở tick sau với số mới nhất.
     */
    private void send(Player player, Quest quest, ActiveQuest active) {
        int tick = Bukkit.getCurrentTick();
        Integer last = lastSentTick.put(player.getUniqueId(), tick);
        if (last != null && last == tick) {
            resendNextTick.add(player.getUniqueId());
            if (!resendScheduled) {
                resendScheduled = true;
                Bukkit.getScheduler().runTask(plugin, this::resendPending);
            }
            return;
        }
        player.sendActionBar(progressLine(quest, active));
    }

    /** Tick sau: gửi số mới nhất cho người đã hẹn (còn nhiệm vụ và còn bật thanh tiến độ). */
    private void resendPending() {
        resendScheduled = false;
        try {
            for (UUID uuid : resendNextTick) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) resend(player);
            }
        } catch (Exception e) {
            plugin.errors().report("actionbar-task", e);
        } finally {
            resendNextTick.clear();
        }
    }

    private Component progressLine(Quest quest, ActiveQuest active) {
        Messages m = plugin.messages();
        return m.text("feedback.progress",
                "quest", quest.display(),
                "bar", Format.bar(active.count(), quest.amount(), m),
                "count", Format.number(active.count()),
                "amount", Format.number(quest.amount()),
                "percent", String.valueOf(Format.percent(active.count(), quest.amount())));
    }
}
