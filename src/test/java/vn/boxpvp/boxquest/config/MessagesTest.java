package vn.boxpvp.boxquest.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import vn.boxpvp.boxquest.TestSupport;

class MessagesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final String[] QUEST_LORES = {
            "menu.quest.available.lore", "menu.quest.active.lore", "menu.quest.locked.lore", "menu.quest.blocked.lore"
    };

    private static final String[] PH = {
            "quest", "&aĐào Mine T3", "count", "1.234", "amount", "5.000", "bar", "&a||&7||", "percent", "24",
            "money", "20.000"
    };

    /** Cài đặt {@code lines(path, listKey, values, ...)} của 0.1.0, chép nguyên văn để so sánh. */
    private static List<Component> legacyLines(YamlConfiguration yml, String path, String listKey, List<String> values,
            String... placeholders) {
        String token = "{" + listKey + "}";
        List<Component> out = new ArrayList<>();
        for (String line : yml.getStringList(path)) {
            if (line.contains(token)) {
                for (String value : values) out.add(Messages.itemLine(legacyApply(line.replace(token, value), placeholders)));
            } else {
                out.add(Messages.itemLine(legacyApply(line, placeholders)));
            }
        }
        return out;
    }

    private static String legacyApply(String s, String... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return s;
    }

    private static YamlConfiguration yml(String resource) {
        return TestSupport.yaml(TestSupport.resource(resource));
    }

    private static List<String> plain(List<Component> lines) {
        List<String> out = new ArrayList<>();
        for (Component c : lines) out.add(PLAIN.serialize(c));
        return out;
    }

    private static Map<String, List<String>> lists(List<String> description, List<String> reward, List<String> money) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("description", description);
        lists.put("reward", reward);
        lists.put("money", money);
        return lists;
    }

    @Test
    void singleKeyOverloadIsIdenticalToLegacy() {
        for (String resource : new String[] {"messages-0.1.0.yml", "messages.yml"}) {
            YamlConfiguration yml = yml(resource);
            Messages m = new Messages(yml);
            for (String path : QUEST_LORES) {
                for (List<String> desc : List.of(List.<String>of(), List.of("Một dòng"), List.of("Dòng {money}", "&cDòng 2"))) {
                    assertEquals(legacyLines(yml, path, "description", desc, PH),
                            m.lines(path, "description", desc, PH), resource + " " + path + " " + desc);
                }
            }
        }
    }

    /** Hồi quy (test 46): messages.yml 0.1.0, quest không có lệnh, money &gt; 0: lore y hệt 0.1.0. */
    @Test
    void oldMessagesWithoutCommandsRenderLikeBefore() {
        YamlConfiguration yml = yml("messages-0.1.0.yml");
        Messages m = new Messages(yml);
        List<String> desc = List.of("Đào x5.000 block đá", "ở /warp mine");
        for (String path : QUEST_LORES) {
            assertEquals(legacyLines(yml, path, "description", desc, PH),
                    m.lines(path, lists(desc, List.of(), List.of("20.000")), PH), path);
        }
        // Ô trạng thái và màn xác nhận: trước đây là lines(path, ph)
        for (String path : new String[] {"menu.status.active.lore", "accept-confirm.info.lore"}) {
            Map<String, List<String>> rewardAndMoney = new LinkedHashMap<>();
            rewardAndMoney.put("reward", List.of());
            rewardAndMoney.put("money", List.of("20.000"));
            assertEquals(m.lines(path, PH), m.lines(path, rewardAndMoney, PH), path);
        }
    }

    @Test
    void emptyListDropsTheLine() {
        Messages m = new Messages(TestSupport.yaml("""
                lore:
                  - 'a'
                  - '&7• &a${money}'
                  - '&7• {reward}'
                  - 'b'
                """));
        assertEquals(List.of("a", "b"), plain(m.lines("lore", lists(List.of(), List.of(), List.of()))));
        assertEquals(List.of("a", "• $20.000", "• 50 Gem", "• 3 Key", "b"),
                plain(m.lines("lore", lists(List.of(), List.of("&b50 Gem", "&e3 Key"), List.of("20.000")))));
    }

    @Test
    void onlyTheFirstListKeyOnALineExpands() {
        Messages m = new Messages(TestSupport.yaml("""
                lore:
                  - '{money} | {reward} | {count}'
                  - '{reward}+{money}'
                """));
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("reward", List.of("R1", "R2"));
        lists.put("money", List.of("M"));
        // Dòng 1: khoá đầu tiên theo thứ tự của Map là 'reward' (không phải theo vị trí trong dòng); 'money' thành rỗng
        assertEquals(List.of(" | R1 | 7", " | R2 | 7", "R1+", "R2+"), plain(m.lines("lore", lists, "count", "7")));

        lists.put("reward", List.of());
        // 'reward' rỗng: bỏ cả hai dòng, dù 'money' có giá trị
        assertEquals(List.of(), plain(m.lines("lore", lists, "count", "7")));
    }

    @Test
    void bundledLoresShowRewardsAndHideMoneyWhenZero() {
        Messages m = TestSupport.messages();
        List<String> withMoney = plain(m.lines("menu.quest.available.lore",
                lists(List.of("Mô tả"), List.of("&b50 Gem"), List.of("20.000")), PH));
        assertEquals(List.of("Phần thưởng:", "• 20.000$", "• 50 Gem"),
                withMoney.subList(withMoney.indexOf("Phần thưởng:"), withMoney.indexOf("Phần thưởng:") + 3));

        List<String> noMoney = plain(m.lines("menu.quest.available.lore",
                lists(List.of("Mô tả"), List.of("&b50 Gem"), List.of()), PH));
        assertEquals(withMoney.size() - 1, noMoney.size());
        assertEquals("• 50 Gem", noMoney.get(noMoney.indexOf("Phần thưởng:") + 1));

        Map<String, List<String>> confirmLists = new LinkedHashMap<>();
        confirmLists.put("reward", List.of("&b50 Gem"));
        confirmLists.put("money", List.of());
        List<String> confirm = plain(m.lines("accept-confirm.info.lore", confirmLists, PH));
        assertEquals(List.of("• Phần thưởng: 50 Gem"), confirm.stream().filter(s -> s.startsWith("• Phần thưởng")).toList());
    }

    @Test
    void logVerbatimKeepsColorCodesOfTheValue() {
        Messages m = TestSupport.messages();
        String line = m.logVerbatim("log.reward.command-failed", "command", "broadcast &bXin chào {player}",
                "quest", "mine-t3", "player", "Steve", "uuid", "u-1", "error", "x");
        assertEquals("Lệnh thưởng của 'mine-t3' cho Steve (u-1) thất bại: /broadcast &bXin chào {player} (x). "
                + "Cần chạy lại bằng tay.", line);
    }
}
