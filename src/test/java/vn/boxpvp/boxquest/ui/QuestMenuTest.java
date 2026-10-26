package vn.boxpvp.boxquest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import vn.boxpvp.boxquest.quest.Quest;

class QuestMenuTest {

    private static Quest quest(String id, boolean npcOnly) {
        return new Quest(id, id, Material.STONE, List.of(), null, 1, 0, List.of(), npcOnly);
    }

    private static List<String> ids(List<Quest> quests) {
        return quests.stream().map(Quest::id).toList();
    }

    @Test
    void questSlotsAreFourRowsOfSevenInsideTheBorder() {
        List<Integer> slots = QuestMenu.questSlots();
        assertEquals(28, slots.size());
        assertEquals(28, new HashSet<>(slots).size());
        assertEquals(List.of(10, 16, 37, 43), List.of(slots.get(0), slots.get(6), slots.get(21), slots.get(27)));
        for (int slot : slots) {
            int col = slot % 9;
            assertEquals(true, slot >= 9 && slot < 45 && col >= 1 && col <= 7, "ô " + slot);
        }
    }

    @Test
    void pageCount() {
        assertEquals(1, QuestMenu.pageCount(0));
        assertEquals(1, QuestMenu.pageCount(28));
        assertEquals(2, QuestMenu.pageCount(29));
        assertEquals(3, QuestMenu.pageCount(57));
    }

    @Test
    void npcOnlyQuestsAreHidden() {
        List<Quest> all = List.of(quest("a", false), quest("npc", true), quest("b", false));
        assertEquals(List.of("a", "b"), ids(QuestMenu.shownQuests(all, null)));
        assertEquals(List.of("a", "b"), ids(QuestMenu.shownQuests(all, "b")));
    }

    @Test
    void heldNpcOnlyQuestComesFirst() {
        List<Quest> all = List.of(quest("a", false), quest("b", false), quest("npc", true), quest("npc2", true));
        assertEquals(List.of("npc", "a", "b"), ids(QuestMenu.shownQuests(all, "npc")));
    }

    @Test
    void headAndPagingAreOutsideTheQuestArea() {
        List<Integer> slots = QuestMenu.questSlots();
        for (int slot : new int[] {QuestMenu.HEAD_SLOT, QuestMenu.PREVIOUS_SLOT, QuestMenu.PAGE_SLOT, QuestMenu.NEXT_SLOT}) {
            assertEquals(false, slots.contains(slot), "ô " + slot);
        }
    }
}
