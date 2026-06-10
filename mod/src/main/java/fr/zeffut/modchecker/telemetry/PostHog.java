package fr.zeffut.modchecker.telemetry;

import fr.zeffut.modchecker.config.ModConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Client PostHog du mod. Mapping-agnostique : ne référence aucune classe Minecraft.
 * Async, fire-and-forget, toutes erreurs avalées. Opt-out via config/modchecker.json
 * ou -Dmodchecker.telemetry=false.
 */
public final class PostHog {

    private static final String API_KEY = "phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359";
    /** App slug attached to every event so a shared PostHog project can segment by mod. */
    private static final String APP = "modchecker";

    private static final boolean ENABLED = resolveEnabled();
    private static final String HOST = resolveHost();
    private static final String INSTALL_ID = ModConfig.get().installId();

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
        captureForApp(APP, event, source, mcVersion, modVersion, properties);
    }

    /**
     * Same as {@link #capture} but tagging the event with an explicit {@code app}. Used by the
     * embedded auto-update module, whose events are segmented under {@code app=autoupdate}
     * (shared across every host mod) instead of this mod's own app slug.
     */
    public static void captureForApp(String app, String event, String source, String mcVersion,
                                     String modVersion, Map<String, Object> properties) {
        if (!ENABLED) return;
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("app", app);
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

    /**
     * Emits the standard {@code session_heartbeat} event every 30 minutes (first beat after
     * 5 minutes) on a daemon scheduler, so the dashboard can chart session length/activity.
     */
    public static void startHeartbeat(String source, String mcVersion, String modVersion) {
        if (!ENABLED) return;
        long startedAt = System.currentTimeMillis();
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modchecker-heartbeat");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            long minutes = (System.currentTimeMillis() - startedAt) / 60_000;
            capture("session_heartbeat", source, mcVersion, modVersion,
                    Map.of("minutes_since_start", minutes));
        }, 5, 30, java.util.concurrent.TimeUnit.MINUTES);
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

    // ---- opt-out ----
    private static boolean resolveEnabled() {
        // Dev runs never emit telemetry (loader-reported dev env).
        if (fr.zeffut.modchecker.platform.Platform.isDevelopment()) return false;
        String sys = System.getProperty("modchecker.telemetry");
        if (sys != null) return Boolean.parseBoolean(sys);
        return ModConfig.get().telemetry();
    }

    private static String resolveHost() {
        String sys = System.getProperty("modchecker.telemetry.host");
        return sys != null ? sys : "https://eu.i.posthog.com";
    }
}
