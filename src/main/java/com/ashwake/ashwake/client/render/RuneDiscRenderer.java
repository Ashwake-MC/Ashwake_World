package com.ashwake.ashwake.client.render;

import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.world.entity.RuneDiscEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class RuneDiscRenderer extends EntityRenderer<RuneDiscEntity> {
    private static final ResourceLocation DISC_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "textures/entity/rune_disc.png");
    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "textures/entity/world_core_glow.png");

    public RuneDiscRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            RuneDiscEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float spin = entity.getSpinDegrees(partialTick);
        int glowAlpha = Mth.clamp((int) (entity.getGlowAlpha(partialTick) * 255.0F), 75, 220);

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        drawDisc(poseStack, buffer, DISC_TEXTURE, 2.55F, 195, LightTexture.FULL_BRIGHT);

        poseStack.mulPose(Axis.ZP.rotationDegrees((-spin * 0.42F) + 18.0F));
        drawDisc(poseStack, buffer, GLOW_TEXTURE, 2.95F, glowAlpha, LightTexture.FULL_BRIGHT);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void drawDisc(
            PoseStack poseStack,
            MultiBufferSource buffer,
            ResourceLocation texture,
            float radius,
            int alpha,
            int light) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose pose = poseStack.last();
        float min = -radius;
        float max = radius;

        addVertex(consumer, pose, min, min, 0.0F, 0.0F, 1.0F, alpha, light);
        addVertex(consumer, pose, max, min, 0.0F, 1.0F, 1.0F, alpha, light);
        addVertex(consumer, pose, max, max, 0.0F, 1.0F, 0.0F, alpha, light);
        addVertex(consumer, pose, min, max, 0.0F, 0.0F, 0.0F, alpha, light);
    }

    private void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int alpha,
            int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(RuneDiscEntity entity) {
        return DISC_TEXTURE;
    }
}
