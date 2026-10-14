package com.onemanager.data;

import com.onemanager.OneManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Unified world data storage for character professions, lineage, and character history.
 * Always stored in the Overworld's DimensionDataStorage to maintain consistent state
 * across all dimensions (Overworld, Nether, The End, and custom dimensions).
 */
public class CharacterWorldData extends SavedData {
    public static final String DATA_NAME = "onemanager_character_data";
    private static final String KEY_PLAYERS = "players";
    public static final String KEY_KNYAZ_NAME = "knyaz_name";
    public static final String KEY_KNYAZ_UUID = "knyaz_uuid";
    public static final String KEY_WORLD_LEVEL = "world_level";
    public static final String KEY_COMPLETED_ADVANCEMENTS = "completed_advancements";
    public static final String KEY_TREASURY = "treasury";
    public static final String KEY_TAX_RATE = "tax_rate";
    public static final String KEY_TAX_TICKS = "tax_ticks";
    public static final String KEY_CURRENT_WEEK = "current_week";
    public static final String KEY_CURRENT_DAY = "current_day";
    public static final String KEY_TOTAL_DAYS = "total_days";
    public static final String KEY_WEEK_TICKS = "week_ticks";
    public static final String KEY_TREASURY_LOG = "treasury_log";
    public static final String KEY_LAWS = "laws";

    public static final int DEFAULT_TAX_RATE = 10;
    public static final int TAX_CYCLE_TICKS = 24000; // 20 minutes (20 * 60 * 20) = 1 in-game day
    public static final int WEEK_CYCLE_TICKS = 168000; // 140 minutes (7 * 24000) = 1 in-game week

    private final Map<UUID, PlayerCharacterData> players = new ConcurrentHashMap<>();
    private String knyazName = "";
    private UUID knyazUuid = null;
    private int worldLevel = 0;
    private long treasury = 0;
    private int taxRate = DEFAULT_TAX_RATE;
    private int taxTicksRemaining = TAX_CYCLE_TICKS;
    private int currentWeek = 1;
    private int currentDay = 1;
    private int totalDays = 1;
    private int weekTicksRemaining = WEEK_CYCLE_TICKS;
    private boolean weekCompletedThisTick = false;
    private final Set<String> completedAdvancements = ConcurrentHashMap.newKeySet();
    private final List<TreasuryLogEntry> treasuryLog = new CopyOnWriteArrayList<>();
    private final List<LawEntry> laws = new CopyOnWriteArrayList<>();

    public static final SavedData.Factory<CharacterWorldData> FACTORY = new SavedData.Factory<>(
            CharacterWorldData::new,
            CharacterWorldData::load,
            DataFixTypes.LEVEL
    );

    public CharacterWorldData() {
    }

    /**
     * Retrieves the CharacterWorldData instance for the given ServerLevel.
     * Always resolves to the Overworld storage.
     */
    public static CharacterWorldData get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return get(server);
    }

    /**
     * Retrieves the CharacterWorldData instance from the MinecraftServer.
     * Always uses the Overworld's DimensionDataStorage.
     */
    public static CharacterWorldData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld level is not loaded, unable to access CharacterWorldData.");
        }
        DimensionDataStorage storage = overworld.getDataStorage();
        CharacterWorldData worldData = storage.computeIfAbsent(FACTORY, DATA_NAME);
        if (worldData.getAllPlayerData().isEmpty()) {
            CharacterWorldData legacyData = storage.get(FACTORY, "pcmanager_character_data");
            if (legacyData != null && !legacyData.getAllPlayerData().isEmpty()) {
                worldData = legacyData;
                worldData.setDirty();
            }
        }
        try {
            KnyazSavedData knyazData = KnyazSavedData.get(server);
            if (knyazData != null) {
                worldData.setKnyaz(knyazData.getKnyazName(), knyazData.getKnyazUUID());
            }
        } catch (Exception ignored) {
        }
        return worldData;
    }

    // --- Player Data Accessors ---

    public PlayerCharacterData getPlayerData(UUID uuid) {
        if (uuid == null) return null;
        return players.get(uuid);
    }

    public PlayerCharacterData getOrCreatePlayerData(UUID uuid) {
        Objects.requireNonNull(uuid, "Player UUID cannot be null");
        return players.computeIfAbsent(uuid, id -> {
            setDirty();
            return new PlayerCharacterData(id);
        });
    }

    public void setPlayerData(PlayerCharacterData data) {
        if (data != null && data.getPlayerUuid() != null) {
            players.put(data.getPlayerUuid(), data);
            setDirty();
        }
    }

    public void updatePlayerName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty()) return;
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        if (!name.equals(data.getLastKnownPlayerName())) {
            data.setLastKnownPlayerName(name);
            setDirty();
        }
    }

    public Map<UUID, PlayerCharacterData> getAllPlayerData() {
        return Collections.unmodifiableMap(players);
    }

    // --- Active Character Operations ---

    public ActiveCharacter getActiveCharacter(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getActiveCharacter() : null;
    }

    public void setActiveCharacter(UUID uuid, ActiveCharacter character) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setActiveCharacter(character);
        setDirty();
    }

    public CompoundTag getActiveCharacterNBT(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getActiveCharacterNBT() : new CompoundTag();
    }

    public void setActiveCharacterNBT(UUID uuid, CompoundTag tag) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setActiveCharacterNBT(tag);
        setDirty();
    }

    // --- Lineage Operations ---

    public String getLineage(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getLineage() : "";
    }

    public void setLineage(UUID uuid, String lineage) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setLineage(lineage);
        setDirty();
    }

    // --- Faction Operations ---

    public String getFaction(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getFaction() : "";
    }

    public void setFaction(UUID uuid, String faction) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setFaction(faction);
        setDirty();
    }

    // --- License Operations ---

    public Set<String> getLicenses(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getLicenses() : Collections.emptySet();
    }

    public boolean addLicense(UUID uuid, String license) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        boolean added = data.addLicense(license);
        if (added) setDirty();
        return added;
    }

    public boolean removeLicense(UUID uuid, String license) {
        PlayerCharacterData data = getPlayerData(uuid);
        if (data != null) {
            boolean removed = data.removeLicense(license);
            if (removed) setDirty();
            return removed;
        }
        return false;
    }

    // --- Profession Operations ---

    public Set<String> getProfessions(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getProfessions() : Collections.emptySet();
    }

    public boolean addProfession(UUID uuid, String profession) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        boolean added = data.addProfession(profession);
        if (added) setDirty();
        return added;
    }

    public boolean removeProfession(UUID uuid, String profession) {
        PlayerCharacterData data = getPlayerData(uuid);
        if (data != null) {
            boolean removed = data.removeProfession(profession);
            if (removed) setDirty();
            return removed;
        }
        return false;
    }

    public boolean hasProfession(UUID uuid, String profession) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null && data.hasProfession(profession);
    }

    public void setProfessions(UUID uuid, Collection<String> professions) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setProfessions(professions);
        setDirty();
    }

    // --- Character History Operations ---

    public List<DeadCharacter> getCharacterHistory(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getCharacterHistory() : Collections.emptyList();
    }

    public void addDeadCharacter(UUID uuid, DeadCharacter deadCharacter) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.addDeadCharacter(deadCharacter);
        setDirty();
    }

    public DeadCharacter killCharacter(UUID uuid, String deathCause) {
        PlayerCharacterData data = getPlayerData(uuid);
        if (data != null && data.hasActiveCharacter()) {
            DeadCharacter dead = data.killActiveCharacter(deathCause);
            setDirty();
            return dead;
        }
        return null;
    }

    // --- State Brands (Гос. Марки) Operations ---

    public int getStateBrands(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getStateBrands() : 0;
    }

    public void setStateBrands(UUID uuid, int amount) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setStateBrands(amount);
        setDirty();
    }

    public void addStateBrands(UUID uuid, int amount) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.addStateBrands(amount);
        setDirty();
    }

    public int removeStateBrands(UUID uuid, int amount) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        int removed = data.removeStateBrands(amount);
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    // --- Profession Slots Operations ---

    public int getMainProfessionSlots(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getMainProfessionSlots() : 1;
    }

    public void setMainProfessionSlots(UUID uuid, int slots) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setMainProfessionSlots(slots);
        setDirty();
    }

    public void addMainProfessionSlots(UUID uuid, int amount) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.addMainProfessionSlots(amount);
        setDirty();
    }

    public int getSecondaryProfessionSlots(UUID uuid) {
        PlayerCharacterData data = getPlayerData(uuid);
        return data != null ? data.getSecondaryProfessionSlots() : 2;
    }

    public void setSecondaryProfessionSlots(UUID uuid, int slots) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.setSecondaryProfessionSlots(slots);
        setDirty();
    }

    public void addSecondaryProfessionSlots(UUID uuid, int amount) {
        PlayerCharacterData data = getOrCreatePlayerData(uuid);
        data.addSecondaryProfessionSlots(amount);
        setDirty();
    }

    // --- Knyaz Operations ---

    public String getKnyazName() {
        return knyazName != null ? knyazName : "";
    }

    public UUID getKnyazUuid() {
        return knyazUuid;
    }

    public void setKnyaz(String name, UUID uuid) {
        this.knyazName = name != null ? name : "";
        this.knyazUuid = uuid;
        setDirty();
    }

    public boolean isKnyaz(UUID uuid) {
        return uuid != null && knyazUuid != null && knyazUuid.equals(uuid);
    }

    public boolean hasKnyaz() {
        return knyazName != null && !knyazName.isEmpty();
    }

    // --- Laws (Государственные законы) Operations ---

    public List<LawEntry> getLaws() {
        return Collections.unmodifiableList(laws);
    }

    public LawEntry getLaw(UUID id) {
        if (id == null) return null;
        for (LawEntry law : laws) {
            if (law.getId().equals(id)) {
                return law;
            }
        }
        return null;
    }

    public void addLaw(LawEntry law) {
        if (law != null) {
            laws.add(law);
            setDirty();
        }
    }

    public boolean updateLaw(UUID id, List<String> pages, String editorName, String modifiedDate) {
        LawEntry law = getLaw(id);
        if (law != null) {
            law.updatePages(pages, editorName, modifiedDate);
            setDirty();
            return true;
        }
        return false;
    }

    public boolean removeLaw(UUID id) {
        if (id == null) return false;
        boolean removed = laws.removeIf(l -> l.getId().equals(id));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // --- World Level & Progression Operations ---

    public int getWorldLevel() {
        return worldLevel;
    }

    public void setWorldLevel(int level) {
        this.worldLevel = Math.max(0, level);
        setDirty();
    }

    public void addWorldLevel(int amount) {
        setWorldLevel(this.worldLevel + amount);
    }

    public boolean recordAdvancement(String advancementId) {
        if (advancementId != null && !advancementId.isEmpty() && completedAdvancements.add(advancementId)) {
            this.worldLevel++;
            setDirty();
            return true;
        }
        return false;
    }

    public Set<String> getCompletedAdvancements() {
        return Collections.unmodifiableSet(completedAdvancements);
    }

    // --- Treasury (Казна) Operations ---

    public long getTreasury() {
        return treasury;
    }

    public void setTreasury(long amount) {
        this.treasury = Math.max(0, amount);
        setDirty();
    }

    public void addTreasury(long amount) {
        setTreasury(this.treasury + amount);
    }

    public long removeTreasury(long amount) {
        long actual = Math.min(this.treasury, Math.max(0, amount));
        setTreasury(this.treasury - actual);
        return actual;
    }

    // --- Taxes & Economy ---

    public int getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(int taxRate) {
        this.taxRate = Math.max(0, taxRate);
        setDirty();
    }

    public int getTaxTicksRemaining() {
        return taxTicksRemaining;
    }

    public void setTaxTicksRemaining(int ticks) {
        this.taxTicksRemaining = Math.max(0, ticks);
        setDirty();
    }

    public int getWeekTicksRemaining() {
        return weekTicksRemaining;
    }

    public void setWeekTicksRemaining(int ticks) {
        this.weekTicksRemaining = Math.max(0, ticks);
        setDirty();
    }

    public int getCurrentWeek() {
        return currentWeek;
    }

    public void setCurrentWeek(int currentWeek) {
        this.currentWeek = Math.max(1, currentWeek);
        setDirty();
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public void setCurrentDay(int currentDay) {
        this.currentDay = Math.max(1, Math.min(7, currentDay));
        setDirty();
    }

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = Math.max(1, totalDays);
        setDirty();
    }

    /**
     * Ticks the day (20m) and week (140m) cycle timers.
     * Returns true when a 20-minute day cycle completes.
     */
    public boolean tickTax() {
        weekCompletedThisTick = false;
        if (taxTicksRemaining > 0) {
            taxTicksRemaining--;
        }
        if (weekTicksRemaining > 0) {
            weekTicksRemaining--;
        }

        boolean dayCompleted = false;
        if (taxTicksRemaining <= 0) {
            taxTicksRemaining = TAX_CYCLE_TICKS;
            totalDays++;
            if (currentDay < 7) {
                currentDay++;
            } else {
                currentDay = 1;
            }
            dayCompleted = true;
            setDirty();
        }

        if (weekTicksRemaining <= 0) {
            weekTicksRemaining = WEEK_CYCLE_TICKS;
            currentWeek++;
            currentDay = 1;
            weekCompletedThisTick = true;
            setDirty();
        }

        return dayCompleted;
    }

    public boolean isWeekCompletedThisTick() {
        return weekCompletedThisTick;
    }

    public boolean isTreasurer(UUID uuid) {
        if (uuid == null) return false;
        PlayerCharacterData pd = getPlayerData(uuid);
        if (pd != null) {
            Set<String> profs = pd.getProfessions();
            return profs != null && (profs.contains("Казначей") || profs.contains("treasurer"));
        }
        return false;
    }

    public boolean canManageTaxes(UUID uuid) {
        return isKnyaz(uuid) || isTreasurer(uuid);
    }

    public List<TreasuryLogEntry> getTreasuryLog() {
        return Collections.unmodifiableList(treasuryLog);
    }

    public void logTransaction(String characterName, String playerName, String action, long treasuryAfter) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        TreasuryLogEntry entry = new TreasuryLogEntry(characterName, playerName, action, timestamp, treasuryAfter);
        treasuryLog.add(0, entry);
        while (treasuryLog.size() > 200) {
            treasuryLog.remove(treasuryLog.size() - 1);
        }
        setDirty();
        writeToLogFile(entry);
    }

    private static void writeToLogFile(TreasuryLogEntry entry) {
        try {
            Path logDir = Path.of("logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            Path logFile = logDir.resolve("treasury.log");
            Files.writeString(
                    logFile,
                    entry.toLogFileLine() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            OneManager.LOGGER.error("Failed to write to treasury log file: logs/treasury.log", e);
        }
    }

    // --- Persistence (SavedData 1.21.1) ---

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider registries) {
        return save(compoundTag);
    }

    public CompoundTag save(CompoundTag compoundTag) {
        ListTag playersList = new ListTag();
        for (PlayerCharacterData playerData : players.values()) {
            playersList.add(playerData.toNBT());
        }
        compoundTag.put(KEY_PLAYERS, playersList);
        if (knyazName != null && !knyazName.isEmpty()) {
            compoundTag.putString(KEY_KNYAZ_NAME, knyazName);
        }
        if (knyazUuid != null) {
            compoundTag.putUUID(KEY_KNYAZ_UUID, knyazUuid);
        }
        compoundTag.putInt(KEY_WORLD_LEVEL, worldLevel);
        compoundTag.putLong(KEY_TREASURY, treasury);
        compoundTag.putInt(KEY_TAX_RATE, taxRate);
        compoundTag.putInt(KEY_TAX_TICKS, taxTicksRemaining);
        compoundTag.putInt(KEY_CURRENT_WEEK, currentWeek);
        compoundTag.putInt(KEY_CURRENT_DAY, currentDay);
        compoundTag.putInt(KEY_TOTAL_DAYS, totalDays);
        compoundTag.putInt(KEY_WEEK_TICKS, weekTicksRemaining);
        ListTag advList = new ListTag();
        for (String adv : completedAdvancements) {
            advList.add(StringTag.valueOf(adv));
        }
        compoundTag.put(KEY_COMPLETED_ADVANCEMENTS, advList);

        ListTag logList = new ListTag();
        for (TreasuryLogEntry entry : treasuryLog) {
            logList.add(entry.toNBT());
        }
        compoundTag.put(KEY_TREASURY_LOG, logList);

        ListTag lawsList = new ListTag();
        for (LawEntry law : laws) {
            lawsList.add(law.toNBT());
        }
        compoundTag.put(KEY_LAWS, lawsList);

        return compoundTag;
    }

    public static CharacterWorldData load(CompoundTag compoundTag, HolderLookup.Provider registries) {
        return load(compoundTag);
    }

    public static CharacterWorldData load(CompoundTag compoundTag) {
        CharacterWorldData worldData = new CharacterWorldData();

        if (compoundTag != null) {
            if (compoundTag.contains(KEY_KNYAZ_NAME, Tag.TAG_STRING)) {
                worldData.knyazName = compoundTag.getString(KEY_KNYAZ_NAME);
            }
            if (compoundTag.hasUUID(KEY_KNYAZ_UUID)) {
                worldData.knyazUuid = compoundTag.getUUID(KEY_KNYAZ_UUID);
            }
            if (compoundTag.contains(KEY_WORLD_LEVEL, Tag.TAG_INT)) {
                worldData.worldLevel = compoundTag.getInt(KEY_WORLD_LEVEL);
            }
            if (compoundTag.contains(KEY_TREASURY, Tag.TAG_ANY_NUMERIC)) {
                worldData.treasury = compoundTag.getLong(KEY_TREASURY);
            }
            if (compoundTag.contains(KEY_TAX_RATE, Tag.TAG_ANY_NUMERIC)) {
                worldData.taxRate = compoundTag.getInt(KEY_TAX_RATE);
            }
            if (compoundTag.contains(KEY_TAX_TICKS, Tag.TAG_ANY_NUMERIC)) {
                worldData.taxTicksRemaining = compoundTag.getInt(KEY_TAX_TICKS);
            }
            if (compoundTag.contains(KEY_CURRENT_WEEK, Tag.TAG_ANY_NUMERIC)) {
                worldData.currentWeek = Math.max(1, compoundTag.getInt(KEY_CURRENT_WEEK));
            }
            if (compoundTag.contains(KEY_CURRENT_DAY, Tag.TAG_ANY_NUMERIC)) {
                worldData.currentDay = Math.max(1, Math.min(7, compoundTag.getInt(KEY_CURRENT_DAY)));
            }
            if (compoundTag.contains(KEY_TOTAL_DAYS, Tag.TAG_ANY_NUMERIC)) {
                worldData.totalDays = Math.max(1, compoundTag.getInt(KEY_TOTAL_DAYS));
            }
            if (compoundTag.contains(KEY_WEEK_TICKS, Tag.TAG_ANY_NUMERIC)) {
                worldData.weekTicksRemaining = compoundTag.getInt(KEY_WEEK_TICKS);
            } else {
                worldData.weekTicksRemaining = (7 - worldData.currentDay) * TAX_CYCLE_TICKS + worldData.taxTicksRemaining;
            }
            if (compoundTag.contains(KEY_COMPLETED_ADVANCEMENTS, Tag.TAG_LIST)) {
                ListTag advList = compoundTag.getList(KEY_COMPLETED_ADVANCEMENTS, Tag.TAG_STRING);
                for (int i = 0; i < advList.size(); i++) {
                    worldData.completedAdvancements.add(advList.getString(i));
                }
            }
            if (compoundTag.contains(KEY_TREASURY_LOG, Tag.TAG_LIST)) {
                ListTag logList = compoundTag.getList(KEY_TREASURY_LOG, Tag.TAG_COMPOUND);
                for (int i = 0; i < logList.size(); i++) {
                    worldData.treasuryLog.add(TreasuryLogEntry.fromNBT(logList.getCompound(i)));
                }
            }
            if (compoundTag.contains(KEY_LAWS, Tag.TAG_LIST)) {
                ListTag lawsList = compoundTag.getList(KEY_LAWS, Tag.TAG_COMPOUND);
                for (int i = 0; i < lawsList.size(); i++) {
                    try {
                        worldData.laws.add(LawEntry.fromNBT(lawsList.getCompound(i)));
                    } catch (Exception e) {
                        OneManager.LOGGER.error("Failed to load law entry from tag", e);
                    }
                }
            }

            if (compoundTag.contains(KEY_PLAYERS, Tag.TAG_LIST)) {
                ListTag playersList = compoundTag.getList(KEY_PLAYERS, Tag.TAG_COMPOUND);
                for (int i = 0; i < playersList.size(); i++) {
                    CompoundTag playerTag = playersList.getCompound(i);
                    try {
                        PlayerCharacterData playerData = PlayerCharacterData.fromNBT(playerTag);
                        worldData.players.put(playerData.getPlayerUuid(), playerData);
                    } catch (Exception e) {
                        OneManager.LOGGER.error("Failed to load player character data from tag: {}", playerTag, e);
                    }
                }
            }
        }

        return worldData;
    }
}
