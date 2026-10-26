package vn.boxpvp.boxquest.listener;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.quest.objective.MineBlockObjective;

/**
 * Đếm block. MONITOR + ignoreCancelled: chạy sau cùng, bỏ qua sự kiện đã bị huỷ (ví dụ CataMines chặn cúp yếu).
 * Không chạm đĩa, không gọi WorldGuard, không duyệt danh sách quest.
 */
public final class MineBreakListener implements Listener {

    private final BoxQuestPlugin plugin;

    public MineBreakListener(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        try {
            Player player = event.getPlayer();
            ActiveQuest active = plugin.progress().get(player.getUniqueId());
            if (active == null || active.questId() == null) return;

            // Quest đã bị xoá khỏi config: bỏ qua lặng lẽ, giữ nguyên tiến độ
            Quest quest = plugin.registry().get(active.questId());
            if (quest == null || !(quest.objective() instanceof MineBlockObjective mine)) return;

            Block block = event.getBlock();
            // 1. Vị trí BLOCK bị phá (không phải người chơi) nằm trong một region của quest
            if (!inAnyRegion(mine, block)) return;
            // 2. Loại block nằm trong danh sách trắng
            if (!mine.blocks().contains(block.getType())) return;
            // 3. Cộng tiến độ
            plugin.quests().increment(player, active, quest);
        } catch (Exception e) {
            plugin.errors().report("count-block", e);
        }
    }

    /** Vòng for theo chỉ số: không tạo iterator trên đường nóng. Thường chỉ có 1–3 region. */
    private boolean inAnyRegion(MineBlockObjective mine, Block block) {
        List<String> regions = mine.regions();
        for (int i = 0; i < regions.size(); i++) {
            if (plugin.mineIndex().isInside(regions.get(i), block)) return true;
        }
        return false;
    }
}
