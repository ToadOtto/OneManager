package com.pcmanager.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a deceased character in a player's lineage/history.
 * Stores name, age, active professions at death, lineage, death timestamp, cause of death, and days alive.
 */
public class DeadCharacter {
    public static final String KEY_NAME = "name";
    public static final String KEY_AGE = "age";
    public static final String KEY_PROFESSIONS = "professions";
    public static final String KEY_LINEAGE = "lineage";
    public static final String KEY_FACTION = "faction";
    public static final String KEY_DEATH_DATE = "death_date";
    public static final String KEY_DEATH_CAUSE = "death_cause";
    public static final String KEY_BURIED = "buried";
    public static final String KEY_DAYS_ALIVE = "days_alive";
    public static final String KEY_RACE = "race";
    public static final String KEY_APPEARANCE = "appearance";
    public static final String KEY_DESCRIPTION = "description";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private String name;
    private int age;
    private final Set<String> professions = new LinkedHashSet<>();
    private String lineage;
    private long deathDate;
    private String deathCause;
    private boolean buried;
    private long daysAlive = -1L;
    private String race;
    private String appearance;
    private String description;

    public DeadCharacter() {
        this("", 0, Collections.emptyList(), "", System.currentTimeMillis(), "Unknown", false, -1L, "", "", "");
    }

    public DeadCharacter(String name, int age, Collection<String> professions, String lineage, long deathDate, String deathCause) {
        this(name, age, professions, lineage, deathDate, deathCause, false, -1L, "", "", "");
    }

    public DeadCharacter(String name, int age, Collection<String> professions, String lineage, long deathDate, String deathCause, boolean buried) {
        this(name, age, professions, lineage, deathDate, deathCause, buried, -1L, "", "", "");
    }

    public DeadCharacter(String name, int age, Collection<String> professions, String lineage, long deathDate, String deathCause, boolean buried, long daysAlive) {
        this(name, age, professions, lineage, deathDate, deathCause, buried, daysAlive, "", "", "");
    }

    public DeadCharacter(String name, int age, Collection<String> professions, String lineage, long deathDate, String deathCause, boolean buried, long daysAlive, String race, String appearance, String description) {
        this.name = name != null ? name : "";
        this.age = age;
        if (professions != null) {
            this.professions.addAll(professions);
        }
        this.lineage = lineage != null ? lineage : "";
        this.deathDate = deathDate;
        this.deathCause = deathCause != null ? deathCause : "Unknown";
        this.buried = buried;
        this.daysAlive = daysAlive;
        this.race = race != null ? race : "";
        this.appearance = appearance != null ? appearance : "";
        this.description = description != null ? description : "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Set<String> getProfessions() {
        return Collections.unmodifiableSet(professions);
    }

    public void setProfessions(Collection<String> professions) {
        this.professions.clear();
        if (professions != null) {
            this.professions.addAll(professions);
        }
    }

    public void addProfession(String profession) {
        if (profession != null && !profession.trim().isEmpty()) {
            this.professions.add(profession.trim());
        }
    }

    public String getLineage() {
        return lineage;
    }

    public void setLineage(String lineage) {
        this.lineage = lineage != null ? lineage : "";
    }

    public String getFaction() {
        return lineage != null ? lineage : "";
    }

    public void setFaction(String faction) {
        this.lineage = faction != null ? faction : "";
    }

    public long getDeathDate() {
        return deathDate;
    }

    public void setDeathDate(long deathDate) {
        this.deathDate = deathDate;
    }

    public String getFormattedDeathDate() {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(deathDate));
    }

    public String getDeathCause() {
        return deathCause;
    }

    public void setDeathCause(String deathCause) {
        this.deathCause = deathCause != null ? deathCause : "Unknown";
    }

    public boolean isBuried() {
        return buried;
    }

    public void setBuried(boolean buried) {
        this.buried = buried;
    }

    public long getDaysAlive() {
        return daysAlive;
    }

    public void setDaysAlive(long daysAlive) {
        this.daysAlive = daysAlive;
    }

    public String getRace() {
        return race != null ? race : "";
    }

    public void setRace(String race) {
        this.race = race != null ? race : "";
    }

    public String getAppearance() {
        return appearance != null ? appearance : "";
    }

    public void setAppearance(String appearance) {
        this.appearance = appearance != null ? appearance : "";
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_NAME, name);
        tag.putInt(KEY_AGE, age);

        ListTag professionsTag = new ListTag();
        for (String prof : professions) {
            professionsTag.add(StringTag.valueOf(prof));
        }
        tag.put(KEY_PROFESSIONS, professionsTag);

        tag.putString(KEY_LINEAGE, lineage);
        tag.putString(KEY_FACTION, lineage);
        tag.putLong(KEY_DEATH_DATE, deathDate);
        tag.putString(KEY_DEATH_CAUSE, deathCause);
        tag.putBoolean(KEY_BURIED, buried);
        if (daysAlive >= 0) {
            tag.putLong(KEY_DAYS_ALIVE, daysAlive);
        }
        tag.putString(KEY_RACE, race != null ? race : "");
        tag.putString(KEY_APPEARANCE, appearance != null ? appearance : "");
        tag.putString(KEY_DESCRIPTION, description != null ? description : "");
        return tag;
    }

    public static DeadCharacter fromNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new DeadCharacter();
        }

        String name = tag.getString(KEY_NAME);
        int age = tag.getInt(KEY_AGE);
        String lineage = tag.contains(KEY_FACTION) && !tag.getString(KEY_FACTION).isEmpty()
                ? tag.getString(KEY_FACTION)
                : tag.getString(KEY_LINEAGE);
        long deathDate = tag.contains(KEY_DEATH_DATE) ? tag.getLong(KEY_DEATH_DATE) : System.currentTimeMillis();
        String deathCause = tag.contains(KEY_DEATH_CAUSE) ? tag.getString(KEY_DEATH_CAUSE) : "Unknown";
        boolean buried = tag.contains(KEY_BURIED) ? tag.getBoolean(KEY_BURIED) : false;
        long daysAlive = tag.contains(KEY_DAYS_ALIVE) ? tag.getLong(KEY_DAYS_ALIVE) : -1L;
        String race = tag.getString(KEY_RACE);
        String appearance = tag.getString(KEY_APPEARANCE);
        String description = tag.getString(KEY_DESCRIPTION);

        Set<String> professions = new LinkedHashSet<>();
        if (tag.contains(KEY_PROFESSIONS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_PROFESSIONS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                professions.add(list.getString(i));
            }
        }

        return new DeadCharacter(name, age, professions, lineage, deathDate, deathCause, buried, daysAlive, race, appearance, description);
    }

    public DeadCharacter copy() {
        return new DeadCharacter(name, age, professions, lineage, deathDate, deathCause, buried, daysAlive, race, appearance, description);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeadCharacter that = (DeadCharacter) o;
        return age == that.age &&
                deathDate == that.deathDate &&
                buried == that.buried &&
                daysAlive == that.daysAlive &&
                Objects.equals(name, that.name) &&
                Objects.equals(professions, that.professions) &&
                Objects.equals(lineage, that.lineage) &&
                Objects.equals(deathCause, that.deathCause) &&
                Objects.equals(race, that.race) &&
                Objects.equals(appearance, that.appearance) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, professions, lineage, deathDate, deathCause, buried, daysAlive, race, appearance, description);
    }

    @Override
    public String toString() {
        return "DeadCharacter{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", professions=" + professions +
                ", lineage='" + lineage + '\'' +
                ", deathDate=" + deathDate +
                ", deathCause='" + deathCause + '\'' +
                ", buried=" + buried +
                ", daysAlive=" + daysAlive +
                ", race='" + race + '\'' +
                ", appearance='" + appearance + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
