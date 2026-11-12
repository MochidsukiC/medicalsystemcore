package jp.houlab.mochidsuki.medicalsystemcore.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientTrackingManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

public class RescueTabletBEWLR extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation BASE_MODEL_LOC =
            new ResourceLocation(Medicalsystemcore.MODID, "item/rescue_tablet_base");

    public RescueTabletBEWLR() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();

        BakedModel baseModel = mc.getModelManager().getModel(BASE_MODEL_LOC);

        poseStack.pushPose();

        boolean leftHand = (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

        // ItemRenderer は BEWLR 呼び出し前に translate(-0.5, -0.5, -0.5) を適用済み。
        // しかし builtin/entity にはdisplayトランスフォームが無い。
        // 正しい順序: displayTransform → translate(-0.5) → render
        // よって: 一旦 -0.5 を打ち消し → displayTransform適用 → 再度 -0.5 を適用
        poseStack.translate(0.5F, 0.5F, 0.5F);
        baseModel = baseModel.applyTransform(displayContext, poseStack, leftHand);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        mc.getItemRenderer().renderModelLists(
                baseModel, stack, packedLight, packedOverlay, poseStack, buffer.getBuffer(RenderType.cutout())
        );

        boolean showOverlay = (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.GUI);

        if (showOverlay && ClientTrackingManager.isTracking()) {
            Player player = mc.player;
            RescueData data = ClientTrackingManager.getTrackedData();
            if (player != null && data != null) {
                renderTrackingOverlay(poseStack, buffer, packedLight, player, data);
            }
        }

        poseStack.popPose();
    }

    private void renderTrackingOverlay(PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, Player player, RescueData data) {
        ResourceKey<Level> playerDim = player.level().dimension();
        ResourceKey<Level> targetDim = data.getDimension();
        BlockPos targetPos = data.getLocation();

        boolean isEnd = playerDim == Level.END || targetDim == Level.END;
        boolean crossDimension = !playerDim.equals(targetDim);

        if (isEnd) {
            renderEndWarning(poseStack, buffer, packedLight);
        } else {
            double targetX = targetPos.getX() + 0.5;
            double targetZ = targetPos.getZ() + 0.5;

            if (crossDimension) {
                boolean playerInNether = playerDim == Level.NETHER;
                boolean targetInNether = targetDim == Level.NETHER;

                if (playerInNether && !targetInNether) {
                    targetX /= 8.0;
                    targetZ /= 8.0;
                } else if (!playerInNether && targetInNether) {
                    targetX *= 8.0;
                    targetZ *= 8.0;
                }
            }

            double dx = targetX - player.getX();
            double dz = targetZ - player.getZ();
            float targetAngle = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float playerYaw = player.getYRot();
            float relativeAngle = targetAngle - playerYaw;

            renderArrow(poseStack, buffer, packedLight, relativeAngle);
        }
    }

    private void renderArrow(PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, float angleDeg) {
        poseStack.pushPose();

        // Move to the center of the tablet screen surface
        // The glass layer is at Y=0.75/16, from X=2/16 to X=14/16, Z=4/16 to Z=12/16
        // Center: X=8/16, Z=8/16, Y slightly above the glass
        poseStack.translate(8.0f / 16.0f, 0.76f / 16.0f, 8.0f / 16.0f);

        // Rotate around Y axis by the relative angle
        // Display transform rotates model ~77° around X, so model +Z appears downward on screen.
        // Add 180° to flip the base arrow direction, negate for correct rotation sense.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - angleDeg));

        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        int r = 0, g = 255, b = 100, a = 255;

        // Arrow head (triangle pointing in +Z direction on the screen plane)
        float headLength = 3.0f / 16.0f;
        float headWidth = 2.25f / 16.0f;

        // Triangle: tip at +Z, base at -Z
        consumer.vertex(matrix, 0, 0, headLength).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -headWidth / 2, 0, 0).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, headWidth / 2, 0, 0).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        // Duplicate last vertex for quad format
        consumer.vertex(matrix, headWidth / 2, 0, 0).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();

        // Arrow shaft (rectangle)
        float shaftLength = 2.25f / 16.0f;
        float shaftWidth = 0.6f / 16.0f;

        consumer.vertex(matrix, -shaftWidth / 2, 0, 0).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -shaftWidth / 2, 0, -shaftLength).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, shaftWidth / 2, 0, -shaftLength).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, shaftWidth / 2, 0, 0).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();

        poseStack.popPose();
    }

    private void renderEndWarning(PoseStack poseStack, MultiBufferSource buffer,
                                  int packedLight) {
        poseStack.pushPose();

        poseStack.translate(8.0f / 16.0f, 0.76f / 16.0f, 8.0f / 16.0f);

        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        int r = 255, g = 220, b = 0, a = 255;

        // Warning triangle
        float triSize = 2.5f / 16.0f;

        // Triangle pointing up (+Z)
        consumer.vertex(matrix, 0, 0, triSize * 0.6f).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -triSize / 2, 0, -triSize * 0.4f).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, triSize / 2, 0, -triSize * 0.4f).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, triSize / 2, 0, -triSize * 0.4f).color(r, g, b, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();

        // Exclamation mark: small rectangle in center
        float exclWidth = 0.2f / 16.0f;
        float exclLength = 1.0f / 16.0f;
        float exclOffsetZ = -0.1f / 16.0f;
        int er = 0, eg = 0, eb = 0;

        // Exclamation bar
        consumer.vertex(matrix, -exclWidth, 0.001f, exclOffsetZ + exclLength / 2).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -exclWidth, 0.001f, exclOffsetZ - exclLength / 2).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, exclWidth, 0.001f, exclOffsetZ - exclLength / 2).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, exclWidth, 0.001f, exclOffsetZ + exclLength / 2).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();

        // Exclamation dot
        float dotSize = 0.2f / 16.0f;
        float dotOffset = exclOffsetZ - exclLength / 2 - 0.3f / 16.0f;

        consumer.vertex(matrix, -dotSize, 0.001f, dotOffset + dotSize).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -dotSize, 0.001f, dotOffset - dotSize).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, dotSize, 0.001f, dotOffset - dotSize).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, dotSize, 0.001f, dotOffset + dotSize).color(er, eg, eb, a).uv(0, 0).uv2(packedLight).normal(0, 1, 0).endVertex();

        poseStack.popPose();
    }
}