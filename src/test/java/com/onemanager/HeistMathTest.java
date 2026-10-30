package com.onemanager;

import com.onemanager.heist.HeistMath;
import com.onemanager.heist.HeistSession;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HeistMathTest {

    @Test
    public void testNoiseCalculation() {
        assertEquals(0.0, HeistMath.calculateNoise(0, 10000), 0.001);
        assertEquals(10.0, HeistMath.calculateNoise(1000, 10000), 0.001);
        assertEquals(25.0, HeistMath.calculateNoise(2500, 10000), 0.001);
        assertEquals(50.0, HeistMath.calculateNoise(5000, 10000), 0.001);
        assertEquals(100.0, HeistMath.calculateNoise(15000, 10000), 0.001);
        assertEquals(100.0, HeistMath.calculateNoise(500, 0), 0.001);
    }

    @Test
    public void testRiskLevels() {
        assertEquals("Тихо", HeistMath.getRiskInfo(0.0).name());
        assertEquals(ChatFormatting.GREEN, HeistMath.getRiskInfo(14.9).color());

        assertEquals("Шорох", HeistMath.getRiskInfo(15.0).name());
        assertEquals(ChatFormatting.YELLOW, HeistMath.getRiskInfo(39.9).color());

        assertEquals("Грохот", HeistMath.getRiskInfo(40.0).name());
        assertEquals(ChatFormatting.GOLD, HeistMath.getRiskInfo(69.9).color());

        assertEquals("Тревога близко", HeistMath.getRiskInfo(70.0).name());
        assertEquals(ChatFormatting.DARK_RED, HeistMath.getRiskInfo(100.0).color());
    }

    @Test
    public void testTimerFormatting() {
        assertEquals("03:00", HeistMath.formatTimer(3600));
        assertEquals("01:00", HeistMath.formatTimer(1200));
        assertEquals("00:45", HeistMath.formatTimer(900));
        assertEquals("00:05", HeistMath.formatTimer(100));
        assertEquals("00:00", HeistMath.formatTimer(0));
    }

    @Test
    public void testHeistSessionNbtSerialization() {
        UUID thief = UUID.randomUUID();
        HeistSession session = new HeistSession(thief);

        UUID chest1 = UUID.randomUUID();
        UUID chest2 = UUID.randomUUID();
        session.addLootedChest(chest1, 500);
        session.addLootedChest(chest2, 1250);

        assertEquals(1750, session.getStolenAccumulated());
        assertEquals(3600, session.getTicksRemaining());
        assertEquals(2, session.getLootedChestIds().size());

        CompoundTag tag = session.toNBT();
        HeistSession loaded = HeistSession.fromNBT(tag);

        assertEquals(session.getThiefUuid(), loaded.getThiefUuid());
        assertEquals(session.getStolenAccumulated(), loaded.getStolenAccumulated());
        assertEquals(session.getTicksRemaining(), loaded.getTicksRemaining());
        assertEquals(2, loaded.getLootedChestIds().size());
        assertTrue(loaded.getLootedChestIds().contains(chest1));
        assertTrue(loaded.getLootedChestIds().contains(chest2));
    }

    @Test
    public void testHeistSessionTimerCountdown() {
        UUID thief = UUID.randomUUID();
        HeistSession session = new HeistSession(thief, 500, 2, null);

        assertFalse(session.decrementTicks());
        assertTrue(session.decrementTicks());
        assertTrue(session.decrementTicks());
    }
}
