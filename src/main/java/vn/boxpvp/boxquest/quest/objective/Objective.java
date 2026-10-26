package vn.boxpvp.boxquest.quest.objective;

/**
 * Mục tiêu của một nhiệm vụ. Thêm loại mới (tiêu Xu, thắng KOTH, ...) = thêm một lớp cài đặt giao diện này
 * và một nhánh parse trong QuestRegistry; phần đếm tiến độ và trả thưởng không đổi.
 */
public interface Objective {

    /** Tên loại trong quests.yml, ví dụ {@code MINE_BLOCK}. */
    String type();
}
