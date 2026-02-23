package com.ashwake.ashwake.client.render;

import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.world.entity.WorldCoreOrbEntity;
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

public final class WorldCoreOrbRenderer extends EntityRenderer<WorldCoreOrbEntity> {
    private static final ResourceLocation ORB_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "textures/entity/world_core_orb.png");
    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "textures/entity/world_core_glow.png");

    public WorldCoreOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            WorldCoreOrbEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float t = entity.tickCount + partialTick;
        float yawSpin = t * 2.0F;
        float pitchWobble = (float) Math.sin(t * 0.03F) * 15.0F;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yawSpin));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchWobble));
        drawCrossQuads(poseStack, buffer, ORB_TEXTURE, 2.7F, 255, LightTexture.FULL_BRIGHT);

        poseStack.mulPose(Axis.YP.rotationDegrees(-yawSpin * 0.35F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(25.0F));
        drawCrossQuads(poseStack, buffer, GLOW_TEXTURE, 3.35F, 110, LightTexture.FULL_BRIGHT);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void drawCrossQuads(
            PoseStack poseStack,
            MultiBufferSource buffer,
            ResourceLocation texture,
            float radius,
            int alpha,
            int light) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
        drawSingleQuad(poseStack, consumer, radius, alpha, light);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        drawSingleQuad(poseStack, consumer, radius, alpha, light);
        poseStack.popPose();
    }

    private void drawSingleQuad(PoseStack poseStack, VertexConsumer consumer, float radius, int alpha, int light) {
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
    public ResourceLocation getTextureLocation(WorldCoreOrbEntity entity) {
        return ORB_TEXTURE;
    }
}
