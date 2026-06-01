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

public record HelloPayload(String version) implements CustomPacketPayload {
    public static final Type<HelloPayload> TYPE =
            //? if <1.21.11 {
            /*new Type<>(ResourceLocation.fromNamespaceAndPath("modchecker", "hello"));
            *///?} else {
            new Type<>(Identifier.fromNamespaceAndPath("modchecker", "hello"));
            //?}
    public static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, HelloPayload::version, HelloPayload::new);
    @Override public Type<HelloPayload> type() { return TYPE; }
}
//?}
