//? if neoforge {
package fr.zeffut.modchecker.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
//? if <1.21.11 {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?} else {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import fr.zeffut.modchecker.telemetry.PostHog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = "modchecker", dist = Dist.CLIENT)
public class ModCheckerNeoForge {
    private static final Logger LOG = LoggerFactory.getLogger("ModChecker");

    public ModCheckerNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onLoggingIn);

        Map<String, Object> started = new LinkedHashMap<>();
        started.put("loader", "neoforge");
        started.put("installed_mods_count", ModList.get().size());
        started.put("os_name", System.getProperty("os.name"));
        started.put("os_arch", System.getProperty("os.arch"));
        started.put("java_version", System.getProperty("java.version"));
        PostHog.capture("client_started", "mod-neoforge", mcVersion(), modVersion(), started);
        try {
            PostHog.startHeartbeat("mod-neoforge", mcVersion(), modVersion());
            fr.zeffut.modchecker.update.UpdateService.start();
        } catch (Throwable t) {
            LOG.warn("[ModChecker] auto-update init skipped: {}", t.toString());
        }
    }

    private static String mcVersion() {
        return versionOf("minecraft");
    }

    private static String modVersion() {
        return versionOf("modchecker");
    }

    private static String versionOf(String modId) {
        for (IModInfo mod : ModList.get().getMods()) {
            if (modId.equals(mod.getModId())) return mod.getVersion().toString();
        }
        return "unknown";
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional() : l'envoi vers un serveur non-NeoForge (Paper) n'a lieu que si ce dernier a
        // déclaré accepter modchecker:modlist (= il fait tourner le plugin ModChecker).
        event.registrar("1.0.0").optional()
                .playToServer(ModListPayload.TYPE, ModListPayload.CODEC, (payload, context) -> {});
    }

    // À la connexion, on envoie la liste des mods. On ne dépend PAS de la réception d'un paquet S2C
    // custom (non fiable depuis un serveur Bukkit/Paper) ; le payload `optional` n'est transmis qu'à
    // un serveur qui a déclaré le channel → rien envoyé à un serveur sans le plugin (confidentialité).
    private void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            ModListPayload payload = buildModList();
            //? if <1.21.11 {
            /*PacketDistributor.sendToServer(payload);
            *///?} else {
            ClientPacketDistributor.sendToServer(payload);
            //?}
            LOG.info("[ModChecker] mod list SENT on login");
            Map<String, Object> sent = new LinkedHashMap<>();
            sent.put("mod_count", ModList.get().size());
            PostHog.capture("modlist_sent", "mod-neoforge", mcVersion(), modVersion(), sent);
        } catch (Throwable t) {
            LOG.info("[ModChecker] not sent (server without ModChecker?): {}", t.toString());
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("error", t.toString());
            PostHog.capture("modlist_send_failed", "mod-neoforge", mcVersion(), modVersion(), failed);
        }
    }

    private static ModListPayload buildModList() {
        JsonArray mods = new JsonArray();
        for (IModInfo mod : ModList.get().getMods()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", mod.getModId());
            entry.addProperty("name", mod.getDisplayName());
            entry.addProperty("version", mod.getVersion().toString());
            mods.add(entry);
        }
        return new ModListPayload(mods.toString());
    }
}
//?}
