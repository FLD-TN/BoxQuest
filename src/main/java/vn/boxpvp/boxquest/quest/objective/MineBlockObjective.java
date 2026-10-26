package vn.boxpvp.boxquest.quest.objective;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;

/**
 * Đào block trong một hoặc nhiều region WorldGuard.
 *
 * @param regions tên các region, đã chuyển về chữ thường (khớp khoá của MineIndex), không trùng, không rỗng;
 *                block nằm trong BẤT KỲ region nào đều được tính
 * @param blocks  danh sách trắng loại block được đếm (EnumSet, tra O(1))
 */
public record MineBlockObjective(List<String> regions, Set<Material> blocks) implements Objective {

    public static final String TYPE = "MINE_BLOCK";

    public MineBlockObjective {
        regions = List.copyOf(regions);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
