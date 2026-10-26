package vn.boxpvp.boxquest.hook;

import com.dex.boxcore.api.BoostService;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Lớp DUY NHẤT tham chiếu tới {@code com.dex.boxcore.api}. Chỉ được nạp khi BoxCore đang bật — nếu không,
 * các lớp API không tồn tại lúc chạy và việc nạp lớp này sẽ ném {@link NoClassDefFoundError}.
 * Mọi lời gọi đi qua {@code BoxQuestPlugin#questMultiplier}, nơi giữ cờ "BoxCore có sẵn".
 */
public final class BoxCoreHook {

    private static BoostService service;
    /**
     * {@code false} khi BoxCore đang chạy là bản cũ (API trước 1.2.0, BoxCore trước 1.4.0): interface trên server
     * chưa có {@code getMultiplierTenths}, gọi sẽ ném {@link NoSuchMethodError}. Khi đó dùng hệ số nguyên x 10.
     */
    private static boolean tenthsAvailable = true;

    private BoxCoreHook() {
    }

    /** Gọi khi bật plugin, khi {@code /quest reload} và khi BoxCore được bật lại. */
    public static boolean init() {
        tenthsAvailable = true;
        service = Bukkit.getPluginManager().isPluginEnabled("BoxCore")
                ? Bukkit.getServicesManager().load(BoostService.class)
                : null;
        return service != null;
    }

    public static void reset() {
        service = null;
    }

    /**
     * Hệ số của người chơi cho một loại boost, theo phần mười ({@code 15} = x1.5, {@code 10} = x1).
     * Không cache — đọc lại mỗi lần trả thưởng.
     */
    public static int multiplierTenths(UUID player, String type) {
        BoostService s = service;
        if (s == null) return 10;
        if (tenthsAvailable) {
            try {
                return s.getMultiplierTenths(player, type);
            } catch (NoSuchMethodError oldBoxCore) {
                tenthsAvailable = false; // BoxCore < 1.4.0: chỉ có hệ số nguyên
            }
        }
        return s.getMultiplier(player, type) * 10;
    }
}
