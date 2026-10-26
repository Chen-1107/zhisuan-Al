package zhisuan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GUI 配置持久化（~/.zhisuan_gui_&lt;mode&gt;.json）。
 * 每种模式（default / audit / dispatch）独立保存 serverUrl / lang / mode，
 * 保证两个新实例互不覆盖配置。
 */
public final class Config {
    private static final Path HOME = Paths.get(System.getProperty("user.home"));
    private static String mode = "default";

    private Config() {}

    public static String mode() { return mode; }

    /** 在读取任何配置前调用（Main 启动时先设置模式） */
    public static void setMode(String m) {
        if (m != null && !m.trim().isEmpty()) mode = m.trim();
    }

    private static Path file() {
        return HOME.resolve(".zhisuan_gui_" + mode + ".json");
    }

    private static Map<String, Object> load() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        try {
            if (Files.exists(file())) {
                Object o = Json.parse(new String(Files.readAllBytes(file()), StandardCharsets.UTF_8));
                Map<String, Object> m = Json.obj(o);
                if (m != null) cfg.putAll(m);
            }
        } catch (Exception ignored) {
        }
        return cfg;
    }

    private static void save(Map<String, Object> cfg) throws IOException {
        Files.write(file(), Json.stringify(cfg).getBytes(StandardCharsets.UTF_8));
    }

    public static String loadServerUrl(String dflt) {
        String u = Json.str(load().get("serverUrl"));
        return (u == null || u.isEmpty()) ? dflt : u;
    }

    public static void saveServerUrl(String url) throws IOException {
        Map<String, Object> cfg = load();
        cfg.put("serverUrl", url);
        save(cfg);
    }

    public static String loadLang(String dflt) {
        String l = Json.str(load().get("lang"));
        return (l == null || l.isEmpty()) ? dflt : l;
    }

    public static void saveLang(String lang) throws IOException {
        Map<String, Object> cfg = load();
        cfg.put("lang", lang);
        save(cfg);
    }
}
