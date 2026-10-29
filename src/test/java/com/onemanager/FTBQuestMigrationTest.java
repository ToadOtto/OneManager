package com.onemanager;

import dev.ftb.mods.ftblibrary.snbt.SNBT;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FTBQuestMigrationTest {

    private static final Path SOURCE_DIR = Paths.get("ftbquests_saves/2026-10-29-23-47-06");
    private static final Path RUN_CONFIG_DIR = Paths.get("run/config/ftbquests/quests");
    private static final Path ROOT_CONFIG_DIR = Paths.get("config/ftbquests/quests");
    private static final Path TESTWORLD_FTBQUESTS = Paths.get("run/saves/testworld/ftbquests");
    private static final Path TESTWORLD_FTBTEAMS = Paths.get("run/saves/testworld/ftbteams");
    private static final Path ASSETS_FTBQUESTS = Paths.get("assets/ftbquests");
    private static final Path ASSETS_FTBTEAMS = Paths.get("assets/ftbteams");

    @Test
    @Order(1)
    public void testPerformMigration() throws IOException {
        assertTrue(Files.isDirectory(SOURCE_DIR), "Source directory must exist: " + SOURCE_DIR);

        // Ensure directories exist
        Files.createDirectories(RUN_CONFIG_DIR.resolve("chapters"));
        Files.createDirectories(ROOT_CONFIG_DIR.resolve("chapters"));
        Files.createDirectories(TESTWORLD_FTBQUESTS);
        Files.createDirectories(TESTWORLD_FTBTEAMS.resolve("party"));
        Files.createDirectories(TESTWORLD_FTBTEAMS.resolve("player"));

        // Remove old dummy chapter suka.snbt if present
        Files.deleteIfExists(RUN_CONFIG_DIR.resolve("chapters/suka.snbt"));
        Files.deleteIfExists(ROOT_CONFIG_DIR.resolve("chapters/suka.snbt"));

        // 1. Migrate chapter_groups.snbt
        Path srcGroups = SOURCE_DIR.resolve("chapter_groups.snbt");
        if (Files.exists(srcGroups)) {
            SNBTCompoundTag groupsTag = SNBT.read(srcGroups);
            assertNotNull(groupsTag, "Failed to read chapter_groups.snbt");
            migrateTag(groupsTag);
            SNBT.write(RUN_CONFIG_DIR.resolve("chapter_groups.snbt"), groupsTag);
            SNBT.write(ROOT_CONFIG_DIR.resolve("chapter_groups.snbt"), groupsTag);
        }

        // 2. Migrate data.snbt
        Path srcData = SOURCE_DIR.resolve("data.snbt");
        if (Files.exists(srcData)) {
            SNBTCompoundTag dataTag = SNBT.read(srcData);
            assertNotNull(dataTag, "Failed to read data.snbt");
            migrateTag(dataTag);
            if (!dataTag.contains("fallback_locale")) {
                dataTag.putString("fallback_locale", "en_us");
            }
            if (!dataTag.contains("verify_on_load")) {
                dataTag.putBoolean("verify_on_load", false);
            }
            if (!dataTag.contains("presets")) {
                dataTag.put("presets", new CompoundTag());
            }
            SNBT.write(RUN_CONFIG_DIR.resolve("data.snbt"), dataTag);
            SNBT.write(ROOT_CONFIG_DIR.resolve("data.snbt"), dataTag);
        }

        // 3. Migrate all chapters
        Path srcChapters = SOURCE_DIR.resolve("chapters");
        List<Path> chapterFiles = new ArrayList<>();
        try (var stream = Files.list(srcChapters)) {
            stream.filter(p -> p.toString().endsWith(".snbt")).forEach(chapterFiles::add);
        }
        assertEquals(26, chapterFiles.size(), "Should find exactly 26 source chapter files");

        for (Path chFile : chapterFiles) {
            SNBTCompoundTag chTag = SNBT.read(chFile);
            assertNotNull(chTag, "Failed to read chapter: " + chFile.getFileName());
            migrateTag(chTag);
            Path destRun = RUN_CONFIG_DIR.resolve("chapters").resolve(chFile.getFileName());
            Path destRoot = ROOT_CONFIG_DIR.resolve("chapters").resolve(chFile.getFileName());
            SNBT.write(destRun, chTag);
            SNBT.write(destRoot, chTag);
        }

        // 4. Migrate saves/progress into run/saves/testworld/
        if (Files.isDirectory(ASSETS_FTBQUESTS)) {
            try (var stream = Files.list(ASSETS_FTBQUESTS)) {
                stream.filter(p -> p.toString().endsWith(".snbt")).forEach(p -> {
                    try {
                        Files.copy(p, TESTWORLD_FTBQUESTS.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

        if (Files.isDirectory(ASSETS_FTBTEAMS)) {
            // ftbteams.snbt
            Path srcTeamsMeta = ASSETS_FTBTEAMS.resolve("ftbteams.snbt");
            if (Files.exists(srcTeamsMeta)) {
                Files.copy(srcTeamsMeta, TESTWORLD_FTBTEAMS.resolve("ftbteams.snbt"), StandardCopyOption.REPLACE_EXISTING);
            }
            // party
            Path srcPartyDir = ASSETS_FTBTEAMS.resolve("party");
            if (Files.isDirectory(srcPartyDir)) {
                try (var stream = Files.list(srcPartyDir)) {
                    stream.filter(p -> p.toString().endsWith(".snbt")).forEach(p -> {
                        try {
                            Files.copy(p, TESTWORLD_FTBTEAMS.resolve("party").resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
            // player
            Path srcPlayerDir = ASSETS_FTBTEAMS.resolve("player");
            if (Files.isDirectory(srcPlayerDir)) {
                try (var stream = Files.list(srcPlayerDir)) {
                    stream.filter(p -> p.toString().endsWith(".snbt")).forEach(p -> {
                        try {
                            Files.copy(p, TESTWORLD_FTBTEAMS.resolve("player").resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }

        // 5. Update dev singleplayer file (380df991-f603-344c-a090-369bad2a924a.snbt)
        // so that the dev player in testworld inherits completed quests, started quests, and completion_count
        Path partyQuestFile = TESTWORLD_FTBQUESTS.resolve("24dda9a7-d01f-4196-962f-6c3c6487694c.snbt");
        Path devQuestFile = TESTWORLD_FTBQUESTS.resolve("380df991-f603-344c-a090-369bad2a924a.snbt");
        if (Files.exists(partyQuestFile)) {
            SNBTCompoundTag partyTag = SNBT.read(partyQuestFile);
            SNBTCompoundTag devTag = Files.exists(devQuestFile) ? SNBT.read(devQuestFile) : new SNBTCompoundTag();
            devTag.putInt("version", 1);
            devTag.putString("uuid", "380df991f603344ca090369bad2a924a");
            devTag.putString("name", "Dev#380df991");
            devTag.putBoolean("lock", false);
            devTag.putBoolean("rewards_blocked", false);
            devTag.put("task_progress", partyTag.getCompound("task_progress"));
            devTag.put("started", partyTag.getCompound("started"));
            devTag.put("completed", partyTag.getCompound("completed"));
            devTag.put("repeatable", partyTag.getCompound("repeatable"));
            devTag.put("completion_count", partyTag.getCompound("completion_count"));
            devTag.put("claimed_rewards", partyTag.getCompound("claimed_rewards"));

            CompoundTag playerData = devTag.getCompound("player_data");
            CompoundTag devPlayerData = new CompoundTag();
            devPlayerData.putBoolean("can_edit", true);
            devPlayerData.putBoolean("chapter_pinned", true);
            playerData.put("380df991f603344ca090369bad2a924a", devPlayerData);
            devTag.put("player_data", playerData);

            SNBT.write(devQuestFile, devTag);
        }

        // 6. Verify migrated files
        try (var stream = Files.list(RUN_CONFIG_DIR.resolve("chapters"))) {
            long count = stream.filter(p -> p.toString().endsWith(".snbt")).count();
            assertEquals(26, count, "RUN_CONFIG_DIR must contain 26 migrated chapter files");
        }
        try (var stream = Files.list(ROOT_CONFIG_DIR.resolve("chapters"))) {
            long count = stream.filter(p -> p.toString().endsWith(".snbt")).count();
            assertEquals(26, count, "ROOT_CONFIG_DIR must contain 26 migrated chapter files");
        }
    }

    @Test
    @Order(2)
    public void testValidateMigratedQuests() throws IOException {
        Path chaptersDir = RUN_CONFIG_DIR.resolve("chapters");
        assertTrue(Files.isDirectory(chaptersDir), "RUN chapters dir must exist");

        List<Path> chapterFiles = new ArrayList<>();
        try (var stream = Files.list(chaptersDir)) {
            stream.filter(p -> p.toString().endsWith(".snbt")).forEach(chapterFiles::add);
        }
        assertEquals(26, chapterFiles.size());

        for (Path ch : chapterFiles) {
            String text = Files.readString(ch);
            assertFalse(text.contains("Count:"), "Migrated chapter " + ch.getFileName() + " must not contain 'Count:'");
            assertFalse(text.contains("tag: {"), "Migrated chapter " + ch.getFileName() + " must not contain 'tag: {'");
            assertFalse(text.contains("tag:{"), "Migrated chapter " + ch.getFileName() + " must not contain 'tag:{'");

            // Must parse successfully via SNBT parser
            SNBTCompoundTag tag = SNBT.read(ch);
            assertNotNull(tag, "Failed to parse migrated chapter: " + ch.getFileName());
            assertFalse(tag.getString("id").isEmpty(), "Chapter must have id");
        }

        // Validate data.snbt
        Path dataFile = RUN_CONFIG_DIR.resolve("data.snbt");
        assertTrue(Files.exists(dataFile));
        String dataText = Files.readString(dataFile);
        assertFalse(dataText.contains("Count:"), "data.snbt must not contain 'Count:'");
        assertFalse(dataText.contains("tag: {"), "data.snbt must not contain 'tag: {'");
        SNBTCompoundTag dataTag = SNBT.read(dataFile);
        assertNotNull(dataTag);
        assertEquals(13, dataTag.getInt("version"));
        assertEquals("circle", dataTag.getString("default_quest_shape"));
    }

    private static void migrateTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            migrateCompound(compound);
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                migrateTag(list.get(i));
            }
        }
    }

    private static void migrateCompound(CompoundTag compound) {
        // If this compound represents an item (has "id" and ("Count", "count", or "tag"))
        if (compound.contains("id", Tag.TAG_STRING) && (compound.contains("Count") || compound.contains("tag") || compound.contains("count"))) {
            // Handle Count -> count
            if (compound.contains("Count")) {
                int count = compound.getInt("Count");
                compound.remove("Count");
                compound.putInt("count", count);
            }

            // Handle tag -> components
            if (compound.contains("tag", Tag.TAG_COMPOUND)) {
                CompoundTag oldTag = compound.getCompound("tag");
                CompoundTag components = compound.contains("components", Tag.TAG_COMPOUND)
                        ? compound.getCompound("components")
                        : new CompoundTag();

                // 1. Icon on ftbquests:custom_icon -> ftbquests:icon
                if (oldTag.contains("Icon", Tag.TAG_STRING)) {
                    components.putString("ftbquests:icon", oldTag.getString("Icon"));
                    oldTag.remove("Icon");
                }

                // 2. Damage -> minecraft:damage
                if (oldTag.contains("Damage")) {
                    components.putInt("minecraft:damage", oldTag.getInt("Damage"));
                    oldTag.remove("Damage");
                }

                // 3. Enchantments
                if (oldTag.contains("Enchantments", Tag.TAG_LIST)) {
                    ListTag enchList = oldTag.getList("Enchantments", Tag.TAG_COMPOUND);
                    if (enchList.isEmpty() || (enchList.size() == 1 && enchList.getCompound(0).isEmpty())) {
                        components.putBoolean("minecraft:enchantment_glint_override", true);
                    } else {
                        CompoundTag enchantsTag = new CompoundTag();
                        CompoundTag levelsTag = new CompoundTag();
                        for (int i = 0; i < enchList.size(); i++) {
                            CompoundTag ench = enchList.getCompound(i);
                            String enchId = ench.getString("id");
                            int lvl = ench.getInt("lvl");
                            if (!enchId.isEmpty()) {
                                levelsTag.putInt(enchId, lvl);
                            }
                        }
                        if (!levelsTag.isEmpty()) {
                            enchantsTag.put("levels", levelsTag);
                            components.put("minecraft:enchantments", enchantsTag);
                        } else {
                            components.putBoolean("minecraft:enchantment_glint_override", true);
                        }
                    }
                    oldTag.remove("Enchantments");
                }

                // 4. StoredEnchantments
                if (oldTag.contains("StoredEnchantments", Tag.TAG_LIST)) {
                    ListTag storedList = oldTag.getList("StoredEnchantments", Tag.TAG_COMPOUND);
                    CompoundTag storedTag = new CompoundTag();
                    CompoundTag levelsTag = new CompoundTag();
                    for (int i = 0; i < storedList.size(); i++) {
                        CompoundTag ench = storedList.getCompound(i);
                        String enchId = ench.getString("id");
                        int lvl = ench.getInt("lvl");
                        if (!enchId.isEmpty()) {
                            levelsTag.putInt(enchId, lvl);
                        }
                    }
                    storedTag.put("levels", levelsTag);
                    components.put("minecraft:stored_enchantments", storedTag);
                    oldTag.remove("StoredEnchantments");
                }

                // 5. display: Name and Lore
                if (oldTag.contains("display", Tag.TAG_COMPOUND)) {
                    CompoundTag display = oldTag.getCompound("display");
                    if (display.contains("Name", Tag.TAG_STRING)) {
                        components.putString("minecraft:custom_name", display.getString("Name"));
                    }
                    if (display.contains("Lore", Tag.TAG_LIST)) {
                        components.put("minecraft:lore", display.getList("Lore", Tag.TAG_STRING));
                    }
                    oldTag.remove("display");
                }

                // 6. Potion
                if (oldTag.contains("Potion", Tag.TAG_STRING)) {
                    CompoundTag potionTag = new CompoundTag();
                    potionTag.putString("potion", oldTag.getString("Potion"));
                    components.put("minecraft:potion_contents", potionTag);
                    oldTag.remove("Potion");
                }

                // 7. Custom Data for any mod-specific tags left
                if (!oldTag.isEmpty()) {
                    CompoundTag customData = components.contains("minecraft:custom_data", Tag.TAG_COMPOUND)
                            ? components.getCompound("minecraft:custom_data")
                            : new CompoundTag();
                    for (String k : oldTag.getAllKeys()) {
                        customData.put(k, oldTag.get(k));
                    }
                    components.put("minecraft:custom_data", customData);
                }

                compound.remove("tag");
                if (!components.isEmpty()) {
                    compound.put("components", components);
                }
            }
        }

        // Recursively process all children
        for (String key : new ArrayList<>(compound.getAllKeys())) {
            migrateTag(compound.get(key));
        }
    }
}
