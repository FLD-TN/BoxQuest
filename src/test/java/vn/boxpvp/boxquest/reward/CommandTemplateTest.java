package vn.boxpvp.boxquest.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandTemplateTest {

    private static final String NAME = "Steve";
    private static final String UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String QUEST = "mine-t3";

    /** Lệnh với hệ số nguyên {@code multiplier} (truyền vào resolve theo phần mười). */
    private static String command(String text, int multiplier) {
        return commandTenths(text, multiplier * 10);
    }

    private static String commandTenths(String text, int tenths) {
        return CommandTemplate.parse(text).resolve(NAME, UUID, QUEST, tenths, false);
    }

    private static String menu(String text) {
        return CommandTemplate.parse(text).resolve(NAME, UUID, QUEST, 75, true);
    }

    @Test
    void everyPlaceholderInCommandMode() {
        assertEquals("give Steve 069a79f4-44e9-4726-a5be-fca90e38aaf5 mine-t3 x3 150",
                command("give {player} {uuid} {quest} x{multiplier} {boost:50}", 3));
    }

    @Test
    void everyPlaceholderInMenuMode() {
        // Menu: {multiplier} là 1 và {boost:N} là N chưa nhân, dù hệ số truyền vào là gì
        assertEquals("Steve 069a79f4-44e9-4726-a5be-fca90e38aaf5 mine-t3 x1 50",
                menu("{player} {uuid} {quest} x{multiplier} {boost:50}"));
    }

    @Test
    void boostMultipliesByTheCompletionMultiplier() {
        String text = "boxcore gem give {player} {boost:50} quest:{quest}";
        assertEquals("boxcore gem give Steve 50 quest:mine-t3", command(text, 1));
        assertEquals("boxcore gem give Steve 150 quest:mine-t3", command(text, 3));
        assertEquals("boxcore gem give Steve 5000 quest:mine-t3", command(text, 100));
    }

    @Test
    void decimalMultiplierRoundsHalfUp() {
        String text = "boxcore gem give {player} {boost:5} quest:{quest} x{multiplier}";
        assertEquals("boxcore gem give Steve 8 quest:mine-t3 x1.5", commandTenths(text, 15));   // 7,5 -> 8
        assertEquals("5", commandTenths("{boost:3}", 15));    // 4,5 -> 5
        assertEquals("3", commandTenths("{boost:2}", 15));    // 3
        assertEquals("4", commandTenths("{boost:3}", 13));    // 3,9 -> 4
        assertEquals("3", commandTenths("{boost:3}", 11));    // 3,3 -> 3
        assertEquals("125", commandTenths("{boost:10}", 125));
        assertEquals("x1.1 x2 x12.5", commandTenths("x{multiplier}", 11) + " " + commandTenths("x{multiplier}", 20)
                + " " + commandTenths("x{multiplier}", 125));
        // menu: chưa nhân, dù hệ số lẻ
        assertEquals("5 x1", CommandTemplate.parse("{boost:5} x{multiplier}").resolve(NAME, UUID, QUEST, 15, true));
    }

    @Test
    void commandNumbersHaveNoGroupingButMenuNumbersDo() {
        assertEquals("4500", command("{boost:1500}", 3));
        assertEquals("1.500", menu("{boost:1500}"));
        assertEquals("&b1.000.000 Gem", menu("&b{boost:1000000} Gem"));
    }

    @Test
    void boostBounds() {
        assertEquals("1", command("{boost:1}", 1));
        assertEquals("1000000000000", command("{boost:1000000000000}", 1));
        assertEquals("100000000000000", command("{boost:1000000000000}", 100));
        assertEquals("1.000.000.000.000", menu("{boost:1000000000000}"));
        assertEquals("7", command("{boost:007}", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{boost:0}", "{boost:1000000000001}", "{boost:abc}", "{boost:}", "{boost:-5}",
            "{boost:+5}", "{boost: 5}", "{boost:5 }", "{boost:1.5}", "{boost:1e3}", "{boost:99999999999999999999999}",
            "give {player} {boost:0} x"})
    void invalidBoostIsRejected(String text) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CommandTemplate.parse(text));
        assertTrue(e.getMessage().startsWith("{boost:"), e.getMessage());
    }

    @Test
    void overflowSaturatesAtLongMax() {
        CommandTemplate template = CommandTemplate.parse("{boost:1000000000000}");
        assertFalse(template.overflows(1000));
        assertFalse(template.overflows(100_000)); // x10000, giới hạn của BoxCore
        assertTrue(template.overflows(Integer.MAX_VALUE));
        assertEquals(String.valueOf(Long.MAX_VALUE),
                template.resolve(NAME, UUID, QUEST, Integer.MAX_VALUE, false));
        assertEquals(Long.MAX_VALUE, CommandTemplate.boost(Long.MAX_VALUE, 20));
        assertEquals(Long.MAX_VALUE, CommandTemplate.boost(Long.MAX_VALUE / 10 + 1, 10));
        assertFalse(CommandTemplate.parse("no boost here").overflows(Integer.MAX_VALUE));
    }

    @Test
    void unknownPlaceholderKeptVerbatimAndReported() {
        CommandTemplate template = CommandTemplate.parse("say {foo} {player} {foo} {Player} {abc}");
        assertEquals(List.of("{foo}", "{Player}", "{abc}"), template.unknownPlaceholders());
        assertEquals("say {foo} Steve {foo} {Player} {abc}", template.resolve(NAME, UUID, QUEST, 20, false));
        assertEquals("say {foo} Steve {foo} {Player} {abc}", template.resolve(NAME, UUID, QUEST, 20, true));
    }

    @Test
    void jsonBracesAreTextNotPlaceholders() {
        String text = "tellraw {player} {\"text\":\"{player} +{boost:5} Gem\",\"color\":\"aqua\"}";
        CommandTemplate template = CommandTemplate.parse(text);
        assertEquals(List.of(), template.unknownPlaceholders());
        assertEquals("tellraw Steve {\"text\":\"Steve +10 Gem\",\"color\":\"aqua\"}",
                template.resolve(NAME, UUID, QUEST, 20, false));
    }

    @Test
    void unbalancedBracesAreText() {
        assertEquals("a { b } c {", command("a { b } c {", 1));
        assertEquals("{{Steve}}", command("{{{player}}}", 1));
        assertEquals("}{", command("}{", 1));
        assertEquals(List.of(), CommandTemplate.parse("a {} b").unknownPlaceholders());
    }

    @Test
    void plainTextAndEmpty() {
        assertEquals("", command("", 3));
        assertEquals("broadcast &bXin chào", command("broadcast &bXin chào", 3));
    }

    @Test
    void leadingSlashAndWhitespaceNormalized() {
        assertEquals("boxcore gem give {player} 10", CommandTemplate.normalizeCommand("/boxcore gem give {player} 10"));
        assertEquals("boxcore gem give", CommandTemplate.normalizeCommand("  / boxcore gem give  "));
        assertEquals("/wand", CommandTemplate.normalizeCommand("//wand"));
        assertEquals("", CommandTemplate.normalizeCommand(" / "));
        assertEquals("say hi", CommandTemplate.normalizeCommand("say hi"));
    }

    @Test
    void sourceIsTheParsedText() {
        assertEquals("give {player} {boost:5}", CommandTemplate.parse("give {player} {boost:5}").source());
    }
}
