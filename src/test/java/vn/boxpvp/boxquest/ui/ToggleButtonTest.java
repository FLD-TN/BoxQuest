package vn.boxpvp.boxquest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToggleButtonTest {

    @Test
    void layout() {
        // Góc trái: Tự động nhận lại, Âm thanh. Góc phải (đối xứng): Bossbar, Thanh tiến độ.
        assertEquals(ToggleButton.AUTO_REPEAT, ToggleButton.bySlot(45));
        assertEquals(ToggleButton.SOUND, ToggleButton.bySlot(46));
        assertEquals(ToggleButton.BOSSBAR, ToggleButton.bySlot(52));
        assertEquals(ToggleButton.ACTIONBAR, ToggleButton.bySlot(53));
    }

    @Test
    void noTwoButtonsShareASlot() {
        Set<Integer> used = new HashSet<>();
        for (ToggleButton b : ToggleButton.values()) {
            assertEquals(true, used.add(b.slot()), b.name());
        }
    }

    @Test
    void otherSlotsAreNotButtons() {
        for (int slot = 0; slot < 54; slot++) {
            if (Set.of(45, 46, 52, 53).contains(slot)) continue;
            assertNull(ToggleButton.bySlot(slot), "ô " + slot);
        }
    }

    @Test
    void buttonsDoNotOverlapPagingOrQuests() {
        for (ToggleButton b : ToggleButton.values()) {
            assertEquals(false, QuestMenu.questSlots().contains(b.slot()), b.name());
            assertEquals(false, Set.of(QuestMenu.PREVIOUS_SLOT, QuestMenu.PAGE_SLOT, QuestMenu.NEXT_SLOT)
                    .contains(b.slot()), b.name());
        }
    }
}
