package fr.zeffut.modchecker.velocity;

import fr.zeffut.modchecker.ModListCodec;
import fr.zeffut.modchecker.ModStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ModCheckDecision} — pure logic, no Velocity runtime needed.
 */
class ModCheckDecisionTest {

    private static final String KICK_TEMPLATE = "Banned mods detected: {mods}";

    // Helper: encode a JSON mod list as a raw MC-String byte array
    private static byte[] encodeModList(String json) {
        return ModListCodec.encode(json);
    }

    // -----------------------------------------------------------------------
    // Test 1: clean player (no banned mods) → pass
    // -----------------------------------------------------------------------

    @Test
    void cleanPlayer_shouldPass() {
        byte[] payload = encodeModList(
                "[{\"id\":\"fabric-api\",\"name\":\"Fabric API\",\"version\":\"0.141.3\"}]");

        Map<String, ModStatus> statusMap = Map.of(
                "fabric-api", ModStatus.ALLOWED,
                "xray",       ModStatus.BANNED
        );

        ModCheckDecision.Result result = ModCheckDecision.evaluate(payload, statusMap, KICK_TEMPLATE);

        assertFalse(result.isShouldDisconnect(), "Clean player should not be disconnected");
        assertEquals(1, result.getAllMods().size(), "Should have parsed 1 mod");
        assertTrue(result.getBannedMods().isEmpty(), "No banned mods should be reported");
        assertNull(result.getDisconnectMessage(), "No disconnect message for clean player");
    }

    // -----------------------------------------------------------------------
    // Test 2: player with a banned mod → disconnect
    // -----------------------------------------------------------------------

    @Test
    void bannedModPlayer_shouldDisconnect() {
        byte[] payload = encodeModList(
                "[{\"id\":\"xray\",\"name\":\"XRay Ultimate\",\"version\":\"1.0\"}," +
                " {\"id\":\"fabric-api\",\"name\":\"Fabric API\",\"version\":\"0.141.3\"}]");

        Map<String, ModStatus> statusMap = Map.of(
                "fabric-api", ModStatus.ALLOWED,
                "xray",       ModStatus.BANNED
        );

        ModCheckDecision.Result result = ModCheckDecision.evaluate(payload, statusMap, KICK_TEMPLATE);

        assertTrue(result.isShouldDisconnect(), "Player with banned mod should be disconnected");
        assertEquals(2, result.getAllMods().size(), "Should have parsed 2 mods");
        assertEquals(1, result.getBannedMods().size(), "One banned mod should be reported");
        assertTrue(result.getBannedMods().get(0).contains("xray"),
                "Banned mod entry should mention xray");
        assertNotNull(result.getDisconnectMessage(), "Disconnect message must be present");
        assertTrue(result.getDisconnectMessage().contains("xray"),
                "Disconnect message should contain the banned mod id");
    }

    // -----------------------------------------------------------------------
    // Test 3: multiple banned mods → all listed in disconnect message
    // -----------------------------------------------------------------------

    @Test
    void multipleBannedMods_allListedInMessage() {
        byte[] payload = encodeModList(
                "[{\"id\":\"xray\",\"name\":\"XRay\",\"version\":\"1.0\"}," +
                " {\"id\":\"killaura\",\"name\":\"KillAura\",\"version\":\"2.0\"}]");

        Map<String, ModStatus> statusMap = Map.of(
                "xray",      ModStatus.BANNED,
                "killaura",  ModStatus.BANNED
        );

        ModCheckDecision.Result result = ModCheckDecision.evaluate(payload, statusMap, KICK_TEMPLATE);

        assertTrue(result.isShouldDisconnect());
        assertEquals(2, result.getBannedMods().size(), "Both mods should be banned");
        assertTrue(result.getDisconnectMessage().contains("xray"),
                "Message should contain xray");
        assertTrue(result.getDisconnectMessage().contains("killaura"),
                "Message should contain killaura");
    }

    // -----------------------------------------------------------------------
    // Test 4: malformed/null payload → pass (don't crash)
    // -----------------------------------------------------------------------

    @Test
    void malformedPayload_shouldPass() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};  // garbage

        ModCheckDecision.Result result = ModCheckDecision.evaluate(
                payload, Map.of("xray", ModStatus.BANNED), KICK_TEMPLATE);

        assertFalse(result.isShouldDisconnect(), "Malformed payload should not disconnect");
    }

    @Test
    void nullPayload_shouldPass() {
        ModCheckDecision.Result result = ModCheckDecision.evaluate(
                null, Map.of("xray", ModStatus.BANNED), KICK_TEMPLATE);

        assertFalse(result.isShouldDisconnect(), "Null payload should not disconnect");
    }

    // -----------------------------------------------------------------------
    // Test 5: empty status map → pass (no bans configured)
    // -----------------------------------------------------------------------

    @Test
    void emptyStatusMap_shouldAlwaysPass() {
        byte[] payload = encodeModList("[{\"id\":\"xray\",\"name\":\"XRay\",\"version\":\"1.0\"}]");

        ModCheckDecision.Result result = ModCheckDecision.evaluate(payload, Map.of(), KICK_TEMPLATE);

        assertFalse(result.isShouldDisconnect(), "No bans configured → should pass");
    }

    // -----------------------------------------------------------------------
    // Test 6: mod with UNKNOWN status → pass
    // -----------------------------------------------------------------------

    @Test
    void unknownStatusMod_shouldPass() {
        byte[] payload = encodeModList("[{\"id\":\"some-mod\",\"name\":\"SomeMod\",\"version\":\"1.0\"}]");

        Map<String, ModStatus> statusMap = Map.of(
                "some-mod", ModStatus.UNKNOWN
        );

        ModCheckDecision.Result result = ModCheckDecision.evaluate(payload, statusMap, KICK_TEMPLATE);

        assertFalse(result.isShouldDisconnect(), "UNKNOWN status should not disconnect");
    }
}
