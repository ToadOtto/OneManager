package com.pcmanager.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class KnyazSavedData extends SavedData {
    public static final String DATA_NAME = "monarchcrownmod_knyaz";

    private String knyazName = "";
    @Nullable
    private UUID knyazUUID = null;
    private boolean pendingReturn = false;

    public KnyazSavedData() {
    }

    public static final SavedData.Factory<KnyazSavedData> FACTORY = new SavedData.Factory<>(
            KnyazSavedData::new,
            KnyazSavedData::load,
            null
    );

    public static KnyazSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        KnyazSavedData data = new KnyazSavedData();
        if (tag.contains("KnyazName", Tag.TAG_STRING)) {
            data.knyazName = tag.getString("KnyazName");
        }
        if (tag.hasUUID("KnyazUUID")) {
            data.knyazUUID = tag.getUUID("KnyazUUID");
        }
        if (tag.contains("PendingReturn", Tag.TAG_BYTE)) {
            data.pendingReturn = tag.getBoolean("PendingReturn");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (knyazName != null && !knyazName.isEmpty()) {
            tag.putString("KnyazName", knyazName);
        }
        if (knyazUUID != null) {
            tag.putUUID("KnyazUUID", knyazUUID);
        }
        tag.putBoolean("PendingReturn", pendingReturn);
        return tag;
    }

    public static KnyazSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new KnyazSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public String getKnyazName() {
        return knyazName;
    }

    @Nullable
    public UUID getKnyazUUID() {
        return knyazUUID;
    }

    public boolean hasKnyaz() {
        return knyazName != null && !knyazName.isEmpty();
    }

    public void setKnyaz(String name, @Nullable UUID uuid) {
        this.knyazName = name != null ? name : "";
        this.knyazUUID = uuid;
        this.pendingReturn = false;
        setDirty();
    }

    public void clearKnyaz() {
        this.knyazName = "";
        this.knyazUUID = null;
        this.pendingReturn = false;
        setDirty();
    }

    public boolean isPendingReturn() {
        return pendingReturn;
    }

    public void setPendingReturn(boolean pendingReturn) {
        this.pendingReturn = pendingReturn;
        setDirty();
    }
}
