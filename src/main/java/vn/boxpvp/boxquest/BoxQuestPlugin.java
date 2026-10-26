package vn.boxpvp.boxquest;

import java.io.File;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import vn.boxpvp.boxquest.command.QuestCommand;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.config.Settings;
import vn.boxpvp.boxquest.feedback.BossBarService;
import vn.boxpvp.boxquest.feedback.FeedbackService;
import vn.boxpvp.boxquest.hook.BoxCoreHook;
import vn.boxpvp.boxquest.listener.BoxCoreListener;
import vn.boxpvp.boxquest.listener.MenuClickListener;
import vn.boxpvp.boxquest.listener.MineBreakListener;
import vn.boxpvp.boxquest.listener.PlayerSessionListener;
import vn.boxpvp.boxquest.mine.MineIndex;
import vn.boxpvp.boxquest.progress.ProgressCache;
import vn.boxpvp.boxquest.progress.ProgressStorage;
import vn.boxpvp.boxquest.quest.QuestRegistry;
import vn.boxpvp.boxquest.quest.QuestService;
import vn.boxpvp.boxquest.reward.RewardService;
import vn.boxpvp.boxquest.ui.MenuManager;
import vn.boxpvp.boxquest.util.ErrorLog;

public final class BoxQuestPlugin extends JavaPlugin {

    private ErrorLog errors;
    private Settings settings;
    private Messages messages;
    private MineIndex mineIndex;
    private QuestRegistry registry;
    private ProgressStorage storage;
    private ProgressCache progress;
    private RewardService rewards;
    private FeedbackService feedback;
    private BossBarService bossbars;
    private QuestService quests;
    private MenuManager menus;
    /**
     * BoxCore có sẵn và API nạp được. Lớp này KHÔNG import API của BoxCore; mọi lời gọi BoxCoreHook đi qua cờ này
     * để không bao giờ nạp lớp hook khi BoxCore vắng mặt.
     */
    private boolean boxCore;

    @Override
    public void onEnable() {
        errors = new ErrorLog(this);
        saveDefaultConfig();
        if (!new File(getDataFolder(), "quests.yml").exists()) saveResource("quests.yml", false);
        messages = new Messages(this);
        messages.load();
        settings = Settings.load(getConfig(), getLogger(), messages);

        storage = new ProgressStorage(getLogger(), messages);
        try {
            storage.open(new File(getDataFolder(), "data.db"));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, messages.log("log.database.open-failed"), e);
            storage = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        mineIndex = new MineIndex();
        registry = new QuestRegistry(this, mineIndex);
        progress = new ProgressCache(this, storage);
        rewards = new RewardService(this);
        feedback = new FeedbackService(this);
        bossbars = new BossBarService(this);
        quests = new QuestService(this);
        menus = new MenuManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MineBreakListener(this), this);
        pm.registerEvents(new PlayerSessionListener(this), this);
        pm.registerEvents(new MenuClickListener(this), this);
        pm.registerEvents(new BoxCoreListener(this), this);
        initBoxCore();

        PluginCommand command = getCommand("quest");
        if (command != null) {
            QuestCommand executor = new QuestCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // Người chơi đang online (trường hợp /reload hoặc plugman)
        for (Player player : Bukkit.getOnlinePlayers()) progress.load(player.getUniqueId());
        progress.startFlushTask();
        feedback.startKeepAlive();
        bossbars.start();

        // Tick đầu tiên: mọi world và plugin kinh tế đã sẵn sàng
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                registry.load();
                // Người đang online (reload / plugman) đã được nạp ở trên, giờ mới biết nhiệm vụ của họ
                bossbars.syncAll();
            } catch (Exception e) {
                errors.report("quests-load", e);
            }
            rewards.hook();
        });
    }

    @Override
    public void onDisable() {
        if (feedback != null) feedback.stopKeepAlive();
        if (bossbars != null) {
            bossbars.stop();
            bossbars.hideAll();
        }
        if (menus != null) menus.closeAll();
        if (progress != null) {
            progress.stopFlushTask();
            // ĐỒNG BỘ: scheduler đã dừng, task async sẽ không bao giờ chạy
            progress.flushAllSync();
        }
        if (storage != null) storage.close();
    }

    /**
     * {@code /quest reload}, an toàn khi có người đang làm nhiệm vụ:
     * flush toàn bộ -> đóng mọi GUI (bản đồ ô -> quest đã cũ) -> nạp config.yml, messages.yml, quests.yml
     * và hộp giới hạn region. Tiến độ trong RAM giữ nguyên, kể cả với quest đã bị xoá khỏi config.
     */
    public QuestRegistry.LoadResult reload() {
        progress.flushAllSync();
        menus.closeAll();
        reloadConfig();
        messages.load();
        settings = Settings.load(getConfig(), getLogger(), messages);
        QuestRegistry.LoadResult result = registry.load();
        progress.startFlushTask();
        feedback.startKeepAlive();
        bossbars.start();
        bossbars.syncAll();
        initBoxCore();
        return result;
    }

    /** Nối lại BoxCore (bật plugin, reload, BoxCore được bật lại). Không có BoxCore thì hệ số luôn là 1. */
    public void initBoxCore() {
        boxCore = false;
        if (!getServer().getPluginManager().isPluginEnabled("BoxCore")) {
            getLogger().info(messages.log("log.boxcore.absent"));
            return;
        }
        try {
            boxCore = BoxCoreHook.init();
            getLogger().info(messages.log(boxCore ? "log.boxcore.hooked" : "log.boxcore.no-service"));
        } catch (NoClassDefFoundError e) {
            getLogger().warning(messages.log("log.boxcore.api-failed"));
        }
    }

    /** BoxCore bị tắt khi BoxQuest đang chạy: ngừng dùng boost (hệ số về 1). */
    public void disableBoxCore() {
        if (boxCore) BoxCoreHook.reset();
        boxCore = false;
    }

    /**
     * Hệ số boost {@code quest} của người chơi theo phần mười ({@code 15} = x1.5, {@code 10} = x1, luôn >= 10).
     * Có thể lẻ một chữ số thập phân khi BoxCore khai báo {@code quest} với {@code decimal: true}.
     * Đọc tại thời điểm trả thưởng, không cache.
     */
    public int questMultiplierTenths(UUID player) {
        return boxCore ? Math.max(10, BoxCoreHook.multiplierTenths(player, QUEST_BOOST_TYPE)) : 10;
    }

    /** Loại boost của BoxQuest trong BoxCore. */
    public static final String QUEST_BOOST_TYPE = "quest";

    public ErrorLog errors() {
        return errors;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public MineIndex mineIndex() {
        return mineIndex;
    }

    public QuestRegistry registry() {
        return registry;
    }

    public ProgressCache progress() {
        return progress;
    }

    public RewardService rewards() {
        return rewards;
    }

    public FeedbackService feedback() {
        return feedback;
    }

    public BossBarService bossbars() {
        return bossbars;
    }

    public QuestService quests() {
        return quests;
    }

    public MenuManager menus() {
        return menus;
    }
}
