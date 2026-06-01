//? if fabric {
package fr.zeffut.modchecker.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HelloPayload(String version) implements CustomPayload {
    public static final Id<HelloPayload> ID = new Id<>(Identifier.of("modchecker", "hello"));
    public static final PacketCodec<RegistryByteBuf, HelloPayload> CODEC =
            PacketCodecs.STRING.xmap(HelloPayload::new, HelloPayload::version).cast();
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
//?}
