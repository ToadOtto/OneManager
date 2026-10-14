package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.MailEntry;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.item.NoteItem;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ClaimLetterPacket(UUID letterId) implements CustomPacketPayload {
    public static final Type<ClaimLetterPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "claim_letter_c2s"));

    public static final StreamCodec<ByteBuf, ClaimLetterPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), ClaimLetterPacket::letterId,
            ClaimLetterPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClaimLetterPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player) || player.server == null) return;

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
            if (pd == null) return;

            MailEntry letter = pd.getMail(msg.letterId());
            if (letter == null || letter.isClaimed()) return;

            if (player.getInventory().getFreeSlot() == -1) {
                player.sendSystemMessage(Component.literal("❌ Инвентарь полон! Освободите место, чтобы забрать копию записки.").withStyle(ChatFormatting.RED));
                return;
            }

            ItemStack openedStack = NoteItem.createOpenedLetter(letter.getTitle(), letter.getPages(), letter.getAuthor(), false);
            player.getInventory().add(openedStack);

            String title = letter.getTitle().isEmpty() ? "Записка" : letter.getTitle();
            pd.removeMail(msg.letterId());
            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
            player.sendSystemMessage(Component.literal("✔ «" + title + "» получена в инвентарь (вскрытое письмо)!").withStyle(ChatFormatting.GREEN));
        });
    }
}
