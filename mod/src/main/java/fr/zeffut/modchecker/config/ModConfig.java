package fr.zeffut.modchecker.config;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JSON config manager for {@code config/modchecker.json}. Mapping-agnostic: references no Minecraft
 * class, uses no JSON library (hand-rolled minimal reader/writer) so it compiles identically across
 * every node and pulls in no extra dependency.
 *
 * <p>Schema:
 * <pre>
 * {
 *   "telemetry": true,             // bool, default true — master opt-out switch
 *   "install_id": "&lt;uuid&gt;",  // stable anonymous id, generated once
 *   "settings": { ... }            // free-form map of mod-specific string settings
 * }
 * </pre>
 *
 * <p>The file is created on first access. A single instance is cached via {@link #get()}.
 */
public final class ModConfig {

    private static final String FILE_NAME = "modchecker.json";
    private static volatile ModConfig instance;

    private boolean telemetry = true;
    private String installId;
    private final Map<String, String> settings = new LinkedHashMap<>();

    private ModConfig() {}

    /** Lazily loads (and creates if missing) the config from {@code config/modchecker.json}. */
    public static ModConfig get() {
        ModConfig local = instance;
        if (local == null) {
            synchronized (ModConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    public boolean telemetry() { return telemetry; }

    public void setTelemetry(boolean value) { this.telemetry = value; save(); }

    public String installId() { return installId; }

    /** Mod-specific extensible settings. Call {@link #save()} after mutating. */
    public Map<String, String> settings() { return settings; }

    public String setting(String key, String fallback) {
        return settings.getOrDefault(key, fallback);
    }

    public void putSetting(String key, String value) { settings.put(key, value); save(); }

    private static File file() { return new File("config", FILE_NAME); }

    private static ModConfig load() {
        ModConfig cfg = new ModConfig();
        try {
            File f = file();
            if (f.exists()) {
                String c = Files.readString(f.toPath());
                cfg.telemetry = !c.replaceAll("\\s", "").contains("\"telemetry\":false");
                cfg.installId = extractString(c, "install_id");
                parseSettings(c, cfg.settings);
            }
        } catch (Throwable ignored) {
            // fall through to defaults
        }
        boolean dirty = false;
        if (cfg.installId == null || cfg.installId.isBlank()) {
            cfg.installId = UUID.randomUUID().toString();
            dirty = true;
        }
        // Seed the user-facing update options so the generated file documents them.
        dirty |= cfg.settings.putIfAbsent("auto_update", "true") == null;
        dirty |= cfg.settings.putIfAbsent("update_owner", "Zeffut") == null;
        dirty |= cfg.settings.putIfAbsent("update_all", "false") == null;
        dirty |= cfg.settings.putIfAbsent("update_exclude", "") == null;
        if (dirty) cfg.save();
        return cfg;
    }

    /** Reads back the flat string map inside the {@code "settings"} object. */
    private static void parseSettings(String json, Map<String, String> into) {
        int start = json.indexOf("\"settings\"");
        if (start < 0) return;
        int open = json.indexOf('{', start);
        int close = open < 0 ? -1 : json.indexOf('}', open);
        if (open < 0 || close < 0) return;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json.substring(open + 1, close));
        while (m.find()) into.put(unesc(m.group(1)), unesc(m.group(2)));
    }

    private static String unesc(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Persists the current state. Best-effort; failures are swallowed. */
    public void save() {
        try {
            File f = file();
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"telemetry\": ").append(telemetry).append(",\n");
            sb.append("  \"install_id\": \"").append(esc(installId)).append("\",\n");
            sb.append("  \"settings\": {");
            boolean first = true;
            for (Map.Entry<String, String> e : settings.entrySet()) {
                sb.append(first ? "\n" : ",\n");
                first = false;
                sb.append("    \"").append(esc(e.getKey())).append("\": \"")
                        .append(esc(e.getValue())).append('"');
            }
            sb.append(settings.isEmpty() ? "}" : "\n  }").append("\n}\n");
            Files.writeString(f.toPath(), sb.toString());
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static String extractString(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int colon = json.indexOf(':', i);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        int q2 = q1 < 0 ? -1 : json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
