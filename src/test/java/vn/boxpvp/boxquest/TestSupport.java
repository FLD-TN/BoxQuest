package vn.boxpvp.boxquest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.boxpvp.boxquest.config.Messages;

/** Dữ liệu dùng chung cho test: file YAML trên classpath và logger ghi lại thông báo. */
public final class TestSupport {

    private TestSupport() {
    }

    public static String resource(String name) {
        try (InputStream in = TestSupport.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static YamlConfiguration yaml(String text) {
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException(e);
        }
        return yml;
    }

    /** {@code messages.yml} đóng gói trong jar (bản hiện tại). */
    public static Messages messages() {
        return messages("messages.yml");
    }

    public static Messages messages(String resource) {
        try (InputStream in = TestSupport.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new Messages(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Logger ghi lại mọi thông báo, kèm mức. */
    public static final class Capture extends Handler {

        public final List<LogRecord> records = new ArrayList<>();
        public final Logger logger;

        public Capture() {
            logger = Logger.getAnonymousLogger();
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            logger.addHandler(this);
        }

        public List<String> messages(Level level) {
            List<String> out = new ArrayList<>();
            for (LogRecord r : records) if (r.getLevel().equals(level)) out.add(r.getMessage());
            return out;
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
