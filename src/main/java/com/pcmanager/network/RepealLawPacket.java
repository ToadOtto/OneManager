package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.data.LawEntry;
import com.pcmanager.data.PlayerCharacterData;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RepealLawPacket(UUID lawId) implements CustomPacketPayload {
    public static final Type<RepealLawPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "repeal_law_c2s"));

    public static final StreamCodec<ByteBuf, RepealLawPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), RepealLawPacket::lawId,
            RepealLawPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RepealLawPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player) || player.server == null) return;

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            if (!worldData.isKnyaz(player.getUUID())) {
                player.sendSystemMessage(Component.literal("❌ Только действующий Князь имеет право отменять государственные законы!").withStyle(ChatFormatting.RED));
                return;
            }

            LawEntry law = worldData.getLaw(msg.lawId());
            if (law == null) {
                player.sendSystemMessage(Component.literal("❌ Закон не найден!").withStyle(ChatFormatting.RED));
                return;
            }

            String lawTitle = law.getTitle();
            worldData.removeLaw(msg.lawId());
            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
            String knyazCharName = pd != null && pd.hasActiveCharacter() && pd.getActiveCharacter().getName() != null && !pd.getActiveCharacter().getName().isBlank()
                    ? pd.getActiveCharacter().getName().trim()
                    : player.getName().getString();

            Component announce = Component.literal("📜 Князь " + knyazCharName + " отменил государственный закон «" + lawTitle + "»!")
                    .withStyle(ChatFormatting.GOLD);
            player.server.getPlayerList().broadcastSystemMessage(announce, false);
        });
    }
}
