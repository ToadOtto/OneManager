package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncAllCharactersPacket(CompoundTag worldDataTag, boolean openGui) implements CustomPacketPayload {
    public SyncAllCharactersPacket(CompoundTag worldDataTag, boolean openGui) {
        this.worldDataTag = com.onemanager.data.CharacterWorldData.stripServerSecrets(worldDataTag);
        this.openGui = openGui;
    }

    public static final Type<SyncAllCharactersPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "sync_all_characters"));

    public static final StreamCodec<ByteBuf, SyncAllCharactersPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, SyncAllCharactersPacket::worldDataTag,
            ByteBufCodecs.BOOL, SyncAllCharactersPacket::openGui,
            SyncAllCharactersPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAllCharactersPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleSyncAll(payload.worldDataTag(), payload.openGui());
        });
    }
}
