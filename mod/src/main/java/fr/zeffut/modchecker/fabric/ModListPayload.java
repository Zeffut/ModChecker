//? if fabric {
package fr.zeffut.modchecker.fabric;

//? if >=26.1 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ModListPayload(String json) implements CustomPacketPayload {
    public static final Type<ModListPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("modchecker", "modlist"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ModListPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ModListPayload::json, ModListPayload::new);
    @Override public Type<ModListPayload> type() { return ID; }
}
//?} else {
/*import net.minecraft.network.RegistryByteBuf;
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
*///?}
//?}
