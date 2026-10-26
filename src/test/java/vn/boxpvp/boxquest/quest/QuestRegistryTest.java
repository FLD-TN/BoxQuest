package vn.boxpvp.boxquest.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import vn.boxpvp.boxquest.TestSupport;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.quest.objective.MineBlockObjective;
import vn.boxpvp.boxquest.reward.CommandReward;

class QuestRegistryTest {

    /**
     * Region tồn tại trừ khi tên bắt đầu bằng {@code missing}; Material#isItem/isBlock cần registry của server nên
     * thay bằng tập cố định.
     */
    private static final QuestRegistry.Environment ENV = new QuestRegistry.Environment() {
        @Override
        public boolean indexRegion(String region) {
            return !region.startsWith("missing");
        }

        @Override
        public boolean isIcon(Material material) {
            return material != Material.AIR;
        }

        @Override
        public boolean isBlock(Material material) {
            return material != Material.DIAMOND;
        }
    };

    private final Messages m = TestSupport.messages();
    private final TestSupport.Capture log = new TestSupport.Capture();

    private QuestRegistry.Parsed parse(String questsYaml) {
        YamlConfiguration yml = TestSupport.yaml(questsYaml);
        return QuestRegistry.parseAll(yml.getConfigurationSection("quests"), log.logger, m, ENV);
    }

    /** Một quest {@code q} hợp lệ, phần {@code rewards} do test viết (thụt 4 cách). */
    private QuestRegistry.Parsed quest(String rewards) {
        return parse("""
                quests:
                  q:
                    display: '&aQ'
                    icon: STONE
                    objective:
                      region: mine
                      blocks: [STONE]
                    amount: 5
                """ + rewards.indent(4));
    }

    private void assertSkipped(QuestRegistry.Parsed parsed, String reasonKey, String... placeholders) {
        assertEquals(0, parsed.quests().size(), log.messages(Level.WARNING).toString());
        assertEquals(1, parsed.skipped());
        String reason = m.log("log.quest.reason." + reasonKey, placeholders);
        assertEquals(List.of(m.log("log.quest.skipped", "id", "q", "reason", reason)), log.messages(Level.WARNING));
    }

    private Quest single(QuestRegistry.Parsed parsed) {
        assertEquals(0, parsed.skipped(), log.messages(Level.WARNING).toString());
        assertEquals(1, parsed.quests().size());
        return parsed.quests().get("q");
    }

    // --- Lỗi: bỏ qua cả nhiệm vụ (bảng §5) ---

    @Test
    void commandsNotAList() {
        assertSkipped(quest("""
                rewards:
                  money: 10
                  commands: 'boxcore gem give {player} 10'
                """), "commands-type");
    }

    @Test
    void commandsMapIsNotAList() {
        assertSkipped(quest("""
                rewards:
                  money: 10
                  commands:
                    command: 'say hi'
                """), "commands-type");
    }

    @Test
    void moreThanTenCommands() {
        StringBuilder sb = new StringBuilder("rewards:\n  commands:\n");
        for (int i = 0; i < 11; i++) sb.append("    - 'say ").append(i).append("'\n");
        assertSkipped(quest(sb.toString()), "commands-count");
    }

    @Test
    void tenCommandsAreFine() {
        StringBuilder sb = new StringBuilder("rewards:\n  commands:\n");
        for (int i = 0; i < 10; i++) sb.append("    - 'say ").append(i).append("'\n");
        assertEquals(10, single(quest(sb.toString())).commandRewards().size());
    }

    @Test
    void entryIsANumber() {
        assertSkipped(quest("""
                rewards:
                  money: 10
                  commands:
                    - 'say ok'
                    - 5
                """), "command-entry", "index", "1");
    }

    @Test
    void entryMapWithoutCommand() {
        assertSkipped(quest("""
                rewards:
                  money: 10
                  commands:
                    - display: '&b50 Gem'
                """), "command-entry", "index", "0");
    }

    @Test
    void entryCommandNotAString() {
        assertSkipped(quest("""
                rewards:
                  commands:
                    - command: 123
                """), "command-entry", "index", "0");
    }

    @Test
    void entryDisplayNotAString() {
        assertSkipped(quest("""
                rewards:
                  commands:
                    - command: 'say hi'
                      display: [a, b]
                """), "command-entry", "index", "0");
    }

    @Test
    void entryIsAList() {
        assertSkipped(quest("""
                rewards:
                  commands:
                    - [say, hi]
                """), "command-entry", "index", "0");
    }

    @Test
    void commandEmptyAfterTrimAndSlash() {
        assertSkipped(quest("""
                rewards:
                  commands:
                    - ' / '
                """), "command-empty", "index", "0");
    }

    @Test
    void commandTooLong() {
        String longCommand = "say " + "x".repeat(QuestRegistry.MAX_COMMAND_LENGTH - 3);
        assertSkipped(quest("rewards:\n  commands:\n    - '/" + longCommand + "'\n"), "command-empty", "index", "0");
    }

    @Test
    void commandOfExactly256CharactersIsFine() {
        String command = "say " + "x".repeat(QuestRegistry.MAX_COMMAND_LENGTH - 4);
        Quest q = single(quest("rewards:\n  commands:\n    - '  /" + command + "  '\n"));
        assertEquals(command, q.commandRewards().get(0).command().source());
    }

    @Test
    void invalidBoostInCommand() {
        for (String boost : new String[] {"{boost:abc}", "{boost:0}", "{boost:1000000000001}"}) {
            log.records.clear();
            assertSkipped(quest("rewards:\n  money: 10\n  commands:\n    - 'boxcore gem give {player} " + boost + "'\n"),
                    "command-boost", "index", "0");
        }
    }

    @Test
    void invalidBoostInDisplay() {
        assertSkipped(quest("""
                rewards:
                  commands:
                    - 'say ok'
                    - command: 'boxcore gem give {player} {boost:5}'
                      display: '&b{boost:x} Gem'
                """), "command-boost", "index", "1");
    }

    // --- Hợp lệ ---

    @Test
    void stringAndMapFormsAndSlashStripped() {
        Quest q = single(quest("""
                rewards:
                  money: 20000
                  commands:
                    - '/boxcore gem give {player} 10'
                    - command: 'boxcore gem give {player} {boost:50} quest:{quest}'
                      display: '&b{boost:50} Gem'
                    - command: 'say hi'
                      display: '   '
                """));
        List<CommandReward> rewards = q.commandRewards();
        assertEquals(3, rewards.size());
        assertEquals("boxcore gem give {player} 10", rewards.get(0).command().source());
        assertNull(rewards.get(0).display());
        assertEquals("boxcore gem give Steve 150 quest:q",
                rewards.get(1).command().resolve("Steve", "u", q.id(), 30, false));
        assertEquals("&b50 Gem", rewards.get(1).display().resolve("Steve", "u", q.id(), 30, true));
        assertNull(rewards.get(2).display(), "display trắng thì không hiện");
        assertEquals(List.of(), log.messages(Level.WARNING));
    }

    @Test
    void commandsListIsImmutable() {
        Quest q = single(quest("rewards:\n  commands:\n    - 'say hi'\n"));
        try {
            q.commandRewards().clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("commandRewards phải bất biến");
    }

    @Test
    void moneyZeroWithCommandsHasNoWarning() {
        Quest q = single(quest("""
                rewards:
                  money: 0
                  commands:
                    - 'boxcore gem give {player} {boost:50}'
                """));
        assertEquals(0, q.rewardMoney());
        assertEquals(List.of(), log.messages(Level.WARNING));
    }

    // --- Cảnh báo (nhiệm vụ vẫn nạp) ---

    @Test
    void unknownPlaceholderWarnsOncePerEntryAtLoad() {
        Quest q = single(quest("""
                rewards:
                  money: 10
                  commands:
                    - command: 'say {foo} {foo} {player}'
                      display: '{foo} {money}'
                """));
        assertEquals(1, q.commandRewards().size());
        assertEquals(List.of(
                m.log("log.quest.unknown-placeholder", "quest", "q", "placeholder", "{foo}", "index", "0"),
                m.log("log.quest.unknown-placeholder", "quest", "q", "placeholder", "{money}", "index", "0")),
                log.messages(Level.WARNING));
        assertEquals("say {foo} {foo} Steve", q.commandRewards().get(0).command().resolve("Steve", "u", "q", 10, false));
    }

    @Test
    void noRewardAtAllWarns() {
        Quest q = single(quest("rewards:\n  money: 0\n"));
        assertEquals(List.of(), q.commandRewards());
        assertEquals(List.of(m.log("log.quest.no-reward", "quest", "q")), log.messages(Level.WARNING));

        log.records.clear();
        single(quest("rewards:\n  commands: []\n"));
        assertEquals(List.of(m.log("log.quest.no-reward", "quest", "q")), log.messages(Level.WARNING));
    }

    @Test
    void badQuestDoesNotAffectOthers() {
        QuestRegistry.Parsed parsed = parse("""
                quests:
                  bad:
                    icon: STONE
                    objective: {region: mine, blocks: [STONE]}
                    amount: 5
                    rewards:
                      commands: ['give {player} {boost:0}']
                  good:
                    icon: STONE
                    objective: {region: mine, blocks: [STONE]}
                    amount: 5
                    rewards:
                      money: 1
                """);
        assertEquals(List.of("good"), List.copyOf(parsed.quests().keySet()));
        assertEquals(1, parsed.skipped());
    }

    // --- Nhiều region (0.2.1) ---

    /** Quest {@code q} hợp lệ, phần {@code objective} (trừ blocks) do test viết (thụt 6 cách). */
    private QuestRegistry.Parsed objective(String regionLines) {
        return parse("""
                quests:
                  q:
                    icon: STONE
                    amount: 5
                    rewards:
                      money: 1
                    objective:
                      blocks: [STONE]
                """ + regionLines.indent(6));
    }

    private List<String> regions(QuestRegistry.Parsed parsed) {
        return ((MineBlockObjective) single(parsed).objective()).regions();
    }

    @Test
    void npcOnlyDefaultsToFalse() {
        assertFalse(single(quest("rewards:\n  money: 10")).npcOnly());
        assertTrue(single(quest("npc-only: true\nrewards:\n  money: 10")).npcOnly());
    }

    @Test
    void singleRegionUnchanged() {
        assertEquals(List.of("mine_cap_3"), regions(objective("region: Mine_Cap_3")));
    }

    @Test
    void regionsList() {
        assertEquals(List.of("mine_cap_3", "mine_cap_4", "mine_vip"),
                regions(objective("regions: [mine_cap_3, Mine_Cap_4, ' mine_vip ']")));
    }

    @Test
    void regionAndRegionsAreMergedWithoutDuplicates() {
        assertEquals(List.of("mine_a", "mine_b", "mine_c"),
                regions(objective("""
                        region: mine_a
                        regions: [mine_b, MINE_A, mine_c, mine_b]
                        """)));
    }

    @Test
    void eitherKeyAcceptsOneNameOrAList() {
        assertEquals(List.of("mine_a"), regions(objective("regions: mine_a")));
        assertEquals(List.of("mine_a", "mine_b"), regions(objective("region: [mine_a, mine_b]")));
    }

    @Test
    void oneMissingRegionSkipsTheQuest() {
        assertSkipped(objective("regions: [mine_a, missing_x]"), "region-missing", "region", "missing_x");
    }

    @Test
    void noRegionAtAll() {
        assertSkipped(objective("regions: []"), "no-region");
        log.records.clear();
        assertSkipped(objective("region: ''"), "no-region");
        log.records.clear();
        assertSkipped(objective("# không có region"), "no-region");
    }

    @Test
    void invalidRegionEntries() {
        assertSkipped(objective("regions: [mine_a, 5]"), "region-invalid", "key", "regions");
        log.records.clear();
        assertSkipped(objective("regions: [mine_a, '  ']"), "region-invalid", "key", "regions");
        log.records.clear();
        assertSkipped(objective("""
                region:
                  name: mine_a
                """), "region-invalid", "key", "region");
    }

    @Test
    void regionsListIsImmutable() {
        List<String> regions = regions(objective("regions: [mine_a, mine_b]"));
        try {
            regions.add("x");
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("regions phải bất biến");
    }

    // --- Hồi quy ---

    /** quests.yml của 0.1.0 nạp ra đúng các Quest như trước, commandRewards rỗng, không cảnh báo. */
    @Test
    void questsFromVersion010LoadUnchanged() {
        QuestRegistry.Parsed parsed = parse(TestSupport.resource("quests-0.1.0.yml"));
        assertEquals(0, parsed.skipped());
        assertEquals(List.of(), log.messages(Level.WARNING));
        assertEquals(List.of("mine-t3", "mine-t5"), List.copyOf(parsed.quests().keySet()));

        Quest t3 = parsed.quests().get("mine-t3");
        assertEquals(new Quest("mine-t3", "&aĐào Mine T3", Material.STONE, List.of("Đào x5.000 block đá ở /warp mine"),
                new MineBlockObjective(List.of("mine_cap_3"), EnumSet.of(Material.STONE, Material.COBBLESTONE)),
                5000, 20000, List.of()), t3);

        Quest t5 = parsed.quests().get("mine-t5");
        assertEquals(new Quest("mine-t5", "&bĐào Mine T5", Material.IRON_ORE, List.of("Đào x7.000 quặng sắt", "ở /warp mine"),
                new MineBlockObjective(List.of("mine_cap_5"), EnumSet.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE)),
                7000, 42000, List.of()), t5);
    }

    /**
     * quests.yml đóng gói: 17 nhiệm vụ mine, đúng thứ tự, mỗi nhiệm vụ trả Xu và đúng một lệnh Gem. Số block, Xu và
     * Gem do admin chỉnh tay nên không ghim ở đây.
     */
    @Test
    void bundledQuestsLoad() {
        QuestRegistry.Parsed parsed = parse(TestSupport.resource("quests.yml"));
        assertEquals(0, parsed.skipped());
        assertEquals(List.of(), log.messages(Level.WARNING));
        assertEquals(List.of("stone", "coal-ore", "coal-block", "copper-ore", "copper-block", "lapis-ore", "lapis-block",
                "redstone-ore", "redstone-block", "iron-ore", "iron-block", "gold-ore", "gold-block", "diamond-ore",
                "diamond-block", "emerald-ore", "emerald-block"), List.copyOf(parsed.quests().keySet()));
        for (Quest q : parsed.quests().values()) assertFalse(q.npcOnly(), q.id());

        Quest stone = parsed.quests().get("stone");
        assertEquals(Material.STONE, stone.icon());
        assertEquals(List.of("1stone"), ((MineBlockObjective) stone.objective()).regions());
        assertEquals(List.of("2coal", "2coall"),
                ((MineBlockObjective) parsed.quests().get("coal-block").objective()).regions());

        for (Quest q : parsed.quests().values()) {
            assertTrue(q.amount() > 0, q.id() + " amount");
            assertTrue(q.rewardMoney() > 0, q.id() + " Xu");
            assertEquals(1, q.commandRewards().size(), q.id());
            String command = q.commandRewards().get(0).command().resolve("P", "u", q.id(), 10, false);
            assertTrue(command.matches("boxcore gem give P [1-9][0-9]* quest:" + q.id()), q.id() + ": " + command);
            String display = q.commandRewards().get(0).display().resolve("P", "u", q.id(), 30, true);
            assertTrue(display.endsWith(" Gem"), q.id() + ": " + display);
        }
    }
}
