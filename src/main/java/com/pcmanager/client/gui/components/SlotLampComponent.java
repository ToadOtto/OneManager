package com.pcmanager.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;

/**
 * An owo-ui component for miniature square status lamps (e.g. profession slot indicators).
 */
public class SlotLampComponent extends BaseComponent {
    private final boolean lit;
    private final int litColor;
    private final int unlitColor;
    private final int outlineColor;
    private final int size;

    public SlotLampComponent(boolean lit, int litColor, int unlitColor, int outlineColor, int size, Component tooltip) {
        this.lit = lit;
        this.litColor = litColor;
        this.unlitColor = unlitColor;
        this.outlineColor = outlineColor;
        this.size = size;
        this.sizing(Sizing.fixed(size), Sizing.fixed(size));
        if (tooltip != null) {
            this.tooltip(tooltip);
        }
    }

    public static SlotLampComponent primaryFree(boolean free, int size, Component tooltip) {
        // Green lamp when free, dark when occupied (Main professions)
        return new SlotLampComponent(free, 0xFF4CAF50, 0xFF2A2D33, free ? 0xFF66BB6A : 0xFF444852, size, tooltip);
    }

    public static SlotLampComponent secondaryFree(boolean free, int size, Component tooltip) {
        // Orange lamp when free, dark when occupied (Secondary professions)
        return new SlotLampComponent(free, 0xFFFFA500, 0xFF2A2D33, free ? 0xFFFFB74D : 0xFF444852, size, tooltip);
    }

    public static SlotLampComponent occupant(boolean occupied, int activeColor, int size, Component tooltip) {
        // Card lamp: glowing when spot occupied, dark when available
        return new SlotLampComponent(occupied, activeColor, 0xFF252830, occupied ? 0xFFFFFFFF : 0xFF404550, size, tooltip);
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        int fill = lit ? litColor : unlitColor;
        context.fill(this.x, this.y, this.x + size, this.y + size, fill);
        context.renderOutline(this.x, this.y, size, size, outlineColor);
    }
}
