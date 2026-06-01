//? if fabric {
package fr.zeffut.modchecker.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ModListPayload(String json) implements CustomPayload {
    public static final Id<ModListPayload> ID = new Id<>(Identifier.of("modchecker", "modlist"));
    public static final PacketCodec<RegistryByteBuf, ModListPayload> CODEC =
            PacketCodecs.STRING.xmap(ModListPayload::new, ModListPayload::json).cast();
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
//?}
