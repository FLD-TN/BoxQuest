package vn.boxpvp.boxquest.reward;

import java.util.Objects;

/**
 * Một phần tử của {@code rewards.commands}, đã phân tích sẵn.
 *
 * @param command lệnh chạy bằng console (đã bỏ {@code /} ở đầu)
 * @param display dòng hiện trong menu, {@code null} nếu không hiện (phần thưởng vẫn được trả)
 */
public record CommandReward(CommandTemplate command, CommandTemplate display) {

    public CommandReward {
        Objects.requireNonNull(command, "command");
    }
}
