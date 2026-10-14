package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.data.CharacterWorldData;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S packet sent when a player presses the character history keybinding.
 * Requests the server to sync character data and open the character history screen.
 */
public record RequestCharHistoryC2SPacket() implements CustomPacketPayload {
    public static final Type<RequestCharHistoryC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "request_char_history"));

    public static final StreamCodec<ByteBuf, RequestCharHistoryC2SPacket> STREAM_CODEC = StreamCodec.unit(new RequestCharHistoryC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestCharHistoryC2SPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (p instanceof ServerPlayer player && player.server != null) {
                CharacterWorldData worldData = CharacterWorldData.get(player.server);
                worldData.updatePlayerName(player.getUUID(), player.getName().getString());
                NetworkHandler.sendToPlayer(player, new SyncAllCharactersPacket(worldData.save(new CompoundTag()), true));
            }
        });
    }
}
