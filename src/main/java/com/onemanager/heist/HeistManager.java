package com.onemanager.heist;

import com.onemanager.OneManager;
import com.onemanager.block.TreasuryChestBlock;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.TreasuryChestEntry;
import com.onemanager.item.LockpickItem;
import com.onemanager.network.LockpickEndS2CPacket;
import com.onemanager.network.LockpickQteS2CPacket;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.OpenLockpickScreenS2CPacket;
import com.onemanager.network.SyncAllCharactersPacket;
import com.oneshop.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HeistManager {
    private static final Map<UUID, LockpickSession> activeLockpicks = new ConcurrentHashMap<>();
    private static int alarmTicksRemaining = 0;

    private HeistManager() {}

    public static void tryStartLockpick(ServerPlayer player, InteractionHand hand, BlockPos chestPos) {
        if (player == null || chestPos == null) return;
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof LockpickItem lockpickItem)) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;
        CharacterWorldData worldData = CharacterWorldData.get(server);

        // Knyaz cannot rob own treasury
        if (worldData.canAccessTreasury(player.getUUID())) {
            player.sendSystemMessage(Component.literal("❌ Князь не может грабить собственную казну!").withStyle(ChatFormatting.RED));
            return;
        }

        // Online player requirement (at least 4 players online for non-creative/spectator)
        if (!player.isCreative() && !player.isSpectator()) {
            int online = server.getPlayerList().getPlayerCount();
            if (online < 4) {
                player.sendSystemMessage(Component.literal("❌ Для взлома казны на сервере должно быть не менее 4 игроков онлайн!").withStyle(ChatFormatting.RED));
                return;
            }
        }

        String dim = player.level().dimension().location().toString();
        TreasuryChestEntry entry = worldData.getTreasuryChestAt(dim, chestPos);
        if (entry == null) {
            player.sendSystemMessage(Component.literal("❌ Этот блок не зарегистрирован в казне.").withStyle(ChatFormatting.RED));
            return;
        }

        if (entry.isSealBroken()) {
            player.sendSystemMessage(Component.literal("❌ Пломбы этого хранилища уже сорваны! Хранилище не активно.").withStyle(ChatFormatting.RED));
            return;
        }

        if (entry.isLooted()) {
            player.sendSystemMessage(Component.literal("❌ Этот сундук уже опустошен во время текущей вылазки!").withStyle(ChatFormatting.RED));
            return;
        }

        if (entry.getEffectiveAmount() <= 0) {
            player.sendSystemMessage(Component.literal("❌ В этом сундуке нет монет для похищения!").withStyle(ChatFormatting.RED));
            return;
        }

        // Cancel existing lockpick if any
        if (activeLockpicks.containsKey(player.getUUID())) {
            cancelLockpick(player, "Предыдущий взлом прерван.");
        }

        LockpickSession session = new LockpickSession(
                player.getUUID(),
                chestPos,
                player.level().dimension(),
                player.position(),
                hand,
                lockpickItem,
                player.level().getRandom()
        );
        activeLockpicks.put(player.getUUID(), session);

        // Open GUI on client
        NetworkHandler.sendToPlayer(player, new OpenLockpickScreenS2CPacket(session.getTotalTicks(), lockpickItem.getDescriptionId()));

        // Local quiet click sound in 10 block radius
        playSoundInRadius(server, player.level().dimension(), chestPos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.7F, 1.4F, 10.0);
    }

    public static void tickServer(MinecraftServer server) {
        if (server == null) return;

        // 1. Tick lockpick sessions
        for (Map.Entry<UUID, LockpickSession> e : activeLockpicks.entrySet()) {
            UUID uuid = e.getKey();
            LockpickSession session = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);

            if (player == null || !player.isAlive()) {
                activeLockpicks.remove(uuid);
                continue;
            }

            // Movement validation: player must not move more than 0.55 blocks from start pos
            if (player.position().distanceTo(session.getStartPlayerPos()) > 0.55D) {
                failLockpick(server, player, session, "Взлом прерван: вы сдвинулись с места!");
                activeLockpicks.remove(uuid);
                continue;
            }

            // Chest validation: must still be a TreasuryChestBlock
            ServerLevel level = server.getLevel(session.getDimensionKey());
            if (level == null || !(level.getBlockState(session.getChestPos()).getBlock() instanceof TreasuryChestBlock)) {
                failLockpick(server, player, session, "Хранилище было разрушено!");
                activeLockpicks.remove(uuid);
                continue;
            }

            session.incrementElapsedTicks();

            // Check QTE timeout
            if (session.isQteTimedOut()) {
                handleMistake(server, player, session, "Время на реакцию истекло!");
                if (session.hasExceededMistakes()) {
                    activeLockpicks.remove(uuid);
                    continue;
                }
            } else if (session.shouldStartQte()) {
                int qteId = session.generateQte(level.getRandom());
                NetworkHandler.sendToPlayer(player, new LockpickQteS2CPacket(
                        qteId,
                        session.getActiveTargetDigit(),
                        session.getLockpickItem().getQteWindowTicks()
                ));
            }

            // Check completion
            if (session.isComplete()) {
                completeLockpick(server, player, session);
                activeLockpicks.remove(uuid);
            }
        }

        // 2. Tick heist escape timers
        CharacterWorldData worldData = CharacterWorldData.get(server);
        Map<UUID, HeistSession> heistSessions = worldData.getHeistSessions();
        if (!heistSessions.isEmpty()) {
            for (HeistSession heist : heistSessions.values()) {
                ServerPlayer thief = server.getPlayerList().getPlayer(heist.getThiefUuid());
                boolean expired = heist.decrementTicks();

                // Send real-time Actionbar status to the thief
                if (thief != null && thief.isAlive()) {
                    double noise = HeistMath.calculateNoise(heist.getStolenAccumulated(), worldData.getTreasury());
                    HeistMath.RiskInfo risk = HeistMath.getRiskInfo(noise);
                    String timer = HeistMath.formatTimer(heist.getTicksRemaining());
                    MutableComponent bar = Component.literal(String.format("[Шум: %.1f%%] СТАТУС: %s | Отход: %s",
                            noise, risk.name(), timer)).withStyle(risk.color());
                    thief.sendSystemMessage(bar, true);
                }

                if (expired) {
                    triggerAlarm(server, heist, "Таймер отхода (3 мин) истек! Пломбы хранилища разрушились.");
                    worldData.removeHeistSession(heist.getThiefUuid());
                }
            }
        }

        // 3. Resonate alarm bell (rings for 10 seconds / 200 ticks)
        if (alarmTicksRemaining > 0) {
            if (alarmTicksRemaining % 20 == 0) {
                broadcastGlobalSound(server, SoundEvents.BELL_RESONATE, 1.0F, 0.8F);
            }
            alarmTicksRemaining--;
        }
    }

    public static void onQteAnswer(ServerPlayer player, int qteId, int pressedDigit) {
        if (player == null) return;
        LockpickSession session = activeLockpicks.get(player.getUUID());
        if (session == null || session.getActiveQteId() != qteId) return;

        if (pressedDigit == session.getActiveTargetDigit()) {
            // Correct digit pressed!
            playSoundInRadius(player.getServer(), session.getDimensionKey(), session.getChestPos(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.7F, 1.4F, 10.0);
            session.clearQte(player.level().getRandom());
        } else {
            // Wrong digit pressed!
            handleMistake(player.getServer(), player, session, "Неверный штифт!");
            if (session.hasExceededMistakes()) {
                activeLockpicks.remove(player.getUUID());
            }
        }
    }

    public static void onCancel(ServerPlayer player) {
        if (player == null) return;
        LockpickSession session = activeLockpicks.remove(player.getUUID());
        if (session != null) {
            NetworkHandler.sendToPlayer(player, new LockpickEndS2CPacket(false, "Взлом отменен."));
        }
    }

    public static void onPlayerHurt(ServerPlayer player) {
        if (player == null) return;
        LockpickSession session = activeLockpicks.remove(player.getUUID());
        if (session != null) {
            failLockpick(player.getServer(), player, session, "Взлом сорван получением урона!");
        }
    }

    public static void onPlayerLogout(UUID uuid) {
        if (uuid != null) {
            activeLockpicks.remove(uuid);
        }
    }

    private static void cancelLockpick(ServerPlayer player, String reason) {
        LockpickSession session = activeLockpicks.remove(player.getUUID());
        if (session != null) {
            NetworkHandler.sendToPlayer(player, new LockpickEndS2CPacket(false, reason));
        }
    }

    private static void failLockpick(MinecraftServer server, ServerPlayer player, LockpickSession session, String reason) {
        breakTool(player, session);
        playSoundInRadius(server, session.getDimensionKey(), session.getChestPos(), SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 0.9F, 10.0);
        NetworkHandler.sendToPlayer(player, new LockpickEndS2CPacket(false, reason));
        player.sendSystemMessage(Component.literal("❌ " + reason).withStyle(ChatFormatting.RED), true);
    }

    private static void handleMistake(MinecraftServer server, ServerPlayer player, LockpickSession session, String reason) {
        session.recordMistake();
        playSoundInRadius(server, session.getDimensionKey(), session.getChestPos(), SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 0.9F, 10.0);

        if (session.hasExceededMistakes()) {
            breakTool(player, session);
            NetworkHandler.sendToPlayer(player, new LockpickEndS2CPacket(false, "Отмычка сломалась в замке!"));
            player.sendSystemMessage(Component.literal("❌ Отмычка сломалась в замке!").withStyle(ChatFormatting.RED), true);
        } else {
            session.addPenaltyTicks(100); // +5 seconds penalty
            session.clearQte(player.level().getRandom());
            player.sendSystemMessage(Component.literal("⚠️ " + reason + " +5 сек. штрафа к взлому!").withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static void breakTool(ServerPlayer player, LockpickSession session) {
        ItemStack held = player.getItemInHand(session.getHand());
        if (!held.isEmpty() && held.getItem() instanceof LockpickItem) {
            held.shrink(1);
        }
    }

    private static void completeLockpick(MinecraftServer server, ServerPlayer player, LockpickSession session) {
        // Break/consume 1 tool upon success
        breakTool(player, session);
        NetworkHandler.sendToPlayer(player, new LockpickEndS2CPacket(true, "Сейф успешно взломан!"));

        CharacterWorldData worldData = CharacterWorldData.get(server);
        String dim = session.getDimensionKey().location().toString();
        TreasuryChestEntry entry = worldData.getTreasuryChestAt(dim, session.getChestPos());
        if (entry == null) return;

        long stolen = entry.getEffectiveAmount();
        entry.setPendingStolen(entry.getPendingStolen() + stolen);
        worldData.setDirty();

        // Give physical Sprota coins to player
        giveSprota(player, (int) stolen);

        // Sound at chest
        playSoundInRadius(server, session.getDimensionKey(), session.getChestPos(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F, 10.0);

        // Update or create HeistSession
        HeistSession heist = worldData.getHeistSession(player.getUUID());
        if (heist == null) {
            heist = new HeistSession(player.getUUID());
            worldData.putHeistSession(heist);
        }
        heist.addLootedChest(entry.getId(), stolen);
        worldData.setDirty();

        double noise = HeistMath.calculateNoise(heist.getStolenAccumulated(), worldData.getTreasury());
        if (noise >= 25.0) {
            player.sendSystemMessage(Component.literal(String.format("§4[Шум: %.1f%%] КРИТИЧЕСКИЙ УЩЕРБ! СИСТЕМА СОРВАНА!", noise)), true);
            triggerAlarm(server, heist, "Критический ущерб казне (" + String.format("%.1f%%", noise) + ")! Сигнализация сработала мгновенно.");
            worldData.removeHeistSession(player.getUUID());
        } else {
            player.sendSystemMessage(Component.literal(String.format("§a[Шум: %.1f%%] Взлом успешен! Похищено: %d $PR. Таймер отхода: 03:00", noise, stolen)), true);
        }
    }

    public static void triggerAlarm(MinecraftServer server, HeistSession heist, String reason) {
        CharacterWorldData worldData = CharacterWorldData.get(server);
        worldData.applyTheftDiscovery(server, heist);
        NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

        alarmTicksRemaining = 200; // 10 seconds of alarm bell
        broadcastGlobalSound(server, SoundEvents.BELL_RESONATE, 1.0F, 0.8F);

        // Titles broadcast
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "title @a title {\"text\":\"КАЗНА РАЗГРАБЛЕНА!\",\"color\":\"dark_red\",\"bold\":true}"
            );
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "title @a subtitle {\"text\":\"Физические пломбы хранилища сорваны\",\"color\":\"red\"}"
            );
        } catch (Exception ex) {
            OneManager.LOGGER.error("Failed to execute alarm title commands", ex);
        }

        // Anonymous public chat announcement
        Component chatNotice = Component.literal("[Казна] Зафиксирован факт взлома хранилища. Злоумышленник скрылся.")
                .withStyle(ChatFormatting.RED);
        server.getPlayerList().broadcastSystemMessage(chatNotice, false);

        OneManager.LOGGER.warn("TREASURY HEIST ALARM: " + reason + " | Total stolen: " + heist.getStolenAccumulated());
    }

    public static void onChestDiscovered(MinecraftServer server, TreasuryChestEntry entry, ServerPlayer knyaz) {
        if (server == null || entry == null) return;
        CharacterWorldData worldData = CharacterWorldData.get(server);

        HeistSession heist = worldData.getHeistSessionForChest(entry.getId());
        if (heist != null) {
            if (knyaz != null) {
                knyaz.sendSystemMessage(Component.literal("🚨 Вы обнаружили взломанный сейф! Поднята тревога!").withStyle(ChatFormatting.RED));
            }
            triggerAlarm(server, heist, "Князь лично обнаружил сорванные пломбы хранилища!");
            worldData.removeHeistSession(heist.getThiefUuid());
        } else if (entry.getPendingStolen() > 0) {
            long stolen = entry.getPendingStolen();
            entry.setAmount(Math.max(0, entry.getAmount() - stolen));
            entry.setPendingStolen(0);
            entry.setSealBroken(true);
            worldData.recalculateTreasuryFromChests();
            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
    }

    public static void giveSprota(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            ItemStack sprota = new ItemStack(ModItems.SPROTA.get(), batch);
            if (!player.getInventory().add(sprota)) {
                Containers.dropItemStack(player.serverLevel(), player.getX(), player.getY(), player.getZ(), sprota);
            }
            remaining -= batch;
        }
    }

    public static void playSoundInRadius(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos,
                                         SoundEvent sound, SoundSource source, float vol, float pitch, double radius) {
        if (server == null || pos == null || sound == null) return;
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;

        double radSq = radius * radius;
        Vec3 center = Vec3.atCenterOf(pos);

        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= radSq) {
                player.playNotifySound(sound, source, vol, pitch);
            }
        }
    }

    public static void broadcastGlobalSound(MinecraftServer server, SoundEvent sound, float vol, float pitch) {
        if (server == null || sound == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.MASTER, vol, pitch);
        }
    }
}
