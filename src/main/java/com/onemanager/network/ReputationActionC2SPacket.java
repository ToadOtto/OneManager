package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.economy.EconomyManager;
import com.onemanager.reputation.ReputationAction;
import com.onemanager.reputation.ReputationManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: a player performs a Knyaz reputation action (+N / -N).
 * All validation (Knyaz restriction, faction requirement, cooldown, cost) happens on the server.
 */
public record ReputationActionC2SPacket(int actionId, boolean positive) implements CustomPacketPayload {
    public static final Type<ReputationActionC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "reputation_action"));

    public static final StreamCodec<ByteBuf, ReputationActionC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReputationActionC2SPacket::actionId,
            ByteBufCodecs.BOOL, ReputationActionC2SPacket::positive,
            ReputationActionC2SPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReputationActionC2SPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }
            ReputationAction action = ReputationAction.byId(payload.actionId());
            if (action == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            if (!worldData.hasKnyaz()) {
                fail(player, "В государстве нет Князя — влиять на репутацию некому.");
                return;
            }
            if (worldData.isKnyaz(player.getUUID())) {
                fail(player, "Князь не может влиять на собственную репутацию.");
                return;
            }
            if (action.isFactionAction()) {
                int influence = ReputationManager.getFactionInfluencePercent(worldData, player.getUUID());
                if (influence < action.getRequiredFactionInfluence()) {
                    fail(player, "Требуется не менее " + action.getRequiredFactionInfluence() + "% влияния фракции. Система влияния фракций в разработке.");
                    return;
                }
            }

            PlayerCharacterData pd = worldData.getOrCreatePlayerData(player.getUUID());
            long now = worldData.getTotalElapsedTicks();
            long remaining = ReputationManager.getCooldownRemaining(pd.getReputationActionLastUsed(action.getId()), action, now);
            if (remaining > 0) {
                fail(player, "Действие ещё недоступно. Осталось: " + ReputationManager.formatTicks(remaining));
                return;
            }

            // Pay the cost (money is burned, not added to the treasury)
            if (action.getCostSprota() > 0) {
                if (!EconomyManager.canAfford(player, action.getCostSprota())
                        || !EconomyManager.deductBalance(player, action.getCostSprota())) {
                    fail(player, "Недостаточно средств на счёте! Требуется " + action.getCostSprota() + " $PR.");
                    return;
                }
            }
            if (action.getCostBrands() > 0) {
                if (pd.getStateBrands() < action.getCostBrands()) {
                    fail(player, "Недостаточно Гос. Марок на счёте! Требуется " + action.getCostBrands() + " шт.");
                    return;
                }
                worldData.removeStateBrands(player.getUUID(), action.getCostBrands());
            }

            int delta = action.getDelta(payload.positive());
            int applied = worldData.applyReputationChange(delta, "action:" + action.getId());
            pd.markReputationActionUsed(action.getId(), now);
            worldData.setDirty();

            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            String signed = (delta > 0 ? "+" : "") + delta;
            Component msg = Component.literal("👑 " + action.getName(payload.positive()) + ": ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(signed + " к репутации Князя").withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" (текущая: " + worldData.getKnyazReputation() + ")").withStyle(ChatFormatting.GRAY));
            if (applied == 0) {
                msg = msg.copy().append(Component.literal(" — шкала уже на пределе.").withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(msg);
        });
    }

    private static void fail(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("❌ " + text).withStyle(ChatFormatting.RED));
    }
}
