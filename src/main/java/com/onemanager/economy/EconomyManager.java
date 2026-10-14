package com.onemanager.economy;

import com.oneshop.client.ClientBankCache;
import com.oneshop.service.PlayerAccountService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Economy abstraction layer backed by OneShop's PlayerAccountService and ClientBankCache.
 */
public class EconomyManager {

    public static double getBalance(Player player) {
        if (player == null) return 0.0;
        return PlayerAccountService.getBalance(player);
    }

    public static double getBalance(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) return 0.0;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        return player != null ? PlayerAccountService.getBalance(player) : 0.0;
    }

    public static double getClientBalance() {
        return ClientBankCache.getBalance();
    }

    public static void setBalance(ServerPlayer player, double amount) {
        if (player == null) return;
        PlayerAccountService.setBalance(player, Math.max(0.0, amount));
    }

    public static void addBalance(ServerPlayer player, double amount) {
        if (player == null || amount <= 0) return;
        PlayerAccountService.addBalance(player, amount);
    }

    public static boolean deductBalance(ServerPlayer player, double amount) {
        if (player == null || amount <= 0) return false;
        return PlayerAccountService.withdraw(player, amount);
    }

    public static boolean canAfford(Player player, double amount) {
        if (player == null || amount <= 0) return true;
        return PlayerAccountService.getBalance(player) >= amount;
    }
}
