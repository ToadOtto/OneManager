package com.onemanager.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.onemanager.data.KnyazSavedData;
import com.onemanager.event.CrownEvents;
import com.onemanager.item.CrownItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Collections;

public class GiveCrownCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Single variant: /oh:give_crown [targets]
        dispatcher.register(buildGiveCrownLiteral("oh:give_crown"));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildGiveCrownLiteral(String literalName) {
        return Commands.literal(literalName)
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> giveCrown(ctx.getSource(), Collections.singleton(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> giveCrown(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildGiveCrownSubLiteral(String subLiteralName) {
        return Commands.literal(subLiteralName)
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> giveCrown(ctx.getSource(), Collections.singleton(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> giveCrown(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))));
    }

    public static int giveCrown(CommandSourceStack source, Collection<ServerPlayer> targets) {
        KnyazSavedData data = KnyazSavedData.get(source.getServer());
        String currentKnyaz = (data != null && data.hasKnyaz()) ? data.getKnyazName() : null;

        for (ServerPlayer player : targets) {
            ItemStack crown = CrownItem.createCrown(source.getServer().registryAccess(), currentKnyaz);
            CrownEvents.giveOrEquipCrown(player, crown);
        }

        source.sendSuccess(
                () -> Component.translatable("command.onemanager.give_success", targets.size())
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return targets.size();
    }
}
