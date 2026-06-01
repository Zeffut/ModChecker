package fr.zeffut.modchecker.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ModCheckerConfig} — no Velocity runtime needed.
 * Uses a JUnit {@code @TempDir} so each test gets a clean directory.
 */
class ModCheckerConfigTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModCheckerConfigTest.class);

    // -----------------------------------------------------------------------
    // Test 1: first load → writes defaults (including exempt-players: [])
    //         then reads them back correctly
    // -----------------------------------------------------------------------

    @Test
    void defaultConfig_writtenAndReadBack(@TempDir Path tmp) {
        ModCheckerConfig config = new ModCheckerConfig(tmp, LOGGER);
        config.load();

        // Verify defaults
        assertEquals(ModCheckerConfig.DEFAULT_SERVER_NAME,   config.getServerName());
        assertEquals(ModCheckerConfig.DEFAULT_KICK_MESSAGE,  config.getKickMessage());
        assertEquals(ModCheckerConfig.DEFAULT_GRACE_SECONDS, config.getGraceSeconds());
        assertEquals(ModCheckerConfig.DEFAULT_KICK_WITHOUT_MOD, config.isKickWithoutMod());
        assertTrue(config.getExemptPlayers().isEmpty(), "Default exempt-players must be empty");

        // Reload from the written file to verify round-trip
        ModCheckerConfig reload = new ModCheckerConfig(tmp, LOGGER);
        reload.load();
        assertEquals(config.getServerName(),    reload.getServerName());
        assertEquals(config.getKickMessage(),   reload.getKickMessage());
        assertEquals(config.getGraceSeconds(),  reload.getGraceSeconds());
        assertEquals(config.isKickWithoutMod(), reload.isKickWithoutMod());
        assertTrue(reload.getExemptPlayers().isEmpty(), "Reloaded exempt-players must still be empty");
    }

    // -----------------------------------------------------------------------
    // Test 2: hand-written config.json with exempt-players, kick-without-mod,
    //         grace-period-seconds round-trips correctly
    // -----------------------------------------------------------------------

    @Test
    void handWrittenConfig_exemptPlayersKickWithoutModGracePeriod_roundTrip(@TempDir Path tmp) throws Exception {
        String uuid1 = "550e8400-e29b-41d4-a716-446655440000";
        String uuid2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

        // Write a config.json manually
        String json = "{\n" +
                "  \"server-name\": \"TestProxy\",\n" +
                "  \"kick-message\": \"You used a banned mod: {mods}\",\n" +
                "  \"grace-period-seconds\": 30,\n" +
                "  \"kick-without-mod\": true,\n" +
                "  \"exempt-players\": [\"" + uuid1 + "\", \"" + uuid2 + "\"]\n" +
                "}\n";
        java.nio.file.Files.writeString(tmp.resolve("config.json"), json);

        ModCheckerConfig config = new ModCheckerConfig(tmp, LOGGER);
        config.load();

        assertEquals("TestProxy", config.getServerName());
        assertEquals("You used a banned mod: {mods}", config.getKickMessage());
        assertEquals(30, config.getGraceSeconds());
        assertTrue(config.isKickWithoutMod());

        Set<String> exempt = config.getExemptPlayers();
        assertEquals(2, exempt.size(), "Both UUIDs should be loaded");
        assertTrue(exempt.contains(uuid1), "First UUID must be in exempt set");
        assertTrue(exempt.contains(uuid2), "Second UUID must be in exempt set");
    }

    // -----------------------------------------------------------------------
    // Test 3: invalid UUID entries in exempt-players are skipped, valid ones kept
    // -----------------------------------------------------------------------

    @Test
    void invalidUUIDsInExemptPlayers_areSkipped_validOnesKept(@TempDir Path tmp) throws Exception {
        String validUuid = "550e8400-e29b-41d4-a716-446655440000";
        String json = "{\n" +
                "  \"server-name\": \"Test\",\n" +
                "  \"kick-message\": \"msg\",\n" +
                "  \"grace-period-seconds\": 10,\n" +
                "  \"kick-without-mod\": false,\n" +
                "  \"exempt-players\": [\"" + validUuid + "\", \"not-a-uuid\", \"12345\", \"\"]\n" +
                "}\n";
        java.nio.file.Files.writeString(tmp.resolve("config.json"), json);

        ModCheckerConfig config = new ModCheckerConfig(tmp, LOGGER);
        config.load();  // must not throw

        Set<String> exempt = config.getExemptPlayers();
        assertEquals(1, exempt.size(), "Only the valid UUID should survive");
        assertTrue(exempt.contains(validUuid));
    }

    // -----------------------------------------------------------------------
    // Test 4: missing exempt-players key → empty set (backward-compat)
    // -----------------------------------------------------------------------

    @Test
    void missingExemptPlayersKey_yieldsEmptySet(@TempDir Path tmp) throws Exception {
        String json = "{\n" +
                "  \"server-name\": \"Test\",\n" +
                "  \"kick-message\": \"msg\",\n" +
                "  \"grace-period-seconds\": 10,\n" +
                "  \"kick-without-mod\": false\n" +
                "}\n";
        java.nio.file.Files.writeString(tmp.resolve("config.json"), json);

        ModCheckerConfig config = new ModCheckerConfig(tmp, LOGGER);
        config.load();

        assertTrue(config.getExemptPlayers().isEmpty(),
                "Missing exempt-players key must result in empty set");
    }

    // -----------------------------------------------------------------------
    // Test 5: kick-without-mod=true + grace-period round-trip
    // -----------------------------------------------------------------------

    @Test
    void kickWithoutMod_and_gracePeriod_roundTrip(@TempDir Path tmp) throws Exception {
        String json = "{\n" +
                "  \"server-name\": \"S\",\n" +
                "  \"kick-message\": \"m\",\n" +
                "  \"grace-period-seconds\": 45,\n" +
                "  \"kick-without-mod\": true,\n" +
                "  \"exempt-players\": []\n" +
                "}\n";
        java.nio.file.Files.writeString(tmp.resolve("config.json"), json);

        ModCheckerConfig config = new ModCheckerConfig(tmp, LOGGER);
        config.load();

        assertEquals(45, config.getGraceSeconds());
        assertTrue(config.isKickWithoutMod());
    }
}
