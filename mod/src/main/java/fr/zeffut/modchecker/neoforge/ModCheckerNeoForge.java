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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = "modchecker", dist = Dist.CLIENT)
public class ModCheckerNeoForge {
    private static final Logger LOG = LoggerFactory.getLogger("ModChecker");

    public ModCheckerNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onLoggingIn);
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
        } catch (Throwable t) {
            LOG.info("[ModChecker] not sent (server without ModChecker?): {}", t.toString());
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
