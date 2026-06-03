package fr.zeffut.modchecker;

import fr.zeffut.modchecker.telemetry.PostHogClient;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mappe les événements métier Paper vers PostHog. Aucune logique réseau ici. */
public final class Telemetry {

    private final PostHogClient client;
    private final String serverName;
    private final String serverInstallId;

    public Telemetry(PostHogClient client, String serverName, String serverInstallId) {
        this.client = client;
        this.serverName = serverName;
        this.serverInstallId = serverInstallId;
    }

    private static String ip(Player p) {
        InetSocketAddress a = p.getAddress();
        return a == null || a.getAddress() == null ? "unknown" : a.getAddress().getHostAddress();
    }

    public void pluginEnabled(String serverVersion, boolean kickWithoutMod,
                              long graceSeconds, int registeredMods, boolean onlineMode) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("server_install_id", serverInstallId);
        p.put("server_version", serverVersion);
        p.put("server_name", serverName);
        p.put("kick_without_mod", kickWithoutMod);
        p.put("grace_period_seconds", graceSeconds);
        p.put("registered_mod_count", registeredMods);
        p.put("online_mode", onlineMode);
        client.capture("plugin_enabled", serverInstallId, p);
    }

    public void playerJoin(Player player) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", player.getName());
        p.put("player_ip", ip(player));
        p.put("server_name", serverName);
        p.put("server_install_id", serverInstallId);
        client.capture("player_join", player.getUniqueId().toString(), p);
    }

    public void modlistReceived(Player player, int modCount, int payloadChars,
                                int newModsCount, boolean hasBanned) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", player.getName());
        p.put("mod_count", modCount);
        p.put("payload_chars", payloadChars);
        p.put("new_mods_count", newModsCount);
        p.put("has_banned", hasBanned);
        p.put("server_install_id", serverInstallId);
        client.capture("modlist_received", player.getUniqueId().toString(), p);
    }

    public void modDiscovered(String modId, String modName, String modVersion) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mod_id", modId);
        p.put("mod_name", modName);
        p.put("mod_version", modVersion);
        p.put("server_install_id", serverInstallId);
        client.capture("mod_discovered", serverInstallId, p);
    }

    public void playerKicked(Player player, String reason, List<String> bannedMods) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", player.getName());
        p.put("player_ip", ip(player));
        p.put("reason", reason);
        p.put("banned_mods", bannedMods);
        p.put("server_install_id", serverInstallId);
        client.capture("player_kicked", player.getUniqueId().toString(), p);
    }

    public void playerNoMod(Player player) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("username", player.getName());
        p.put("server_install_id", serverInstallId);
        client.capture("player_no_mod", player.getUniqueId().toString(), p);
    }

    public void modStatusChanged(String modId, String newStatus, String admin) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mod_id", modId);
        p.put("new_status", newStatus);
        p.put("admin", admin);
        p.put("server_install_id", serverInstallId);
        client.capture("mod_status_changed", "admin:" + admin, p);
    }

    public void commandUsed(String subcommand, String admin) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("subcommand", subcommand);
        p.put("admin", admin);
        p.put("server_install_id", serverInstallId);
        client.capture("command_used", "admin:" + admin, p);
    }
}
