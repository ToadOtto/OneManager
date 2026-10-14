package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.data.MailEntry;
import com.pcmanager.data.PlayerCharacterData;
import com.pcmanager.item.NoteItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public record SendNotePacket(boolean isMainHand, UUID recipientUuid, boolean hideAuthor) implements CustomPacketPayload {
    public static final Type<SendNotePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "send_note_c2s"));

    public static final StreamCodec<ByteBuf, SendNotePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SendNotePacket::isMainHand,
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), SendNotePacket::recipientUuid,
            ByteBufCodecs.BOOL, SendNotePacket::hideAuthor,
            SendNotePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SendNotePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player) || player.server == null) return;

            InteractionHand hand = msg.isMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof NoteItem) || !NoteItem.isSealed(stack) || NoteItem.isOpened(stack)) {
                InteractionHand altHand = msg.isMainHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack altStack = player.getItemInHand(altHand);
                if (altStack.getItem() instanceof NoteItem && NoteItem.isSealed(altStack) && !NoteItem.isOpened(altStack)) {
                    stack = altStack;
                    hand = altHand;
                } else {
                    player.sendSystemMessage(Component.literal("❌ Невозможно отправить данную записку!").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            UUID targetUuid = msg.recipientUuid() != null ? msg.recipientUuid() : player.getUUID();
            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            PlayerCharacterData recipientPd = worldData.getOrCreatePlayerData(targetUuid);

            String title = NoteItem.getTitle(stack);
            List<String> pages = NoteItem.getPages(stack);
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            String author = NoteItem.getAuthor(stack);
            if (msg.hideAuthor()) {
                author = "Неизвестный автор";
            }

            recipientPd.addMail(new MailEntry(UUID.randomUUID(), title, pages, date, author, false));
            stack.shrink(1);
            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            if (targetUuid.equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("✔ Записка отправлена себе! Проверьте вкладку «Письма» в меню.").withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.literal("✔ Записка успешно отправлена получателю.").withStyle(ChatFormatting.GREEN));
            }
        });
    }
}
