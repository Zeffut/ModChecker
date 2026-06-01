//? if neoforge {
package fr.zeffut.modchecker.neoforge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if <1.21.11 {
/*import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.resources.Identifier;
//?}

public record ModListPayload(String json) implements CustomPacketPayload {
    public static final Type<ModListPayload> TYPE =
            //? if <1.21.11 {
            /*new Type<>(ResourceLocation.fromNamespaceAndPath("modchecker", "modlist"));
            *///?} else {
            new Type<>(Identifier.fromNamespaceAndPath("modchecker", "modlist"));
            //?}
    public static final StreamCodec<FriendlyByteBuf, ModListPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ModListPayload::json, ModListPayload::new);
    @Override public Type<ModListPayload> type() { return TYPE; }
}
//?}
