package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.data.ActiveCharacter;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.data.DeadCharacter;
import com.pcmanager.data.PlayerCharacterData;
import com.pcmanager.effect.ModEffects;
import com.pcmanager.event.BurdenEventHandler;
import com.pcmanager.item.ModItems;
import com.pcmanager.item.RemainsItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RebirthC2SPacket(
        String name,
        int age,
        String race,
        String appearance,
        String description,
        String deathCause,
        boolean isRewrite
) implements CustomPacketPayload {
    public static final Type<RebirthC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "rebirth_c2s"));

    public static final StreamCodec<ByteBuf, RebirthC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.name());
                ByteBufCodecs.VAR_INT.encode(buf, msg.age());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.race());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.appearance());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.description());
                ByteBufCodecs.STRING_UTF8.encode(buf, msg.deathCause());
                ByteBufCodecs.BOOL.encode(buf, msg.isRewrite());
            },
            buf -> new RebirthC2SPacket(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RebirthC2SPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            worldData.updatePlayerName(player.getUUID(), player.getName().getString());
            PlayerCharacterData playerData = worldData.getOrCreatePlayerData(player.getUUID());

            String newName = msg.name().trim();
            if (newName.isEmpty()) {
                newName = "Безымянный";
            }
            int newAge = Math.max(1, msg.age());
            String newRace = msg.race().trim();
            String newAppearance = msg.appearance().trim();
            String newDescription = msg.description().trim();

            if (msg.isRewrite()) {
                // Character rewrite via New Life Structure (no death, no remains, no burden)
                ItemStack mainHand = player.getMainHandItem();
                ItemStack offHand = player.getOffhandItem();
                if (!player.isCreative()) {
                    if (mainHand.is(ModItems.NEW_LIFE_STRUCTURE.get())) {
                        mainHand.shrink(1);
                    } else if (offHand.is(ModItems.NEW_LIFE_STRUCTURE.get())) {
                        offHand.shrink(1);
                    }
                }

                long currentGameTime = player.serverLevel().getGameTime();
                boolean preserveBurden = playerData.hasActiveCharacter() && playerData.getActiveCharacter().isStatusBurden();
                ActiveCharacter newChar = new ActiveCharacter(newName, newAge, newRace, newAppearance, newDescription, System.currentTimeMillis(), preserveBurden, currentGameTime);
                playerData.setActiveCharacter(newChar);
                worldData.setDirty();

                NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                player.sendSystemMessage(Component.literal("Данные персонажа успешно сохранены!").withStyle(ChatFormatting.GREEN));
                PCManager.LOGGER.info("Player {} ({}) rewrote character: name='{}', age={}, race='{}'",
                        player.getName().getString(), player.getUUID(), newName, newAge, newRace);
                return;
            }

            // Normal rebirth upon death
            String cause = msg.deathCause().trim();
            if (cause.isEmpty()) {
                cause = "Неизвестная причина";
            }

            // 1. Current active character is moved to characterHistory
            DeadCharacter dead;
            if (playerData.hasActiveCharacter()) {
                dead = playerData.killActiveCharacter(cause, System.currentTimeMillis(), player.serverLevel().getGameTime());
            } else {
                dead = new DeadCharacter(
                        "Предок",
                        Math.max(1, msg.age()),
                        playerData.getProfessions(),
                        playerData.getFaction(),
                        System.currentTimeMillis(),
                        cause,
                        false,
                        -1L,
                        newRace,
                        newAppearance,
                        newDescription
                );
                playerData.addDeadCharacter(dead);
            }

            // 2. Create new active character with input Name, Age, Race, Appearance, Description.
            long currentGameTime = player.serverLevel().getGameTime();
            ActiveCharacter newChar = new ActiveCharacter(newName, newAge, newRace, newAppearance, newDescription, System.currentTimeMillis(), true, currentGameTime);
            playerData.setActiveCharacter(newChar);
            worldData.setDirty();

            // Broadcast updated character state to all connected clients
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            PCManager.LOGGER.info("Player {} ({}) completed Rebirth: new character '{}', age {}, race '{}'",
                    player.getName().getString(), player.getUUID(), newName, newAge, newRace);

            // 3. Respawn player on the server
            ServerPlayer targetPlayer = player;
            if (player.server != null && (player.isDeadOrDying() || player.getHealth() <= 0.0F)) {
                ServerPlayer newPlayer = player.server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                if (player.connection != null) {
                    player.connection.player = newPlayer;
                }
                targetPlayer = newPlayer;
            }

            // 4. Give remains to player (forced inventory replacement if full)
            ItemStack remains = RemainsItem.createRemains(dead, player.getUUID());
            BurdenEventHandler.forceAddToInventory(targetPlayer, remains);
            targetPlayer.inventoryMenu.broadcastChanges();

            // 5. Apply Ancestor Burden effect to newly spawned character
            targetPlayer.addEffect(new MobEffectInstance(ModEffects.ANCESTOR_BURDEN, -1, 0, false, false, true));
        });
    }
}
