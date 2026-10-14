package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenRebirthScreenS2CPacket(
        boolean isRewrite,
        String initialName,
        int initialAge,
        String initialRace,
        String initialAppearance,
        String initialDescription
) implements CustomPacketPayload {
    public static final Type<OpenRebirthScreenS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "open_rebirth_screen"));

    public static final StreamCodec<ByteBuf, OpenRebirthScreenS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                ByteBufCodecs.BOOL.encode(buf, msg.isRewrite());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.initialName());
                ByteBufCodecs.VAR_INT.encode(buf, msg.initialAge());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.initialRace());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.initialAppearance());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.initialDescription());
            },
            buf -> new OpenRebirthScreenS2CPacket(
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRebirthScreenS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.openRebirthScreen(
                    payload.isRewrite(),
                    payload.initialName(),
                    payload.initialAge(),
                    payload.initialRace(),
                    payload.initialAppearance(),
                    payload.initialDescription()
            );
        });
    }
}
