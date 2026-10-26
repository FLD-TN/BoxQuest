package vn.boxpvp.boxquest.mine;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import vn.boxpvp.boxquest.config.Messages;

/**
 * Cache hộp giới hạn của các region mine. WorldGuard chỉ được gọi lúc nạp (khởi động / {@code /quest reload});
 * mỗi sự kiện đào chỉ còn một lần tra HashMap và sáu phép so sánh số nguyên.
 */
public final class MineIndex {

    private final Map<String, Box> boxes = new HashMap<>();

    public void clear() {
        boxes.clear();
    }

    /**
     * Đọc region từ WorldGuard (tìm trong mọi world) và lưu hộp giới hạn.
     *
     * @return {@code false} nếu region không tồn tại
     */
    public boolean index(String regionName, Logger log, Messages messages) {
        String key = regionName.toLowerCase(Locale.ROOT);
        if (boxes.containsKey(key)) return true;

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        Box found = null;
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) continue;
            ProtectedRegion region = manager.getRegion(key);
            if (region == null || region instanceof GlobalProtectedRegion) continue;
            if (found != null) {
                log.warning(messages.log("log.region.multiple-worlds", "region", key));
                continue;
            }
            if (!(region instanceof ProtectedCuboidRegion)) {
                log.warning(messages.log("log.region.not-cuboid", "region", key));
            }
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            found = new Box(world.getUID(),
                    min.x(), min.y(), min.z(),
                    max.x(), max.y(), max.z());
        }
        if (found == null) return false;
        boxes.put(key, found);
        return true;
    }

    /**
     * Vị trí BLOCK (không phải người chơi) có nằm trong region không.
     *
     * @param region tên region đã chuyển về chữ thường
     */
    public boolean isInside(String region, Block block) {
        Box box = boxes.get(region);
        return box != null && box.contains(block);
    }

    record Box(UUID world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        boolean contains(Block b) {
            int x = b.getX();
            int y = b.getY();
            int z = b.getZ();
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ
                    && world.equals(b.getWorld().getUID());
        }
    }
}
