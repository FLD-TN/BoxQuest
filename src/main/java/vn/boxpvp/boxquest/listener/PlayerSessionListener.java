package vn.boxpvp.boxquest.listener;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vn.boxpvp.boxquest.BoxQuestPlugin;

/** Nạp dữ liệu khi vào; ghi ngay rồi xoá khỏi RAM khi thoát. */
public final class PlayerSessionListener implements Listener {

    private final BoxQuestPlugin plugin;

    public PlayerSessionListener(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            plugin.bossbars().sync(player, plugin.progress().load(player.getUniqueId()));
        } catch (Exception e) {
            plugin.errors().report("player-load", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Mỗi bước bọc riêng: một bước lỗi không được chặn việc ghi dữ liệu và giải phóng RAM
        try {
            plugin.menus().forget(uuid);
        } catch (Exception e) {
            plugin.errors().report("player-quit-menu", e);
        }
        try {
            plugin.feedback().forget(uuid);
        } catch (Exception e) {
            plugin.errors().report("player-quit-feedback", e);
        }
        try {
            plugin.bossbars().forget(uuid);
        } catch (Exception e) {
            plugin.errors().report("player-quit-bossbar", e);
        }
        try {
            plugin.progress().unload(uuid);
        } catch (Exception e) {
            plugin.errors().report("player-quit-save", e);
        }
    }
}
