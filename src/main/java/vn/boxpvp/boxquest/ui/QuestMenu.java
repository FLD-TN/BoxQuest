package vn.boxpvp.boxquest.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import vn.boxpvp.boxquest.BoxQuestPlugin;
import vn.boxpvp.boxquest.progress.ActiveQuest;
import vn.boxpvp.boxquest.quest.Quest;

/**
 * Màn hình chính {@code /quest}, 54 ô, có phân trang.
 *
 * <pre>
 *  hàng 0   B  B  B  B [H] B  B  B  B      B = viền, H = đầu người chơi (nhiệm vụ đang giữ + quy tắc)
 *  hàng 1   B  .  .  .  .  .  .  .  B      . = khu nhiệm vụ 7x4 = 28 ô mỗi trang, xếp tuần tự từ ô 10
 *  hàng 2   B  .  .  .  .  .  .  .  B          theo quests.yml
 *  hàng 3   B  .  .  .  .  .  .  .  B
 *  hàng 4   B  .  .  .  .  .  .  .  B
 *  hàng 5  [A][S] B [<][P][>] B [1][2]     A = tự động nhận lại, S = âm thanh, 1 = bossbar, 2 = action bar,
 *                                          < > = trang trước / sau (hết trang thì là viền), P = số trang
 * </pre>
 *
 * <p>Nhiệm vụ {@code npc-only} không nằm trong danh sách; người đang làm một nhiệm vụ như vậy thấy nó ở ô đầu
 * tiên của trang 1. Menu mở ở trang chứa nhiệm vụ đang làm.
 *
 * <p>Thao tác: chuột trái vào nhiệm vụ để nhận, chuột phải vào nhiệm vụ đang làm để huỷ (đều qua màn hình
 * xác nhận). Nhiệm vụ đang giữ đã bị xoá khỏi quests.yml ("không còn tồn tại") thì huỷ bằng chuột phải vào
 * đầu người chơi; không tự xoá tiến độ.
 */
public final class QuestMenu extends MenuView {

    private static final int SIZE = 54;
    static final int HEAD_SLOT = 4;
    static final int PREVIOUS_SLOT = 48;
    static final int PAGE_SLOT = 49;
    static final int NEXT_SLOT = 50;
    /** Ô đầu (cột 1) của bốn hàng nhiệm vụ. */
    private static final int[] QUEST_ROWS = {10, 19, 28, 37};
    private static final int ROW_WIDTH = 7;
    static final int PAGE_SIZE = QUEST_ROWS.length * ROW_WIDTH;
    /** Trang chưa chọn: lần vẽ đầu mở trang chứa nhiệm vụ đang làm. */
    private static final int AUTO_PAGE = -1;

    private final BoxQuestPlugin plugin;
    private final UUID owner;
    /** Bản đồ ô → quest ID do SERVER giữ, dựng lại mỗi lần vẽ; nguồn duy nhất để biết người chơi bấm quest nào. */
    private final Map<Integer, String> slotToQuest = new HashMap<>();
    /** Trang đang xem, đếm từ 0. */
    private int page;
    /** Đầu người chơi, dựng ở lần vẽ đầu; các lần sau chỉ đổi tên và lore. */
    private ItemStack head;
    private long renderedVersion = -1;

    /** Mở ở trang chứa nhiệm vụ đang làm (trang 1 nếu chưa nhận). */
    public QuestMenu(BoxQuestPlugin plugin, UUID owner) {
        this(plugin, owner, AUTO_PAGE);
    }

    /** @param page trang đếm từ 0; vượt quá số trang hiện có thì về trang cuối */
    public QuestMenu(BoxQuestPlugin plugin, UUID owner, int page) {
        super(SIZE, plugin.messages().text("menu.title"));
        this.plugin = plugin;
        this.owner = owner;
        this.page = page;
    }

    @Override
    public void render() {
        ActiveQuest active = plugin.progress().get(owner);
        if (active == null) return;
        Quest current = plugin.quests().current(active);
        boolean missing = active.questId() != null && current == null;
        OfflinePlayer viewer = Bukkit.getOfflinePlayer(owner);
        IconFactory icons = new IconFactory(plugin.messages(), plugin.settings(), owner, IconFactory.name(viewer));

        List<Quest> shown = shownQuests(plugin.registry().list(), active.questId());
        int pages = pageCount(shown.size());
        if (page == AUTO_PAGE) page = Math.max(0, indexOf(shown, active.questId())) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, pages - 1));

        drawFrame(icons);
        if (head == null) head = IconFactory.playerHead(viewer);
        icons.updateHead(head, active, current);
        inventory.setItem(HEAD_SLOT, head);

        slotToQuest.clear();
        List<Integer> slots = questSlots();
        int from = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && from + i < shown.size(); i++) {
            Quest quest = shown.get(from + i);
            slotToQuest.put(slots.get(i), quest.id());
            inventory.setItem(slots.get(i), missing ? icons.blockedQuest(quest) : icons.quest(quest, active, current));
        }

        inventory.setItem(PREVIOUS_SLOT, page > 0 ? icons.pageButton("previous-page", page) : icons.border());
        inventory.setItem(PAGE_SLOT, icons.pageInfo(page + 1, pages, shown.size()));
        inventory.setItem(NEXT_SLOT, page < pages - 1 ? icons.pageButton("next-page", page + 2) : icons.border());
        for (ToggleButton toggle : ToggleButton.values()) {
            toggle.render(inventory, plugin.messages(), plugin.settings(), active);
        }
        renderedVersion = active.version();
    }

    /** Viền ngoài; khu nhiệm vụ để trống. */
    private void drawFrame(IconFactory icons) {
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean edge = row == 0 || row == 5 || col == 0 || col == 8;
            inventory.setItem(slot, edge ? icons.border() : null);
        }
    }

    /**
     * Nhiệm vụ hiện trong danh sách, theo thứ tự trong quests.yml. Bỏ nhiệm vụ {@code npc-only}, trừ nhiệm vụ người
     * chơi đang giữ ({@code heldId}): nhiệm vụ đó đứng đầu danh sách để người chơi luôn thấy và huỷ được.
     */
    static List<Quest> shownQuests(List<Quest> all, String heldId) {
        List<Quest> out = new ArrayList<>(all.size());
        Quest held = null;
        for (Quest quest : all) {
            if (!quest.npcOnly()) {
                out.add(quest);
            } else if (quest.id().equals(heldId)) {
                held = quest;
            }
        }
        if (held != null) out.add(0, held);
        return out;
    }

    /** Ít nhất 1 trang, kể cả khi không có nhiệm vụ nào. */
    static int pageCount(int quests) {
        return Math.max(1, (quests + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static int indexOf(List<Quest> quests, String id) {
        if (id == null) return -1;
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    /** 28 ô nhiệm vụ của một trang: 10–16, 19–25, 28–34, 37–43 (bỏ qua cột viền hai bên). */
    static List<Integer> questSlots() {
        List<Integer> slots = new ArrayList<>(PAGE_SIZE);
        for (int rowStart : QUEST_ROWS) {
            for (int col = 0; col < ROW_WIDTH; col++) slots.add(rowStart + col);
        }
        return slots;
    }

    /** Chỉ vẽ lại khi trạng thái người chơi đổi. */
    @Override
    public void refresh() {
        ActiveQuest active = plugin.progress().get(owner);
        if (active != null && active.version() != renderedVersion) render();
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        ActiveQuest active = plugin.progress().get(owner);
        if (active == null) return;

        ToggleButton toggle = ToggleButton.bySlot(slot);
        if (toggle != null) {
            toggle.flip(active);
            toggle.render(inventory, plugin.messages(), plugin.settings(), active);
            renderedVersion = active.version();
            plugin.feedback().click(player);
            if (toggle == ToggleButton.ACTIONBAR) plugin.feedback().refresh(player, active);
            if (toggle == ToggleButton.BOSSBAR) plugin.bossbars().sync(player, active);
            return;
        }

        // Hết trang thì ô đó là viền: bấm không làm gì
        if (slot == PREVIOUS_SLOT || slot == NEXT_SLOT) {
            int target = page + (slot == NEXT_SLOT ? 1 : -1);
            int pages = pageCount(shownQuests(plugin.registry().list(), active.questId()).size());
            if (target < 0 || target >= pages) return;
            page = target;
            plugin.feedback().click(player);
            render();
            return;
        }

        // Nhiệm vụ đang giữ không còn tồn tại: huỷ bằng chuột phải vào đầu người chơi
        if (slot == HEAD_SLOT) {
            if (click.isRightClick() && active.questId() != null && plugin.quests().current(active) == null) {
                plugin.feedback().click(player);
                plugin.menus().open(player, new CancelConfirmMenu(plugin, owner, active.questId(), page));
            }
            return;
        }

        String questId = slotToQuest.get(slot);
        if (questId == null) return;                      // ô không phải nút quest
        Quest target = plugin.registry().get(questId);
        if (target == null) return;                       // quest đã bị xoá khỏi config

        if (active.questId() == null) {
            if (!click.isLeftClick()) return;             // chuột trái: nhận
            plugin.feedback().click(player);
            plugin.menus().open(player, AcceptConfirmMenu.fromMenu(plugin, owner, target.id(), page));
        } else if (target.id().equals(active.questId())) {
            if (!click.isRightClick()) return;            // chuột phải: huỷ
            plugin.feedback().click(player);
            plugin.menus().open(player, new CancelConfirmMenu(plugin, owner, active.questId(), page));
        }
        // Còn lại: nhiệm vụ bị khoá vì đang làm nhiệm vụ khác; lore đã giải thích, không làm gì
    }
}
