package vn.boxpvp.boxquest.config;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

/** Giá trị đọc từ config.yml, bất biến; nạp lại bằng cách tạo bản mới. */
public record Settings(
        boolean defaultAutoRepeat,
        boolean defaultShowActionbar,
        boolean defaultSound,
        boolean defaultShowBossbar,
        int actionbarRefreshTicks,
        boolean actionbarAlways,
        int actionbarHideAfterTicks,
        BossBar.Color bossbarColor,
        BossBar.Overlay bossbarOverlay,
        int bossbarUpdateTicks,
        Sound completeSound,
        long soundCooldownMillis,
        long offerCooldownMillis,
        int flushIntervalSeconds,
        Material border,
        Material filler,
        Map<String, Material> icons) {

    /** Icon mặc định cho các khoá dưới {@code gui.icons}. */
    private static final Map<String, Material> DEFAULT_ICONS = Map.ofEntries(
            Map.entry("auto-repeat", Material.REPEATER),
            Map.entry("actionbar", Material.OAK_SIGN),
            Map.entry("sound", Material.NOTE_BLOCK),
            Map.entry("bossbar", Material.DRAGON_HEAD),
            Map.entry("cancel", Material.BARRIER),
            Map.entry("locked", Material.BARRIER),
            Map.entry("confirm", Material.LIME_CONCRETE),
            Map.entry("back", Material.RED_CONCRETE),
            Map.entry("page", Material.PAPER),
            Map.entry("previous-page", Material.ARROW),
            Map.entry("next-page", Material.ARROW));

    public static Settings load(FileConfiguration cfg, Logger log, Messages messages) {
        Map<String, Material> icons = new java.util.HashMap<>();
        DEFAULT_ICONS.forEach((key, fallback) ->
                icons.put(key, material(cfg.getString("gui.icons." + key), fallback, log, messages)));

        return new Settings(
                cfg.getBoolean("defaults.auto-repeat", false),
                cfg.getBoolean("defaults.show-actionbar", true),
                cfg.getBoolean("defaults.sound", true),
                cfg.getBoolean("defaults.show-bossbar", true),
                Math.max(1, Math.min(40, cfg.getInt("actionbar.refresh-ticks", 10))),
                "always".equalsIgnoreCase(cfg.getString("actionbar.mode", "mining")),
                (int) Math.max(1, Math.min(60, cfg.getDouble("actionbar.hide-after-seconds", 3)) * 20),
                option(cfg, "bossbar.color", BossBar.Color.class, BossBar.Color.GREEN, log, messages),
                option(cfg, "bossbar.style", BossBar.Overlay.class, BossBar.Overlay.PROGRESS, log, messages),
                Math.max(1, Math.min(20, cfg.getInt("bossbar.update-ticks", 5))),
                sound(cfg.getString("sound.complete", "ENTITY_PLAYER_LEVELUP"), log, messages),
                Math.max(0, cfg.getLong("sound.cooldown-seconds", 3)) * 1000L,
                (long) (Math.max(0, Math.min(60, cfg.getDouble("offer.cooldown-seconds", 1))) * 1000),
                Math.max(1, cfg.getInt("storage.flush-interval-seconds", 30)),
                material(cfg.getString("gui.border"), Material.BLACK_STAINED_GLASS_PANE, log, messages),
                material(cfg.getString("gui.filler"), Material.GRAY_STAINED_GLASS_PANE, log, messages),
                Map.copyOf(icons));
    }

    public Material icon(String key) {
        return icons.getOrDefault(key, DEFAULT_ICONS.getOrDefault(key, Material.STONE));
    }

    @SuppressWarnings("deprecation")
    private static Sound sound(String name, Logger log, Messages messages) {
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warning(messages.log("log.config.invalid-sound",
                    "value", String.valueOf(name), "fallback", "ENTITY_PLAYER_LEVELUP"));
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
    }

    /** Giá trị enum trong config.yml (không phân biệt hoa thường); sai thì cảnh báo và dùng mặc định. */
    private static <E extends Enum<E>> E option(FileConfiguration cfg, String path, Class<E> type, E fallback,
            Logger log, Messages messages) {
        String name = cfg.getString(path);
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning(messages.log("log.config.invalid-option", "key", path, "value", name, "fallback", fallback.name()));
            return fallback;
        }
    }

    private static Material material(String name, Material fallback, Logger log, Messages messages) {
        Material m = name == null ? null : Material.matchMaterial(name);
        if (m == null || !m.isItem() || m.isAir()) {
            if (name != null) {
                log.warning(messages.log("log.config.invalid-material", "value", name, "fallback", fallback.name()));
            }
            return fallback;
        }
        return m;
    }
}
