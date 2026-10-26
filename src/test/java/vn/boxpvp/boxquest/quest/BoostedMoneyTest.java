package vn.boxpvp.boxquest.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Xu thưởng nhân hệ số theo phần mười (BoxCore 1.4.0, hệ số quest có thể lẻ). */
class BoostedMoneyTest {

    @Test
    void wholeAndDecimalMultipliers() {
        assertEquals(2500.0, QuestService.boostedMoney(2500, 10));
        assertEquals(7500.0, QuestService.boostedMoney(2500, 30));
        assertEquals(3750.0, QuestService.boostedMoney(2500, 15));
        assertEquals(3250.0, QuestService.boostedMoney(2500, 13));
        assertEquals(12650.0, QuestService.boostedMoney(11500, 11));
        assertEquals(0.0, QuestService.boostedMoney(0, 15));
    }

    @Test
    void roundedToTwoDecimals() {
        assertEquals(1234.567, QuestService.boostedMoney(1234.567, 10)); // x1: giữ nguyên số trong quests.yml
        assertEquals(1851.85, QuestService.boostedMoney(1234.567, 15));  // 1851,8505
    }
}
