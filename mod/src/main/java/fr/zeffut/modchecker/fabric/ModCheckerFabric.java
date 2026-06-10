//? if fabric {
package fr.zeffut.modchecker.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.zeffut.modchecker.telemetry.PostHog;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModCheckerFabric implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("ModChecker");

    @Override
    public void onInitializeClient() {
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(ModListPayload.ID, ModListPayload.CODEC);
        //?} else {
        /*PayloadTypeRegistry.playC2S().register(ModListPayload.ID, ModListPayload.CODEC);
        *///?}

        String mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        String modVer = FabricLoader.getInstance().getModContainer("modchecker")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        Map<String, Object> started = new LinkedHashMap<>();
        started.put("loader", "fabric");
        started.put("installed_mods_count", FabricLoader.getInstance().getAllMods().size());
        started.put("os_name", System.getProperty("os.name"));
        started.put("os_arch", System.getProperty("os.arch"));
        started.put("java_version", System.getProperty("java.version"));
        PostHog.capture("client_started", "mod-fabric", mc, modVer, started);
        try {
            PostHog.startHeartbeat("mod-fabric", mc, modVer);
            fr.zeffut.modchecker.update.UpdateService.start();
        } catch (Throwable t) {
            LOG.warn("[ModChecker] auto-update init skipped: {}", t.toString());
        }

        // Handshake natif (minecraft:register) : à la connexion, si le serveur déclare accepter
        // modchecker:modlist (= il fait tourner le plugin ModChecker), on lui envoie la liste des
        // mods. On ne dépend PAS de la réception d'un paquet S2C custom (non fiable depuis un
        // serveur Bukkit/Paper) ; on n'envoie rien à un serveur qui n'a pas le plugin (confidentialité).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            boolean canSend = ClientPlayNetworking.canSend(ModListPayload.ID);
            Map<String, Object> joined = new LinkedHashMap<>();
            joined.put("server_has_modchecker", canSend);
            PostHog.capture("server_joined", "mod-fabric", mc, modVer, joined);
            LOG.info("[ModChecker] join — server accepts modchecker:modlist = {}", canSend);
            if (!canSend) return;
            client.execute(() -> {
                JsonArray mods = new JsonArray();
                for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                    JsonObject e = new JsonObject();
                    e.addProperty("id", mod.getMetadata().getId());
                    e.addProperty("name", mod.getMetadata().getName());
                    e.addProperty("version", mod.getMetadata().getVersion().getFriendlyString());
                    mods.add(e);
                }
                String json = mods.toString();
                try {
                    ClientPlayNetworking.send(new ModListPayload(json));
                    LOG.info("[ModChecker] mod list SENT — {} mods, {} chars", mods.size(), json.length());
                    Map<String, Object> sent = new LinkedHashMap<>();
                    sent.put("mod_count", mods.size());
                    sent.put("payload_chars", json.length());
                    PostHog.capture("modlist_sent", "mod-fabric", mc, modVer, sent);
                } catch (Throwable t) {
                    LOG.error("[ModChecker] send FAILED", t);
                    Map<String, Object> failed = new LinkedHashMap<>();
                    failed.put("error", t.toString());
                    PostHog.capture("modlist_send_failed", "mod-fabric", mc, modVer, failed);
                }
            });
        });
    }
}
//?}
