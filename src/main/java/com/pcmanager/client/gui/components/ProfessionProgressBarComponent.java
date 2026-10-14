package com.pcmanager.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Visual progress bar component for profession mastery (0 - 100%).
 * Renders a gray background track with a white filled progress line,
 * a distinct vertical notch marker at the current percentage, and
 * a percentage text label on the right: (- - - | - - - - - - - 25%)
 */
public class ProfessionProgressBarComponent extends BaseComponent {
    private final int progress;

    public ProfessionProgressBarComponent(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        this.sizing(Sizing.fill(92), Sizing.fixed(14));
        this.tooltip(Component.literal("Прогресс профессии: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(this.progress + "%").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("\n0% — Новичок  •  100% — Мастер").withStyle(ChatFormatting.DARK_GRAY)));
    }

    public int getProgress() {
        return progress;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = Minecraft.getInstance().font;
        String percentText = progress + "%";
        int textWidth = font.width(percentText);

        int totalWidth = this.width();
        int totalHeight = this.height();

        // Right side: Percentage label
        int textX = this.x() + totalWidth - textWidth;
        int textY = this.y() + (totalHeight - font.lineHeight) / 2;

        // Left side: Progress track
        int trackX = this.x();
        int trackHeight = 3;
        int trackY = this.y() + (totalHeight - trackHeight) / 2;
        int trackWidth = Math.max(8, textX - trackX - 6);

        // 1. Background gray track (- - - - - - -)
        int grayTrackColor = 0xFF3D4452;
        context.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, grayTrackColor);

        // 2. White filled progress line (- - -)
        int filledWidth = (int) Math.round(trackWidth * (progress / 100.0));
        if (filledWidth > 0) {
            context.fill(trackX, trackY, trackX + filledWidth, trackY + trackHeight, 0xFFFFFFFF);
        }

        // 3. Distinct vertical notch marker (|) at current percentage
        int markerX = trackX + filledWidth;
        int markerHeight = 9;
        int markerY = trackY + (trackHeight / 2) - (markerHeight / 2);
        context.fill(markerX - 1, markerY, markerX + 1, markerY + markerHeight, 0xFFFFFFFF);

        // 4. Render percent text label
        context.drawString(font, percentText, textX, textY, 0xFFE0E5EC, false);
    }
}
