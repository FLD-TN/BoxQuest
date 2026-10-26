package vn.boxpvp.boxquest.quest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.mine.MineIndex;
import vn.boxpvp.boxquest.quest.objective.MineBlockObjective;
import vn.boxpvp.boxquest.quest.objective.Objective;
import vn.boxpvp.boxquest.reward.CommandReward;
import vn.boxpvp.boxquest.reward.CommandTemplate;

/**
 * Đọc quests.yml và xác thực từng mục. Mục sai bị bỏ qua kèm cảnh báo có tên quest; plugin không bao giờ tắt vì config.
 */
public final class QuestRegistry {

    /** Số phần tử tối đa của {@code rewards.commands} mỗi nhiệm vụ. */
    public static final int MAX_COMMANDS = 10;

    /** Độ dài tối đa của một {@code command}, sau khi bỏ khoảng trắng và {@code /} ở đầu. */
    public static final int MAX_COMMAND_LENGTH = 256;

    private final BoxQuestPlugin plugin;
    private final MineIndex mineIndex;
    private Map<String, Quest> quests = Map.of();

    public QuestRegistry(BoxQuestPlugin plugin, MineIndex mineIndex) {
        this.plugin = plugin;
        this.mineIndex = mineIndex;
    }

    /** @param failed quests.yml lỗi cú pháp; danh sách cũ được giữ nguyên */
    public record LoadResult(int loaded, int skipped, boolean failed) {
    }

    /** Kết quả phân tích mục {@code quests}: nhiệm vụ hợp lệ theo thứ tự trong file, và số mục bị bỏ qua. */
    record Parsed(Map<String, Quest> quests, int skipped) {
    }

    /** Phần cần server thật lúc nạp: region WorldGuard và loại Material (test thay bằng bản giả). */
    interface Environment {

        /** Nạp region vào chỉ mục; {@code false} nếu region không tồn tại. */
        boolean indexRegion(String region);

        default boolean isIcon(Material material) {
            return material.isItem() && !material.isAir();
        }

        default boolean isBlock(Material material) {
            return material.isBlock();
        }
    }

    public LoadResult load() {
        Logger log = plugin.getLogger();
        Messages m = plugin.messages();
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) plugin.saveResource("quests.yml", false);

        YamlConfiguration yml = new YamlConfiguration();
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            yml.loadFromString(dropDuplicateIds(text, log, m));
        } catch (IOException | InvalidConfigurationException e) {
            log.severe(m.log("log.quest.file-failed", "count", String.valueOf(quests.size()), "error", String.valueOf(e.getMessage())));
            return new LoadResult(quests.size(), 0, true);
        }

        mineIndex.clear();
        Map<String, Quest> next = new LinkedHashMap<>();
        int skipped = 0;
        ConfigurationSection root = yml.getConfigurationSection("quests");
        if (root == null) {
            log.warning(m.log("log.quest.no-section"));
        } else {
            Parsed parsed = parseAll(root, log, m, region -> mineIndex.index(region, log, m));
            next = parsed.quests();
            skipped = parsed.skipped();
        }
        logChanges(quests, next, log, m);
        quests = Collections.unmodifiableMap(next);
        log.info(m.log("log.quest.loaded", "loaded", String.valueOf(next.size()), "skipped", String.valueOf(skipped)));
        return new LoadResult(next.size(), skipped, false);
    }

    public Quest get(String id) {
        return id == null ? null : quests.get(id);
    }

    /** Theo thứ tự trong quests.yml. */
    public List<Quest> list() {
        return List.copyOf(quests.values());
    }

    /**
     * Phân tích mọi mục dưới {@code quests}. Mục sai bị bỏ qua kèm cảnh báo, không ảnh hưởng mục khác.
     *
     * @param env region WorldGuard và kiểm tra Material
     */
    static Parsed parseAll(ConfigurationSection root, Logger log, Messages m, Environment env) {
        Map<String, Quest> next = new LinkedHashMap<>();
        int skipped = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            try {
                if (section == null) throw new IllegalArgumentException(m.log("log.quest.reason.not-section"));
                next.put(id, parse(id, section, log, m, env));
            } catch (IllegalArgumentException e) {
                log.warning(m.log("log.quest.skipped", "id", id, "reason", String.valueOf(e.getMessage())));
                skipped++;
            }
        }
        return new Parsed(next, skipped);
    }

    private static Quest parse(String id, ConfigurationSection s, Logger log, Messages m, Environment env) {
        String display = s.getString("display", id);
        List<String> description = s.isList("description")
                ? List.copyOf(s.getStringList("description"))
                : s.getString("description") == null ? List.of() : List.of(s.getString("description"));

        int amount = s.getInt("amount", 0);
        if (amount <= 0) throw new IllegalArgumentException(m.log("log.quest.reason.amount"));

        double money = s.getDouble("rewards.money", 0);
        if (money < 0) throw new IllegalArgumentException(m.log("log.quest.reason.money"));

        String iconName = s.getString("icon");
        Material icon = iconName == null ? null : Material.matchMaterial(iconName);
        if (icon == null || !env.isIcon(icon)) {
            log.warning(m.log("log.quest.invalid-icon", "id", id, "value", String.valueOf(iconName)));
            icon = Material.STONE;
        }

        ConfigurationSection obj = s.getConfigurationSection("objective");
        if (obj == null) throw new IllegalArgumentException(m.log("log.quest.reason.no-objective"));
        Objective objective = parseObjective(id, obj, log, m, env);

        List<CommandReward> commands = parseCommands(id, s, log, m);
        if (money == 0 && commands.isEmpty()) log.warning(m.log("log.quest.no-reward", "quest", id));

        return new Quest(id, display, icon, description, objective, amount, money, commands,
                s.getBoolean("npc-only", false));
    }

    /**
     * {@code rewards.commands}: mỗi phần tử là một chuỗi (lệnh), hoặc một map có {@code command} (chuỗi) và
     * {@code display} (chuỗi, tuỳ chọn). Phần tử sai làm hỏng cả nhiệm vụ, vì bỏ âm thầm một phần thưởng là trả
     * thiếu cho người chơi mà không ai biết. Không kiểm tra lệnh có tồn tại: plugin đích có thể bật sau BoxQuest.
     */
    static List<CommandReward> parseCommands(String id, ConfigurationSection s, Logger log, Messages m) {
        String path = "rewards.commands";
        if (s.get(path) == null) return List.of();
        if (!s.isList(path)) throw new IllegalArgumentException(m.log("log.quest.reason.commands-type"));
        List<?> list = s.getList(path, List.of());
        if (list.size() > MAX_COMMANDS) throw new IllegalArgumentException(m.log("log.quest.reason.commands-count"));

        List<CommandReward> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            String index = String.valueOf(i);
            Object entry = list.get(i);
            if (entry instanceof ConfigurationSection section) entry = section.getValues(false);

            String command;
            String display = null;
            if (entry instanceof String str) {
                command = str;
            } else if (entry instanceof Map<?, ?> map && map.get("command") instanceof String str
                    && (map.get("display") == null || map.get("display") instanceof String)) {
                command = str;
                display = (String) map.get("display");
            } else {
                throw new IllegalArgumentException(m.log("log.quest.reason.command-entry", "index", index));
            }

            command = CommandTemplate.normalizeCommand(command);
            if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH) {
                throw new IllegalArgumentException(m.log("log.quest.reason.command-empty", "index", index));
            }
            if (display != null && display.isBlank()) display = null;

            CommandTemplate commandTemplate;
            CommandTemplate displayTemplate;
            try {
                commandTemplate = CommandTemplate.parse(command);
                displayTemplate = display == null ? null : CommandTemplate.parse(display);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(m.log("log.quest.reason.command-boost", "index", index));
            }

            // Cảnh báo MỘT lần lúc nạp, không phải mỗi lần trả
            Set<String> unknown = new LinkedHashSet<>(commandTemplate.unknownPlaceholders());
            if (displayTemplate != null) unknown.addAll(displayTemplate.unknownPlaceholders());
            for (String placeholder : unknown) {
                log.warning(m.log("log.quest.unknown-placeholder", "quest", id, "placeholder", placeholder, "index", index));
            }
            out.add(new CommandReward(commandTemplate, displayTemplate));
        }
        return out;
    }

    private static Objective parseObjective(String id, ConfigurationSection obj, Logger log, Messages m,
            Environment env) {
        String type = obj.getString("type", MineBlockObjective.TYPE).toUpperCase(Locale.ROOT);
        return switch (type) {
            case MineBlockObjective.TYPE -> parseMineBlock(id, obj, log, m, env);
            default -> throw new IllegalArgumentException(m.log("log.quest.reason.objective-type", "type", type));
        };
    }

    private static MineBlockObjective parseMineBlock(String id, ConfigurationSection obj, Logger log, Messages m,
            Environment env) {
        Set<String> regions = new LinkedHashSet<>();
        readRegions(obj, "region", regions, m);
        readRegions(obj, "regions", regions, m);
        if (regions.isEmpty()) throw new IllegalArgumentException(m.log("log.quest.reason.no-region"));
        for (String region : regions) {
            if (!env.indexRegion(region)) {
                throw new IllegalArgumentException(m.log("log.quest.reason.region-missing", "region", region));
            }
        }

        Set<Material> blocks = EnumSet.noneOf(Material.class);
        for (String name : obj.getStringList("blocks")) {
            Material block = Material.matchMaterial(name);
            if (block == null || !env.isBlock(block)) {
                log.warning(m.log("log.quest.invalid-block", "id", id, "value", name));
                continue;
            }
            blocks.add(block);
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException(m.log("log.quest.reason.no-blocks"));
        return new MineBlockObjective(List.copyOf(regions), Collections.unmodifiableSet(blocks));
    }

    /**
     * Thêm tên region từ {@code objective.<key>} vào {@code out} (chữ thường). Cả {@code region} và {@code regions}
     * đều nhận một tên hoặc một danh sách tên; khai báo cả hai thì gộp lại. Một tên đơn rỗng được coi như không có
     * (giữ hành vi 0.1.0); phần tử rỗng hay không phải chuỗi trong danh sách, hoặc một mục cấu hình con, là lỗi.
     */
    private static void readRegions(ConfigurationSection obj, String key, Set<String> out, Messages m) {
        Object value = obj.get(key);
        if (value == null) return;
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof String name) || name.isBlank()) {
                    throw new IllegalArgumentException(m.log("log.quest.reason.region-invalid", "key", key));
                }
                out.add(name.strip().toLowerCase(Locale.ROOT));
            }
        } else if (value instanceof ConfigurationSection) {
            throw new IllegalArgumentException(m.log("log.quest.reason.region-invalid", "key", key));
        } else {
            String name = String.valueOf(value);
            if (!name.isBlank()) out.add(name.toLowerCase(Locale.ROOT));
        }
    }

    /** Quest còn tồn tại nhưng amount đổi: count của người chơi được giữ nguyên, ghi rõ hệ quả. */
    private static void logChanges(Map<String, Quest> before, Map<String, Quest> after, Logger log, Messages m) {
        for (Quest old : before.values()) {
            Quest now = after.get(old.id());
            if (now == null) {
                log.warning(m.log("log.quest.removed", "id", old.id()));
            } else if (now.amount() != old.amount()) {
                log.warning(m.log("log.quest.amount-changed", "id", old.id(),
                        "old", String.valueOf(old.amount()), "new", String.valueOf(now.amount())));
            }
        }
    }

    /**
     * YAML không cho phép khoá trùng (bản sau ghi đè hoặc gây lỗi). Quét văn bản, giữ mục đầu tiên của mỗi id
     * dưới {@code quests:} và bỏ các mục trùng phía sau, kèm cảnh báo.
     */
    private static String dropDuplicateIds(String text, Logger log, Messages m) {
        String[] lines = text.split("\\R", -1);
        List<String> out = new ArrayList<>(lines.length);
        Set<String> seen = new HashSet<>();
        boolean inQuests = false;
        int childIndent = -1;
        boolean skipping = false;

        for (String line : lines) {
            String trimmed = line.strip();
            boolean content = !trimmed.isEmpty() && !trimmed.startsWith("#");
            int indent = line.length() - line.stripLeading().length();

            if (content && indent == 0) {
                inQuests = trimmed.startsWith("quests:");
                childIndent = -1;
                skipping = false;
            } else if (inQuests && content) {
                if (childIndent < 0) childIndent = indent;
                if (indent <= childIndent) {
                    skipping = false;
                    if (indent == childIndent) {
                        int colon = trimmed.indexOf(':');
                        String id = unquote(colon < 0 ? trimmed : trimmed.substring(0, colon));
                        if (!seen.add(id)) {
                            log.warning(m.log("log.quest.duplicate-id", "id", id));
                            skipping = true;
                        }
                    }
                }
            }
            if (!skipping) out.add(line);
        }
        return String.join("\n", out);
    }

    private static String unquote(String s) {
        s = s.strip();
        if (s.length() >= 2 && (s.charAt(0) == '\'' || s.charAt(0) == '"') && s.charAt(s.length() - 1) == s.charAt(0)) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
