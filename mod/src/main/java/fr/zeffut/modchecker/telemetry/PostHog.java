package fr.zeffut.modchecker.telemetry;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Client PostHog du mod. Mapping-agnostique : ne référence aucune classe Minecraft.
 * Async, fire-and-forget, toutes erreurs avalées. Opt-out via config/modchecker.json
 * ou -Dmodchecker.telemetry=false.
 */
public final class PostHog {

    private static final String API_KEY = "phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359";

    private static final boolean ENABLED = resolveEnabled();
    private static final String HOST = resolveHost();
    private static final String INSTALL_ID = resolveInstallId();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .executor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "modchecker-posthog");
                t.setDaemon(true);
                return t;
            })).build();

    private PostHog() {}

    public static String installId() { return INSTALL_ID; }

    /** source = "mod-fabric" | "mod-neoforge". */
    public static void capture(String event, String source, String mcVersion,
                               String modVersion, Map<String, Object> properties) {
        if (!ENABLED) return;
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("source", source);
            props.put("mc_version", mcVersion);
            props.put("component_version", modVersion);
            props.put("install_id", INSTALL_ID);
            if (properties != null) props.putAll(properties);

            String body = "{\"api_key\":\"" + API_KEY + "\",\"event\":\"" + esc(event)
                    + "\",\"distinct_id\":\"" + esc(INSTALL_ID) + "\",\"timestamp\":\""
                    + Instant.now() + "\",\"properties\":" + obj(props) + "}";

            HttpRequest req = HttpRequest.newBuilder(URI.create(HOST.replaceAll("/+$", "") + "/i/v0/e/"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
            // fire-and-forget
        }
    }

    // ---- JSON minimal ----
    private static String obj(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(esc(e.getKey())).append("\":").append(val(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String val(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "\"" + esc(v.toString()) + "\"";
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.toString();
    }

    // ---- opt-out + install id ----
    private static boolean resolveEnabled() {
        String sys = System.getProperty("modchecker.telemetry");
        if (sys != null) return Boolean.parseBoolean(sys);
        try {
            File f = new File("config", "modchecker.json");
            if (f.exists()) {
                String c = Files.readString(f.toPath());
                if (c.contains("\"telemetry\"") && c.replaceAll("\\s", "").contains("\"telemetry\":false"))
                    return false;
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private static String resolveHost() {
        String sys = System.getProperty("modchecker.telemetry.host");
        return sys != null ? sys : "https://eu.i.posthog.com";
    }

    private static String resolveInstallId() {
        try {
            File dir = new File("config");
            File f = new File(dir, "modchecker.json");
            if (f.exists()) {
                String c = Files.readString(f.toPath());
                int i = c.indexOf("\"install_id\"");
                if (i >= 0) {
                    int q1 = c.indexOf('"', c.indexOf(':', i) + 1);
                    int q2 = c.indexOf('"', q1 + 1);
                    if (q1 >= 0 && q2 > q1) return c.substring(q1 + 1, q2);
                }
            }
            String id = UUID.randomUUID().toString();
            dir.mkdirs();
            Files.writeString(f.toPath(),
                    "{\n  \"telemetry\": true,\n  \"install_id\": \"" + id + "\"\n}\n");
            return id;
        } catch (Throwable t) {
            return "ephemeral-" + UUID.randomUUID();
        }
    }
}
