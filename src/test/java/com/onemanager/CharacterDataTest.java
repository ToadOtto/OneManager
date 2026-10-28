package com.onemanager;

import com.onemanager.data.ActiveCharacter;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.DeadCharacter;
import com.onemanager.data.PlayerCharacterData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CharacterDataTest {

    @Test
    public void testActiveCharacterNBT() {
        ActiveCharacter character = new ActiveCharacter("Eldrin", 28, 1700000000000L, true);
        CompoundTag tag = character.toNBT();

        assertEquals("Eldrin", tag.getString(ActiveCharacter.KEY_NAME));
        assertEquals(28, tag.getInt(ActiveCharacter.KEY_AGE));
        assertEquals(1700000000000L, tag.getLong(ActiveCharacter.KEY_CREATION_DATE));
        assertTrue(tag.getBoolean(ActiveCharacter.KEY_STATUS_BURDEN));

        ActiveCharacter loaded = ActiveCharacter.fromNBT(tag);
        assertEquals(character, loaded);
        assertTrue(loaded.isStatusBurden());
        assertEquals("Eldrin", loaded.getName());
        assertEquals(28, loaded.getAge());
        assertEquals(1700000000000L, loaded.getCreationDate());
    }

    @Test
    public void testDeadCharacterNBT() {
        DeadCharacter dead = new DeadCharacter("Alistair", 55, Set.of("Blacksmith", "Miner"), "House of Iron", 1700500000000L, "Fell from high place");
        CompoundTag tag = dead.toNBT();

        assertEquals("Alistair", tag.getString(DeadCharacter.KEY_NAME));
        assertEquals(55, tag.getInt(DeadCharacter.KEY_AGE));
        assertEquals("House of Iron", tag.getString(DeadCharacter.KEY_LINEAGE));
        assertEquals(1700500000000L, tag.getLong(DeadCharacter.KEY_DEATH_DATE));
        assertEquals("Fell from high place", tag.getString(DeadCharacter.KEY_DEATH_CAUSE));

        assertFalse(dead.isBuried());
        DeadCharacter loaded = DeadCharacter.fromNBT(tag);
        assertEquals(dead, loaded);
        assertFalse(loaded.isBuried());
        assertTrue(loaded.getProfessions().contains("Blacksmith"));
        assertTrue(loaded.getProfessions().contains("Miner"));

        // Test buried = true
        dead.setBuried(true);
        assertTrue(dead.isBuried());
        CompoundTag buriedTag = dead.toNBT();
        assertTrue(buriedTag.getBoolean(DeadCharacter.KEY_BURIED));
        DeadCharacter loadedBuried = DeadCharacter.fromNBT(buriedTag);
        assertTrue(loadedBuried.isBuried());
        assertEquals(dead, loadedBuried);
    }

    @Test
    public void testPlayerCharacterDataLifecycle() {
        UUID uuid = UUID.randomUUID();
        PlayerCharacterData data = new PlayerCharacterData(uuid);

        data.setLineage("Valerius");
        data.addProfession("Warrior");
        data.addProfession("Herbalist");

        ActiveCharacter character = new ActiveCharacter("Leon", 22, 1700000000000L, false);
        data.setActiveCharacter(character);

        assertTrue(data.hasActiveCharacter());
        assertEquals("Valerius", data.getLineage());
        assertTrue(data.hasProfession("Warrior"));
        assertTrue(data.hasProfession("Herbalist"));

        // Kill character
        DeadCharacter dead = data.killActiveCharacter("Slain by Zombie", 1701000000000L);
        assertNotNull(dead);
        assertEquals("Leon", dead.getName());
        assertEquals(22, dead.getAge());
        assertEquals("Valerius", dead.getLineage());
        assertEquals("Slain by Zombie", dead.getDeathCause());
        assertTrue(dead.getProfessions().contains("Warrior"));
        assertFalse(data.hasActiveCharacter());
        assertEquals(1, data.getCharacterHistory().size());

        // Roundtrip to NBT
        CompoundTag tag = data.toNBT();
        PlayerCharacterData loaded = PlayerCharacterData.fromNBT(tag);

        assertEquals(uuid, loaded.getPlayerUuid());
        assertEquals("Valerius", loaded.getLineage());
        assertTrue(loaded.hasProfession("Warrior"));
        assertFalse(loaded.hasActiveCharacter());
        assertEquals(1, loaded.getCharacterHistory().size());
        assertEquals("Leon", loaded.getCharacterHistory().get(0).getName());
    }

    @Test
    public void testCharacterWorldDataSaveAndLoad() {
        CharacterWorldData worldData = new CharacterWorldData();

        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // Player 1 setup
        worldData.setLineage(player1, "Stormwind");
        worldData.addProfession(player1, "Mage");
        worldData.setActiveCharacter(player1, new ActiveCharacter("Jaina", 30, 1700000000000L, true));

        // Player 2 setup
        worldData.setLineage(player2, "Ironforge");
        worldData.addProfession(player2, "Warrior");
        worldData.setActiveCharacter(player2, new ActiveCharacter("Muradin", 40, 1700000000000L, false));
        worldData.killCharacter(player2, "Crushed by Anvil");

        // Save world data
        CompoundTag savedTag = worldData.save(new CompoundTag());
        assertNotNull(savedTag);

        // Load into new instance
        CharacterWorldData loadedData = CharacterWorldData.load(savedTag);

        // Verify Player 1
        PlayerCharacterData p1 = loadedData.getPlayerData(player1);
        assertNotNull(p1);
        assertEquals("Stormwind", p1.getLineage());
        assertTrue(p1.hasProfession("Mage"));
        assertNotNull(p1.getActiveCharacter());
        assertEquals("Jaina", p1.getActiveCharacter().getName());
        assertTrue(p1.getActiveCharacter().isStatusBurden());

        // Verify Player 2
        PlayerCharacterData p2 = loadedData.getPlayerData(player2);
        assertNotNull(p2);
        assertEquals("Ironforge", p2.getLineage());
        assertNull(p2.getActiveCharacter());
        List<DeadCharacter> history = p2.getCharacterHistory();
        assertEquals(1, history.size());
        assertEquals("Muradin", history.get(0).getName());
        assertEquals("Crushed by Anvil", history.get(0).getDeathCause());
        assertEquals("Ironforge", history.get(0).getLineage());
    }

    @Test
    public void testRebirthProcess() {
        CharacterWorldData worldData = new CharacterWorldData();
        UUID playerId = UUID.randomUUID();

        // 1. Initial character setup
        worldData.setLineage(playerId, "Targaryen");
        worldData.addProfession(playerId, "Dragon Rider");
        worldData.addProfession(playerId, "Blacksmith");
        worldData.setActiveCharacter(playerId, new ActiveCharacter("Aegon I", 64, 1000L, false));

        PlayerCharacterData data = worldData.getPlayerData(playerId);
        assertNotNull(data);
        assertTrue(data.hasActiveCharacter());
        assertEquals("Aegon I", data.getActiveCharacter().getName());
        assertEquals("Targaryen", data.getLineage());
        assertEquals(2, data.getProfessions().size());

        // 2. Perform Rebirth
        // Step A: Kill active character
        DeadCharacter dead = data.killActiveCharacter("Old Age & Slain in Battle", 2000L);
        assertNotNull(dead);
        assertEquals("Aegon I", dead.getName());
        assertEquals(64, dead.getAge());
        assertEquals("Old Age & Slain in Battle", dead.getDeathCause());
        assertEquals("Targaryen", dead.getLineage());
        assertTrue(dead.getProfessions().contains("Dragon Rider"));
        assertEquals(1, data.getCharacterHistory().size());

        // Step B: Create new active character (Name & Age input by player)
        ActiveCharacter newChar = new ActiveCharacter("Aenys I", 30, 2001L, false);
        data.setActiveCharacter(newChar);

        // Step C: Verify inheritance of Lineage and Professions
        assertEquals("Aenys I", data.getActiveCharacter().getName());
        assertEquals(30, data.getActiveCharacter().getAge());
        assertEquals("Targaryen", data.getLineage());
        assertTrue(data.hasProfession("Dragon Rider"));
        assertTrue(data.hasProfession("Blacksmith"));
        assertEquals(1, data.getCharacterHistory().size());
        assertEquals("Aegon I", data.getCharacterHistory().get(0).getName());
    }

    @Test
    public void testPlayerNameTrackingAndMultiplayerSync() {
        CharacterWorldData worldData = new CharacterWorldData();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        worldData.updatePlayerName(player1, "Alex");
        worldData.setActiveCharacter(player1, new ActiveCharacter("Alexius", 25, 1000L, false));
        worldData.setLineage(player1, "House of Alex");

        worldData.updatePlayerName(player2, "Boris");
        worldData.setActiveCharacter(player2, new ActiveCharacter("Boromir", 35, 2000L, true));
        worldData.killCharacter(player2, "Orc arrow");

        // Save to tag
        CompoundTag tag = worldData.save(new CompoundTag());

        // Load into client-side worldData
        CharacterWorldData clientWorldData = CharacterWorldData.load(tag);
        assertEquals(2, clientWorldData.getAllPlayerData().size());

        PlayerCharacterData cP1 = clientWorldData.getPlayerData(player1);
        assertEquals("Alex", cP1.getLastKnownPlayerName());
        assertTrue(cP1.hasActiveCharacter());
        assertEquals("Alexius", cP1.getActiveCharacter().getName());

        PlayerCharacterData cP2 = clientWorldData.getPlayerData(player2);
        assertEquals("Boris", cP2.getLastKnownPlayerName());
        assertFalse(cP2.hasActiveCharacter());
        assertEquals(1, cP2.getCharacterHistory().size());
        assertEquals("Boromir", cP2.getCharacterHistory().get(0).getName());
    }

    @Test
    public void testRemainsNBTStructure() {
        UUID owner = UUID.randomUUID();
        DeadCharacter dead = new DeadCharacter("Rhaegar", 24, Set.of("Knight", "Bard"), "Targaryen", 1700000000000L, "Warhammer to chest");

        CompoundTag tag = new CompoundTag();
        tag.putUUID(com.onemanager.item.RemainsItem.KEY_OWNER_UUID, owner);
        tag.putString(com.onemanager.item.RemainsItem.KEY_DEAD_CHAR_NAME, dead.getName());
        tag.putInt(com.onemanager.item.RemainsItem.KEY_AGE, dead.getAge());
        tag.putString(com.onemanager.item.RemainsItem.KEY_LINEAGE, dead.getLineage());
        tag.putLong(com.onemanager.item.RemainsItem.KEY_DEATH_DATE, dead.getDeathDate());
        tag.putString(com.onemanager.item.RemainsItem.KEY_DEATH_REASON, dead.getDeathCause());

        assertEquals(owner, tag.getUUID(com.onemanager.item.RemainsItem.KEY_OWNER_UUID));
        assertEquals("Rhaegar", tag.getString(com.onemanager.item.RemainsItem.KEY_DEAD_CHAR_NAME));
        assertEquals(24, tag.getInt(com.onemanager.item.RemainsItem.KEY_AGE));
        assertEquals("Targaryen", tag.getString(com.onemanager.item.RemainsItem.KEY_LINEAGE));
        assertEquals(1700000000000L, tag.getLong(com.onemanager.item.RemainsItem.KEY_DEATH_DATE));
        assertEquals("Warhammer to chest", tag.getString(com.onemanager.item.RemainsItem.KEY_DEATH_REASON));
    }

    @Test
    public void testPendingRemainsAndStatusBurden() {
        UUID playerId = UUID.randomUUID();
        PlayerCharacterData data = new PlayerCharacterData(playerId);

        ActiveCharacter active = new ActiveCharacter("Bran", 16, 1000L, true);
        data.setActiveCharacter(active);
        assertTrue(data.getActiveCharacter().isStatusBurden());

        // Bury remains -> burden lifted
        data.getActiveCharacter().setStatusBurden(false);
        assertFalse(data.getActiveCharacter().isStatusBurden());

        // Offline desecration: pending remains added
        CompoundTag remainsTag = new CompoundTag();
        remainsTag.putString("deadCharName", "Ned");
        data.addPendingRemains(remainsTag);

        assertEquals(1, data.getPendingRemains().size());
        assertEquals("Ned", data.getPendingRemains().get(0).getString("deadCharName"));

        // Roundtrip to NBT
        CompoundTag nbt = data.toNBT();
        PlayerCharacterData loaded = PlayerCharacterData.fromNBT(nbt);

        assertFalse(loaded.getActiveCharacter().isStatusBurden());
        assertEquals(1, loaded.getPendingRemains().size());
        assertEquals("Ned", loaded.getPendingRemains().get(0).getString("deadCharName"));

        // Clear upon delivery
        loaded.clearPendingRemains();
        assertEquals(0, loaded.getPendingRemains().size());
    }

    @Test
    public void testAshesAndBurialTracking() {
        UUID owner = UUID.randomUUID();
        DeadCharacter dead = new DeadCharacter("Aemon", 88, Set.of("Maester"), "Targaryen", 1700000000000L, "Old age", false);
        assertFalse(dead.isBuried());

        // Remains NBT tag
        CompoundTag remainsTag = new CompoundTag();
        remainsTag.putUUID(com.onemanager.item.RemainsItem.KEY_OWNER_UUID, owner);
        remainsTag.putString(com.onemanager.item.RemainsItem.KEY_DEAD_CHAR_NAME, dead.getName());
        remainsTag.putInt(com.onemanager.item.RemainsItem.KEY_AGE, dead.getAge());
        remainsTag.putString(com.onemanager.item.RemainsItem.KEY_LINEAGE, dead.getLineage());
        remainsTag.putLong(com.onemanager.item.RemainsItem.KEY_DEATH_DATE, dead.getDeathDate());
        remainsTag.putString(com.onemanager.item.RemainsItem.KEY_DEATH_REASON, dead.getDeathCause());

        // Ashes NBT tag copied from remains
        CompoundTag ashesTag = remainsTag.copy();
        ashesTag.putLong(com.onemanager.item.AshesItem.KEY_CREMATION_DATE, 1700000005000L);

        assertEquals(owner, ashesTag.getUUID(com.onemanager.item.AshesItem.KEY_OWNER_UUID));
        assertEquals("Aemon", ashesTag.getString(com.onemanager.item.AshesItem.KEY_DEAD_CHAR_NAME));
        assertEquals(1700000000000L, ashesTag.getLong(com.onemanager.item.AshesItem.KEY_DEATH_DATE));
        assertEquals(1700000005000L, ashesTag.getLong(com.onemanager.item.AshesItem.KEY_CREMATION_DATE));

        // Place in urn -> buried = true
        dead.setBuried(true);
        assertTrue(dead.isBuried());

        // Break urn -> buried = false
        dead.setBuried(false);
        assertFalse(dead.isBuried());
    }

    @Test
    public void testStateBrandBalance() {
        UUID uuid = UUID.randomUUID();
        PlayerCharacterData data = new PlayerCharacterData(uuid);

        assertEquals(0, data.getStateBrands());

        data.addStateBrands(50);
        assertEquals(50, data.getStateBrands());

        data.addStateBrands(25);
        assertEquals(75, data.getStateBrands());

        int removed = data.removeStateBrands(30);
        assertEquals(30, removed);
        assertEquals(45, data.getStateBrands());

        // Removing more than balance clamps to 0 and returns actual removed amount
        int removedExcess = data.removeStateBrands(100);
        assertEquals(45, removedExcess);
        assertEquals(0, data.getStateBrands());

        data.setStateBrands(150);
        assertEquals(150, data.getStateBrands());

        // NBT serialization roundtrip
        CompoundTag tag = data.toNBT();
        assertEquals(150, tag.getInt(PlayerCharacterData.KEY_STATE_BRANDS));

        PlayerCharacterData loaded = PlayerCharacterData.fromNBT(tag);
        assertEquals(150, loaded.getStateBrands());

        // Test CharacterWorldData
        CharacterWorldData worldData = new CharacterWorldData();
        worldData.setStateBrands(uuid, 200);
        assertEquals(200, worldData.getStateBrands(uuid));

        worldData.addStateBrands(uuid, 50);
        assertEquals(250, worldData.getStateBrands(uuid));

        int wdRemoved = worldData.removeStateBrands(uuid, 100);
        assertEquals(100, wdRemoved);
        assertEquals(150, worldData.getStateBrands(uuid));

        CompoundTag wdTag = worldData.save(new CompoundTag());
        CharacterWorldData loadedWorldData = CharacterWorldData.load(wdTag);
        assertEquals(150, loadedWorldData.getStateBrands(uuid));
    }

    @Test
    public void testProfessionSlots() {
        UUID uuid = UUID.randomUUID();
        PlayerCharacterData data = new PlayerCharacterData(uuid);

        // Verify defaults
        assertEquals(1, data.getMainProfessionSlots());
        assertEquals(2, data.getSecondaryProfessionSlots());

        // Modify slots
        data.setMainProfessionSlots(3);
        data.setSecondaryProfessionSlots(5);
        assertEquals(3, data.getMainProfessionSlots());
        assertEquals(5, data.getSecondaryProfessionSlots());

        data.addMainProfessionSlots(2);
        data.addSecondaryProfessionSlots(1);
        assertEquals(5, data.getMainProfessionSlots());
        assertEquals(6, data.getSecondaryProfessionSlots());

        // Serialization roundtrip
        CompoundTag tag = data.toNBT();
        assertEquals(5, tag.getInt(PlayerCharacterData.KEY_MAIN_PROFESSION_SLOTS));
        assertEquals(6, tag.getInt(PlayerCharacterData.KEY_SECONDARY_PROFESSION_SLOTS));

        PlayerCharacterData loaded = PlayerCharacterData.fromNBT(tag);
        assertEquals(5, loaded.getMainProfessionSlots());
        assertEquals(6, loaded.getSecondaryProfessionSlots());

        // CharacterWorldData tests
        CharacterWorldData worldData = new CharacterWorldData();
        assertEquals(1, worldData.getMainProfessionSlots(uuid)); // default for non-existent player
        assertEquals(2, worldData.getSecondaryProfessionSlots(uuid));

        worldData.setMainProfessionSlots(uuid, 4);
        worldData.setSecondaryProfessionSlots(uuid, 3);
        assertEquals(4, worldData.getMainProfessionSlots(uuid));
        assertEquals(3, worldData.getSecondaryProfessionSlots(uuid));

        worldData.addMainProfessionSlots(uuid, 2);
        worldData.addSecondaryProfessionSlots(uuid, 1);
        assertEquals(6, worldData.getMainProfessionSlots(uuid));
        assertEquals(4, worldData.getSecondaryProfessionSlots(uuid));

        CompoundTag wdTag = worldData.save(new CompoundTag());
        CharacterWorldData loadedWorldData = CharacterWorldData.load(wdTag);
        assertEquals(6, loadedWorldData.getMainProfessionSlots(uuid));
        assertEquals(4, loadedWorldData.getSecondaryProfessionSlots(uuid));
    }

    @Test
    public void testDaysAliveCalculation() {
        long creationGameTime = 10000L;
        ActiveCharacter character = new ActiveCharacter("Chronos", 25, System.currentTimeMillis(), false, creationGameTime);

        assertEquals(0, character.getDaysAlive(10000L));
        assertEquals(0, character.getDaysAlive(24000L)); // 14,000 ticks < 24,000
        assertEquals(1, character.getDaysAlive(34000L)); // 24,000 ticks = 1 full day
        assertEquals(2, character.getDaysAlive(58000L)); // 48,000 ticks = 2 full days
        assertEquals(10, character.getDaysAlive(250000L)); // 240,000 ticks = 10 full days

        // Serialization roundtrip
        CompoundTag tag = character.toNBT();
        assertEquals(10000L, tag.getLong(ActiveCharacter.KEY_CREATION_GAME_TIME));

        ActiveCharacter loaded = ActiveCharacter.fromNBT(tag);
        assertEquals(creationGameTime, loaded.getCreationGameTime());
        assertEquals(2, loaded.getDaysAlive(58000L));

        // Fallback test when creationGameTime is 0
        long twoDaysAgo = System.currentTimeMillis() - (2 * 1200000L); // 2 full MC days in millis
        ActiveCharacter legacyChar = new ActiveCharacter("Legacy", 30, twoDaysAgo, false, 0L);
        assertEquals(2, legacyChar.getDaysAlive(0L));
    }

    @Test
    public void testFactionAndLicenses() {
        UUID uuid = UUID.randomUUID();
        PlayerCharacterData data = new PlayerCharacterData(uuid);

        assertEquals("", data.getFaction());
        assertTrue(data.getLicenses().isEmpty());

        data.setFaction("Северный Альянс");
        data.addLicense("Автоматизированный завод красной пыли");
        data.addLicense("Литейная мануфактура");

        assertEquals("Северный Альянс", data.getFaction());
        assertEquals(2, data.getLicenses().size());
        assertTrue(data.hasLicense("Автоматизированный завод красной пыли"));
        assertTrue(data.hasLicense("Литейная мануфактура"));
        assertFalse(data.hasLicense("Ткацкая фабрика"));

        // Serialization roundtrip
        CompoundTag tag = data.toNBT();
        assertEquals("Северный Альянс", tag.getString(PlayerCharacterData.KEY_FACTION));

        PlayerCharacterData loaded = PlayerCharacterData.fromNBT(tag);
        assertEquals("Северный Альянс", loaded.getFaction());
        assertEquals(2, loaded.getLicenses().size());
        assertTrue(loaded.hasLicense("Автоматизированный завод красной пыли"));

        // Remove license
        loaded.removeLicense("Литейная мануфактура");
        assertEquals(1, loaded.getLicenses().size());
        assertFalse(loaded.hasLicense("Литейная мануфактура"));

        // CharacterWorldData tests
        CharacterWorldData worldData = new CharacterWorldData();
        worldData.setFaction(uuid, "Совет Общины");
        worldData.addLicense(uuid, "Шахтерская концессия");

        assertEquals("Совет Общины", worldData.getFaction(uuid));
        assertTrue(worldData.getLicenses(uuid).contains("Шахтерская концессия"));

        CompoundTag wdTag = worldData.save(new CompoundTag());
        CharacterWorldData loadedWD = CharacterWorldData.load(wdTag);
        assertEquals("Совет Общины", loadedWD.getFaction(uuid));
        assertTrue(loadedWD.getLicenses(uuid).contains("Шахтерская концессия"));
    }

    @Test
    public void testWeeklyCycleAndGrants() {
        assertEquals(140 * 60 * 1000L, PlayerCharacterData.BRAND_RESET_INTERVAL_MS);

        UUID uuid = UUID.randomUUID();
        PlayerCharacterData data = new PlayerCharacterData(uuid);
        assertEquals(0, data.getStateBrands());

        data.addProfession("Фермер");
        data.addProfession("Шахтер");
        assertEquals(2, data.getProfessions().size());

        long startTime = 1000000L;
        data.setLastBrandResetTime(startTime);

        // Before interval: no cycle reset, no grants
        int grantedBefore = data.triggerBrandCycleReset(startTime + 1000L, true);
        assertEquals(0, grantedBefore);
        assertEquals(0, data.getStateBrands());

        // Exactly 1 cycle elapsed (140 mins)
        long oneCycleLater = startTime + PlayerCharacterData.BRAND_RESET_INTERVAL_MS;
        int grantedOne = data.triggerBrandCycleReset(oneCycleLater, true);
        assertEquals(2, grantedOne); // 2 professions * 1 cycle
        assertEquals(2, data.getStateBrands());
        assertEquals(oneCycleLater, data.getLastBrandResetTime());

        // 2 cycles elapsed later
        long twoCyclesLater = oneCycleLater + (2 * PlayerCharacterData.BRAND_RESET_INTERVAL_MS);
        int grantedTwo = data.triggerBrandCycleReset(twoCyclesLater, true);
        assertEquals(4, grantedTwo); // 2 professions * 2 cycles
        assertEquals(6, data.getStateBrands());
        assertEquals(twoCyclesLater, data.getLastBrandResetTime());
    }

    @Test
    public void testWorldCycleProgress() {
        CharacterWorldData wd = new CharacterWorldData();
        assertEquals(1, wd.getCurrentWeek());
        assertEquals(1, wd.getCurrentDay());
        assertEquals(1, wd.getTotalDays());
        assertEquals(CharacterWorldData.TAX_CYCLE_TICKS, wd.getTaxTicksRemaining());
        assertEquals(CharacterWorldData.WEEK_CYCLE_TICKS, wd.getWeekTicksRemaining());

        // Fast-forward 1 day (24000 ticks)
        for (int i = 0; i < CharacterWorldData.TAX_CYCLE_TICKS; i++) {
            boolean dayDone = wd.tickTax();
            if (i == CharacterWorldData.TAX_CYCLE_TICKS - 1) {
                assertTrue(dayDone);
            } else {
                assertFalse(dayDone);
            }
        }
        assertEquals(2, wd.getCurrentDay());
        assertEquals(2, wd.getTotalDays());
        assertEquals(1, wd.getCurrentWeek());
        assertFalse(wd.isWeekCompletedThisTick());

        // Fast-forward 6 more days (total 7 days)
        for (int day = 2; day <= 7; day++) {
            for (int i = 0; i < CharacterWorldData.TAX_CYCLE_TICKS; i++) {
                wd.tickTax();
            }
        }
        // At the end of day 7, week 1 ends, week 2 begins, day resets to 1
        assertEquals(1, wd.getCurrentDay());
        assertEquals(8, wd.getTotalDays());
        assertEquals(2, wd.getCurrentWeek());

        // Test NBT persistence
        CompoundTag tag = wd.save(new CompoundTag());
        CharacterWorldData loaded = CharacterWorldData.load(tag);
        assertEquals(2, loaded.getCurrentWeek());
        assertEquals(1, loaded.getCurrentDay());
        assertEquals(8, loaded.getTotalDays());
        assertEquals(CharacterWorldData.WEEK_CYCLE_TICKS, loaded.getWeekTicksRemaining());
        assertEquals(CharacterWorldData.TAX_CYCLE_TICKS, loaded.getTaxTicksRemaining());
    }

    @Test
    public void testProfessionStartDates() {
        UUID uuid = UUID.randomUUID();
        PlayerCharacterData pd = new PlayerCharacterData(uuid);

        pd.addProfession("Фермер-скотовод", "01.04.00");
        pd.addProfession("Шеф-повар", "03.02.01");

        assertEquals("01.04.00", pd.getProfessionStartDate("Фермер-скотовод"));
        assertEquals("03.02.01", pd.getProfessionStartDate("Шеф-повар"));
        assertEquals("01.04.00", pd.getProfessionStartDate("Неизвестная"));

        CompoundTag tag = pd.toNBT();
        PlayerCharacterData loaded = PlayerCharacterData.fromNBT(tag);

        assertEquals("01.04.00", loaded.getProfessionStartDate("Фермер-скотовод"));
        assertEquals("03.02.01", loaded.getProfessionStartDate("Шеф-повар"));

        loaded.removeProfession("Шеф-повар");
        assertEquals("01.04.00", loaded.getProfessionStartDate("Шеф-повар"));
    }
}

