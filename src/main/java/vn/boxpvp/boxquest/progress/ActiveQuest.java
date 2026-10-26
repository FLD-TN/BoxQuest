package vn.boxpvp.boxquest.progress;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trạng thái của một người chơi (đúng một dòng trong database). Chỉ đọc/ghi trên main thread;
 * luồng async chỉ nhận bản chụp bất biến {@link Row}.
 */
public final class ActiveQuest {

    /**
     * Bộ đếm phiên bản toàn cục, tăng đơn điệu. Mỗi lần thay đổi nhận số mới, nhờ đó ProgressStorage
     * bỏ qua được bản chụp cũ ghi đến muộn (ví dụ lượt flush async chạy sau lần ghi ngay khi hoàn thành).
     */
    private static final AtomicLong VERSIONS = new AtomicLong();

    private final UUID uuid;
    private String questId;
    private int count;
    private int completions;
    private double totalMoney;
    private boolean autoRepeat;
    private boolean showActionbar;
    private boolean sound;
    private boolean showBossbar;

    private boolean dirty;
    private long version = VERSIONS.incrementAndGet();

    ActiveQuest(UUID uuid, String questId, int count, int completions, double totalMoney,
                boolean autoRepeat, boolean showActionbar, boolean sound, boolean showBossbar) {
        this.uuid = uuid;
        this.questId = questId;
        this.count = count;
        this.completions = completions;
        this.totalMoney = totalMoney;
        this.autoRepeat = autoRepeat;
        this.showActionbar = showActionbar;
        this.sound = sound;
        this.showBossbar = showBossbar;
    }

    static ActiveQuest fromRow(Row row) {
        return new ActiveQuest(row.uuid(), row.questId(), row.count(), row.completions(), row.totalMoney(),
                row.autoRepeat(), row.showActionbar(), row.sound(), row.showBossbar());
    }

    /** Bản chụp để ghi đĩa; gọi trên main thread. */
    Row snapshot() {
        return new Row(uuid, questId, count, completions, totalMoney, autoRepeat, showActionbar, sound, showBossbar,
                version);
    }

    public void markDirty() {
        dirty = true;
        version = VERSIONS.incrementAndGet();
    }

    boolean dirty() {
        return dirty;
    }

    void clearDirty() {
        dirty = false;
    }

    /** Khôi phục cờ dirty sau lần ghi thất bại, nếu bản ghi chưa đổi từ lúc chụp. */
    void redirtyIf(long snapshotVersion) {
        if (version == snapshotVersion) dirty = true;
    }

    /** Tăng mỗi lần thay đổi; menu dùng để biết có cần vẽ lại không. */
    public long version() {
        return version;
    }

    public UUID uuid() {
        return uuid;
    }

    public String questId() {
        return questId;
    }

    public void setQuestId(String questId) {
        this.questId = questId;
    }

    public int count() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int completions() {
        return completions;
    }

    public void setCompletions(int completions) {
        this.completions = completions;
    }

    public double totalMoney() {
        return totalMoney;
    }

    public void setTotalMoney(double totalMoney) {
        this.totalMoney = totalMoney;
    }

    public boolean autoRepeat() {
        return autoRepeat;
    }

    public void setAutoRepeat(boolean autoRepeat) {
        this.autoRepeat = autoRepeat;
    }

    public boolean showActionbar() {
        return showActionbar;
    }

    public void setShowActionbar(boolean showActionbar) {
        this.showActionbar = showActionbar;
    }

    public boolean sound() {
        return sound;
    }

    public void setSound(boolean sound) {
        this.sound = sound;
    }

    public boolean showBossbar() {
        return showBossbar;
    }

    public void setShowBossbar(boolean showBossbar) {
        this.showBossbar = showBossbar;
    }

    /** Bản chụp bất biến của một dòng, an toàn để chuyển sang luồng async. */
    public record Row(UUID uuid, String questId, int count, int completions, double totalMoney,
                      boolean autoRepeat, boolean showActionbar, boolean sound, boolean showBossbar,
                      long version) {
    }
}
