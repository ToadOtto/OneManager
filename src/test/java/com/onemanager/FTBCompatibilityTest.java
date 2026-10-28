package com.onemanager;

import dev.ftb.mods.ftblibrary.snbt.SNBT;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class FTBCompatibilityTest {

    private static final Path ASSETS_DIR = Paths.get("assets");
    private static final Path FTBQUESTS_DIR = ASSETS_DIR.resolve("ftbquests");
    private static final Path FTBTEAMS_DIR = ASSETS_DIR.resolve("ftbteams");

    @Test
    public void testFTBQuestsDataCompatibility() throws IOException {
        assertTrue(Files.isDirectory(FTBQUESTS_DIR), "assets/ftbquests directory should exist");

        Path partyQuestFile = FTBQUESTS_DIR.resolve("24dda9a7-d01f-4196-962f-6c3c6487694c.snbt");
        assertTrue(Files.exists(partyQuestFile), "Party quest file should exist");

        // Read using FTB Library SNBT parser
        SNBTCompoundTag tag = SNBT.read(partyQuestFile);
        assertNotNull(tag, "Failed to parse SNBT file using FTBLibrary");

        assertEquals(1, tag.getInt("version"));
        assertEquals("24dda9a7d01f4196962f6c3c6487694c", tag.getString("uuid"));
        assertEquals("oh3#24dda9a7", tag.getString("name"));

        CompoundTag startedTag = tag.getCompound("started");
        assertNotNull(startedTag);
        assertEquals(14, startedTag.getAllKeys().size(), "Party should have 14 started quests");

        CompoundTag completedTag = tag.getCompound("completed");
        assertNotNull(completedTag);
        assertEquals(10, completedTag.getAllKeys().size(), "Party should have 10 completed quests");

        // Verify quest completion timestamps and entries from SNBT
        assertTrue(completedTag.contains("00297257B7F81AA1"));
        assertEquals(1783022453861L, completedTag.getLong("00297257B7F81AA1"));

        // Verify repeatable count
        CompoundTag completionCountTag = tag.getCompound("completion_count");
        assertNotNull(completionCountTag);
        assertEquals(185, completionCountTag.getInt("744B736AEBF08CEF"));

        // Verify player quest files
        Path playerQuest1 = FTBQUESTS_DIR.resolve("19f07d67-2d94-3fe8-8a2d-4a899f47b0d6.snbt");
        assertTrue(Files.exists(playerQuest1));
        SNBTCompoundTag playerTag1 = SNBT.read(playerQuest1);
        assertNotNull(playerTag1);
        assertEquals("stoffer1#19f07d67", playerTag1.getString("name"));

        Path playerQuest2 = FTBQUESTS_DIR.resolve("e5e13c68-482c-11e7-816a-002590a1379b.snbt");
        assertTrue(Files.exists(playerQuest2));
        SNBTCompoundTag playerTag2 = SNBT.read(playerQuest2);
        assertNotNull(playerTag2);
        assertEquals("artpik13#e5e13c68", playerTag2.getString("name"));
    }

    @Test
    public void testFTBTeamsDataCompatibility() throws IOException {
        assertTrue(Files.isDirectory(FTBTEAMS_DIR), "assets/ftbteams directory should exist");

        // Manager metadata
        Path metaFile = FTBTEAMS_DIR.resolve("ftbteams.snbt");
        assertTrue(Files.exists(metaFile));
        SNBTCompoundTag metaTag = SNBT.read(metaFile);
        assertNotNull(metaTag);
        assertEquals("72044c08-8285-462a-ae5f-af491097ab25", metaTag.getString("id"));

        // Party team
        Path partyFile = FTBTEAMS_DIR.resolve("party/24dda9a7-d01f-4196-962f-6c3c6487694c.snbt");
        assertTrue(Files.exists(partyFile));
        SNBTCompoundTag partyTag = SNBT.read(partyFile);
        assertNotNull(partyTag);
        assertEquals("party", partyTag.getString("type"));
        assertEquals("e5e13c68-482c-11e7-816a-002590a1379b", partyTag.getString("owner"));
        assertEquals("oh3", partyTag.getCompound("properties").getString("ftbteams:display_name"));

        // Player teams
        Path player1 = FTBTEAMS_DIR.resolve("player/19f07d67-2d94-3fe8-8a2d-4a899f47b0d6.snbt");
        assertTrue(Files.exists(player1));
        SNBTCompoundTag playerTag1 = SNBT.read(player1);
        assertNotNull(playerTag1);
        assertEquals("player", playerTag1.getString("type"));

        Path player2 = FTBTEAMS_DIR.resolve("player/e5e13c68-482c-11e7-816a-002590a1379b.snbt");
        assertTrue(Files.exists(player2));
        SNBTCompoundTag playerTag2 = SNBT.read(player2);
        assertNotNull(playerTag2);
        assertEquals("player", playerTag2.getString("type"));
    }
}
