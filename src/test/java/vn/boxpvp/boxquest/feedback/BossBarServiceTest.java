package vn.boxpvp.boxquest.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BossBarServiceTest {

    @Test
    void progressIsAlwaysWithinZeroAndOne() {
        assertEquals(0f, BossBarService.progress(0, 5000));
        assertEquals(0.5f, BossBarService.progress(2500, 5000));
        assertEquals(1f, BossBarService.progress(5000, 5000));
        // amount bị giảm khi reload: count có thể vượt amount cho tới lần đào kế tiếp
        assertEquals(1f, BossBarService.progress(9000, 5000));
        assertEquals(0f, BossBarService.progress(-3, 5000));
        assertEquals(0f, BossBarService.progress(10, 0));
    }
}
