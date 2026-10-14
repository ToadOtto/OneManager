package com.pcmanager.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Encapsulates all character and lineage data for a specific player (by UUID).
 */
public class PlayerCharacterData {
    public static final String KEY_UUID = "uuid";
    public static final String KEY_PROFESSIONS = "professions";
    public static final String KEY_LINEAGE = "lineage";
    public static final String KEY_FACTION = "faction";
    public static final String KEY_LICENSES = "licenses";
    public static final String KEY_ACTIVE_CHARACTER = "activeCharacter";
    public static final String KEY_CHARACTER_HISTORY = "characterHistory";
    public static final String KEY_PLAYER_NAME = "playerName";
    public static final String KEY_PENDING_REMAINS = "pendingRemains";
    public static final String KEY_STATE_BRANDS = "stateBrands";
    public static final String KEY_MAIN_PROFESSION_SLOTS = "mainProfSlots";
    public static final String KEY_SECONDARY_PROFESSION_SLOTS = "secProfSlots";
    public static final String KEY_BALANCE = "balance";
    public static final String KEY_TAX_DEBT = "taxDebt";
    public static final String KEY_TAX_STATUS = "taxStatus";
    public static final String KEY_BRAND_TIER = "brandTier";
    public static final String KEY_BRAND_PROGRESS = "brandProgress";
    public static final String KEY_LAST_BRAND_RESET = "lastBrandReset";
    public static final String KEY_LEFT_MAIN_CYCLE = "leftMainCycle";
    public static final String KEY_LEFT_SEC_CYCLE = "leftSecCycle";
    public static final String KEY_MAILBOX = "mailbox";
    public static final String KEY_PROFESSION_PROGRESS = "professionProgress";
    public static final String KEY_ONLINE_BRAND_TICKS = "onlineBrandTicks";

    public static final int[] BRAND_PROGRESSION = {250, 500, 1000, 2000, 4000, 8000, 16000};
    public static final int BRAND_CYCLE_TICKS = 168_000; // 140 minutes in ticks (7 minecraft days)
    public static final long BRAND_RESET_INTERVAL_MS = 140 * 60 * 1000L; // 140 minutes = 7 minecraft days = 2h 20m (Long Cycle)

    private final UUID playerUuid;
    private String lastKnownPlayerName = "";
    private final Set<String> professions = new LinkedHashSet<>();
    private final Map<String, Integer> professionProgress = new LinkedHashMap<>();
    private String lineage = "";
    private String faction = "";
    private final Set<String> licenses = new LinkedHashSet<>();
    private ActiveCharacter activeCharacter;
    private final List<DeadCharacter> characterHistory = new ArrayList<>();
    private final List<CompoundTag> pendingRemains = new ArrayList<>();
    private final List<MailEntry> mailbox = new ArrayList<>();
    private int stateBrands = 0;
    private int mainProfessionSlots = 1;
    private int secondaryProfessionSlots = 2;
    private long balance = 0L;
    private long taxStatus = 0L;
    private int brandTier = 0;
    private long brandProgress = 0L;
    private long lastBrandResetTime = 0L;
    private int onlineBrandTicks = 0;
    private boolean leftMainThisCycle = false;
    private boolean leftSecondaryThisCycle = false;

    public PlayerCharacterData(UUID playerUuid) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "Player UUID cannot be null");
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getLastKnownPlayerName() {
        return lastKnownPlayerName != null && !lastKnownPlayerName.isEmpty() ? lastKnownPlayerName : playerUuid.toString().substring(0, 8);
    }

    public void setLastKnownPlayerName(String lastKnownPlayerName) {
        this.lastKnownPlayerName = lastKnownPlayerName != null ? lastKnownPlayerName : "";
    }

    // --- Professions ---

    public Set<String> getProfessions() {
        return Collections.unmodifiableSet(professions);
    }

    public void setProfessions(Collection<String> newProfessions) {
        this.professions.clear();
        if (newProfessions != null) {
            for (String prof : newProfessions) {
                if (prof != null && !prof.trim().isEmpty()) {
                    this.professions.add(prof.trim());
                }
            }
        }
    }

    public boolean addProfession(String profession) {
        if (profession != null && !profession.trim().isEmpty()) {
            return this.professions.add(profession.trim());
        }
        return false;
    }

    public boolean removeProfession(String profession) {
        if (profession != null) {
            return this.professions.remove(profession.trim());
        }
        return false;
    }

    public boolean hasProfession(String profession) {
        if (profession == null) return false;
        return this.professions.contains(profession.trim());
    }

    public void clearProfessions() {
        this.professions.clear();
    }

    public int getProfessionProgress(String profession) {
        if (profession == null) return 0;
        String key = profession.trim().toLowerCase();
        if (professionProgress.containsKey(key)) {
            return professionProgress.get(key);
        }
        return 25; // Наглядное значение по умолчанию (25%) согласно ТЗ, если прогресс ещё не был задан
    }

    public void setProfessionProgress(String profession, int progress) {
        if (profession == null) return;
        int clamped = Math.max(0, Math.min(100, progress));
        this.professionProgress.put(profession.trim().toLowerCase(), clamped);
    }

    public Map<String, Integer> getAllProfessionProgress() {
        return Collections.unmodifiableMap(professionProgress);
    }

    // --- Lineage ---

    public String getLineage() {
        return getFaction();
    }

    public void setLineage(String lineage) {
        this.lineage = lineage != null ? lineage : "";
        if (this.faction == null || this.faction.isEmpty()) {
            this.faction = this.lineage;
        }
    }

    // --- Faction ---

    public String getFaction() {
        if (faction != null && !faction.isEmpty()) {
            return faction;
        }
        return lineage != null ? lineage : "";
    }

    public void setFaction(String faction) {
        this.faction = faction != null ? faction : "";
        this.lineage = this.faction;
    }

    // --- Licenses ---

    public Set<String> getLicenses() {
        return Collections.unmodifiableSet(licenses);
    }

    public void setLicenses(Collection<String> newLicenses) {
        this.licenses.clear();
        if (newLicenses != null) {
            for (String lic : newLicenses) {
                if (lic != null && !lic.trim().isEmpty()) {
                    this.licenses.add(lic.trim());
                }
            }
        }
    }

    public boolean addLicense(String license) {
        if (license != null && !license.trim().isEmpty()) {
            return this.licenses.add(license.trim());
        }
        return false;
    }

    public boolean removeLicense(String license) {
        if (license != null) {
            return this.licenses.remove(license.trim());
        }
        return false;
    }

    public boolean hasLicense(String license) {
        if (license == null) return false;
        return this.licenses.contains(license.trim());
    }

    public void clearLicenses() {
        this.licenses.clear();
    }

    // --- Active Character ---

    public boolean hasActiveCharacter() {
        return activeCharacter != null;
    }

    public ActiveCharacter getActiveCharacter() {
        return activeCharacter;
    }

    public void setActiveCharacter(ActiveCharacter activeCharacter) {
        this.activeCharacter = activeCharacter;
    }

    public CompoundTag getActiveCharacterNBT() {
        return activeCharacter != null ? activeCharacter.toNBT() : new CompoundTag();
    }

    public void setActiveCharacterNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            this.activeCharacter = null;
        } else {
            this.activeCharacter = ActiveCharacter.fromNBT(tag);
        }
    }

    // --- Character History ---

    public List<DeadCharacter> getCharacterHistory() {
        return Collections.unmodifiableList(characterHistory);
    }

    public void addDeadCharacter(DeadCharacter deadCharacter) {
        if (deadCharacter != null) {
            this.characterHistory.add(deadCharacter);
        }
    }

    public ListTag getCharacterHistoryNBT() {
        ListTag listTag = new ListTag();
        for (DeadCharacter dead : characterHistory) {
            listTag.add(dead.toNBT());
        }
        return listTag;
    }

    public void setCharacterHistoryNBT(ListTag listTag) {
        this.characterHistory.clear();
        if (listTag != null) {
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag compound = listTag.getCompound(i);
                this.characterHistory.add(DeadCharacter.fromNBT(compound));
            }
        }
    }

    // --- Character Lifecycle Helper ---

    /**
     * Retires the active character upon death, archives it into characterHistory,
     * and clears the active character.
     *
     * @param deathCause The cause or description of death.
     * @param deathDate The timestamp of death in milliseconds.
     * @return The newly created DeadCharacter record, or null if there was no active character.
     */
    public DeadCharacter killActiveCharacter(String deathCause, long deathDate, long deathGameTime) {
        if (this.activeCharacter == null) {
            return null;
        }

        long daysAlive = -1L;
        if (this.activeCharacter.getCreationGameTime() > 0 && deathGameTime >= this.activeCharacter.getCreationGameTime()) {
            daysAlive = (deathGameTime - this.activeCharacter.getCreationGameTime()) / 24000L;
        } else if (this.activeCharacter.getCreationDate() > 0) {
            long elapsed = Math.max(0L, deathDate - this.activeCharacter.getCreationDate());
            daysAlive = elapsed / 1200000L;
        }

        DeadCharacter dead = new DeadCharacter(
                activeCharacter.getName(),
                activeCharacter.getAge(),
                new LinkedHashSet<>(this.professions),
                getFaction(),
                deathDate,
                deathCause,
                false,
                daysAlive,
                activeCharacter.getRace(),
                activeCharacter.getAppearance(),
                activeCharacter.getDescription()
        );

        this.characterHistory.add(dead);
        this.activeCharacter = null;
        return dead;
    }

    public DeadCharacter killActiveCharacter(String deathCause, long deathDate) {
        return killActiveCharacter(deathCause, deathDate, 0L);
    }

    public DeadCharacter killActiveCharacter(String deathCause) {
        return killActiveCharacter(deathCause, System.currentTimeMillis(), 0L);
    }

    public List<CompoundTag> getPendingRemains() {
        return Collections.unmodifiableList(pendingRemains);
    }

    public void addPendingRemains(CompoundTag tag) {
        if (tag != null) {
            this.pendingRemains.add(tag);
        }
    }

    public void clearPendingRemains() {
        this.pendingRemains.clear();
    }

    // --- Mailbox (Письма) ---

    public List<MailEntry> getMailbox() {
        return Collections.unmodifiableList(mailbox);
    }

    public void addMail(MailEntry entry) {
        if (entry != null) {
            this.mailbox.add(0, entry);
        }
    }

    public MailEntry getMail(UUID id) {
        if (id == null) return null;
        for (MailEntry entry : mailbox) {
            if (id.equals(entry.getId())) {
                return entry;
            }
        }
        return null;
    }

    public boolean removeMail(UUID id) {
        if (id == null) return false;
        return this.mailbox.removeIf(entry -> id.equals(entry.getId()));
    }

    // --- State Brands (Гос. Марки) ---

    public int getStateBrands() {
        return stateBrands;
    }

    public void setStateBrands(int stateBrands) {
        this.stateBrands = Math.max(0, stateBrands);
    }

    public void addStateBrands(int amount) {
        if (amount > 0) {
            this.stateBrands = Math.max(0, this.stateBrands + amount);
        }
    }

    public int removeStateBrands(int amount) {
        if (amount <= 0) return 0;
        int toRemove = Math.min(this.stateBrands, amount);
        this.stateBrands -= toRemove;
        return toRemove;
    }

    // --- Profession Slots ---

    public int getMainProfessionSlots() {
        return mainProfessionSlots;
    }

    public void setMainProfessionSlots(int mainProfessionSlots) {
        this.mainProfessionSlots = Math.max(0, mainProfessionSlots);
    }

    public void addMainProfessionSlots(int amount) {
        this.mainProfessionSlots = Math.max(0, this.mainProfessionSlots + amount);
    }

    public int getSecondaryProfessionSlots() {
        return secondaryProfessionSlots;
    }

    public void setSecondaryProfessionSlots(int secondaryProfessionSlots) {
        this.secondaryProfessionSlots = Math.max(0, secondaryProfessionSlots);
    }

    public void addSecondaryProfessionSlots(int amount) {
        this.secondaryProfessionSlots = Math.max(0, this.secondaryProfessionSlots + amount);
    }

    // --- Economy & Taxes ---

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = Math.max(0L, balance);
    }

    public void addBalance(long amount) {
        setBalance(this.balance + amount);
    }

    public long removeBalance(long amount) {
        long actual = Math.min(this.balance, Math.max(0L, amount));
        setBalance(this.balance - actual);
        return actual;
    }

    // --- Tax Status ---

    public long getTaxStatus() {
        return taxStatus;
    }

    public void setTaxStatus(long taxStatus) {
        this.taxStatus = taxStatus;
    }

    public void decreaseTaxStatus(long amount) {
        this.taxStatus -= amount;
    }

    public void increaseTaxStatus(long amount, long maxStatus) {
        this.taxStatus = Math.min(maxStatus, this.taxStatus + amount);
    }

    public long getTaxDebt() {
        return Math.max(0L, -taxStatus);
    }

    // --- State Brand Treasury Progression ---

    public int getBrandTier() {
        checkBrandReset();
        return brandTier;
    }

    public long getBrandProgress() {
        checkBrandReset();
        return brandProgress;
    }

    public long getLastBrandResetTime() {
        return lastBrandResetTime;
    }

    public void setLastBrandResetTime(long lastBrandResetTime) {
        this.lastBrandResetTime = lastBrandResetTime;
    }

    public int triggerBrandCycleReset(long now, boolean grantMarks) {
        if (lastBrandResetTime == 0) {
            lastBrandResetTime = now;
            return 0;
        } else if (now - lastBrandResetTime >= BRAND_RESET_INTERVAL_MS) {
            long cyclesPassed = (now - lastBrandResetTime) / BRAND_RESET_INTERVAL_MS;
            lastBrandResetTime += cyclesPassed * BRAND_RESET_INTERVAL_MS;
            brandTier = 0;
            brandProgress = 0;
            leftMainThisCycle = false;
            leftSecondaryThisCycle = false;

            if (grantMarks) {
                int marks = this.professions.size() * (int) cyclesPassed;
                if (marks > 0) {
                    this.addStateBrands(marks);
                    return marks;
                }
            }
        }
        return 0;
    }

    public void checkBrandReset() {
        triggerBrandCycleReset(System.currentTimeMillis(), false);
    }

    public boolean hasLeftMainThisCycle() {
        checkBrandReset();
        return leftMainThisCycle;
    }

    public void setLeftMainThisCycle(boolean leftMainThisCycle) {
        this.leftMainThisCycle = leftMainThisCycle;
    }

    public boolean hasLeftSecondaryThisCycle() {
        checkBrandReset();
        return leftSecondaryThisCycle;
    }

    public void setLeftSecondaryThisCycle(boolean leftSecondaryThisCycle) {
        this.leftSecondaryThisCycle = leftSecondaryThisCycle;
    }

    public int getOnlineBrandTicks() {
        return onlineBrandTicks;
    }

    public void setOnlineBrandTicks(int onlineBrandTicks) {
        this.onlineBrandTicks = Math.max(0, onlineBrandTicks);
    }

    /**
     * Advances the brand timer only while the player is in the world (online).
     * When reaching BRAND_CYCLE_TICKS (168 000 ticks = 140 min), resets cycle cooldowns
     * and grants State Brands based on the number of currently occupied professions.
     *
     * @param ticks Ticks to advance.
     * @return Number of State Brands granted, or 0 if cycle did not complete.
     */
    public int tickOnlineBrand(int ticks) {
        if (ticks <= 0) return 0;
        this.onlineBrandTicks += ticks;
        if (this.onlineBrandTicks >= BRAND_CYCLE_TICKS) {
            int cycles = this.onlineBrandTicks / BRAND_CYCLE_TICKS;
            this.onlineBrandTicks %= BRAND_CYCLE_TICKS;
            this.brandTier = 0;
            this.brandProgress = 0;
            this.leftMainThisCycle = false;
            this.leftSecondaryThisCycle = false;
            this.lastBrandResetTime = System.currentTimeMillis();

            int marks = this.professions.size() * cycles;
            if (marks > 0) {
                this.addStateBrands(marks);
                return marks;
            }
        }
        return 0;
    }

    public long getLongCycleRemainingMs() {
        if (onlineBrandTicks > 0) {
            long remTicks = Math.max(0L, BRAND_CYCLE_TICKS - onlineBrandTicks);
            return remTicks * 50L;
        }
        if (lastBrandResetTime == 0) {
            return BRAND_RESET_INTERVAL_MS;
        }
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - lastBrandResetTime);
        long remainder = elapsed % BRAND_RESET_INTERVAL_MS;
        return BRAND_RESET_INTERVAL_MS - remainder;
    }

    public int getCurrentBrandCost() {
        checkBrandReset();
        return BRAND_PROGRESSION[Math.min(brandTier, BRAND_PROGRESSION.length - 1)];
    }

    public int applyBrandDeposit(long amount) {
        triggerBrandCycleReset(System.currentTimeMillis(), true);
        int marksEarned = 0;
        long remaining = amount;

        while (remaining > 0) {
            int currentCost = getCurrentBrandCost();
            long needed = currentCost - brandProgress;
            if (remaining >= needed) {
                remaining -= needed;
                brandProgress = 0;
                brandTier = Math.min(brandTier + 1, BRAND_PROGRESSION.length - 1);
                marksEarned++;
            } else {
                brandProgress += remaining;
                remaining = 0;
            }
        }
        return marksEarned;
    }

    // --- Serialization ---

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_UUID, playerUuid);
        tag.putString(KEY_PLAYER_NAME, lastKnownPlayerName);

        ListTag profTag = new ListTag();
        for (String prof : professions) {
            profTag.add(StringTag.valueOf(prof));
        }
        tag.put(KEY_PROFESSIONS, profTag);

        tag.putString(KEY_LINEAGE, lineage);
        tag.putString(KEY_FACTION, faction);

        ListTag licenseTag = new ListTag();
        for (String lic : licenses) {
            licenseTag.add(StringTag.valueOf(lic));
        }
        tag.put(KEY_LICENSES, licenseTag);

        if (activeCharacter != null) {
            tag.put(KEY_ACTIVE_CHARACTER, activeCharacter.toNBT());
        }

        ListTag historyTag = new ListTag();
        for (DeadCharacter dead : characterHistory) {
            historyTag.add(dead.toNBT());
        }
        tag.put(KEY_CHARACTER_HISTORY, historyTag);

        ListTag pendingTag = new ListTag();
        for (CompoundTag rem : pendingRemains) {
            pendingTag.add(rem);
        }
        tag.put(KEY_PENDING_REMAINS, pendingTag);

        tag.putInt(KEY_STATE_BRANDS, stateBrands);
        tag.putInt(KEY_MAIN_PROFESSION_SLOTS, mainProfessionSlots);
        tag.putInt(KEY_SECONDARY_PROFESSION_SLOTS, secondaryProfessionSlots);
        tag.putLong(KEY_BALANCE, balance);
        tag.putLong(KEY_TAX_STATUS, taxStatus);
        tag.putInt(KEY_BRAND_TIER, brandTier);
        tag.putLong(KEY_BRAND_PROGRESS, brandProgress);
        tag.putLong(KEY_LAST_BRAND_RESET, lastBrandResetTime);
        tag.putInt(KEY_ONLINE_BRAND_TICKS, onlineBrandTicks);
        tag.putBoolean(KEY_LEFT_MAIN_CYCLE, leftMainThisCycle);
        tag.putBoolean(KEY_LEFT_SEC_CYCLE, leftSecondaryThisCycle);

        ListTag mailList = new ListTag();
        for (MailEntry entry : mailbox) {
            mailList.add(entry.toNBT());
        }
        tag.put(KEY_MAILBOX, mailList);

        CompoundTag progTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : professionProgress.entrySet()) {
            progTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put(KEY_PROFESSION_PROGRESS, progTag);

        return tag;
    }

    public static PlayerCharacterData fromNBT(CompoundTag tag) {
        UUID uuid;
        if (tag.hasUUID(KEY_UUID)) {
            uuid = tag.getUUID(KEY_UUID);
        } else if (tag.contains(KEY_UUID, Tag.TAG_STRING)) {
            uuid = UUID.fromString(tag.getString(KEY_UUID));
        } else {
            throw new IllegalArgumentException("CompoundTag missing valid player UUID");
        }

        PlayerCharacterData data = new PlayerCharacterData(uuid);

        if (tag.contains(KEY_PLAYER_NAME, Tag.TAG_STRING)) {
            data.setLastKnownPlayerName(tag.getString(KEY_PLAYER_NAME));
        }

        if (tag.contains(KEY_PROFESSIONS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_PROFESSIONS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.addProfession(list.getString(i));
            }
        }

        if (tag.contains(KEY_LINEAGE, Tag.TAG_STRING)) {
            data.setLineage(tag.getString(KEY_LINEAGE));
        }

        if (tag.contains(KEY_FACTION, Tag.TAG_STRING)) {
            data.setFaction(tag.getString(KEY_FACTION));
        }

        if (tag.contains(KEY_LICENSES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_LICENSES, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.addLicense(list.getString(i));
            }
        }

        if (tag.contains(KEY_ACTIVE_CHARACTER, Tag.TAG_COMPOUND)) {
            data.setActiveCharacter(ActiveCharacter.fromNBT(tag.getCompound(KEY_ACTIVE_CHARACTER)));
        }

        if (tag.contains(KEY_CHARACTER_HISTORY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_CHARACTER_HISTORY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.addDeadCharacter(DeadCharacter.fromNBT(list.getCompound(i)));
            }
        }

        if (tag.contains(KEY_PENDING_REMAINS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_PENDING_REMAINS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.addPendingRemains(list.getCompound(i));
            }
        }

        if (tag.contains(KEY_STATE_BRANDS, Tag.TAG_INT)) {
            data.setStateBrands(tag.getInt(KEY_STATE_BRANDS));
        }

        if (tag.contains(KEY_MAIN_PROFESSION_SLOTS, Tag.TAG_INT)) {
            data.setMainProfessionSlots(tag.getInt(KEY_MAIN_PROFESSION_SLOTS));
        } else {
            data.setMainProfessionSlots(1);
        }

        if (tag.contains(KEY_SECONDARY_PROFESSION_SLOTS, Tag.TAG_INT)) {
            data.setSecondaryProfessionSlots(tag.getInt(KEY_SECONDARY_PROFESSION_SLOTS));
        } else {
            data.setSecondaryProfessionSlots(2);
        }

        if (tag.contains(KEY_BALANCE, Tag.TAG_ANY_NUMERIC)) {
            data.setBalance(tag.getLong(KEY_BALANCE));
        }

        if (tag.contains(KEY_TAX_STATUS, Tag.TAG_ANY_NUMERIC)) {
            data.setTaxStatus(tag.getLong(KEY_TAX_STATUS));
        } else if (tag.contains(KEY_TAX_DEBT, Tag.TAG_ANY_NUMERIC)) {
            data.setTaxStatus(-tag.getLong(KEY_TAX_DEBT));
        }

        if (tag.contains(KEY_BRAND_TIER, Tag.TAG_INT)) {
            data.brandTier = tag.getInt(KEY_BRAND_TIER);
        }
        if (tag.contains(KEY_BRAND_PROGRESS, Tag.TAG_ANY_NUMERIC)) {
            data.brandProgress = tag.getLong(KEY_BRAND_PROGRESS);
        }
        if (tag.contains(KEY_LAST_BRAND_RESET, Tag.TAG_ANY_NUMERIC)) {
            data.lastBrandResetTime = tag.getLong(KEY_LAST_BRAND_RESET);
        }
        if (tag.contains(KEY_ONLINE_BRAND_TICKS, Tag.TAG_ANY_NUMERIC)) {
            data.onlineBrandTicks = tag.getInt(KEY_ONLINE_BRAND_TICKS);
        }
        if (tag.contains(KEY_LEFT_MAIN_CYCLE, Tag.TAG_BYTE)) {
            data.leftMainThisCycle = tag.getBoolean(KEY_LEFT_MAIN_CYCLE);
        }
        if (tag.contains(KEY_LEFT_SEC_CYCLE, Tag.TAG_BYTE)) {
            data.leftSecondaryThisCycle = tag.getBoolean(KEY_LEFT_SEC_CYCLE);
        }
        if (tag.contains(KEY_MAILBOX, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_MAILBOX, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.mailbox.add(MailEntry.fromNBT(list.getCompound(i)));
            }
        }
        if (tag.contains(KEY_PROFESSION_PROGRESS, Tag.TAG_COMPOUND)) {
            CompoundTag progTag = tag.getCompound(KEY_PROFESSION_PROGRESS);
            for (String key : progTag.getAllKeys()) {
                data.setProfessionProgress(key, progTag.getInt(key));
            }
        }

        return data;
    }
}
