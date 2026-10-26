package vn.boxpvp.boxquest.reward;

import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.util.Format;

/**
 * Trả Xu qua Vault và chạy lệnh thưởng bằng console. Provider được tìm lại khi cần, vì plugin kinh tế có thể bật
 * sau BoxQuest.
 */
public final class RewardService {

    private final BoxQuestPlugin plugin;
    private Economy economy;

    public RewardService(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hook() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
        if (economy == null) {
            plugin.getLogger().severe(plugin.messages().log("log.reward.no-economy"));
        } else {
            plugin.getLogger().info(plugin.messages().log("log.reward.hooked", "economy", economy.getName()));
        }
        return economy != null;
    }

    public boolean deposit(Player player, double amount) {
        if (amount <= 0) return true;
        if (economy == null && !hook()) {
            plugin.getLogger().severe(plugin.messages().log("log.reward.deposit-no-economy",
                    "money", Format.money(amount), "player", player.getName(), "uuid", player.getUniqueId().toString()));
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().severe(plugin.messages().log("log.reward.deposit-failed",
                    "money", Format.money(amount), "player", player.getName(), "uuid", player.getUniqueId().toString(),
                    "error", String.valueOf(response.errorMessage)));
            return false;
        }
        return true;
    }

    /**
     * Chạy các lệnh {@code rewards.commands} của một lần hoàn thành, bằng console, theo thứ tự trong file.
     * Gọi trên main thread ngay trong tick hoàn thành, SAU khi đã ghi đĩa (A), và luôn chạy dù trả Xu (B) thành công
     * hay không. Không bao giờ ném lỗi: một lệnh lỗi không chặn lệnh sau, phản hồi, hay các lần hoàn thành còn lại.
     *
     * <p><b>Độ tin cậy.</b> {@code dispatchCommand} chỉ cho biết lệnh có được xử lý hay không, KHÔNG cho biết plugin
     * đích có trả thưởng thành công hay không. Trả {@code false} (lệnh không tồn tại, ví dụ BoxCore đang tắt) hoặc
     * ném lỗi thì ghi SEVERE kèm nguyên văn lệnh để admin chạy lại bằng tay. Với {@code boxcore gem give} của
     * BoxCore 1.3.0:
     * <ul>
     *   <li>Người chơi đang online, kể cả khi dữ liệu Gem chưa tải xong: luôn cộng được, BoxCore tự ghi qua database.</li>
     *   <li>Chỉ thất bại khi Gem của BoxCore đang tắt (database lỗi) hoặc số dư sẽ vượt mức tối đa. Khi đó BoxCore tự
     *       in lỗi ra console ({@code gem.disabled}, {@code gem.admin.limit}) nhưng {@code dispatchCommand} vẫn trả
     *       {@code true}, nên BoxQuest không biết được. Admin theo dõi console của BoxCore.</li>
     *   <li>Mỗi lệnh thành công, BoxCore in một dòng {@code ✔ Đã cộng ...} ra console: nhật ký đối soát, không phải
     *       lỗi. Người chơi nhận tin {@code gem.received} của BoxCore, trừ khi lệnh có {@code -s}.</li>
     * </ul>
     *
     * @param tenths hệ số boost {@code quest} (phần mười, {@code 15} = x1.5) đã dùng cho Xu của CÙNG lần hoàn thành
     *               này (không đọc lại BoxCore)
     */
    public void runCommands(Player player, Quest quest, int tenths) {
        if (quest.commandRewards().isEmpty()) return;
        runCommands(quest, player.getName(), player.getUniqueId().toString(), tenths,
                command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command),
                plugin.getLogger(), plugin.messages());
    }

    /**
     * Lõi của {@link #runCommands(Player, Quest, int)}, tách khỏi Bukkit để test.
     *
     * @param dispatch chạy một lệnh đã thay placeholder; {@code false} nếu lệnh không được xử lý
     */
    static void runCommands(Quest quest, String name, String uuid, int tenths, Predicate<String> dispatch,
            Logger log, Messages m) {
        for (CommandReward reward : quest.commandRewards()) {
            CommandTemplate template = reward.command();
            String command = template.source();
            try {
                if (template.overflows(tenths)) {
                    log.warning(m.logVerbatim("log.reward.boost-overflow", "command", command,
                            "quest", quest.id(), "player", name, "multiplier", Format.multiplier(tenths),
                            "max", String.valueOf(Long.MAX_VALUE)));
                }
                command = template.resolve(name, uuid, quest.id(), tenths, false);
                if (!dispatch.test(command)) {
                    commandFailed(log, m, quest, name, uuid, command, m.log("log.reward.command-rejected"), null);
                }
            } catch (Throwable t) {
                commandFailed(log, m, quest, name, uuid, command, String.valueOf(t), t);
            }
        }
    }

    /** Không giới hạn tần suất: mỗi dòng là một phần thưởng bị thiếu cần đền. */
    private static void commandFailed(Logger log, Messages m, Quest quest, String name, String uuid, String command,
            String error, Throwable cause) {
        try {
            log.log(Level.SEVERE, m.logVerbatim("log.reward.command-failed", "command", command,
                    "quest", quest.id(), "player", name, "uuid", uuid, "error", error), cause);
        } catch (Throwable ignored) {
            // Ghi log cũng lỗi: không được làm hỏng vòng trả thưởng
        }
    }
}
