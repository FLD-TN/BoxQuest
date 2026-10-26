package vn.boxpvp.boxquest.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import vn.boxpvp.boxquest.config.Messages;

/** Định dạng số kiểu Việt Nam (3.100 / 20.000,5) và thanh tiến độ. Chỉ gọi từ main thread. */
public final class Format {

    private static final int DEFAULT_BAR_LENGTH = 20;
    private static final int MAX_BAR_LENGTH = 60;

    private static final DecimalFormat INT;
    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        INT = new DecimalFormat("#,##0", sym);
        MONEY = new DecimalFormat("#,##0.##", sym);
    }

    private Format() {
    }

    public static String number(long n) {
        return INT.format(n);
    }

    public static String money(double amount) {
        return MONEY.format(amount);
    }

    /** Hệ số theo phần mười: {@code 20} → {@code 2}, {@code 15} → {@code 1.5} (luôn dấu chấm, dùng được trong lệnh). */
    public static String multiplier(int tenths) {
        int decimal = tenths % 10;
        return decimal == 0 ? Integer.toString(tenths / 10) : (tenths / 10) + "." + decimal;
    }

    public static int percent(int count, int amount) {
        return (int) Math.min(100L, Math.max(0L, (long) count * 100L / amount));
    }

    /** Thanh tiến độ theo {@code progress-bar.*} trong messages.yml. */
    public static String bar(int count, int amount, Messages messages) {
        int length = Math.max(1, Math.min(MAX_BAR_LENGTH, messages.integer("progress-bar.length", DEFAULT_BAR_LENGTH)));
        String filled = messages.raw("progress-bar.filled");
        String empty = messages.raw("progress-bar.empty");
        int cells = (int) Math.min(length, Math.max(0L, (long) count * length / amount));
        StringBuilder sb = new StringBuilder(length * (filled.length() + 2));
        for (int i = 0; i < length; i++) sb.append(i < cells ? filled : empty);
        return sb.toString();
    }
}
