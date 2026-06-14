package fr.zeffut.modchecker.telemetry;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class PostHogClientTest {

    private static PostHogClient client(boolean enabled, List<String> sink) {
        return new PostHogClient(enabled, "https://eu.i.posthog.com",
                "paper", "1.21.11", "1.0.0", sink::add);
    }

    @Test
    void buildsBodyWithEventDistinctIdAndApiKey() {
        List<String> sink = new ArrayList<>();
        client(true, sink).capture("player_join", "uuid-1", Map.of("username", "Alice"));
        assertEquals(1, sink.size());
        String body = sink.get(0);
        assertTrue(body.contains("\"event\":\"player_join\""), body);
        assertTrue(body.contains("\"distinct_id\":\"uuid-1\""), body);
        assertTrue(body.contains(PostHogClient.API_KEY), body);
        assertTrue(body.contains("\"username\":\"Alice\""), body);
    }

    @Test
    void alwaysInjectsSuperProperties() {
        List<String> sink = new ArrayList<>();
        client(true, sink).capture("x", "id", Map.of());
        String body = sink.get(0);
        assertTrue(body.contains("\"app\":\"modchecker\""), body);
        assertTrue(body.contains("\"source\":\"paper\""), body);
        assertTrue(body.contains("\"mc_version\":\"1.21.11\""), body);
        assertTrue(body.contains("\"component_version\":\"1.0.0\""), body);
    }

    @Test
    void disabledClientSendsNothing() {
        List<String> sink = new ArrayList<>();
        client(false, sink).capture("x", "id", Map.of());
        assertTrue(sink.isEmpty());
    }

    @Test
    void senderThrowingDoesNotPropagate() {
        PostHogClient c = new PostHogClient(true, "https://eu.i.posthog.com",
                "paper", "1.21.11", "1.0.0", b -> { throw new RuntimeException("boom"); });
        assertDoesNotThrow(() -> c.capture("x", "id", Map.of()));
    }
}
