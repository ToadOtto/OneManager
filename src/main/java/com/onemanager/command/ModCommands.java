package com.onemanager.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.item.ModItems;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.SyncAllCharactersPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.onemanager.data.DeadCharacter;
import com.onemanager.data.KnyazSavedData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.event.BurdenEventHandler;
import com.onemanager.item.RemainsItem;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import com.onemanager.profession.Profession;
import com.onemanager.profession.ProfessionRegistry;
import com.onemanager.economy.EconomyManager;

public class ModCommands {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            worldData.updatePlayerName(player.getUUID(), player.getName().getString());
            worldData.setDirty();
            NetworkHandler.sendToPlayer(player, new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Single variant for each custom command:
        // 1. State Brand: /oh:statebrand
        dispatcher.register(buildStateBrandCommand("oh:statebrand"));

        // 2. Profession slots: /oh:profslots
        dispatcher.register(buildProfSlotsCommand("oh:profslots"));

        // 3. Remains: /oh:giveremains
        dispatcher.register(buildGiveRemainsCommand("oh:giveremains"));

        // 4. Character history hub: /oh:charhistory
        registerCharHistoryCommand(dispatcher, "oh:charhistory");

        // 5. Job management: /oh:getjob, /oh:losejob
        registerJobCommands(dispatcher, "oh:getjob", "oh:losejob");

        // 6. Faction: /oh:faction
        dispatcher.register(buildFactionCommand("oh:faction"));

        // 7. License: /oh:license
        dispatcher.register(buildLicenseCommand("oh:license"));

        // 8. World Level: /oh:worldlevel
        dispatcher.register(buildWorldLevelCommand("oh:worldlevel"));

        // 9. Treasury: /oh:treasury
        dispatcher.register(buildTreasuryCommand("oh:treasury"));

        // 10. Balance: /oh:setbalance
        dispatcher.register(buildSetBalanceCommand("oh:setbalance"));

        // 11. Tax rate: /oh:settax
        dispatcher.register(buildSetTaxCommand("oh:settax"));

        // 12. Cycle / Calendar: /oh:cycle
        dispatcher.register(buildCycleCommand("oh:cycle"));

        // 13. Profession progress: /oh:profprogress
        dispatcher.register(buildProfProgressCommand("oh:profprogress"));

        // 14. Knyaz Management: /oh:knyaz
        dispatcher.register(buildKnyazCommand("oh:knyaz"));
    }

    private static int openCharHistory(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CharacterWorldData worldData = CharacterWorldData.get(player.server);
        worldData.updatePlayerName(player.getUUID(), player.getName().getString());
        NetworkHandler.sendToPlayer(player, new SyncAllCharactersPacket(worldData.save(new CompoundTag()), true));
        return 1;
    }

    private static void registerCharHistoryCommand(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(
                Commands.literal(literal)
                        .executes(ctx -> openCharHistory(ctx.getSource()))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildGiveRemainsCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2) || (source.getEntity() instanceof ServerPlayer sp && sp.isCreative()))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("Использование: /" + name + " <цели> <имя предка> или /" + name + " self <имя предка>").withStyle(ChatFormatting.RED));
                    return 0;
                })
                .then(Commands.literal("self")
                        .then(Commands.argument("ancestorName", StringArgumentType.greedyString())
                                .suggests(getAncestorSuggestions())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String nameArg = StringArgumentType.getString(ctx, "ancestorName");
                                    return giveRemains(ctx.getSource(), Collections.singleton(player), nameArg);
                                })
                        )
                )
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("ancestorName", StringArgumentType.greedyString())
                                .suggests(getAncestorSuggestions())
                                .executes(ctx -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                    String nameArg = StringArgumentType.getString(ctx, "ancestorName");
                                    return giveRemains(ctx.getSource(), targets, nameArg);
                                })
                        )
                );
    }

    private static SuggestionProvider<CommandSourceStack> getAncestorSuggestions() {
        return (ctx, builder) -> {
            try {
                Collection<ServerPlayer> targets = null;
                try {
                    targets = EntityArgument.getPlayers(ctx, "targets");
                } catch (Exception ignored) {
                }

                if (targets == null || targets.isEmpty()) {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer sp) {
                        targets = Collections.singleton(sp);
                    }
                }

                Set<String> suggested = new LinkedHashSet<>();
                if (targets != null) {
                    for (ServerPlayer target : targets) {
                        CharacterWorldData worldData = CharacterWorldData.get(target.server);
                        PlayerCharacterData pData = worldData.getPlayerData(target.getUUID());
                        if (pData != null) {
                            for (DeadCharacter dead : pData.getCharacterHistory()) {
                                suggested.add(dead.getName());
                            }
                        }
                    }
                }

                if (suggested.isEmpty() && ctx.getSource().getServer() != null) {
                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                    for (PlayerCharacterData pData : worldData.getAllPlayerData().values()) {
                        for (DeadCharacter dead : pData.getCharacterHistory()) {
                            suggested.add(dead.getName());
                        }
                    }
                }

                for (String sName : suggested) {
                    if (sName != null && !sName.trim().isEmpty()) {
                        if (sName.contains(" ")) {
                            builder.suggest("\"" + sName + "\"");
                        } else {
                            builder.suggest(sName);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return builder.buildFuture();
        };
    }

    private static int giveRemains(CommandSourceStack source, Collection<ServerPlayer> targets, String rawAncestorName) {
        String ancestorName = rawAncestorName.trim();
        if (ancestorName.startsWith("\"") && ancestorName.endsWith("\"") && ancestorName.length() >= 2) {
            ancestorName = ancestorName.substring(1, ancestorName.length() - 1).trim();
        }

        if (ancestorName.isEmpty()) {
            source.sendFailure(Component.literal("Имя предка не может быть пустым!").withStyle(ChatFormatting.RED));
            return 0;
        }

        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            PlayerCharacterData playerData = worldData.getOrCreatePlayerData(target.getUUID());

            // 1. Look for existing dead character in player's history
            DeadCharacter matched = null;
            for (DeadCharacter dead : playerData.getCharacterHistory()) {
                if (dead.getName().equalsIgnoreCase(ancestorName)) {
                    matched = dead;
                    break;
                }
            }

            // 2. Fallback: search all players' history in the world
            if (matched == null) {
                for (PlayerCharacterData otherData : worldData.getAllPlayerData().values()) {
                    for (DeadCharacter dead : otherData.getCharacterHistory()) {
                        if (dead.getName().equalsIgnoreCase(ancestorName)) {
                            matched = dead;
                            break;
                        }
                    }
                    if (matched != null) break;
                }
            }

            // 3. Fallback: if not found, create new DeadCharacter with custom name
            if (matched == null) {
                String fact = playerData.getFaction().isEmpty() ? (playerData.getLineage().isEmpty() ? "Не указана" : playerData.getLineage()) : playerData.getFaction();
                matched = new DeadCharacter(
                        ancestorName,
                        30,
                        Collections.emptyList(),
                        fact,
                        System.currentTimeMillis(),
                        "Утраченные останки",
                        false
                );
                playerData.addDeadCharacter(matched);
            } else {
                matched.setBuried(false);
            }

            ItemStack remains = RemainsItem.createRemains(matched, target.getUUID());
            BurdenEventHandler.forceAddToInventory(target, remains);
            target.inventoryMenu.broadcastChanges();

            final String finalDeadName = matched.getName();
            target.sendSystemMessage(
                    Component.literal("Вам выданы останки предка: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(finalDeadName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            );
        }

        if (worldData != null) {
            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }

        final String reportName = ancestorName;
        source.sendSuccess(() ->
                Component.literal("Останки предка \"" + reportName + "\" успешно выданы (" + targets.size() + " игр.)")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return targets.size();
    }

    private static void registerJobCommands(CommandDispatcher<CommandSourceStack> dispatcher, String getLiteral, String loseLiteral) {
        dispatcher.register(
                Commands.literal(getLiteral)
                        .then(Commands.argument("job_name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Profession p : ProfessionRegistry.getAll()) {
                                        builder.suggest(String.valueOf(p.getId()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String job = StringArgumentType.getString(ctx, "job_name");
                                    giveJob(ctx.getSource(), Collections.singleton(player), job);
                                    return 1;
                                })
                        )
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("job_name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Profession p : ProfessionRegistry.getAll()) {
                                                builder.suggest(String.valueOf(p.getId()));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            String job = StringArgumentType.getString(ctx, "job_name");
                                            giveJob(ctx.getSource(), targets, job);
                                            return targets.size();
                                        })
                                )
                        )
        );

        dispatcher.register(
                Commands.literal(loseLiteral)
                        .then(Commands.argument("job_name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Profession p : ProfessionRegistry.getAll()) {
                                        builder.suggest(String.valueOf(p.getId()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String job = StringArgumentType.getString(ctx, "job_name");
                                    removeJob(ctx.getSource(), Collections.singleton(player), job);
                                    return 1;
                                })
                        )
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("job_name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Profession p : ProfessionRegistry.getAll()) {
                                                builder.suggest(String.valueOf(p.getId()));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            String job = StringArgumentType.getString(ctx, "job_name");
                                            removeJob(ctx.getSource(), targets, job);
                                            return targets.size();
                                        })
                                )
                        )
        );
    }

    private static void giveJob(CommandSourceStack source, Collection<ServerPlayer> targets, String jobName) {
        Profession prof = ProfessionRegistry.getByIdOrTitle(jobName);
        String finalJobName = prof != null ? prof.getTitle() : jobName;
        String displayJob = prof != null ? prof.getTitle() + " [#" + prof.getId() + "]" : jobName;

        for (ServerPlayer target : targets) {
            CharacterWorldData worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            worldData.addProfession(target.getUUID(), finalJobName);
            target.sendSystemMessage(
                    Component.literal("Вы получили профессию: ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(displayJob).withStyle(ChatFormatting.GOLD))
            );
        }

        if (!targets.isEmpty()) {
            ServerPlayer first = targets.iterator().next();
            CharacterWorldData worldData = CharacterWorldData.get(first.server);
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }

        if (targets.size() > 1 || (source.getEntity() != null && !targets.contains(source.getEntity()))) {
            source.sendSuccess(() ->
                            Component.literal("Профессия \"" + displayJob + "\" выдана игрокам (" + targets.size() + ")")
                                    .withStyle(ChatFormatting.GREEN),
                    true
            );
        }
    }

    private static void removeJob(CommandSourceStack source, Collection<ServerPlayer> targets, String jobName) {
        Profession prof = ProfessionRegistry.getByIdOrTitle(jobName);
        String finalJobName = prof != null ? prof.getTitle() : jobName;
        String displayJob = prof != null ? prof.getTitle() + " [#" + prof.getId() + "]" : jobName;

        for (ServerPlayer target : targets) {
            CharacterWorldData worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            boolean removed = worldData.removeProfession(target.getUUID(), finalJobName);
            if (!removed && prof != null) {
                removed = worldData.removeProfession(target.getUUID(), String.valueOf(prof.getId()));
            }
            if (removed) {
                target.sendSystemMessage(
                        Component.literal("Вы потеряли профессию: ")
                                .withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(displayJob).withStyle(ChatFormatting.GOLD))
                );
            } else {
                target.sendSystemMessage(
                        Component.literal("У вас не было профессии: " + displayJob)
                                .withStyle(ChatFormatting.RED)
                );
            }
        }

        if (!targets.isEmpty()) {
            ServerPlayer first = targets.iterator().next();
            CharacterWorldData worldData = CharacterWorldData.get(first.server);
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }

        if (targets.size() > 1 || (source.getEntity() != null && !targets.contains(source.getEntity()))) {
            source.sendSuccess(() ->
                            Component.literal("Профессия \"" + displayJob + "\" удалена у игроков (" + targets.size() + ")")
                                    .withStyle(ChatFormatting.YELLOW),
                    true
            );
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildProfProgressCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("job_name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Profession p : ProfessionRegistry.getAll()) {
                                builder.suggest(String.valueOf(p.getId()));
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("progress", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String job = StringArgumentType.getString(ctx, "job_name");
                                    int progress = IntegerArgumentType.getInteger(ctx, "progress");
                                    setJobProgress(ctx.getSource(), Collections.singleton(player), job, progress);
                                    return 1;
                                })
                        )
                )
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("job_name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Profession p : ProfessionRegistry.getAll()) {
                                        builder.suggest(String.valueOf(p.getId()));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("progress", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            String job = StringArgumentType.getString(ctx, "job_name");
                                            int progress = IntegerArgumentType.getInteger(ctx, "progress");
                                            setJobProgress(ctx.getSource(), targets, job, progress);
                                            return targets.size();
                                        })
                                )
                        )
                );
    }

    private static void setJobProgress(CommandSourceStack source, Collection<ServerPlayer> targets, String jobName, int progress) {
        Profession prof = ProfessionRegistry.getByIdOrTitle(jobName);
        String finalJobName = prof != null ? prof.getTitle() : jobName;
        String displayJob = prof != null ? prof.getTitle() : jobName;

        for (ServerPlayer target : targets) {
            CharacterWorldData worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            PlayerCharacterData pd = worldData.getPlayerData(target.getUUID());
            if (pd != null) {
                pd.setProfessionProgress(finalJobName, progress);
                worldData.setDirty();
            }
            target.sendSystemMessage(
                    Component.literal("Прогресс профессии «" + displayJob + "» установлен на: ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(progress + "%").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            );
        }

        if (!targets.isEmpty()) {
            ServerPlayer first = targets.iterator().next();
            CharacterWorldData worldData = CharacterWorldData.get(first.server);
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }

        source.sendSuccess(() ->
                        Component.literal("Прогресс профессии «" + displayJob + "» установлен на " + progress + "% (" + targets.size() + " игр.)")
                                .withStyle(ChatFormatting.GREEN),
                true
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildStateBrandCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) {
                        return getStateBrands(ctx.getSource(), player);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("Используйте: /" + name + " <add|remove|set|get|give> ...").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                })
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return getStateBrands(ctx.getSource(), player);
                        })
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    return getStateBrands(ctx.getSource(), target);
                                })
                        )
                )
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            return addStateBrands(ctx.getSource(), targets, amount);
                                        })
                                )
                        )
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    return addStateBrands(ctx.getSource(), Collections.singleton(player), amount);
                                })
                        )
                )
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            return removeStateBrands(ctx.getSource(), targets, amount);
                                        })
                                )
                        )
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    return removeStateBrands(ctx.getSource(), Collections.singleton(player), amount);
                                })
                        )
                )
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            return setStateBrands(ctx.getSource(), targets, amount);
                                        })
                                )
                        )
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    return setStateBrands(ctx.getSource(), Collections.singleton(player), amount);
                                })
                        )
                )
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            return giveStateBrandItems(ctx.getSource(), targets, amount);
                                        })
                                )
                        )
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    return giveStateBrandItems(ctx.getSource(), Collections.singleton(player), amount);
                                })
                        )
                );
    }

    private static int getStateBrands(CommandSourceStack source, ServerPlayer target) {
        CharacterWorldData worldData = CharacterWorldData.get(target.server);
        worldData.updatePlayerName(target.getUUID(), target.getName().getString());
        int balance = worldData.getStateBrands(target.getUUID());
        source.sendSuccess(() ->
                Component.literal("Баланс Гос. Марок игрока ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                        .append(Component.literal(": "))
                        .append(Component.literal(balance + " шт.").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)),
                false
        );
        return balance;
    }

    private static int addStateBrands(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
        if (amount <= 0) {
            source.sendFailure(Component.literal("Количество должно быть больше нуля!").withStyle(ChatFormatting.RED));
            return 0;
        }

        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            worldData.addStateBrands(target.getUUID(), amount);
            int newBalance = worldData.getStateBrands(target.getUUID());

            target.sendSystemMessage(
                    Component.literal("Вам начислено: ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal("+" + amount + " Гос. Марок").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(" (Баланс: " + newBalance + ")").withStyle(ChatFormatting.GRAY))
            );
        }

        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }

        if (targets.size() == 1) {
            ServerPlayer single = targets.iterator().next();
            int newBalance = worldData.getStateBrands(single.getUUID());
            source.sendSuccess(() ->
                    Component.literal("Игроку " + single.getName().getString() + " начислено " + amount + " Гос. Марок. Новый баланс: " + newBalance)
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
        } else {
            source.sendSuccess(() ->
                    Component.literal("Начислено по " + amount + " Гос. Марок игрокам (" + targets.size() + ")")
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
        }
        return targets.size();
    }

    private static int removeStateBrands(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
        if (amount <= 0) {
            source.sendFailure(Component.literal("Количество должно быть больше нуля!").withStyle(ChatFormatting.RED));
            return 0;
        }

        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            int removed = worldData.removeStateBrands(target.getUUID(), amount);
            int newBalance = worldData.getStateBrands(target.getUUID());

            target.sendSystemMessage(
                    Component.literal("С вашего счёта списано: ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component.literal("-" + removed + " Гос. Марок").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(" (Баланс: " + newBalance + ")").withStyle(ChatFormatting.GRAY))
            );
        }

        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }

        if (targets.size() == 1) {
            ServerPlayer single = targets.iterator().next();
            int newBalance = worldData.getStateBrands(single.getUUID());
            source.sendSuccess(() ->
                    Component.literal("С игрока " + single.getName().getString() + " списано " + amount + " Гос. Марок. Новый баланс: " + newBalance)
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
        } else {
            source.sendSuccess(() ->
                    Component.literal("Списано до " + amount + " Гос. Марок у игроков (" + targets.size() + ")")
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
        }
        return targets.size();
    }

    private static int setStateBrands(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
        if (amount < 0) {
            source.sendFailure(Component.literal("Количество не может быть отрицательным!").withStyle(ChatFormatting.RED));
            return 0;
        }

        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            worldData.setStateBrands(target.getUUID(), amount);

            target.sendSystemMessage(
                    Component.literal("Ваш счёт Гос. Марок установлен на: ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(amount + " Гос. Марок").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            );
        }

        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }

        if (targets.size() == 1) {
            ServerPlayer single = targets.iterator().next();
            source.sendSuccess(() ->
                    Component.literal("Баланс Гос. Марок игрока " + single.getName().getString() + " установлен на: " + amount)
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
        } else {
            source.sendSuccess(() ->
                    Component.literal("Баланс Гос. Марок игроков (" + targets.size() + ") установлен на: " + amount)
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
        }
        return targets.size();
    }

    private static int giveStateBrandItems(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
        if (amount <= 0) {
            source.sendFailure(Component.literal("Количество должно быть больше нуля!").withStyle(ChatFormatting.RED));
            return 0;
        }

        for (ServerPlayer target : targets) {
            int remaining = amount;
            while (remaining > 0) {
                int count = Math.min(remaining, 64);
                ItemStack stack = new ItemStack(ModItems.STATE_BRAND.get(), count);
                if (!target.getInventory().add(stack)) {
                    target.drop(stack, false);
                }
                remaining -= count;
            }
            target.inventoryMenu.broadcastChanges();
            target.sendSystemMessage(
                    Component.literal("Вы получили: ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(amount + "x Гос. Марка").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            );
        }

        source.sendSuccess(() ->
                Component.literal("Выдано " + amount + " Гос. Марок (" + targets.size() + " игр.)")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return targets.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildProfSlotsCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) {
                        return getProfSlots(ctx.getSource(), player);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("Используйте: /" + name + " <get|set|add> ...").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                })
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return getProfSlots(ctx.getSource(), player);
                        })
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    return getProfSlots(ctx.getSource(), target);
                                })
                        )
                )
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("main")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 20))
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return setProfSlots(ctx.getSource(), targets, true, amount);
                                                })
                                        )
                                )
                                .then(Commands.literal("secondary")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 20))
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return setProfSlots(ctx.getSource(), targets, false, amount);
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("main")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 20))
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return addProfSlots(ctx.getSource(), targets, true, amount);
                                                })
                                        )
                                )
                                .then(Commands.literal("secondary")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 20))
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return addProfSlots(ctx.getSource(), targets, false, amount);
                                                })
                                        )
                                )
                        )
                );
    }

    private static int getProfSlots(CommandSourceStack source, ServerPlayer target) {
        CharacterWorldData worldData = CharacterWorldData.get(target.server);
        int main = worldData.getMainProfessionSlots(target.getUUID());
        int sec = worldData.getSecondaryProfessionSlots(target.getUUID());
        source.sendSuccess(() ->
                Component.literal("Слоты профессий " + target.getName().getString() + ": ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Основные: " + main).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Дополнительные: " + sec).withStyle(ChatFormatting.GREEN)),
                false
        );
        return main + sec;
    }

    private static int setProfSlots(CommandSourceStack source, Collection<ServerPlayer> targets, boolean isMain, int amount) {
        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            if (isMain) {
                worldData.setMainProfessionSlots(target.getUUID(), amount);
            } else {
                worldData.setSecondaryProfessionSlots(target.getUUID(), amount);
            }
            String slotName = isMain ? "основных" : "дополнительных";
            target.sendSystemMessage(
                    Component.literal("Количество доступных слотов " + slotName + " профессий установлено на: " + amount)
                            .withStyle(ChatFormatting.YELLOW)
            );
        }
        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
        String slotName = isMain ? "основных" : "дополнительных";
        source.sendSuccess(() ->
                Component.literal("Слоты " + slotName + " профессий установлены на " + amount + " (" + targets.size() + " игр.)")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return targets.size();
    }

    private static int addProfSlots(CommandSourceStack source, Collection<ServerPlayer> targets, boolean isMain, int amount) {
        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            if (isMain) {
                worldData.addMainProfessionSlots(target.getUUID(), amount);
            } else {
                worldData.addSecondaryProfessionSlots(target.getUUID(), amount);
            }
            String slotName = isMain ? "основных" : "дополнительных";
            int total = isMain ? worldData.getMainProfessionSlots(target.getUUID()) : worldData.getSecondaryProfessionSlots(target.getUUID());
            target.sendSystemMessage(
                    Component.literal("Вам добавлено " + amount + " слотов " + slotName + " профессий! (Всего: " + total + ")")
                            .withStyle(ChatFormatting.GREEN)
            );
        }
        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
        String slotName = isMain ? "основных" : "дополнительных";
        source.sendSuccess(() ->
                Component.literal("Добавлено " + amount + " слотов " + slotName + " профессий (" + targets.size() + " игр.)")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return targets.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildFactionCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) {
                        return getFaction(ctx.getSource(), player);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("Используйте: /" + name + " <get|set> ...").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                })
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return getFaction(ctx.getSource(), player);
                        })
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    return getFaction(ctx.getSource(), target);
                                })
                        )
                )
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("factionName", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            String faction = StringArgumentType.getString(ctx, "factionName");
                                            return setFaction(ctx.getSource(), targets, faction);
                                        })
                                )
                        )
                );
    }

    private static int getFaction(CommandSourceStack source, ServerPlayer target) {
        CharacterWorldData worldData = CharacterWorldData.get(target.server);
        String faction = worldData.getFaction(target.getUUID());
        String display = faction.isEmpty() ? "Не состоит" : faction;
        source.sendSuccess(() ->
                Component.literal("Фракция игрока " + target.getName().getString() + ": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(display).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)),
                false
        );
        return 1;
    }

    private static int setFaction(CommandSourceStack source, Collection<ServerPlayer> targets, String faction) {
        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            worldData.setFaction(target.getUUID(), faction);
            target.sendSystemMessage(
                    Component.literal("Ваша фракция установлена на: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(faction).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            );
        }
        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
        source.sendSuccess(() ->
                Component.literal("Фракция установлена на \"" + faction + "\" для " + targets.size() + " игроков.")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return targets.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLicenseCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) {
                        return listLicenses(ctx.getSource(), player);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("Используйте: /" + name + " <list|add|remove> ...").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                })
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return listLicenses(ctx.getSource(), player);
                        })
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    return listLicenses(ctx.getSource(), target);
                                })
                        )
                )
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("licenseName", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            String license = StringArgumentType.getString(ctx, "licenseName");
                                            return addLicense(ctx.getSource(), targets, license);
                                        })
                                )
                        )
                )
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("licenseName", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            String license = StringArgumentType.getString(ctx, "licenseName");
                                            return removeLicense(ctx.getSource(), targets, license);
                                        })
                                )
                        )
                );
    }

    private static int listLicenses(CommandSourceStack source, ServerPlayer target) {
        CharacterWorldData worldData = CharacterWorldData.get(target.server);
        Set<String> licenses = worldData.getLicenses(target.getUUID());
        if (licenses.isEmpty()) {
            source.sendSuccess(() ->
                    Component.literal("У игрока " + target.getName().getString() + " нет активных лицензий.")
                            .withStyle(ChatFormatting.GRAY),
                    false
            );
        } else {
            source.sendSuccess(() ->
                    Component.literal("Лицензии игрока " + target.getName().getString() + ": ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(String.join(", ", licenses)).withStyle(ChatFormatting.AQUA)),
                    false
            );
        }
        return licenses.size();
    }

    private static int addLicense(CommandSourceStack source, Collection<ServerPlayer> targets, String license) {
        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            worldData.addLicense(target.getUUID(), license);
            target.sendSystemMessage(
                    Component.literal("Вам выдана лицензия: ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(license).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            );
        }
        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
        source.sendSuccess(() ->
                Component.literal("Лицензия \"" + license + "\" выдана " + targets.size() + " игрокам.")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return targets.size();
    }

    private static int removeLicense(CommandSourceStack source, Collection<ServerPlayer> targets, String license) {
        CharacterWorldData worldData = null;
        for (ServerPlayer target : targets) {
            worldData = CharacterWorldData.get(target.server);
            worldData.updatePlayerName(target.getUUID(), target.getName().getString());
            worldData.removeLicense(target.getUUID(), license);
            target.sendSystemMessage(
                    Component.literal("У вас отозвана лицензия: ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component.literal(license).withStyle(ChatFormatting.YELLOW))
            );
        }
        if (worldData != null) {
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
        source.sendSuccess(() ->
                Component.literal("Лицензия \"" + license + "\" отозвана у " + targets.size() + " игроков.")
                        .withStyle(ChatFormatting.YELLOW),
                true
        );
        return targets.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildWorldLevelCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Текущий Уровень Мира: " + worldData.getWorldLevel()).withStyle(ChatFormatting.GREEN), false);
                    return worldData.getWorldLevel();
                })
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Текущий Уровень Мира: " + worldData.getWorldLevel()).withStyle(ChatFormatting.GREEN), false);
                            return worldData.getWorldLevel();
                        }))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> {
                                    int level = IntegerArgumentType.getInteger(ctx, "level");
                                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                                    worldData.setWorldLevel(level);
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Уровень Мира установлен на " + level).withStyle(ChatFormatting.GREEN), true);
                                    return level;
                                })))
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-1000, 1000))
                                .executes(ctx -> {
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                                    worldData.addWorldLevel(amount);
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Уровень Мира изменён на " + amount + ". Текущий: " + worldData.getWorldLevel()).withStyle(ChatFormatting.GREEN), true);
                                    return worldData.getWorldLevel();
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTreasuryCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                    long treasury = worldData.getTreasury();
                    ctx.getSource().sendSuccess(() -> Component.literal("На данный момент в казне находится: ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(String.format("%,d", treasury).replace(',', ' ') + " $PR")
                                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);
                    return (int) Math.min(Integer.MAX_VALUE, treasury);
                })
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                            long treasury = worldData.getTreasury();
                            ctx.getSource().sendSuccess(() -> Component.literal("На данный момент в казне находится: ")
                                    .withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal(String.format("%,d", treasury).replace(',', ' ') + " $PR")
                                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);
                            return (int) Math.min(Integer.MAX_VALUE, treasury);
                        }))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                                    worldData.setTreasury(amount);
                                    logTreasuryCmd(ctx.getSource(), worldData, "= " + String.format("%,d", (long) amount).replace(',', ' ') + " $PR (Команда /oh:treasury set)");
                                    worldData.setDirty();
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Казна установлена на: ")
                                            .withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal(String.format("%,d", (long) amount).replace(',', ' ') + " $PR")
                                                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), true);
                                    return amount;
                                })))
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                                    worldData.addTreasury(amount);
                                    logTreasuryCmd(ctx.getSource(), worldData, "+" + String.format("%,d", (long) amount).replace(',', ' ') + " $PR (Команда /oh:treasury add)");
                                    worldData.setDirty();
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("В казну добавлено: ")
                                            .withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal("+" + String.format("%,d", (long) amount).replace(',', ' ') + " $PR")
                                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                                            .append(Component.literal(" (Всего: " + String.format("%,d", worldData.getTreasury()).replace(',', ' ') + " $PR)")
                                                    .withStyle(ChatFormatting.YELLOW)), true);
                                    return (int) Math.min(Integer.MAX_VALUE, worldData.getTreasury());
                                })))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                                    long removed = worldData.removeTreasury(amount);
                                    logTreasuryCmd(ctx.getSource(), worldData, "-" + String.format("%,d", removed).replace(',', ' ') + " $PR (Команда /oh:treasury remove)");
                                    worldData.setDirty();
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Из казны убавлено: ")
                                            .withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal("-" + String.format("%,d", removed).replace(',', ' ') + " $PR")
                                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                                            .append(Component.literal(" (Остаток: " + String.format("%,d", worldData.getTreasury()).replace(',', ' ') + " $PR)")
                                                    .withStyle(ChatFormatting.YELLOW)), true);
                                    return (int) Math.min(Integer.MAX_VALUE, worldData.getTreasury());
                                })))
                .then(Commands.literal("sub")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                                    long removed = worldData.removeTreasury(amount);
                                    logTreasuryCmd(ctx.getSource(), worldData, "-" + String.format("%,d", removed).replace(',', ' ') + " $PR (Команда /oh:treasury sub)");
                                    worldData.setDirty();
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Из казны убавлено: ")
                                            .withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal("-" + String.format("%,d", removed).replace(',', ' ') + " $PR")
                                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                                            .append(Component.literal(" (Остаток: " + String.format("%,d", worldData.getTreasury()).replace(',', ' ') + " $PR)")
                                                    .withStyle(ChatFormatting.YELLOW)), true);
                                    return (int) Math.min(Integer.MAX_VALUE, worldData.getTreasury());
                                })));
    }

    private static void logTreasuryCmd(CommandSourceStack source, CharacterWorldData worldData, String action) {
        String pName = source.getEntity() instanceof ServerPlayer sp ? sp.getScoreboardName() : "Консоль";
        String charName = pName;
        if (source.getEntity() instanceof ServerPlayer sp) {
            PlayerCharacterData pd = worldData.getPlayerData(sp.getUUID());
            if (pd != null && pd.hasActiveCharacter()) {
                charName = pd.getActiveCharacter().getName();
            }
        }
        worldData.logTransaction(charName, pName, action, worldData.getTreasury());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetBalanceCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());

                                    for (ServerPlayer target : targets) {
                                        EconomyManager.setBalance(worldData, target.getUUID(), amount);
                                        target.sendSystemMessage(
                                                Component.literal("💰 Ваш баланс установлен на: ")
                                                        .withStyle(ChatFormatting.GOLD)
                                                        .append(Component.literal(String.format("%,d", (long) amount).replace(',', ' ') + " $PR")
                                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                        );
                                    }
                                    worldData.setDirty();
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Баланс установлен для " + targets.size() + " игрок(ов) на " + amount + " $PR")
                                                    .withStyle(ChatFormatting.GREEN),
                                            true
                                    );
                                    return targets.size();
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetTaxCommand(String name) {
        return Commands.literal(name)
                .requires(source -> {
                    if (source.hasPermission(2)) return true;
                    if (source.getEntity() instanceof ServerPlayer player) {
                        CharacterWorldData wd = CharacterWorldData.get(player.server);
                        return wd != null && wd.canManageTaxes(player.getUUID());
                    }
                    return false;
                })
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(ctx -> {
                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                            CharacterWorldData worldData = CharacterWorldData.get(ctx.getSource().getServer());
                            worldData.setTaxRate(amount);
                            worldData.setDirty();
                            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                    Component.literal("⚖ Ставка налога установлена: ").withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal(amount + " $PR").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                            .append(Component.literal(" каждые 20 минут.").withStyle(ChatFormatting.GRAY)),
                                    false
                            );
                            return amount;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCycleCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> queryCycle(ctx.getSource()))
                .then(Commands.literal("query")
                        .executes(ctx -> queryCycle(ctx.getSource())))
                .then(Commands.literal("setweek")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("week", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int week = IntegerArgumentType.getInteger(ctx, "week");
                                    CharacterWorldData wd = CharacterWorldData.get(ctx.getSource().getServer());
                                    wd.setCurrentWeek(week);
                                    wd.setDirty();
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(wd.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§6[Календарь OneHouse3] §aНомер недели установлен на: §e" + week), true);
                                    return week;
                                })))
                .then(Commands.literal("setday")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 7))
                                .executes(ctx -> {
                                    int day = IntegerArgumentType.getInteger(ctx, "day");
                                    CharacterWorldData wd = CharacterWorldData.get(ctx.getSource().getServer());
                                    wd.setCurrentDay(day);
                                    wd.setWeekTicksRemaining((7 - day) * CharacterWorldData.TAX_CYCLE_TICKS + wd.getTaxTicksRemaining());
                                    wd.setDirty();
                                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(wd.save(new CompoundTag()), false));
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§6[Календарь OneHouse3] §aНомер дня установлен на: §e" + day), true);
                                    return day;
                                })));
    }

    private static int queryCycle(CommandSourceStack source) {
        CharacterWorldData wd = CharacterWorldData.get(source.getServer());
        int week = wd.getCurrentWeek();
        int day = wd.getCurrentDay();
        int wSecs = Math.max(0, wd.getWeekTicksRemaining() / 20);
        String wTime = String.format("%02d:%02d:%02d", wSecs / 3600, (wSecs % 3600) / 60, wSecs % 60);

        int dSecs = Math.max(0, wd.getTaxTicksRemaining() / 20);
        String dTime = String.format("%02d:%02d", dSecs / 60, dSecs % 60);

        source.sendSuccess(() ->
                Component.literal("§6[Календарь OneHouse3] §fНеделя §e" + week + " §7(до конца: " + wTime + ")  §fДень §e" + day + " §7(до конца: " + dTime + ")  §8[Всего дней: " + wd.getTotalDays() + "]"),
                false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildKnyazCommand(String name) {
        return Commands.literal(name)
                .executes(ctx -> queryKnyaz(ctx.getSource()))
                .then(Commands.literal("query")
                        .executes(ctx -> queryKnyaz(ctx.getSource())))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                    MinecraftServer server = ctx.getSource().getServer();
                                    KnyazSavedData knyazData = KnyazSavedData.get(server);
                                    CharacterWorldData worldData = CharacterWorldData.get(server);

                                    String pName = player.getGameProfile().getName();
                                    UUID uuid = player.getUUID();
                                    if (knyazData != null) {
                                        knyazData.setKnyaz(pName, uuid);
                                    }
                                    if (worldData != null) {
                                        worldData.setKnyaz(pName, uuid);
                                        worldData.setDirty();
                                        NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                                    }

                                    server.getPlayerList().broadcastSystemMessage(
                                            Component.literal("👑 [OneHouse3] ").withStyle(ChatFormatting.GOLD)
                                                    .append(Component.literal(pName + " возведён в статус Князя!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)),
                                            false
                                    );
                                    ctx.getSource().sendSuccess(() -> Component.literal("Игрок " + pName + " успешно назначен Князем.").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> removeKnyaz(ctx.getSource())))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> removeKnyaz(ctx.getSource())));
    }

    private static int queryKnyaz(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        CharacterWorldData wd = CharacterWorldData.get(server);
        String name = wd != null ? wd.getKnyazName() : "";
        if (name.isEmpty()) {
            source.sendSuccess(() -> Component.literal("👑 В данный момент трон OneHouse3 пуст (Князь не назначен).").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("👑 Правящий Князь OneHouse3: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);
        }
        return 1;
    }

    private static int removeKnyaz(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        KnyazSavedData knyazData = KnyazSavedData.get(server);
        CharacterWorldData worldData = CharacterWorldData.get(server);
        String oldName = worldData != null ? worldData.getKnyazName() : "";
        if (knyazData != null) {
            knyazData.clearKnyaz();
        }
        if (worldData != null) {
            worldData.setKnyaz("", null);
            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("👑 [OneHouse3] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(oldName.isEmpty() ? "Статус Князя аннулирован." : oldName + " лишён статуса Князя.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)),
                false
        );
        source.sendSuccess(() -> Component.literal("Статус Князя успешно снят.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
