//? if neoforge {
package fr.zeffut.modchecker.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
//? if <1.21.11 {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?} else {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforgespi.language.IModInfo;

@Mod(value = "modchecker", dist = Dist.CLIENT)
public class ModCheckerNeoForge {
    public ModCheckerNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
    }
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0").optional();
        registrar.playToClient(HelloPayload.TYPE, HelloPayload.CODEC,
                //? if <1.21.11 {
                /*(payload, context) -> PacketDistributor.sendToServer(buildModList()));
                *///?} else {
                (payload, context) -> ClientPacketDistributor.sendToServer(buildModList()));
                //?}
        registrar.playToServer(ModListPayload.TYPE, ModListPayload.CODEC, (payload, context) -> {});
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
