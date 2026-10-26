package vn.boxpvp.boxquest.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mọi chuỗi hiển thị, đọc từ messages.yml. Khoá thiếu trong file của server sẽ lấy từ bản trong jar.
 * Placeholder truyền theo cặp: {@code text("key", "count", "10", "amount", "20")}.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final JavaPlugin plugin;
    /** volatile: luồng async (flush) cũng đọc để ghi log. */
    private volatile YamlConfiguration yml = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Nội dung cố định, không đọc file (dùng cho test). Không gọi {@link #load()} trên đối tượng này. */
    public Messages(YamlConfiguration yml) {
        this.plugin = null;
        this.yml = yml;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            yml = loaded;
            plugin.getLogger().warning(log("log.messages-default-failed", "error", String.valueOf(e.getMessage())));
            return;
        }
        yml = loaded;
    }

    public String raw(String path, String... placeholders) {
        String s = yml.getString(path);
        return s == null ? path : apply(s, placeholders);
    }

    public List<String> rawList(String path, String... placeholders) {
        List<String> src = yml.getStringList(path);
        List<String> out = new ArrayList<>(src.size());
        for (String s : src) out.add(apply(s, placeholders));
        return out;
    }

    public int integer(String path, int fallback) {
        return yml.getInt(path, fallback);
    }

    /** Chuỗi cho console/log: bỏ mã màu. */
    public String log(String path, String... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(parse(raw(path, placeholders)));
    }

    /**
     * Như {@link #log}, nhưng {@code {verbatimKey}} được thay SAU khi bỏ mã màu: giá trị giữ nguyên văn cả {@code &}
     * (ví dụ một lệnh để admin sao chép chạy lại).
     */
    public String logVerbatim(String path, String verbatimKey, String verbatimValue, String... placeholders) {
        return log(path, placeholders).replace("{" + verbatimKey + "}", verbatimValue);
    }

    public Component text(String path, String... placeholders) {
        return parse(raw(path, placeholders));
    }

    /** Một dòng cho tên/lore item: tắt chữ nghiêng mặc định của Minecraft. */
    public Component line(String path, String... placeholders) {
        return itemLine(raw(path, placeholders));
    }

    /**
     * Như {@link #lines}, nhưng dòng nào chứa {@code {listKey}} được lặp lại cho từng phần tử của {@code values}
     * (danh sách rỗng thì bỏ dòng đó). Dùng cho mô tả nhiều dòng.
     */
    public List<Component> lines(String path, String listKey, List<String> values, String... placeholders) {
        return lines(path, Map.of(listKey, values), placeholders);
    }

    /**
     * Như {@link #lines(String, String, List, String...)} với nhiều khoá danh sách. Một dòng chỉ mở rộng khoá danh sách
     * ĐẦU TIÊN tìm thấy trong dòng (theo thứ tự của {@code lists}, dùng {@link java.util.LinkedHashMap}); các khoá
     * danh sách khác trên cùng dòng được thay bằng chuỗi rỗng.
     */
    public List<Component> lines(String path, Map<String, List<String>> lists, String... placeholders) {
        String[] keys = lists.keySet().toArray(new String[0]);
        String[] tokens = new String[keys.length];
        for (int i = 0; i < keys.length; i++) tokens[i] = "{" + keys[i] + "}";

        List<Component> out = new ArrayList<>();
        for (String line : yml.getStringList(path)) {
            int found = -1;
            for (int i = 0; i < tokens.length && found < 0; i++) {
                if (line.contains(tokens[i])) found = i;
            }
            if (found < 0) {
                out.add(itemLine(apply(line, placeholders)));
                continue;
            }
            for (int i = found + 1; i < tokens.length; i++) line = line.replace(tokens[i], "");
            for (String value : lists.get(keys[found])) {
                out.add(itemLine(apply(line.replace(tokens[found], value), placeholders)));
            }
        }
        return out;
    }

    public List<Component> lines(String path, String... placeholders) {
        List<String> src = rawList(path, placeholders);
        List<Component> out = new ArrayList<>(src.size());
        for (String s : src) out.add(itemLine(s));
        return out;
    }

    public static Component parse(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    public static Component itemLine(String legacy) {
        return parse(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String apply(String s, String... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return s;
    }
}
