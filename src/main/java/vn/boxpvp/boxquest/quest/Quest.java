package vn.boxpvp.boxquest.quest;

import java.util.List;
import org.bukkit.Material;
import vn.boxpvp.boxquest.quest.objective.Objective;
import vn.boxpvp.boxquest.reward.CommandReward;

/**
 * Định nghĩa nhiệm vụ, bất biến, đọc từ quests.yml. Không chứa trạng thái người chơi.
 *
 * @param commandRewards lệnh console trả khi hoàn thành, theo thứ tự trong file; rỗng nếu không có
 * @param npcOnly        ẩn khỏi menu {@code /quest}, chỉ nhận qua {@code /quest offer} hoặc {@code /quest assign}
 */
public record Quest(
        String id,
        String display,
        Material icon,
        List<String> description,
        Objective objective,
        int amount,
        double rewardMoney,
        List<CommandReward> commandRewards,
        boolean npcOnly) {

    public Quest {
        commandRewards = commandRewards == null ? List.of() : List.copyOf(commandRewards);
    }

    /** Nhiệm vụ hiện trong menu {@code /quest}. */
    public Quest(String id, String display, Material icon, List<String> description, Objective objective, int amount,
            double rewardMoney, List<CommandReward> commandRewards) {
        this(id, display, icon, description, objective, amount, rewardMoney, commandRewards, false);
    }
}
