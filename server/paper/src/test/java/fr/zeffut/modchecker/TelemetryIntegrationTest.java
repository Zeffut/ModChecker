package fr.zeffut.modchecker;

import fr.zeffut.modchecker.telemetry.PostHogClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryIntegrationTest {

    private ServerMock server;
    private ModCheckerPlugin plugin;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ModCheckerPlugin.class);
        PostHogClient capturing = new PostHogClient(true, "h", "paper", "test", "1.0.0", events::add);
        plugin.getModChecker().setTelemetry(new Telemetry(capturing, "Mon Serveur", "srv-install-id"));
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void modlistReceivedEmitsEvent() {
        PlayerMock player = server.addPlayer("Alice");
        events.clear();
        String json = "[{\"id\":\"sodium\",\"name\":\"Sodium\",\"version\":\"0.6\"}]";
        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, ModListCodec.encode(json));
        assertTrue(events.stream().anyMatch(e -> e.contains("\"event\":\"modlist_received\"")), events.toString());
        assertTrue(events.stream().anyMatch(e -> e.contains("\"event\":\"mod_discovered\"")), events.toString());
    }

    @Test
    void bannedModEmitsPlayerKicked() {
        PlayerMock player = server.addPlayer("Dave");
        plugin.getModChecker().getModStatus().put("evil", ModStatus.BANNED);
        events.clear();
        String json = "[{\"id\":\"evil\",\"name\":\"Evil\",\"version\":\"1.0\"}]";
        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, ModListCodec.encode(json));
        server.getScheduler().performOneTick();
        assertTrue(events.stream().anyMatch(e -> e.contains("\"event\":\"player_kicked\"")), events.toString());
    }

    @Test
    void statusChangeEmitsEvent() {
        events.clear();
        server.executeConsole("mods", "ban", "test-mod");
        assertTrue(events.stream().anyMatch(e -> e.contains("\"event\":\"mod_status_changed\"")), events.toString());
    }
}
