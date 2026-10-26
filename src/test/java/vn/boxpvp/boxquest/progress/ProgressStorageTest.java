package vn.boxpvp.boxquest.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.boxpvp.boxquest.TestSupport;
import vn.boxpvp.boxquest.progress.ActiveQuest.Row;

class ProgressStorageTest {

    private static final UUID STEVE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir
    Path dir;

    private final TestSupport.Capture log = new TestSupport.Capture();
    private ProgressStorage storage;

    private File db() {
        return dir.resolve("data.db").toFile();
    }

    private ProgressStorage open() throws Exception {
        storage = new ProgressStorage(log.logger, TestSupport.messages());
        storage.open(db());
        return storage;
    }

    @AfterEach
    void close() {
        if (storage != null) storage.close();
    }

    /** Database của 0.2.1 (chưa có show_bossbar): thêm cột, người chơi cũ có bossbar BẬT, dữ liệu cũ giữ nguyên. */
    @Test
    void migratesDatabaseFromBefore022() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db().getAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE players (
                        uuid TEXT PRIMARY KEY, quest_id TEXT NULL,
                        count INTEGER NOT NULL DEFAULT 0, completions INTEGER NOT NULL DEFAULT 0,
                        total_money REAL NOT NULL DEFAULT 0, auto_repeat INTEGER NOT NULL,
                        show_actionbar INTEGER NOT NULL, sound INTEGER NOT NULL)
                    """);
            st.execute("INSERT INTO players VALUES ('" + STEVE + "', 'mine-t3', 1234, 7, 140000.5, 1, 0, 1)");
        }

        Row row = open().load(STEVE).orElseThrow();
        assertEquals("mine-t3", row.questId());
        assertEquals(1234, row.count());
        assertEquals(7, row.completions());
        assertEquals(140000.5, row.totalMoney());
        assertTrue(row.autoRepeat());
        assertFalse(row.showActionbar());
        assertTrue(row.sound());
        assertTrue(row.showBossbar(), "người chơi cũ: bossbar mặc định bật");
        assertEquals(List.of(TestSupport.messages().log("log.database.migrated-bossbar")), log.messages(Level.INFO));

        // Mở lại lần hai: không migrate nữa
        storage.close();
        log.records.clear();
        open();
        assertTrue(log.records.isEmpty());
    }

    @Test
    void bossbarFlagRoundTrips() throws Exception {
        open();
        ActiveQuest active = new ActiveQuest(STEVE, "mine-t5", 10, 2, 500, false, true, false, false);
        active.markDirty();
        assertTrue(storage.write(List.of(active.snapshot())));
        assertFalse(storage.load(STEVE).orElseThrow().showBossbar());

        active.setShowBossbar(true);
        active.markDirty();
        assertTrue(storage.write(List.of(active.snapshot())));
        Row row = storage.load(STEVE).orElseThrow();
        assertTrue(row.showBossbar());
        assertEquals("mine-t5", row.questId());
        assertEquals(10, row.count());
    }

    @Test
    void olderSnapshotIsIgnored() throws Exception {
        open();
        ActiveQuest active = new ActiveQuest(STEVE, "q", 1, 0, 0, false, true, true, true);
        active.markDirty();
        Row old = active.snapshot();
        active.setCount(2);
        active.markDirty();
        assertTrue(storage.write(List.of(active.snapshot())));
        assertTrue(storage.write(List.of(old)));
        assertEquals(2, storage.load(STEVE).orElseThrow().count());
    }

    private static final UUID ALEX = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    @Test
    void synchronousIsNormal() throws Exception {
        open();
        assertEquals(1, storage.synchronousMode()); // NORMAL: a commit does not wait for the disk
    }

    @Test
    void retainVersionsDropsOfflinePlayersOnly() throws Exception {
        open();
        ActiveQuest steve = new ActiveQuest(STEVE, "q", 1, 0, 0, false, true, true, true);
        ActiveQuest alex = new ActiveQuest(ALEX, "q", 1, 0, 0, false, true, true, true);
        steve.markDirty();
        alex.markDirty();
        assertTrue(storage.write(List.of(steve.snapshot(), alex.snapshot())));
        assertEquals(2, storage.trackedVersions());
        storage.retainVersions(java.util.Set.of(STEVE)); // Alex left, no async write in flight
        assertEquals(1, storage.trackedVersions());
        // Steve keeps his mark: an older snapshot of Steve is still ignored
        Row old = steve.snapshot();
        steve.setCount(5);
        steve.markDirty();
        assertTrue(storage.write(List.of(steve.snapshot())));
        assertTrue(storage.write(List.of(old)));
        assertEquals(5, storage.load(STEVE).orElseThrow().count());
    }

    @Test
    void repeatedWriteFailuresAreLoggedOncePerCooldown() throws Exception {
        open();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db().getAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute("DROP TABLE players");
        }
        ActiveQuest steve = new ActiveQuest(STEVE, "q", 1, 0, 0, false, true, true, true);
        for (int i = 0; i < 5; i++) {
            steve.markDirty();
            assertFalse(storage.write(List.of(steve.snapshot())));
        }
        assertEquals(1, log.messages(Level.SEVERE).size());
    }
}
