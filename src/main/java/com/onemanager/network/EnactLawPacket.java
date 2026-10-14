package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.LawEntry;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record EnactLawPacket(boolean isMainHand, String title, List<String> pages) implements CustomPacketPayload {
    public static final Type<EnactLawPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "enact_law_c2s"));

    public static final StreamCodec<ByteBuf, EnactLawPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EnactLawPacket::isMainHand,
            ByteBufCodecs.STRING_UTF8, EnactLawPacket::title,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), EnactLawPacket::pages,
            EnactLawPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EnactLawPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player) || player.server == null) return;

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            if (!worldData.isKnyaz(player.getUUID())) {
                player.sendSystemMessage(Component.literal("❌ Только Князь имеет право издавать государственные законы!").withStyle(ChatFormatting.RED));
                return;
            }

            InteractionHand hand = msg.isMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof NoteItem)) {
                InteractionHand altHand = msg.isMainHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack altStack = player.getItemInHand(altHand);
                if (altStack.getItem() instanceof NoteItem) {
                    stack = altStack;
                } else {
                    player.sendSystemMessage(Component.literal("❌ Записка не найдена в руках!").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
            String knyazCharName = pd != null && pd.hasActiveCharacter() && pd.getActiveCharacter().getName() != null && !pd.getActiveCharacter().getName().isBlank()
                    ? pd.getActiveCharacter().getName().trim()
                    : player.getName().getString();

            String lawTitle = msg.title() != null && !msg.title().isBlank() ? msg.title().trim() : "Государственный указ";
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

            LawEntry newLaw = new LawEntry(
                    UUID.randomUUID(),
                    lawTitle,
                    msg.pages(),
                    knyazCharName,
                    new ArrayList<>(),
                    date,
                    date
            );

            worldData.addLaw(newLaw);
            stack.shrink(1);
            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            Component announce = Component.literal("📜 Князь " + knyazCharName + " издал новый государственный закон «" + lawTitle + "»!")
                    .withStyle(ChatFormatting.GOLD);
            player.server.getPlayerList().broadcastSystemMessage(announce, false);
        });
    }
}
