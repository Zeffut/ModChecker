package fr.zeffut.modchecker.telemetry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Client PostHog : envoie des events vers la Capture API. Async, fire-and-forget,
 * toutes les erreurs avalées — n'impacte jamais le serveur. Opt-out via {@code enabled}.
 */
public final class PostHogClient {

    public static final String API_KEY = "phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359";
    public static final String DEFAULT_HOST = "https://eu.i.posthog.com";

    /** Couture de test : implémentation réseau réelle ou mock. */
    public interface Sender { void send(String jsonBody); }

    private final boolean enabled;
    private final String host;
    private final String source;
    private final String mcVersion;
    private final String componentVersion;
    private final Sender sender;

    /** Constructeur de prod : envoi HTTP async. */
    public PostHogClient(boolean enabled, String host, String source,
                         String mcVersion, String componentVersion) {
        this(enabled, host, source, mcVersion, componentVersion, httpSender(host));
    }

    /** Constructeur testable : {@code sender} injecté. */
    public PostHogClient(boolean enabled, String host, String source,
                         String mcVersion, String componentVersion, Sender sender) {
        this.enabled = enabled;
        this.host = host;
        this.source = source;
        this.mcVersion = mcVersion;
        this.componentVersion = componentVersion;
        this.sender = sender;
    }

    public void capture(String event, String distinctId, Map<String, Object> properties) {
        if (!enabled) return;
        try {
            sender.send(buildBody(event, distinctId, properties));
        } catch (Throwable ignored) {
            // fire-and-forget : jamais d'impact sur l'appelant
        }
    }

    String buildBody(String event, String distinctId, Map<String, Object> properties) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("source", source);
        props.put("mc_version", mcVersion);
        props.put("component_version", componentVersion);
        if (properties != null) props.putAll(properties);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("api_key", API_KEY);
        root.put("event", event);
        root.put("distinct_id", distinctId);
        root.put("timestamp", Instant.now().toString());
        root.put("properties", props);
        return rootJson(root, props);
    }

    /** Sérialise root en injectant l'objet properties déjà construit. */
    private static String rootJson(Map<String, Object> root, Map<String, Object> props) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : root.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            if ("properties".equals(e.getKey())) {
                sb.append(JsonWriter.write(props));
            } else {
                Map<String, Object> single = new LinkedHashMap<>();
                single.put("v", e.getValue());
                String w = JsonWriter.write(single);          // {"v":...}
                sb.append(w, 5, w.length() - 1);              // garde juste la valeur encodée
            }
        }
        return sb.append('}').toString();
    }

    private static final Executor EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "modchecker-posthog");
                t.setDaemon(true);
                return t;
            });

    private static Sender httpSender(String host) {
        HttpClient http = HttpClient.newBuilder().executor(EXECUTOR).build();
        String url = host.replaceAll("/+$", "") + "/i/v0/e/";
        return body -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        };
    }
}
