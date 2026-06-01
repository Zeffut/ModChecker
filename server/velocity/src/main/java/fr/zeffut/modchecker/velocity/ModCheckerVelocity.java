package fr.zeffut.modchecker.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import fr.zeffut.modchecker.ModListCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ModChecker — Velocity proxy plugin.
 * <p>
 * On join: sends {@code modchecker:hello} (S2C) so the client mod knows the plugin is present.
 * On modlist received ({@code modchecker:modlist}, C2S): decodes, applies BanPolicy,
 * and disconnects the player if they carry a banned mod.
 */
@Plugin(
    id      = "modchecker",
    name    = "ModChecker",
    version = "1.0.0",
    authors = {"Zeffut"}
)
public final class ModCheckerVelocity {

    /** Channel the proxy sends to announce its presence (S2C). */
    static final MinecraftChannelIdentifier HELLO_ID =
            MinecraftChannelIdentifier.create("modchecker", "hello");

    /** Channel the client sends its mod list on (C2S). */
    static final MinecraftChannelIdentifier MODLIST_ID =
            MinecraftChannelIdentifier.create("modchecker", "modlist");

    private static final String PLUGIN_VERSION = "1.0.0";

    private final ProxyServer proxy;
    private final Logger      logger;
    private final Path        dataDirectory;

    private ModCheckerConfig config;

    /**
     * Players who have joined but have not yet sent a modlist.
     * Populated on first join; removed when the modlist arrives or the player disconnects.
     * A scheduled grace-period task checks this set and kicks if kick-without-mod is enabled.
     */
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();

    @Inject
    public ModCheckerVelocity(ProxyServer proxy,
                              Logger logger,
                              @DataDirectory Path dataDirectory) {
        this.proxy         = proxy;
        this.logger        = logger;
        this.dataDirectory = dataDirectory;
    }

    // -----------------------------------------------------------------------
    // Proxy initialise
    // -----------------------------------------------------------------------

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Load config from data directory
        config = new ModCheckerConfig(dataDirectory, logger);
        config.load();

        // Register both channels so Velocity routes plugin messages to/from them
        proxy.getChannelRegistrar().register(HELLO_ID, MODLIST_ID);

        // Register this class as an event listener
        proxy.getEventManager().register(this, this);

        logger.info("ModChecker Velocity plugin enabled. Server: {}", config.getServerName());
        logger.info("Grace period: {}s | Kick-without-mod: {}",
                config.getGraceSeconds(), config.isKickWithoutMod());
    }

    // -----------------------------------------------------------------------
    // Player connect → send hello
    // -----------------------------------------------------------------------

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        // Only send hello when the player first joins the proxy (previous server is null)
        if (event.getPreviousServer() != null) {
            return;  // server switch — do not re-send hello
        }

        Player player = event.getPlayer();
        byte[] helloPayload = ModListCodec.encode(PLUGIN_VERSION);
        boolean sent = player.sendPluginMessage(HELLO_ID, helloPayload);
        if (sent) {
            logger.debug("Sent modchecker:hello to {}", player.getUsername());
        } else {
            logger.debug("Could not send modchecker:hello to {} (client may not have mod registered)",
                    player.getUsername());
        }

        // Track this player as pending (no modlist received yet) and schedule a grace-period check
        UUID playerId = player.getUniqueId();
        pendingPlayers.add(playerId);
        int graceSeconds = config.getGraceSeconds();
        proxy.getScheduler()
                .buildTask(this, () -> {
                    pendingPlayers.remove(playerId);
                    if (!config.isKickWithoutMod()) {
                        return;
                    }
                    // Re-fetch the player — they may have disconnected during the grace period
                    proxy.getPlayer(playerId).ifPresent(p -> {
                        if (config.getExemptPlayers().contains(playerId.toString())) {
                            logger.debug("Pending player {} is exempt, not kicking for missing mod", p.getUsername());
                            return;
                        }
                        logger.warn("Player {} did not send a modlist within {}s — disconnecting",
                                p.getUsername(), graceSeconds);
                        Component reason = buildKickComponent(
                                "[" + config.getServerName() + "] You must have the ModChecker mod installed.");
                        p.disconnect(reason);
                    });
                })
                .delay(graceSeconds, TimeUnit.SECONDS)
                .schedule();
    }

    // -----------------------------------------------------------------------
    // Plugin message received (C2S) → modlist
    // -----------------------------------------------------------------------

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Only process modchecker:modlist messages
        if (!MODLIST_ID.equals(event.getIdentifier())) {
            return;
        }

        // Always consume the event — do not forward modlist to backend servers
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Source must be a Player (not a backend server sending upstream)
        if (!(event.getSource() instanceof Player player)) {
            return;
        }

        // Player sent a modlist — remove from pending (grace-period timer no longer relevant)
        pendingPlayers.remove(player.getUniqueId());

        // Check exemptions
        String uuidStr = player.getUniqueId().toString();
        if (config.getExemptPlayers().contains(uuidStr)) {
            logger.debug("Player {} is exempt, skipping ban check", player.getUsername());
            return;
        }

        // Delegate to the pure decision logic
        ModCheckDecision.Result decision = ModCheckDecision.evaluate(
                event.getData(),
                config.getStatusMap(),
                config.getKickMessage()
        );

        // Log what mods the player has
        if (!decision.getAllMods().isEmpty()) {
            logger.info("Player {} reported {} mod(s): {}",
                    player.getUsername(),
                    decision.getAllMods().size(),
                    decision.getAllMods().stream()
                            .map(m -> m.id() + "@" + m.version())
                            .reduce((a, b) -> a + ", " + b).orElse(""));
        } else {
            logger.info("Player {} sent empty or unreadable mod list", player.getUsername());
        }

        // Disconnect if banned mods found
        if (decision.isShouldDisconnect()) {
            logger.warn("Disconnecting {} — banned mods: {}",
                    player.getUsername(), decision.getBannedMods());
            Component reason = buildKickComponent(decision.getDisconnectMessage());
            player.disconnect(reason);
        }
    }

    // -----------------------------------------------------------------------
    // Player disconnect → clean up pending state
    // -----------------------------------------------------------------------

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        pendingPlayers.remove(event.getPlayer().getUniqueId());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Component buildKickComponent(String message) {
        return Component.text()
                .append(Component.text("[ModChecker] ", NamedTextColor.RED))
                .append(Component.text(message, NamedTextColor.WHITE))
                .build();
    }
}
