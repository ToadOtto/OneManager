package com.pcmanager.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pcmanager.PCManager;
import com.pcmanager.entity.MemorialSignEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.UUID;

public class MemorialSignRenderer extends EntityRenderer<MemorialSignEntity> {

    public static final ResourceLocation PLAQUE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "textures/entity/memorial_sign.png");

    private final Font font;

    public MemorialSignRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.font = context.getFont();
    }

    @Override
    public ResourceLocation getTextureLocation(MemorialSignEntity entity) {
        return PLAQUE_TEXTURE;
    }

    @Override
    public void render(MemorialSignEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        poseStack.pushPose();

        // Orient plaque flat against the supporting block face
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));

        // 1. Render the 3D Plaque box (sign board dimensions: 0.84 x 0.52 x 0.04)
        renderPlaqueBox(poseStack, buffer, packedLight);

        // 2. Bound vs Unbound content
        if (entity.isBound()) {
            renderBoundContent(entity, poseStack, buffer, packedLight);
        } else {
            renderUnboundContent(poseStack, buffer, packedLight);
        }

        poseStack.popPose();
    }

    private void renderBoundContent(MemorialSignEntity entity, PoseStack poseStack,
                                    MultiBufferSource buffer, int packedLight) {
        // A) Character 2D Head on the left side
        UUID uuid = entity.getOwnerUUID();
        ResourceLocation skin = null;
        if (Minecraft.getInstance().getConnection() != null && uuid != null) {
            PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(uuid);
            if (info != null) {
                skin = info.getSkin().texture();
            }
        }
        if (skin == null && entity.getResolvableProfile() != null) {
            try {
                skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(entity.getResolvableProfile().gameProfile()).texture();
            } catch (Throwable ignored) {
            }
        }
        if (skin == null) {
            skin = uuid != null ? DefaultPlayerSkin.get(uuid).texture() : DefaultPlayerSkin.getDefaultTexture();
        }

        float headSize = 0.28F;
        float hx1 = -0.34F;
        float hx2 = hx1 + headSize; // -0.06F
        float hy1 = -headSize / 2.0F; // -0.14F
        float hy2 = headSize / 2.0F;  // 0.14F

        VertexConsumer headVc = buffer.getBuffer(RenderType.entityTranslucent(skin));

        // Base head face (UV: [8, 8, 16, 16] in 64x64 texture)
        float u1 = 8.0F / 64.0F;
        float v1 = 8.0F / 64.0F;
        float u2 = 16.0F / 64.0F;
        float v2 = 16.0F / 64.0F;
        float zBase = 0.0335F;

        addQuad(headVc, poseStack,
                hx1, hy1, zBase, u1, v2,
                hx2, hy1, zBase, u2, v2,
                hx2, hy2, zBase, u2, v1,
                hx1, hy2, zBase, u1, v1,
                0.0F, 0.0F, 1.0F, packedLight);

        // Outer hat layer (UV: [40, 8, 48, 16] in 64x64 texture)
        float u1Hat = 40.0F / 64.0F;
        float v1Hat = 8.0F / 64.0F;
        float u2Hat = 48.0F / 64.0F;
        float v2Hat = 16.0F / 64.0F;
        float zHat = 0.0345F;

        addQuad(headVc, poseStack,
                hx1, hy1, zHat, u1Hat, v2Hat,
                hx2, hy1, zHat, u2Hat, v2Hat,
                hx2, hy2, zHat, u2Hat, v1Hat,
                hx1, hy2, zHat, u1Hat, v1Hat,
                0.0F, 0.0F, 1.0F, packedLight);

        // B) Character Name on the right side
        String name = entity.getCharName();
        if (name.isEmpty()) name = "Безымянный";

        poseStack.pushPose();
        poseStack.translate(0.16F, 0.05F, 0.0355F);

        int nameW = this.font.width(name);
        float maxNameW = 42.0F;
        float textScale = Math.min(0.0080F, 0.0080F * (maxNameW / Math.max(maxNameW, (float) nameW)));
        poseStack.scale(textScale, -textScale, textScale);

        this.font.drawInBatch(name, -nameW / 2.0F, -4.0F, 0xFFFFD700, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poseStack.popPose();

        // C) Death Date under character name
        String date = "† " + entity.getFormattedDeathDate();
        poseStack.pushPose();
        poseStack.translate(0.16F, -0.05F, 0.0355F);

        int dateW = this.font.width(date);
        float maxDateW = 44.0F;
        float dateScale = Math.min(0.0065F, 0.0065F * (maxDateW / Math.max(maxDateW, (float) dateW)));
        poseStack.scale(dateScale, -dateScale, dateScale);

        this.font.drawInBatch(date, -dateW / 2.0F, -4.0F, 0xFFAAAAAA, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poseStack.popPose();
    }

    private void renderUnboundContent(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Centered inscription when blank
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.04F, 0.0355F);

        String title = "Ритуальная Табличка";
        int titleW = this.font.width(title);
        float s1 = Math.min(0.007F, 0.007F * (62.0F / Math.max(62.0F, (float) titleW)));
        poseStack.scale(s1, -s1, s1);

        this.font.drawInBatch(title, -titleW / 2.0F, -4.0F, 0xFF8E9BA8, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0F, -0.05F, 0.0355F);

        String hint = "Используйте останки";
        int hintW = this.font.width(hint);
        float s2 = Math.min(0.0055F, 0.0055F * (62.0F / Math.max(62.0F, (float) hintW)));
        poseStack.scale(s2, -s2, s2);

        this.font.drawInBatch(hint, -hintW / 2.0F, -4.0F, 0xFF58626E, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poseStack.popPose();
    }

    private void renderPlaqueBox(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float x1 = -0.40625F, x2 = 0.40625F;
        float y1 = -0.28125F, y2 = 0.28125F;
        float z1 = -0.03125F, z2 = 0.03125F;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(PLAQUE_TEXTURE));

        // Front face (Z = z2) - South: [2, 2, 15, 11]
        addQuad(vc, poseStack,
                x1, y1, z2, 2.0F / 16.0F, 11.0F / 16.0F,
                x2, y1, z2, 15.0F / 16.0F, 11.0F / 16.0F,
                x2, y2, z2, 15.0F / 16.0F, 2.0F / 16.0F,
                x1, y2, z2, 2.0F / 16.0F, 2.0F / 16.0F,
                0.0F, 0.0F, 1.0F, packedLight);

        // Back face (Z = z1) - North: [15, 2, 2, 11]
        addQuad(vc, poseStack,
                x2, y1, z1, 15.0F / 16.0F, 11.0F / 16.0F,
                x1, y1, z1, 2.0F / 16.0F, 11.0F / 16.0F,
                x1, y2, z1, 2.0F / 16.0F, 2.0F / 16.0F,
                x2, y2, z1, 15.0F / 16.0F, 2.0F / 16.0F,
                0.0F, 0.0F, -1.0F, packedLight);

        // Top edge (Y = y2) - Up: [2, 2, 15, 3]
        addQuad(vc, poseStack,
                x1, y2, z2, 2.0F / 16.0F, 3.0F / 16.0F,
                x2, y2, z2, 15.0F / 16.0F, 3.0F / 16.0F,
                x2, y2, z1, 15.0F / 16.0F, 2.0F / 16.0F,
                x1, y2, z1, 2.0F / 16.0F, 2.0F / 16.0F,
                0.0F, 1.0F, 0.0F, packedLight);

        // Bottom edge (Y = y1) - Down: [2, 10, 15, 11]
        addQuad(vc, poseStack,
                x1, y1, z1, 2.0F / 16.0F, 10.0F / 16.0F,
                x2, y1, z1, 15.0F / 16.0F, 10.0F / 16.0F,
                x2, y1, z2, 15.0F / 16.0F, 11.0F / 16.0F,
                x1, y1, z2, 2.0F / 16.0F, 11.0F / 16.0F,
                0.0F, -1.0F, 0.0F, packedLight);

        // Left edge (X = x1) - West: [2, 2, 3, 11]
        addQuad(vc, poseStack,
                x1, y1, z1, 2.0F / 16.0F, 11.0F / 16.0F,
                x1, y1, z2, 3.0F / 16.0F, 11.0F / 16.0F,
                x1, y2, z2, 3.0F / 16.0F, 2.0F / 16.0F,
                x1, y2, z1, 2.0F / 16.0F, 2.0F / 16.0F,
                -1.0F, 0.0F, 0.0F, packedLight);

        // Right edge (X = x2) - East: [14, 2, 15, 11]
        addQuad(vc, poseStack,
                x2, y1, z2, 14.0F / 16.0F, 11.0F / 16.0F,
                x2, y1, z1, 15.0F / 16.0F, 11.0F / 16.0F,
                x2, y2, z1, 15.0F / 16.0F, 2.0F / 16.0F,
                x2, y2, z2, 14.0F / 16.0F, 2.0F / 16.0F,
                1.0F, 0.0F, 0.0F, packedLight);
    }

    private void addQuad(VertexConsumer vc, PoseStack poseStack,
                         float x1, float y1, float z1, float u1, float v1,
                         float x2, float y2, float z2, float u2, float v2,
                         float x3, float y3, float z3, float u3, float v3,
                         float x4, float y4, float z4, float u4, float v4,
                         float nx, float ny, float nz, int light) {
        Matrix4f pose = poseStack.last().pose();
        vc.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(poseStack.last(), nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u2, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(poseStack.last(), nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u3, v3)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(poseStack.last(), nx, ny, nz);
        vc.addVertex(pose, x4, y4, z4).setColor(255, 255, 255, 255).setUv(u4, v4)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(poseStack.last(), nx, ny, nz);
    }
}
