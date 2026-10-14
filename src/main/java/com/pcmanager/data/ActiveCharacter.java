package com.pcmanager.data;

import net.minecraft.nbt.CompoundTag;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents the active character of a player.
 * Contains character details such as name, age, creation timestamp, and burden status.
 */
public class ActiveCharacter {
    public static final String KEY_NAME = "name";
    public static final String KEY_AGE = "age";
    public static final String KEY_RACE = "race";
    public static final String KEY_APPEARANCE = "appearance";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_CREATION_DATE = "creation_date";
    public static final String KEY_STATUS_BURDEN = "status_burden";
    public static final String KEY_CREATION_GAME_TIME = "creation_game_time";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private String name;
    private int age;
    private String race;
    private String appearance;
    private String description;
    private long creationDate;
    private boolean statusBurden;
    private long creationGameTime;

    public ActiveCharacter() {
        this("", 18, "", "", "", System.currentTimeMillis(), false, 0L);
    }

    public ActiveCharacter(String name, int age) {
        this(name, age, "", "", "", System.currentTimeMillis(), false, 0L);
    }

    public ActiveCharacter(String name, int age, long creationDate, boolean statusBurden) {
        this(name, age, "", "", "", creationDate, statusBurden, 0L);
    }

    public ActiveCharacter(String name, int age, long creationDate, boolean statusBurden, long creationGameTime) {
        this(name, age, "", "", "", creationDate, statusBurden, creationGameTime);
    }

    public ActiveCharacter(String name, int age, String race, String appearance, String description, long creationDate, boolean statusBurden, long creationGameTime) {
        this.name = name != null ? name : "";
        this.age = age;
        this.race = race != null ? race : "";
        this.appearance = appearance != null ? appearance : "";
        this.description = description != null ? description : "";
        this.creationDate = creationDate;
        this.statusBurden = statusBurden;
        this.creationGameTime = creationGameTime;
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

    public long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(long creationDate) {
        this.creationDate = creationDate;
    }

    public String getFormattedCreationDate() {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(creationDate));
    }

    public boolean isStatusBurden() {
        return statusBurden;
    }

    public void setStatusBurden(boolean statusBurden) {
        this.statusBurden = statusBurden;
    }

    public long getCreationGameTime() {
        return creationGameTime;
    }

    public void setCreationGameTime(long creationGameTime) {
        this.creationGameTime = creationGameTime;
    }

    /**
     * Calculates the number of full Minecraft days passed since character creation.
     * 1 in-game day = 24,000 ticks.
     * If creationGameTime is set (> 0) and currentGameTime >= creationGameTime, uses tick difference.
     * Otherwise falls back to real-time calculation (1 in-game day = 20 real minutes = 1,200,000 ms).
     *
     * @param currentGameTime Current world game time (level.getGameTime()).
     * @return Number of full Minecraft days alive.
     */
    public long getDaysAlive(long currentGameTime) {
        if (this.creationGameTime > 0 && currentGameTime >= this.creationGameTime) {
            return (currentGameTime - this.creationGameTime) / 24000L;
        }
        // Fallback to real-time: 20 minutes = 1,200,000 ms per Minecraft day
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.creationDate);
        return elapsedMillis / 1200000L;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_NAME, name);
        tag.putInt(KEY_AGE, age);
        tag.putString(KEY_RACE, race != null ? race : "");
        tag.putString(KEY_APPEARANCE, appearance != null ? appearance : "");
        tag.putString(KEY_DESCRIPTION, description != null ? description : "");
        tag.putLong(KEY_CREATION_DATE, creationDate);
        tag.putBoolean(KEY_STATUS_BURDEN, statusBurden);
        tag.putLong(KEY_CREATION_GAME_TIME, creationGameTime);
        return tag;
    }

    public static ActiveCharacter fromNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new ActiveCharacter();
        }

        String name = tag.getString(KEY_NAME);
        int age = tag.getInt(KEY_AGE);
        String race = tag.getString(KEY_RACE);
        String appearance = tag.getString(KEY_APPEARANCE);
        String description = tag.getString(KEY_DESCRIPTION);
        long creationDate = tag.contains(KEY_CREATION_DATE) ? tag.getLong(KEY_CREATION_DATE) : System.currentTimeMillis();
        boolean statusBurden = tag.getBoolean(KEY_STATUS_BURDEN);
        long creationGameTime = tag.contains(KEY_CREATION_GAME_TIME) ? tag.getLong(KEY_CREATION_GAME_TIME) : 0L;

        return new ActiveCharacter(name, age, race, appearance, description, creationDate, statusBurden, creationGameTime);
    }

    public ActiveCharacter copy() {
        return new ActiveCharacter(name, age, race, appearance, description, creationDate, statusBurden, creationGameTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActiveCharacter that = (ActiveCharacter) o;
        return age == that.age &&
                creationDate == that.creationDate &&
                statusBurden == that.statusBurden &&
                creationGameTime == that.creationGameTime &&
                Objects.equals(name, that.name) &&
                Objects.equals(race, that.race) &&
                Objects.equals(appearance, that.appearance) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, race, appearance, description, creationDate, statusBurden, creationGameTime);
    }

    @Override
    public String toString() {
        return "ActiveCharacter{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", race='" + race + '\'' +
                ", appearance='" + appearance + '\'' +
                ", description='" + description + '\'' +
                ", creationDate=" + creationDate +
                ", statusBurden=" + statusBurden +
                ", creationGameTime=" + creationGameTime +
                '}';
    }
}
