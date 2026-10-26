package vn.boxpvp.boxquest.reward;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import vn.boxpvp.boxquest.util.Format;

/**
 * Chuỗi {@code command} / {@code display} của {@code rewards.commands}, phân tích MỘT lần lúc nạp quests.yml thành
 * danh sách đoạn (chữ cố định, placeholder, {@code {boost:N}} đã tách sẵn N). Lúc trả thưởng hay dựng menu chỉ nối
 * chuỗi bằng {@link StringBuilder}: không regex, không {@code String.format}. Bất biến, không phụ thuộc server.
 *
 * <p>Placeholder: {@code {player}} {@code {uuid}} {@code {quest}} {@code {multiplier}} {@code {boost:N}}.
 * Dạng {@code {tên}} khác được giữ nguyên văn và liệt kê trong {@link #unknownPlaceholders()} để cảnh báo lúc nạp.
 * Ngoặc nhọn không có dạng placeholder (ví dụ JSON của {@code tellraw}) là chữ thường, không cảnh báo.
 */
public final class CommandTemplate {

    /** Giới hạn trên của N trong {@code {boost:N}}. */
    public static final long MAX_BOOST = 1_000_000_000_000L;

    private static final String BOOST_PREFIX = "boost:";

    private static final byte TEXT = 0;
    private static final byte PLAYER = 1;
    private static final byte UUID = 2;
    private static final byte QUEST = 3;
    private static final byte MULTIPLIER = 4;
    private static final byte BOOST = 5;

    private final String source;
    private final byte[] kinds;
    /** Chữ của đoạn {@code TEXT}, {@code null} với đoạn khác. */
    private final String[] texts;
    /** N của đoạn {@code BOOST}, 0 với đoạn khác. */
    private final long[] boosts;
    private final long maxBoost;
    private final List<String> unknown;

    private CommandTemplate(String source, byte[] kinds, String[] texts, long[] boosts, List<String> unknown) {
        this.source = source;
        this.kinds = kinds;
        this.texts = texts;
        this.boosts = boosts;
        long max = 0;
        for (long n : boosts) max = Math.max(max, n);
        this.maxBoost = max;
        this.unknown = unknown;
    }

    /**
     * Chuẩn hoá {@code command}: bỏ khoảng trắng hai đầu và MỘT dấu {@code /} ở đầu (nếu có).
     * {@code //wand} thành {@code /wand}, đúng lệnh WorldEdit.
     */
    public static String normalizeCommand(String raw) {
        String s = raw.strip();
        if (s.startsWith("/")) s = s.substring(1).strip();
        return s;
    }

    /**
     * Phân tích một chuỗi.
     *
     * @throws IllegalArgumentException nếu có {@code {boost:…}} mà … không phải số nguyên 1..10^12;
     *         message là nguyên văn placeholder lỗi
     */
    public static CommandTemplate parse(String text) {
        List<Byte> kinds = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Long> boosts = new ArrayList<>();
        Set<String> unknown = new LinkedHashSet<>();
        StringBuilder literal = new StringBuilder();

        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            int close = c == '{' ? text.indexOf('}', i + 1) : -1;
            int nextOpen = close < 0 ? -1 : text.indexOf('{', i + 1);
            if (close < 0 || (nextOpen >= 0 && nextOpen < close)) {
                literal.append(c);
                i++;
                continue;
            }
            String name = text.substring(i + 1, close);
            byte kind = switch (name) {
                case "player" -> PLAYER;
                case "uuid" -> UUID;
                case "quest" -> QUEST;
                case "multiplier" -> MULTIPLIER;
                default -> name.startsWith(BOOST_PREFIX) ? BOOST : TEXT;
            };
            if (kind == TEXT) {
                if (isPlaceholderName(name)) unknown.add(text.substring(i, close + 1));
                literal.append(text, i, close + 1);
            } else {
                long boost = kind == BOOST ? parseBoost(name.substring(BOOST_PREFIX.length()), text.substring(i, close + 1)) : 0;
                if (!literal.isEmpty()) {
                    kinds.add(TEXT);
                    texts.add(literal.toString());
                    boosts.add(0L);
                    literal.setLength(0);
                }
                kinds.add(kind);
                texts.add(null);
                boosts.add(boost);
            }
            i = close + 1;
        }
        if (!literal.isEmpty()) {
            kinds.add(TEXT);
            texts.add(literal.toString());
            boosts.add(0L);
        }

        byte[] k = new byte[kinds.size()];
        long[] b = new long[boosts.size()];
        for (int j = 0; j < k.length; j++) {
            k[j] = kinds.get(j);
            b[j] = boosts.get(j);
        }
        return new CommandTemplate(text, k, texts.toArray(new String[0]), b, List.copyOf(unknown));
    }

    /** Chuỗi gốc (sau chuẩn hoá nếu là {@code command}). */
    public String source() {
        return source;
    }

    /** Các placeholder lạ, nguyên văn kèm ngoặc ({@code {foo}}), không trùng, theo thứ tự xuất hiện. */
    public List<String> unknownPlaceholders() {
        return unknown;
    }

    /** Có {@code {boost:N}} nào mà {@code N × hệ số} tràn {@code long} không ({@code tenths}: phần mười). */
    public boolean overflows(int tenths) {
        return tenths > 0 && maxBoost > (Long.MAX_VALUE - 5) / tenths;
    }

    /**
     * Thay placeholder.
     *
     * @param tenths  hệ số boost {@code quest} của lần hoàn thành này, theo phần mười ({@code 15} = x1.5); bỏ qua khi
     *                {@code forMenu}
     * @param forMenu {@code true}: {@code {multiplier}} là 1, {@code {boost:N}} là N in bằng {@link Format#number}
     *                ({@code 1.500}); {@code false}: {@code {multiplier}} như {@code 1.5}, {@code {boost:N}} là số thô
     *                không phân cách ({@code 150}), đã nhân hệ số, lẻ 0,5 thì làm tròn lên
     */
    public String resolve(String player, String uuid, String questId, int tenths, boolean forMenu) {
        StringBuilder sb = new StringBuilder(source.length() + 32);
        for (int i = 0; i < kinds.length; i++) {
            switch (kinds[i]) {
                case TEXT -> sb.append(texts[i]);
                case PLAYER -> sb.append(player);
                case UUID -> sb.append(uuid);
                case QUEST -> sb.append(questId);
                case MULTIPLIER -> sb.append(forMenu ? "1" : Format.multiplier(tenths));
                case BOOST -> {
                    if (forMenu) sb.append(Format.number(boosts[i]));
                    else sb.append(boost(boosts[i], tenths));
                }
                default -> throw new IllegalStateException();
            }
        }
        return sb.toString();
    }

    /**
     * {@code n × hệ số} ({@code tenths} theo phần mười), lẻ 0,5 thì làm tròn lên: 5 x1.5 = 7,5 → 8; 3 x1.5 = 4,5 → 5.
     * Bão hoà ở {@link Long#MAX_VALUE} khi tràn.
     */
    static long boost(long n, int tenths) {
        try {
            return Math.addExact(Math.multiplyExact(n, (long) tenths), 5L) / 10;
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static long parseBoost(String digits, String token) {
        if (digits.isEmpty()) throw new IllegalArgumentException(token);
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') throw new IllegalArgumentException(token);
        }
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') start++;
        // 10^12 có 13 chữ số; dài hơn chắc chắn vượt giới hạn (và tránh tràn khi parse)
        if (digits.length() - start > 13) throw new IllegalArgumentException(token);
        long n = Long.parseLong(digits, start, digits.length(), 10);
        if (n < 1 || n > MAX_BOOST) throw new IllegalArgumentException(token);
        return n;
    }

    /** {@code {tên}} có dạng placeholder: chữ, số, {@code _ . : -}. */
    private static boolean isPlaceholderName(String name) {
        if (name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == ':' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return source;
    }
}
