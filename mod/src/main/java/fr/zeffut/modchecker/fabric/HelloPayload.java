//? if fabric {
package fr.zeffut.modchecker.fabric;

//? if >=26.1 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HelloPayload(String version) implements CustomPacketPayload {
    public static final Type<HelloPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("modchecker", "hello"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, HelloPayload::version, HelloPayload::new);
    @Override public Type<HelloPayload> type() { return ID; }
}
//?} else {
/*import net.minecraft.network.RegistryByteBuf;
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
*///?}
//?}
