package com.onemanager.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.resources.ResourceLocation;

/**
 * An owo-ui component for rendering a texture sprite with alpha and color modulation.
 */
public class TextureIconComponent extends BaseComponent {
    private final ResourceLocation texture;
    private final int u, v, regionW, regionH, texW, texH;
    private float r = 1.0f, g = 1.0f, b = 1.0f, alpha = 1.0f;

    public TextureIconComponent(ResourceLocation texture, int size) {
        this(texture, 0, 0, 16, 16, 16, 16, size, size);
    }

    public TextureIconComponent(ResourceLocation texture, int u, int v, int regionW, int regionH, int texW, int texH, int width, int height) {
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.regionW = regionW;
        this.regionH = regionH;
        this.texW = texW;
        this.texH = texH;
        this.sizing(Sizing.fixed(width), Sizing.fixed(height));
    }

    public TextureIconComponent color(float r, float g, float b, float alpha) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.alpha = alpha;
        return this;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, alpha);
        context.blit(texture, this.x, this.y, this.width, this.height, u, v, regionW, regionH, texW, texH);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
