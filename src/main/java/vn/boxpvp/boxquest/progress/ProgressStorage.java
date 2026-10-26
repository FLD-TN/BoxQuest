package vn.boxpvp.boxquest.progress;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import vn.boxpvp.boxquest.config.Messages;
import vn.boxpvp.boxquest.progress.ActiveQuest.Row;
import vn.boxpvp.boxquest.util.ErrorLog;

/**
 * SQLite tại {@code plugins/BoxQuest/data.db}. Một kết nối, mọi thao tác {@code synchronized} vì được gọi
 * từ cả main thread (hoàn thành / thoát / tắt plugin) lẫn luồng async (flush định kỳ).
 */
public final class ProgressStorage {

    private static final String UPSERT = """
            INSERT INTO players (uuid, quest_id, count, completions, total_money, auto_repeat, show_actionbar, sound,
                                 show_bossbar)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                quest_id = excluded.quest_id,
                count = excluded.count,
                completions = excluded.completions,
                total_money = excluded.total_money,
                auto_repeat = excluded.auto_repeat,
                show_actionbar = excluded.show_actionbar,
                sound = excluded.sound,
                show_bossbar = excluded.show_bossbar
            """;

    private final Logger log;
    private final Messages messages;
    private Connection conn;

    /** Phiên bản mới nhất đã ghi cho mỗi UUID; bản chụp cũ hơn bị bỏ qua. Dọn bằng {@link #retainVersions}. */
    private final Map<UUID, Long> writtenVersions = new HashMap<>();
    /** Database hỏng thì mỗi block đều thử ghi: stack trace tối đa một lần mỗi {@link ErrorLog#COOLDOWN_MILLIS}. */
    private long lastFailureLog;
    private int suppressedFailures;

    public ProgressStorage(Logger log, Messages messages) {
        this.log = log;
        this.messages = messages;
    }

    public synchronized void open(File file) throws SQLException {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new SQLException(messages.log("log.database.mkdir-failed", "dir", dir.toString()));
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException(messages.log("log.database.driver-missing"), e);
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            // WAL + NORMAL: commit không chờ ổ đĩa (fsync chỉ lúc checkpoint), nên lần hoàn thành ghi trên main
            // thread không làm tụt TPS. Commit vẫn còn khi tiến trình crash hoặc bị kill; chỉ khi mất điện / OS sập
            // mới có thể mất vài commit cuối: tiến độ lùi về lần lưu trước, người chơi phải đào lại số block đó
            // (không có phần thưởng miễn phí). FULL chờ ổ đĩa ở MỖI commit (vài ms, VPS có thể hơn).
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid           TEXT PRIMARY KEY,
                        quest_id       TEXT NULL,
                        count          INTEGER NOT NULL DEFAULT 0,
                        completions    INTEGER NOT NULL DEFAULT 0,
                        total_money    REAL    NOT NULL DEFAULT 0,
                        auto_repeat    INTEGER NOT NULL,
                        show_actionbar INTEGER NOT NULL,
                        sound          INTEGER NOT NULL,
                        show_bossbar   INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            migrate(st);
        }
    }

    /**
     * Database của bản trước 0.2.2 thiếu cột {@code show_bossbar}: thêm cột, mặc định BẬT cho mọi người chơi cũ.
     * Bản cũ vẫn đọc/ghi được database đã thêm cột (câu lệnh của nó liệt kê cột tường minh).
     */
    private void migrate(Statement st) throws SQLException {
        boolean hasBossbar = false;
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(players)")) {
            while (rs.next()) {
                if ("show_bossbar".equalsIgnoreCase(rs.getString("name"))) hasBossbar = true;
            }
        }
        if (!hasBossbar) {
            st.execute("ALTER TABLE players ADD COLUMN show_bossbar INTEGER NOT NULL DEFAULT 1");
            log.info(messages.log("log.database.migrated-bossbar"));
        }
    }

    public synchronized Optional<Row> load(UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quest_id, count, completions, total_money, auto_repeat, show_actionbar, sound, show_bossbar "
                        + "FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Row(uuid,
                        rs.getString(1),
                        rs.getInt(2),
                        rs.getInt(3),
                        rs.getDouble(4),
                        rs.getInt(5) != 0,
                        rs.getInt(6) != 0,
                        rs.getInt(7) != 0,
                        rs.getInt(8) != 0,
                        0L));
            }
        }
    }

    /**
     * Ghi các bản chụp trong một transaction. Bản chụp có phiên bản không mới hơn lần ghi trước bị bỏ qua.
     *
     * @return {@code false} nếu lỗi (đã rollback và ghi log)
     */
    public synchronized boolean write(List<Row> rows) {
        if (conn == null) {
            log.severe(messages.log("log.database.closed", "count", String.valueOf(rows.size())));
            return false;
        }
        List<Row> fresh = new ArrayList<>(rows.size());
        for (Row row : rows) {
            Long written = writtenVersions.get(row.uuid());
            if (written == null || row.version() > written) fresh.add(row);
        }
        if (fresh.isEmpty()) return true;

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                for (Row row : fresh) {
                    ps.setString(1, row.uuid().toString());
                    ps.setString(2, row.questId());
                    ps.setInt(3, row.count());
                    ps.setInt(4, row.completions());
                    ps.setDouble(5, row.totalMoney());
                    ps.setInt(6, row.autoRepeat() ? 1 : 0);
                    ps.setInt(7, row.showActionbar() ? 1 : 0);
                    ps.setInt(8, row.sound() ? 1 : 0);
                    ps.setInt(9, row.showBossbar() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            for (Row row : fresh) writtenVersions.put(row.uuid(), row.version());
            return true;
        } catch (SQLException e) {
            logFailure(fresh.size(), e);
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // kết nối hỏng, lần ghi sau sẽ báo lỗi tiếp
            }
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // như trên
            }
        }
    }

    private void logFailure(int count, SQLException e) {
        long now = System.currentTimeMillis();
        if (lastFailureLog != 0 && now - lastFailureLog < ErrorLog.COOLDOWN_MILLIS) {
            suppressedFailures++;
            return;
        }
        String suffix = suppressedFailures > 0
                ? messages.log("log.error-repeated", "count", String.valueOf(suppressedFailures),
                        "seconds", String.valueOf(ErrorLog.COOLDOWN_MILLIS / 1000))
                : "";
        lastFailureLog = now;
        suppressedFailures = 0;
        log.log(Level.SEVERE, messages.log("log.database.write-failed", "count", String.valueOf(count)) + suffix, e);
    }

    /**
     * Bỏ phiên bản đã ghi của mọi UUID ngoài {@code keep} (người đang online), để map không lớn dần theo số người
     * từng vào server. Chỉ gọi khi KHÔNG còn lần ghi async nào đang chạy: một bản chụp cũ còn trên đường ghi mà mất
     * mốc phiên bản thì sẽ đè lên bản mới hơn đã ghi lúc người chơi thoát.
     */
    public synchronized void retainVersions(Set<UUID> keep) {
        writtenVersions.keySet().retainAll(keep);
    }

    /** Cho test. */
    synchronized int trackedVersions() {
        return writtenVersions.size();
    }

    /** Cho test: {@code PRAGMA synchronous} của kết nối này (1 = NORMAL, 2 = FULL). */
    synchronized int synchronousMode() throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA synchronous")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    public synchronized void close() {
        if (conn == null) return;
        try {
            conn.close();
        } catch (SQLException e) {
            log.log(Level.WARNING, messages.log("log.database.close-failed"), e);
        }
        conn = null;
    }
}
