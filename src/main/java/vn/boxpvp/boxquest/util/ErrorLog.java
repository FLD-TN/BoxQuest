package vn.boxpvp.boxquest.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import vn.boxpvp.boxquest.BoxQuestPlugin;

/**
 * Ghi log ngoại lệ có thời gian chờ: cùng một loại lỗi (khoá + lớp ngoại lệ + vị trí ném) chỉ in stack trace
 * tối đa một lần mỗi {@link #COOLDOWN_MILLIS}; số lần bị nén được báo kèm ở lần in kế tiếp.
 * Tránh một lỗi lặp trong BlockBreakEvent (~200 lần/giây) làm phồng log và tụt TPS. An toàn đa luồng.
 */
public final class ErrorLog {

    public static final long COOLDOWN_MILLIS = 10_000L;

    private final BoxQuestPlugin plugin;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public ErrorLog(BoxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Entry {
        long lastLogged;
        int suppressed;
    }

    /** @param errorKey khoá dưới {@code log.errors} trong messages.yml */
    public void report(String errorKey, Throwable error) {
        String key = errorKey + '|' + error.getClass().getName() + '|' + origin(error);
        long now = System.currentTimeMillis();
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        int suppressed;
        synchronized (entry) {
            if (entry.lastLogged != 0 && now - entry.lastLogged < COOLDOWN_MILLIS) {
                entry.suppressed++;
                return;
            }
            suppressed = entry.suppressed;
            entry.suppressed = 0;
            entry.lastLogged = now;
        }
        String suffix = suppressed > 0
                ? plugin.messages().log("log.error-repeated",
                        "count", String.valueOf(suppressed), "seconds", String.valueOf(COOLDOWN_MILLIS / 1000))
                : "";
        plugin.getLogger().log(Level.SEVERE, plugin.messages().log("log.errors." + errorKey) + suffix, error);
    }

    private static String origin(Throwable error) {
        StackTraceElement[] trace = error.getStackTrace();
        return trace.length == 0 ? "?" : trace[0].toString();
    }
}
