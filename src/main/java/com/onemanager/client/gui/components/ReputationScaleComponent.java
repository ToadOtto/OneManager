package com.onemanager.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Renders the -100..+100 Knyaz reputation horizontal scale with tick marks,
 * color fill from zero, labels, and an indicator pointer.
 */
public class ReputationScaleComponent extends BaseComponent {
    private int reputation;
    private final int widthPx;
    private final int heightPx;

    public ReputationScaleComponent(int initialReputation, int widthPx, int heightPx) {
        this.reputation = Math.max(-100, Math.min(100, initialReputation));
        this.widthPx = Math.max(160, widthPx);
        this.heightPx = Math.max(20, heightPx);
        this.sizing(Sizing.fixed(this.widthPx), Sizing.fixed(this.heightPx));
        updateTooltip();
    }

    public ReputationScaleComponent reputation(int reputation) {
        this.reputation = Math.max(-100, Math.min(100, reputation));
        updateTooltip();
        return this;
    }

    private void updateTooltip() {
        String sign = reputation > 0 ? "+" : "";
        this.tooltip(Component.literal("Репутация Князя: " + sign + reputation + " (от -100 до +100)"));
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = Minecraft.getInstance().font;
        int x0 = this.x;
        int y0 = this.y;

        int labelMargin = 28; // width reserved on left/right for "-100" and "+100"
        int barLeft = x0 + labelMargin;
        int barRight = x0 + widthPx - labelMargin;
        int barWidth = barRight - barLeft;
        int barY = y0 + 10;
        int barH = 5;

        // 1. Text labels "-100" and "+100"
        if (font != null) {
            context.drawString(font, "-100", x0, barY - 2, 0xFF8A93A6, false);
            int plusW = font.width("+100");
            context.drawString(font, "+100", x0 + widthPx - plusW, barY - 2, 0xFF8A93A6, false);
        }

        // 2. Base track background
        context.fill(barLeft, barY, barRight, barY + barH, 0xFF0D0F14);
        context.renderOutline(barLeft, barY, barWidth, barH, 0xFF282E3D);

        // 3. Center point (reputation = 0)
        int centerX = barLeft + barWidth / 2;

        // 4. Colored fill from center to current value
        int repClamped = Math.max(-100, Math.min(100, reputation));
        int targetX = centerX + (int) ((repClamped / 100.0f) * (barWidth / 2.0f));

        if (repClamped < 0) {
            context.fill(targetX, barY + 1, centerX, barY + barH - 1, 0xFFE53935);
        } else if (repClamped > 0) {
            context.fill(centerX, barY + 1, targetX, barY + barH - 1, 0xFF43A047);
        }

        // 5. Ticks at -100, -75, -50, -25, 0, +25, +50, +75, +100
        int[] ticks = {-100, -75, -50, -25, 0, 25, 50, 75, 100};
        for (int t : ticks) {
            int tx = barLeft + (int) (((t + 100) / 200.0f) * barWidth);
            boolean isCenter = (t == 0);
            int tickColor = isCenter ? 0xFFB0B7C6 : 0xFF3D4658;
            int tickH = isCenter ? 7 : 5;
            int ty1 = barY + (barH - tickH) / 2;
            int ty2 = ty1 + tickH;
            context.fill(tx, ty1, tx + 1, ty2, tickColor);
        }

        // 6. Indicator arrow pointer above the bar
        int pointerColor = repClamped < 0 ? 0xFFFF5252 : (repClamped > 0 ? 0xFF69F0AE : 0xFFFFFFFF);
        int px = targetX;
        int py = barY - 4;

        // Downward pointing triangle (3 rows: 5px, 3px, 1px)
        context.fill(px - 2, py - 2, px + 3, py - 1, pointerColor);
        context.fill(px - 1, py - 1, px + 2, py, pointerColor);
        context.fill(px, py, px + 1, py + 1, pointerColor);
    }
}
