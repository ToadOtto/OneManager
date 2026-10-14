package com.pcmanager.economy;

import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.data.PlayerCharacterData;

import java.util.UUID;

/**
 * Modular economy abstraction layer.
 * Currently uses the built-in player balance stored in PlayerCharacterData.
 * When integrating an external economy mod (e.g., Numismatics, Vault, Impactor),
 * simply redirect these methods to the external mod's API.
 */
public class EconomyManager {

    public static long getBalance(CharacterWorldData worldData, UUID playerUuid) {
        if (worldData == null || playerUuid == null) return 0L;
        PlayerCharacterData pd = worldData.getPlayerData(playerUuid);
        return pd != null ? pd.getBalance() : 0L;
    }

    public static void setBalance(CharacterWorldData worldData, UUID playerUuid, long amount) {
        if (worldData == null || playerUuid == null) return;
        PlayerCharacterData pd = worldData.getOrCreatePlayerData(playerUuid);
        pd.setBalance(amount);
        worldData.setDirty();
    }

    public static void addBalance(CharacterWorldData worldData, UUID playerUuid, long amount) {
        if (worldData == null || playerUuid == null) return;
        setBalance(worldData, playerUuid, getBalance(worldData, playerUuid) + amount);
    }

    public static boolean deductBalance(CharacterWorldData worldData, UUID playerUuid, long amount) {
        if (worldData == null || playerUuid == null || amount <= 0) return false;
        long current = getBalance(worldData, playerUuid);
        if (current >= amount) {
            setBalance(worldData, playerUuid, current - amount);
            return true;
        }
        return false;
    }
}
