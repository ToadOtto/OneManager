package com.onemanager.data;

import com.onemanager.OneManager;
import com.onemanager.block.entity.TreasuryChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

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
    public static final String KEY_TREASURY_CHESTS = "treasury_chests";

    public static final int DEFAULT_TAX_RATE = 10;
    public static final int TAX_CYCLE_TICKS = 24000; // 20 minutes (20 * 60 * 20) = 1 in-game day
    public static final int WEEK_CYCLE_TICKS = 168000; // 140 minutes (7 * 24000) = 1 in-game week

    public record PendingTreasuryReason(int amount, boolean demolition, String coords, String chestType) {}

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
    private final List<TreasuryChestEntry> treasuryChests = new CopyOnWriteArrayList<>();
    private final Map<UUID, PendingTreasuryReason> pendingReasons = new ConcurrentHashMap<>();

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
        return isKnyaz(uuid);
    }

    public boolean canAccessTreasury(UUID uuid) {
        return isKnyaz(uuid) || isTreasurer(uuid);
    }

    // --- Treasury Chests (Физические Хранилища Казны) ---

    public List<TreasuryChestEntry> getTreasuryChests() {
        return Collections.unmodifiableList(treasuryChests);
    }

    public long getTreasuryCapacity() {
        long cap = 0L;
        for (TreasuryChestEntry entry : treasuryChests) {
            cap += entry.getCapacity();
        }
        return cap;
    }

    public long getTreasuryFreeSpace() {
        long space = 0L;
        for (TreasuryChestEntry entry : treasuryChests) {
            space += entry.getFreeSpace();
        }
        return space;
    }

    @Nullable
    public TreasuryChestEntry getTreasuryChestAt(String dim, BlockPos pos) {
        if (pos == null) return null;
        for (TreasuryChestEntry entry : treasuryChests) {
            if (entry.matchesLocation(dim, pos)) {
                return entry;
            }
        }
        return null;
    }

    public void recalculateTreasuryFromChests() {
        long sum = 0L;
        for (TreasuryChestEntry entry : treasuryChests) {
            sum += entry.getAmount();
        }
        this.treasury = sum;
        setDirty();
    }

    public TreasuryChestEntry registerTreasuryChest(String dim, BlockPos pos, boolean large, String placedBy) {
        TreasuryChestEntry existing = getTreasuryChestAt(dim, pos);
        if (existing != null) {
            return existing;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        TreasuryChestEntry entry = new TreasuryChestEntry(UUID.randomUUID(), dim, pos, large, 0L, timestamp, placedBy);
        treasuryChests.add(entry);
        recalculateTreasuryFromChests();
        writeChestsToFile();
        return entry;
    }

    public void updateTreasuryChestAmount(String dim, BlockPos pos, int amount) {
        TreasuryChestEntry entry = getTreasuryChestAt(dim, pos);
        if (entry != null) {
            entry.setAmount(amount);
            recalculateTreasuryFromChests();
            writeChestsToFile();
        }
    }

    public boolean removeTreasuryChestAt(String dim, BlockPos pos) {
        boolean removed = treasuryChests.removeIf(entry -> entry.matchesLocation(dim, pos));
        if (removed) {
            recalculateTreasuryFromChests();
            writeChestsToFile();
        }
        return removed;
    }

    /**
     * Deposits money into placed Treasury Chests in order from oldest (#1) to newest.
     * Updates loaded TreasuryChestBlockEntity instances and recalculates total treasury.
     * Returns actual amount deposited.
     */
    public long depositToChests(@Nullable MinecraftServer server, long amount) {
        if (amount <= 0) return 0L;
        long remaining = amount;
        long totalAdded = 0L;

        for (TreasuryChestEntry entry : treasuryChests) {
            if (remaining <= 0) break;
            long space = entry.getFreeSpace();
            if (space > 0) {
                long add = Math.min(space, remaining);
                entry.setAmount(entry.getAmount() + add);
                remaining -= add;
                totalAdded += add;
                syncLoadedChestBlockEntity(server, entry);
            }
        }

        recalculateTreasuryFromChests();
        writeChestsToFile();
        return totalAdded;
    }

    /**
     * Withdraws money from placed Treasury Chests (used when buying new treasury chests from treasury).
     * Updates loaded TreasuryChestBlockEntity instances and recalculates total treasury.
     * Returns actual amount withdrawn.
     */
    public long withdrawFromChests(@Nullable MinecraftServer server, long amount) {
        if (amount <= 0) return 0L;
        long remaining = amount;
        long totalWithdrawn = 0L;

        for (int i = treasuryChests.size() - 1; i >= 0; i--) {
            if (remaining <= 0) break;
            TreasuryChestEntry entry = treasuryChests.get(i);
            long stored = entry.getAmount();
            if (stored > 0) {
                long take = Math.min(stored, remaining);
                entry.setAmount(stored - take);
                remaining -= take;
                totalWithdrawn += take;
                syncLoadedChestBlockEntity(server, entry);
            }
        }

        recalculateTreasuryFromChests();
        writeChestsToFile();
        return totalWithdrawn;
    }

    private void syncLoadedChestBlockEntity(@Nullable MinecraftServer server, TreasuryChestEntry entry) {
        if (server == null || entry == null) return;
        try {
            ResourceLocation dimLoc = ResourceLocation.tryParse(entry.getDimension());
            ServerLevel targetLevel = null;
            if (dimLoc != null) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimLoc);
                targetLevel = server.getLevel(key);
            }
            if (targetLevel == null) {
                targetLevel = server.overworld();
            }
            if (targetLevel != null && targetLevel.isLoaded(entry.getPos())) {
                BlockEntity be = targetLevel.getBlockEntity(entry.getPos());
                if (be instanceof TreasuryChestBlockEntity chestBe) {
                    chestBe.setStoredAmountFromWorldData((int) entry.getAmount());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void writeChestsToFile() {
        try {
            Path logDir = Path.of("logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            Path chestsFile = logDir.resolve("treasury_chests.txt");
            StringBuilder sb = new StringBuilder();
            sb.append("=== СПИСОК ХРАНИЛИЩ КАЗНЫ ===").append(System.lineSeparator());
            sb.append("Всего в казне: ").append(this.treasury).append(" / ").append(getTreasuryCapacity()).append(" $PR").append(System.lineSeparator());
            sb.append("Количество хранилищ: ").append(treasuryChests.size()).append(System.lineSeparator());
            sb.append("--------------------------------------------------").append(System.lineSeparator());
            for (int i = 0; i < treasuryChests.size(); i++) {
                sb.append(treasuryChests.get(i).toFileLine(i + 1)).append(System.lineSeparator());
            }
            Files.writeString(
                    chestsFile,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception e) {
            OneManager.LOGGER.error("Failed to write treasury chests file: logs/treasury_chests.txt", e);
        }
    }

    public void setPendingTreasuryReason(UUID playerUuid, PendingTreasuryReason pending) {
        if (playerUuid != null && pending != null) {
            pendingReasons.put(playerUuid, pending);
        }
    }

    public boolean completePendingTreasuryReason(ServerPlayer player, String reason) {
        if (player == null) return false;
        PendingTreasuryReason pending = pendingReasons.remove(player.getUUID());
        if (pending == null) return false;

        String cleanReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Не указана";
        PlayerCharacterData pd = getPlayerData(player.getUUID());
        String charName = (pd != null && pd.hasActiveCharacter()) ? pd.getActiveCharacter().getName() : player.getScoreboardName();
        String pName = player.getScoreboardName();
        String formattedAmt = String.format("%,d", pending.amount()).replace(',', ' ');

        String actionDesc;
        if (pending.demolition()) {
            actionDesc = "Снос хранилища (" + pending.chestType() + " [" + pending.coords() + "], выпало " + formattedAmt + " $PR) | Причина: " + cleanReason;
        } else {
            actionDesc = "-" + formattedAmt + " $PR (Списание из сундука казны [" + pending.coords() + "]) | Причина: " + cleanReason;
        }
        logTransaction(charName, pName, actionDesc, this.treasury);
        return true;
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

        ListTag chestsList = new ListTag();
        for (TreasuryChestEntry chest : treasuryChests) {
            chestsList.add(chest.toNBT());
        }
        compoundTag.put(KEY_TREASURY_CHESTS, chestsList);

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
            if (compoundTag.contains(KEY_TREASURY_CHESTS, Tag.TAG_LIST)) {
                ListTag chestsList = compoundTag.getList(KEY_TREASURY_CHESTS, Tag.TAG_COMPOUND);
                for (int i = 0; i < chestsList.size(); i++) {
                    try {
                        worldData.treasuryChests.add(TreasuryChestEntry.fromNBT(chestsList.getCompound(i)));
                    } catch (Exception e) {
                        OneManager.LOGGER.error("Failed to load treasury chest entry from tag", e);
                    }
                }
                if (!worldData.treasuryChests.isEmpty()) {
                    long sum = 0L;
                    for (TreasuryChestEntry c : worldData.treasuryChests) {
                        sum += c.getAmount();
                    }
                    worldData.treasury = sum;
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
