package com.onemanager.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * An owo-ui component for rendering text at a custom scale (e.g. 0.75x) with centering.
 */
public class ScaledLabelComponent extends BaseComponent {
    private final Component text;
    private final float scale;
    private final int color;
    private final boolean centered;

    public ScaledLabelComponent(Component text, float scale, int color, boolean centered) {
        this.text = text != null ? text : Component.empty();
        this.scale = scale > 0 ? scale : 1.0f;
        this.color = color;
        this.centered = centered;
        Font font = Minecraft.getInstance().font;
        int textH = (int) Math.ceil((font != null ? font.lineHeight : 9) * this.scale);
        this.sizing(Sizing.fill(100), Sizing.fixed(textH));
    }

    public static ScaledLabelComponent of(Component text, float scale, int color) {
        return new ScaledLabelComponent(text, scale, color, true);
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = Minecraft.getInstance().font;
        if (font == null) return;

        context.pose().pushPose();
        context.pose().translate(this.x, this.y, 0);
        context.pose().scale(scale, scale, 1.0f);

        if (centered) {
            int unscaledW = font.width(text);
            int targetW = (int) (this.width / scale);
            int xOffset = Math.max(0, (targetW - unscaledW) / 2);
            context.drawString(font, text, xOffset, 0, color, false);
        } else {
            context.drawString(font, text, 0, 0, color, false);
        }

        context.pose().popPose();
    }
}
