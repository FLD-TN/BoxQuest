package vn.boxpvp.boxquest.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import vn.boxpvp.boxquest.TestSupport;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.quest.Quest;
import vn.boxpvp.boxquest.quest.objective.MineBlockObjective;

class RewardServiceTest {

    private static final String UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private final Messages m = TestSupport.messages();
    private final TestSupport.Capture log = new TestSupport.Capture();
    private final List<String> dispatched = new ArrayList<>();

    private static Quest quest(String... commands) {
        List<CommandReward> rewards = new ArrayList<>();
        for (String c : commands) rewards.add(new CommandReward(CommandTemplate.parse(c), null));
        return new Quest("mine-t3", "&aT3", Material.STONE, List.of(),
                new MineBlockObjective(List.of("mine"), Set.of(Material.STONE)), 5, 0, rewards);
    }

    private String failed(String command, String error) {
        return m.logVerbatim("log.reward.command-failed", "command", command,
                "quest", "mine-t3", "player", "Steve", "uuid", UUID, "error", error);
    }

    @Test
    void runsEveryCommandInFileOrderWithTheGivenMultiplier() {
        RewardService.runCommands(quest("a {player} {boost:50}", "b {multiplier} {quest}", "c {uuid}"),
                "Steve", UUID, 30, c -> dispatched.add(c), log.logger, m);
        assertEquals(List.of("a Steve 150", "b 3 mine-t3", "c " + UUID), dispatched);
        assertTrue(log.records.isEmpty());
    }

    @Test
    void decimalMultiplier() {
        RewardService.runCommands(quest("boxcore gem give {player} {boost:5} quest:{quest}", "say x{multiplier}"),
                "Steve", UUID, 15, c -> dispatched.add(c), log.logger, m);
        assertEquals(List.of("boxcore gem give Steve 8 quest:mine-t3", "say x1.5"), dispatched);
        assertTrue(log.records.isEmpty());
    }

    @Test
    void rejectedCommandLogsSevereVerbatimAndContinues() {
        RewardService.runCommands(quest("boxcore gem giv {player} {boost:50}", "say &bok {player}"),
                "Steve", UUID, 20, c -> dispatched.add(c) && !c.startsWith("boxcore"), log.logger, m);
        assertEquals(List.of("boxcore gem giv Steve 100", "say &bok Steve"), dispatched);
        assertEquals(List.of(failed("boxcore gem giv Steve 100", m.log("log.reward.command-rejected"))),
                log.messages(Level.SEVERE));
    }

    @Test
    void throwingCommandDoesNotStopTheNextOnesAndNeverEscapes() {
        RewardService.runCommands(quest("first", "second", "third"), "Steve", UUID, 10, c -> {
            dispatched.add(c);
            if (c.equals("first")) throw new IllegalStateException("boom");
            if (c.equals("second")) throw new StackOverflowError();
            return true;
        }, log.logger, m);
        assertEquals(List.of("first", "second", "third"), dispatched);
        assertEquals(List.of(failed("first", "java.lang.IllegalStateException: boom"),
                failed("second", "java.lang.StackOverflowError")), log.messages(Level.SEVERE));
        assertTrue(log.records.get(0).getThrown() instanceof IllegalStateException, "kèm stack trace trong log");
    }

    @Test
    void overflowWarnsAndUsesLongMax() {
        RewardService.runCommands(quest("give {player} {boost:1000000000000}"), "Steve", UUID, Integer.MAX_VALUE,
                c -> dispatched.add(c), log.logger, m);
        assertEquals(List.of("give Steve " + Long.MAX_VALUE), dispatched);
        assertEquals(1, log.messages(Level.WARNING).size());
        assertTrue(log.messages(Level.WARNING).get(0).contains("/give {player} {boost:1000000000000}"));
    }

    @Test
    void noCommandsDoesNothing() {
        RewardService.runCommands(quest(), "Steve", UUID, 10, c -> dispatched.add(c), log.logger, m);
        assertTrue(dispatched.isEmpty());
        assertTrue(log.records.isEmpty());
    }
}
