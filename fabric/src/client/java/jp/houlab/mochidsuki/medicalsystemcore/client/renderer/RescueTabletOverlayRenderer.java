package jp.houlab.mochidsuki.medicalsystemcore.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientTrackingManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 救急タブレットの追跡オーバーレイ(通報の方角を示す矢印、またはエンド警告)。
 * Forge の RescueTabletBEWLR の「オーバーレイを描く部分」だけを引き継ぐ special model。
 * <p>
 * 26.2 では BEWLR が無く、items JSON の {@code minecraft:special} に置き換わる。タブレット本体(rescue_tablet_base)は
 * 通常のモデルとして items JSON が描き、手と GUI のときだけこのレンダラーを重ねる(Forge の showOverlay に相当。
 * 表示コンテキストの分岐は items JSON の minecraft:select で行う)。
 * special の base に rescue_tablet_base を指定するため、表示変換(左手の反転を含む)と -0.5 の移動は適用済みで渡される。
 * これは Forge の BEWLR が「translate(0.5) → applyTransform → translate(-0.5)」で作っていた座標系と同じ。
 * <p>
 * 何を描くかは submit ではなく extractArgument で決める。GUI のアイテムはモデルの同一性で描画結果がキャッシュされ、
 * 同一性には argument が含まれるため、矢印の角度が変わるたびに argument(値が等しいかで比較される record)が変わるようにする。
 */
public class RescueTabletOverlayRenderer implements SpecialModelRenderer<RescueTabletOverlayRenderer.Overlay> {

    /** 描く内容。endWarning なら警告三角(角度は使わないので 0 に揃える)、そうでなければ relativeAngle の方向の矢印。 */
    public record Overlay(boolean endWarning, float relativeAngle) {
    }

    @Override
    public @Nullable Overlay extractArgument(ItemStack stack) {
        if (!ClientTrackingManager.isTracking()) {
            return null;
        }
        Player player = Minecraft.getInstance().player;
        RescueData data = ClientTrackingManager.getTrackedData();
        if (player == null || data == null) {
            return null;
        }

        ResourceKey<Level> playerDim = player.level().dimension();
        ResourceKey<Level> targetDim = data.getDimension();
        BlockPos targetPos = data.getLocation();

        boolean isEnd = playerDim == Level.END || targetDim == Level.END;
        boolean crossDimension = !playerDim.equals(targetDim);

        if (isEnd) {
            return new Overlay(true, 0.0f);
        }

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

        return new Overlay(false, relativeAngle);
    }

    @Override
    public void submit(@Nullable Overlay overlay, PoseStack poseStack, SubmitNodeCollector collector,
                       int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        if (overlay == null) {
            return;
        }
        if (overlay.endWarning()) {
            submitEndWarning(poseStack, collector);
        } else {
            submitArrow(poseStack, collector, overlay.relativeAngle());
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        // オーバーレイはタブレット本体(base モデル)の範囲内。独自の範囲は持たない
    }

    private void submitArrow(PoseStack poseStack, SubmitNodeCollector collector, float angleDeg) {
        poseStack.pushPose();

        // Move to the center of the tablet screen surface
        // The glass layer is at Y=0.75/16, from X=2/16 to X=14/16, Z=4/16 to Z=12/16
        // Center: X=8/16, Z=8/16, Y slightly above the glass
        poseStack.translate(8.0f / 16.0f, 0.76f / 16.0f, 8.0f / 16.0f);

        // Rotate around Y axis by the relative angle
        // Display transform rotates model ~77° around X, so model +Z appears downward on screen.
        // Add 180° to flip the base arrow direction, negate for correct rotation sense.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - angleDeg));

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
            int r = 0, g = 255, b = 100, a = 255;

            // Arrow head (triangle pointing in +Z direction on the screen plane)
            float headLength = 3.0f / 16.0f;
            float headWidth = 2.25f / 16.0f;

            // Triangle: tip at +Z, base at -Z
            consumer.addVertex(pose, 0, 0, headLength).setColor(r, g, b, a);
            consumer.addVertex(pose, -headWidth / 2, 0, 0).setColor(r, g, b, a);
            consumer.addVertex(pose, headWidth / 2, 0, 0).setColor(r, g, b, a);
            // Duplicate last vertex for quad format
            consumer.addVertex(pose, headWidth / 2, 0, 0).setColor(r, g, b, a);

            // Arrow shaft (rectangle)
            float shaftLength = 2.25f / 16.0f;
            float shaftWidth = 0.6f / 16.0f;

            consumer.addVertex(pose, -shaftWidth / 2, 0, 0).setColor(r, g, b, a);
            consumer.addVertex(pose, -shaftWidth / 2, 0, -shaftLength).setColor(r, g, b, a);
            consumer.addVertex(pose, shaftWidth / 2, 0, -shaftLength).setColor(r, g, b, a);
            consumer.addVertex(pose, shaftWidth / 2, 0, 0).setColor(r, g, b, a);
        });

        poseStack.popPose();
    }

    private void submitEndWarning(PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();

        poseStack.translate(8.0f / 16.0f, 0.76f / 16.0f, 8.0f / 16.0f);

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
            int r = 255, g = 220, b = 0, a = 255;

            // Warning triangle
            float triSize = 2.5f / 16.0f;

            // Triangle pointing up (+Z)
            consumer.addVertex(pose, 0, 0, triSize * 0.6f).setColor(r, g, b, a);
            consumer.addVertex(pose, -triSize / 2, 0, -triSize * 0.4f).setColor(r, g, b, a);
            consumer.addVertex(pose, triSize / 2, 0, -triSize * 0.4f).setColor(r, g, b, a);
            consumer.addVertex(pose, triSize / 2, 0, -triSize * 0.4f).setColor(r, g, b, a);

            // Exclamation mark: small rectangle in center
            float exclWidth = 0.2f / 16.0f;
            float exclLength = 1.0f / 16.0f;
            float exclOffsetZ = -0.1f / 16.0f;
            int er = 0, eg = 0, eb = 0;

            // Exclamation bar
            consumer.addVertex(pose, -exclWidth, 0.001f, exclOffsetZ + exclLength / 2).setColor(er, eg, eb, a);
            consumer.addVertex(pose, -exclWidth, 0.001f, exclOffsetZ - exclLength / 2).setColor(er, eg, eb, a);
            consumer.addVertex(pose, exclWidth, 0.001f, exclOffsetZ - exclLength / 2).setColor(er, eg, eb, a);
            consumer.addVertex(pose, exclWidth, 0.001f, exclOffsetZ + exclLength / 2).setColor(er, eg, eb, a);

            // Exclamation dot
            float dotSize = 0.2f / 16.0f;
            float dotOffset = exclOffsetZ - exclLength / 2 - 0.3f / 16.0f;

            consumer.addVertex(pose, -dotSize, 0.001f, dotOffset + dotSize).setColor(er, eg, eb, a);
            consumer.addVertex(pose, -dotSize, 0.001f, dotOffset - dotSize).setColor(er, eg, eb, a);
            consumer.addVertex(pose, dotSize, 0.001f, dotOffset - dotSize).setColor(er, eg, eb, a);
            consumer.addVertex(pose, dotSize, 0.001f, dotOffset + dotSize).setColor(er, eg, eb, a);
        });

        poseStack.popPose();
    }

    /** items JSON の {@code "type": "medicalsystemcore:rescue_tablet_overlay"} に対応する、設定なしの special model。 */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<Overlay> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public @Nullable SpecialModelRenderer<Overlay> bake(SpecialModelRenderer.BakingContext context) {
            return new RescueTabletOverlayRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
