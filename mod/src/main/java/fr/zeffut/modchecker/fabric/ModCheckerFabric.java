//? if fabric {
package fr.zeffut.modchecker.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public class ModCheckerFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(HelloPayload.ID, HelloPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ModListPayload.ID, ModListPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(HelloPayload.ID, (payload, context) -> {
            JsonArray mods = new JsonArray();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", mod.getMetadata().getId());
                entry.addProperty("name", mod.getMetadata().getName());
                entry.addProperty("version", mod.getMetadata().getVersion().getFriendlyString());
                mods.add(entry);
            }
            context.responseSender().sendPacket(new ModListPayload(mods.toString()));
        });
    }
}
//?}
