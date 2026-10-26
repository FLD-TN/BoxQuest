package vn.boxpvp.boxquest.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.quest.QuestRegistry;
import vn.boxpvp.boxquest.ui.AcceptConfirmMenu;
import vn.boxpvp.boxquest.ui.QuestMenu;

/**
 * {@code /quest}, {@code /nv}, {@code /nhiemvu} mở menu. Lệnh quản trị:
 * <ul>
 *   <li>{@code /quest reload} ({@code boxquest.reload})</li>
 *   <li>{@code /quest offer <player> <quest>} ({@code boxquest.assign}): mở màn hình xác nhận nhận nhiệm vụ cho
 *       người chơi, dùng cho NPC (chạy bằng console). Đang giữ nhiệm vụ khác thì báo vào chat người chơi.
 *       Mỗi người chơi có cooldown {@code offer.cooldown-seconds}.</li>
 *   <li>{@code /quest assign <player> <quest>} ({@code boxquest.assign}): gán thẳng, không hỏi. Đang giữ nhiệm vụ
 *       khác thì không gán và báo cho cả hai bên.</li>
 * </ul>
 */
public final class QuestCommand implements TabExecutor {

    private static final String USE = "boxquest.use";
    private static final String RELOAD = "boxquest.reload";
    private static final String ASSIGN = "boxquest.assign";

    private final BoxQuestPlugin plugin;
    /** {@code /quest offer}: mốc hết cooldown theo người chơi. Mục đã hết hạn được dọn mỗi lần gọi. */
    private final Map<UUID, Long> offerCooldowns = new HashMap<>();

    public QuestCommand(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "reload" -> {
                    reload(sender);
                    return true;
                }
                case "offer", "assign" -> {
                    give(sender, label, sub, args);
                    return true;
                }
                default -> {
                    // Tham số lạ: người chơi thường vẫn mở menu như trước
                }
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().text("command.player-only"));
            return true;
        }
        if (!player.hasPermission(USE)) {
            player.sendMessage(plugin.messages().text("command.no-permission"));
            return true;
        }
        ActiveQuest active = loadFor(player);
        if (active == null) {
            player.sendMessage(plugin.messages().text("command.not-loaded"));
            return true;
        }
        plugin.menus().open(player, new QuestMenu(plugin, player.getUniqueId()));
        return true;
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD)) {
            sender.sendMessage(plugin.messages().text("command.no-permission"));
            return;
        }
        QuestRegistry.LoadResult result = plugin.reload();
        // messages.yml vừa được nạp lại
        sender.sendMessage(result.failed()
                ? plugin.messages().text("command.reload-failed")
                : plugin.messages().text("command.reloaded",
                        "loaded", String.valueOf(result.loaded()),
                        "skipped", String.valueOf(result.skipped())));
    }

    /** {@code offer} và {@code assign}: cùng phần kiểm tra, khác ở bước cuối. */
    private void give(CommandSender sender, String label, String sub, String[] args) {
        if (!sender.hasPermission(ASSIGN)) {
            sender.sendMessage(plugin.messages().text("command.no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.messages().text("command.usage-" + sub, "label", label));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.messages().text("command.player-not-found", "player", args[1]));
            return;
        }
        Quest quest = plugin.registry().get(args[2]);
        if (quest == null) {
            sender.sendMessage(plugin.messages().text("command.quest-not-found", "id", args[2]));
            return;
        }
        ActiveQuest active = loadFor(target);
        if (active == null) {
            sender.sendMessage(plugin.messages().text("command.target-not-loaded", "player", target.getName()));
            return;
        }

        if (sub.equals("offer")) {
            // Bấm NPC liên tục: bỏ qua im lặng, không mở lại màn hình hay báo lỗi lặp
            if (onOfferCooldown(target.getUniqueId())) return;
            // Lý do không nhận được đã báo vào chat người chơi; console (NPC) không cần thêm log
            if (plugin.quests().canAccept(target, active, quest)) {
                plugin.menus().open(target, AcceptConfirmMenu.offer(plugin, target.getUniqueId(), quest.id()));
            }
            return;
        }
        if (!plugin.quests().canAccept(target, active, quest)) {
            if (sender != target) {
                sender.sendMessage(plugin.messages().text("command.assign-busy", "player", target.getName()));
            }
            return;
        }
        plugin.quests().accept(target, active, quest);
        if (sender != target) {
            sender.sendMessage(plugin.messages().text("command.assigned",
                    "player", target.getName(), "quest", quest.display()));
        }
    }

    /** {@code true} nếu người chơi vừa được offer trong khoảng cooldown; không thì bắt đầu cooldown mới. */
    private boolean onOfferCooldown(UUID player) {
        long cooldown = plugin.settings().offerCooldownMillis();
        if (cooldown <= 0) return false;
        long now = System.currentTimeMillis();
        offerCooldowns.values().removeIf(until -> until <= now);
        if (offerCooldowns.containsKey(player)) return true;
        offerCooldowns.put(player, now + cooldown);
        return false;
    }

    /**
     * Dữ liệu nhiệm vụ của người chơi, nạp lại nếu lúc vào server nạp lỗi; {@code null} nếu vẫn chưa nạp được.
     * Vừa nạp được thì hiện bossbar luôn.
     */
    private ActiveQuest loadFor(Player player) {
        boolean wasLoaded = plugin.progress().get(player.getUniqueId()) != null;
        ActiveQuest active = plugin.progress().load(player.getUniqueId());
        if (active != null && !wasLoaded) plugin.bossbars().sync(player, active);
        return active;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(RELOAD)) options.add("reload");
            if (sender.hasPermission(ASSIGN)) {
                options.add("offer");
                options.add("assign");
            }
        } else if (sender.hasPermission(ASSIGN) && isGive(args[0])) {
            if (args.length == 2) {
                for (Player player : Bukkit.getOnlinePlayers()) options.add(player.getName());
            } else if (args.length == 3) {
                for (Quest quest : plugin.registry().list()) options.add(quest.id());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }

    private static boolean isGive(String sub) {
        return sub.equalsIgnoreCase("offer") || sub.equalsIgnoreCase("assign");
    }
}
