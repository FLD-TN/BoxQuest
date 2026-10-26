package vn.boxpvp.boxquest.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import vn.boxpvp.boxquest.BoxQuestPlugin;

/**
 * Theo dõi BoxCore bật/tắt trong lúc BoxQuest đang chạy (plugman, reload riêng BoxCore).
 * Lớp này KHÔNG tham chiếu API của BoxCore.
 */
public final class BoxCoreListener implements Listener {

    private static final String BOXCORE = "BoxCore";

    private final BoxQuestPlugin plugin;

    public BoxCoreListener(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnable(PluginEnableEvent event) {
        if (BOXCORE.equals(event.getPlugin().getName())) plugin.initBoxCore();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        if (BOXCORE.equals(event.getPlugin().getName())) plugin.disableBoxCore();
    }
}
