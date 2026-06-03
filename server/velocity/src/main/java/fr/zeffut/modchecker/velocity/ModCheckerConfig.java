package fr.zeffut.modchecker.velocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zeffut.modchecker.ModStatus;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Loads and persists the ModChecker Velocity configuration.
 * <p>
 * File layout inside the data directory:
 * <ul>
 *   <li>{@code mods.json}   — Map&lt;modId, ModStatus&gt; (ALLOWED/BANNED/UNKNOWN)</li>
 *   <li>{@code config.json} — general settings (server-name, messages, grace-period, etc.)</li>
 * </ul>
 */
public final class ModCheckerConfig {

    // ---- defaults ----
    public static final String DEFAULT_SERVER_NAME   = "ModChecker";
    public static final String DEFAULT_KICK_MESSAGE  = "You are using a banned mod: {mods}";
    public static final int    DEFAULT_GRACE_SECONDS = 10;
    public static final boolean DEFAULT_KICK_WITHOUT_MOD = false;
    public static final boolean DEFAULT_TELEMETRY = true;
    public static final String  DEFAULT_TELEMETRY_HOST = "https://eu.i.posthog.com";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- fields loaded from disk ----
    private Map<String, ModStatus> statusMap    = new HashMap<>();
    private Set<String>            exemptPlayers = new HashSet<>();  // UUIDs as strings
    private String  serverName      = DEFAULT_SERVER_NAME;
    private String  kickMessage     = DEFAULT_KICK_MESSAGE;
    private int     graceSeconds    = DEFAULT_GRACE_SECONDS;
    private boolean kickWithoutMod  = DEFAULT_KICK_WITHOUT_MOD;
    private boolean telemetry     = DEFAULT_TELEMETRY;
    private String  telemetryHost = DEFAULT_TELEMETRY_HOST;

    private final Path dataDirectory;
    private final org.slf4j.Logger logger;

    public ModCheckerConfig(Path dataDirectory, org.slf4j.Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    // ---- load ----

    public void load() {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.error("Could not create data directory", e);
        }
        loadStatusMap();
        loadGeneralConfig();
    }

    private void loadStatusMap() {
        Path modsFile = dataDirectory.resolve("mods.json");
        if (!Files.exists(modsFile)) {
            writeDefaultStatusMap(modsFile);
            return;
        }
        try (Reader r = Files.newBufferedReader(modsFile)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            Map<String, ModStatus> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                ModStatus status = ModStatus.fromString(entry.getValue().getAsString());
                map.put(entry.getKey(), status);
            }
            this.statusMap = map;
            logger.info("Loaded {} mod status entries from mods.json", statusMap.size());
        } catch (Exception e) {
            logger.error("Failed to load mods.json, using empty map", e);
        }
    }

    private void writeDefaultStatusMap(Path modsFile) {
        Map<String, ModStatus> defaults = new HashMap<>();
        defaults.put("xray", ModStatus.BANNED);
        defaults.put("fabric-api", ModStatus.ALLOWED);
        try (Writer w = Files.newBufferedWriter(modsFile)) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, ModStatus> e : defaults.entrySet()) {
                obj.addProperty(e.getKey(), e.getValue().name());
            }
            GSON.toJson(obj, w);
            logger.info("Created default mods.json with sample entries");
        } catch (IOException e) {
            logger.error("Failed to write default mods.json", e);
        }
        this.statusMap = defaults;
    }

    private void loadGeneralConfig() {
        Path cfgFile = dataDirectory.resolve("config.json");
        if (!Files.exists(cfgFile)) {
            writeDefaultGeneralConfig(cfgFile);
            return;
        }
        try (Reader r = Files.newBufferedReader(cfgFile)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("server-name"))      serverName     = obj.get("server-name").getAsString();
            if (obj.has("kick-message"))     kickMessage    = obj.get("kick-message").getAsString();
            if (obj.has("grace-period-seconds")) graceSeconds = obj.get("grace-period-seconds").getAsInt();
            if (obj.has("kick-without-mod")) kickWithoutMod = obj.get("kick-without-mod").getAsBoolean();
            if (obj.has("telemetry"))      telemetry     = obj.get("telemetry").getAsBoolean();
            if (obj.has("telemetry-host")) telemetryHost = obj.get("telemetry-host").getAsString();
            if (obj.has("exempt-players")) {
                Set<String> loaded = new HashSet<>();
                JsonArray arr = obj.getAsJsonArray("exempt-players");
                for (JsonElement el : arr) {
                    try {
                        String uuidStr = el.getAsString().trim();
                        // Validate UUID format before accepting
                        java.util.UUID.fromString(uuidStr);
                        loaded.add(uuidStr);
                    } catch (Exception ignored) {
                        logger.warn("Skipping invalid UUID in exempt-players: {}", el);
                    }
                }
                this.exemptPlayers = loaded;
            }
        } catch (Exception e) {
            logger.error("Failed to load config.json, using defaults", e);
        }
    }

    private void writeDefaultGeneralConfig(Path cfgFile) {
        try (Writer w = Files.newBufferedWriter(cfgFile)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("server-name",          DEFAULT_SERVER_NAME);
            obj.addProperty("kick-message",         DEFAULT_KICK_MESSAGE);
            obj.addProperty("grace-period-seconds", DEFAULT_GRACE_SECONDS);
            obj.addProperty("kick-without-mod",     DEFAULT_KICK_WITHOUT_MOD);
            obj.addProperty("telemetry",      DEFAULT_TELEMETRY);
            obj.addProperty("telemetry-host", DEFAULT_TELEMETRY_HOST);
            obj.add("exempt-players", new JsonArray());
            GSON.toJson(obj, w);
            logger.info("Created default config.json");
        } catch (IOException e) {
            logger.error("Failed to write default config.json", e);
        }
    }

    // ---- accessors ----

    public Map<String, ModStatus> getStatusMap()    { return Collections.unmodifiableMap(statusMap); }
    public Set<String>            getExemptPlayers() { return Collections.unmodifiableSet(exemptPlayers); }
    public String  getServerName()     { return serverName; }
    public String  getKickMessage()    { return kickMessage; }
    public int     getGraceSeconds()   { return graceSeconds; }
    public boolean isKickWithoutMod()  { return kickWithoutMod; }
    public boolean isTelemetry()      { return telemetry; }
    public String  getTelemetryHost() { return telemetryHost; }

}
