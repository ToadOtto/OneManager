package com.onemanager.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Helper utility for playing standard Minecraft UI sounds across all owo-ui custom screens and components.
 */
public final class UiSoundHelper {

    private UiSoundHelper() {}

    /**
     * Plays the standard Minecraft UI button click sound from the options/settings menu.
     * Equivalent to vanilla AbstractWidget.playDownSound.
     */
    public static void playButtonClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
