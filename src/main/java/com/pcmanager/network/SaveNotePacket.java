package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.item.NoteItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
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

import java.util.ArrayList;
import java.util.List;

public record SaveNotePacket(boolean isMainHand, String title, List<String> pages, boolean seal, boolean sign) implements CustomPacketPayload {
    public static final Type<SaveNotePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "save_note_c2s"));

    public static final StreamCodec<ByteBuf, SaveNotePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SaveNotePacket::isMainHand,
            ByteBufCodecs.STRING_UTF8, SaveNotePacket::title,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), SaveNotePacket::pages,
            ByteBufCodecs.BOOL, SaveNotePacket::seal,
            ByteBufCodecs.BOOL, SaveNotePacket::sign,
            SaveNotePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveNotePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player)) return;

            InteractionHand hand = msg.isMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof NoteItem && !NoteItem.isSealed(stack) && !NoteItem.isCopy(stack)) {
                String authorName = "Неизвестный автор";
                if (msg.sign()) {
                    com.pcmanager.data.CharacterWorldData worldData = com.pcmanager.data.CharacterWorldData.get(player.server);
                    com.pcmanager.data.PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
                    if (pd != null && pd.hasActiveCharacter()) {
                        String cName = pd.getActiveCharacter().getName();
                        if (cName != null && !cName.isBlank()) {
                            authorName = cName.trim();
                        }
                    }
                }
                NoteItem.setNoteData(stack, msg.title(), msg.pages(), msg.seal(), false, false, authorName);
                if (msg.seal()) {
                    player.sendSystemMessage(Component.literal("✔ Записка завершена и запечатана.").withStyle(ChatFormatting.GREEN));
                }
            }
        });
    }
}
