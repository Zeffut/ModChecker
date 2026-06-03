package fr.zeffut.modchecker.velocity;

import fr.zeffut.modchecker.telemetry.PostHogClient;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VelocityTelemetryTest {

    @Test
    void modlistReceivedEmitsEvent() {
        List<String> sink = new ArrayList<>();
        PostHogClient c = new PostHogClient(true, "h", "velocity", "3.4", "1.0.0", sink::add);
        Telemetry tel = new Telemetry(c, "Proxy", "proxy-install-id");
        tel.modlistReceived("uuid-1", "Alice", 3, 2, true);
        assertTrue(sink.stream().anyMatch(e -> e.contains("\"event\":\"modlist_received\"")), sink.toString());
        assertTrue(sink.get(0).contains("\"source\":\"velocity\""), sink.toString());
    }

    @Test
    void disabledClientEmitsNothing() {
        List<String> sink = new ArrayList<>();
        PostHogClient c = new PostHogClient(false, "h", "velocity", "3.4", "1.0.0", sink::add);
        new Telemetry(c, "Proxy", "id").playerKicked("u", "Bob", "1.2.3.4", "banned_mod", List.of("x"));
        assertTrue(sink.isEmpty());
    }
}
