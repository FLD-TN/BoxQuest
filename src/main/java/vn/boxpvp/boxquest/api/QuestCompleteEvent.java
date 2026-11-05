package vn.boxpvp.boxquest.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Một lần hoàn thành nhiệm vụ (0.3.2). Bắn sau khi đã trả thưởng, trên main thread, mỗi lần hoàn thành một lần
 * (nhiệm vụ lặp lại tự động bắn nhiều lần). Chỉ để plugin khác lắng nghe (BoxPet cộng XP cho pet), không huỷ được.
 */
public final class QuestCompleteEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String questId;
    private final int completions;
    private final double reward;

    public QuestCompleteEvent(@NotNull Player player, @NotNull String questId, int completions, double reward) {
        super(player);
        this.questId = questId;
        this.completions = completions;
        this.reward = reward;
    }

    /** Mã nhiệm vụ trong quests.yml. */
    public @NotNull String getQuestId() {
        return questId;
    }

    /** Tổng số lần người chơi đã hoàn thành nhiệm vụ đang giữ, tính cả lần này. */
    public int getCompletions() {
        return completions;
    }

    /** Xu đã thưởng cho lần này (đã nhân boost). */
    public double getReward() {
        return reward;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
