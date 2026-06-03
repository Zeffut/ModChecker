package fr.zeffut.modchecker.velocity;

import fr.zeffut.modchecker.telemetry.PostHogClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mappe les événements métier Velocity vers PostHog. */
public final class Telemetry {

    private final PostHogClient client;
    private final String serverName;
    private final String serverInstallId;

    public Telemetry(PostHogClient client, String serverName, String serverInstallId) {
        this.client = client;
        this.serverName = serverName;
        this.serverInstallId = serverInstallId;
    }

    public void proxyEnabled(String proxyVersion, boolean kickWithoutMod,
                             int graceSeconds, int exemptCount) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("server_install_id", serverInstallId);
        p.put("proxy_version", proxyVersion);
        p.put("server_name", serverName);
        p.put("kick_without_mod", kickWithoutMod);
        p.put("grace_period_seconds", graceSeconds);
        p.put("exempt_count", exemptCount);
        client.capture("proxy_enabled", serverInstallId, p);
    }

    public void playerJoin(String uuid, String username, String ip) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", username);
        p.put("player_ip", ip);
        p.put("server_name", serverName);
        p.put("server_install_id", serverInstallId);
        client.capture("player_join", uuid, p);
    }

    public void modlistReceived(String uuid, String username, int modCount,
                                int newModsCount, boolean hasBanned) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", username);
        p.put("mod_count", modCount);
        p.put("new_mods_count", newModsCount);
        p.put("has_banned", hasBanned);
        p.put("server_install_id", serverInstallId);
        client.capture("modlist_received", uuid, p);
    }

    public void modDiscovered(String modId, String modName, String modVersion) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mod_id", modId);
        p.put("mod_name", modName);
        p.put("mod_version", modVersion);
        p.put("server_install_id", serverInstallId);
        client.capture("mod_discovered", serverInstallId, p);
    }

    public void playerKicked(String uuid, String username, String ip,
                             String reason, List<String> bannedMods) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", username);
        p.put("player_ip", ip);
        p.put("reason", reason);
        p.put("banned_mods", bannedMods);
        p.put("server_install_id", serverInstallId);
        client.capture("player_kicked", uuid, p);
    }

    public void playerNoMod(String uuid, String username) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", username);
        p.put("server_install_id", serverInstallId);
        client.capture("player_no_mod", uuid, p);
    }
}
