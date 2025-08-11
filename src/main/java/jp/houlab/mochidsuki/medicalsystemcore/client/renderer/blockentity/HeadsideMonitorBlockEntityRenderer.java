package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

public class HeadsideMonitorBlockEntityRenderer implements BlockEntityRenderer<HeadsideMonitorBlockEntity> {
    private final Font font;

    public HeadsideMonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(HeadsideMonitorBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        pPoseStack.pushPose();

        // 描画の基準点をブロックの前面中央に移動し、こちらを向ける
        pPoseStack.translate(0.5D, 0.5D, 0.001D); // Zを少し手前に
        pPoseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        // 描画範囲をモデルのスクリーン(14x12ピクセル)に合わせる
        // (X: -7～+7, Y: -6～+6)
        pPoseStack.translate(0, -1/16f, 0); // Y座標を少し上に調整
        float scale = (14f / 16f) / 100f; // スケールを調整
        pPoseStack.scale(scale, -scale, scale);


        // --- 各要素の描画 ---
        long time = pBlockEntity.getLevel().getGameTime();
        int maxLight = 15728880; // 最大光量

        // 1. 心拍数(HR)と心電図 (緑)
        renderHeartRate(pPoseStack, pBuffer, maxLight, pBlockEntity.heartRate, time);

        // 2. 血液量(Blood)と波形 (ピンク/マゼンタ)
        renderBloodLevel(pPoseStack, pBuffer, maxLight, pBlockEntity.bloodLevel, time);

        // 3. SpO2と波形 (シアン)
        renderSpO2(pPoseStack, pBuffer, maxLight, 99, time); // 99は固定値

        pPoseStack.popPose();
    }

    // --- ヘルパーメソッド群 ---

    private void renderHeartRate(PoseStack poseStack, MultiBufferSource bufferSource, int light, int heartRate, long time) {
        poseStack.pushPose();
        poseStack.translate(-40, -35, 0); // 位置を左上に調整

        // 数値
        String text = String.valueOf(heartRate);
        float scale = 3.0f; // 文字を大きく
        poseStack.scale(scale, scale, scale);
        this.font.drawInBatch(text, 0, 0, 0xFF00FF00, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        // 心電図
        poseStack.translate(30, 5, 0); // 数値の右に配置
        drawEkgWave(poseStack, bufferSource, heartRate, time);

        poseStack.popPose();
    }

    private void renderBloodLevel(PoseStack poseStack, MultiBufferSource bufferSource, int light, float bloodLevel, long time) {
        poseStack.pushPose();
        poseStack.translate(-40, -10, 0); // 位置を左中央に調整

        String text = String.format("%.0f", bloodLevel);
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        this.font.drawInBatch(text + "%", 0, 0, 0xFFFF00FF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawSimpleWave(poseStack, bufferSource, time, 0.3f, 0xFFFF00FF);

        poseStack.popPose();
    }

    private void renderSpO2(PoseStack poseStack, MultiBufferSource bufferSource, int light, int spo2, long time) {
        poseStack.pushPose();
        poseStack.translate(-40, 25, 0); // 位置を左下に調整

        String text = String.valueOf(spo2);
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        this.font.drawInBatch(text, 0, 0, 0xFF00FFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawSimpleWave(poseStack, bufferSource, time, 0.5f, 0xFF00FFFF);

        poseStack.popPose();
    }

    /**
     * 心電図の波形を描画する
     * @param time ゲーム内時間
     */
    private void drawEkgWave(PoseStack poseStack, MultiBufferSource bufferSource, int heartRate, long time) {
        poseStack.pushPose();

        // 描画の基準点を画面の中央あたりに移動
        poseStack.translate(0, 0, 0);
        float scale = 0.005f; // 波形の大きさ
        poseStack.scale(scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines()); // 線を描画するRenderType

        // 線の色と太さ
        RenderSystem.lineWidth(2.0f);
        int color = 0xFF00FF00; // 緑色

        // 波形の描画
        for (int i = 0; i < 50; i++) { // 50個の線分で波を描く
            float x1 = i - 25;
            float x2 = i + 1 - 25;

            // Math.sinを使ってY座標を周期的に変化させ、波を作る
            // timeをずらすことで、波が左に流れていくように見える
            float y1 = (float) (Math.sin((time * 0.2 + i) * 0.5) * 5.0);
            float y2 = (float) (Math.sin((time * 0.2 + i + 1) * 0.5) * 5.0);

            // "p"波や"QRS"波を擬似的に表現
            if ( (time + i) % 100 > 80 && (time + i) % 100 < 90) {
                y1 *= 3.5; // QRS波 (大きな山)
            }
            if ( (time + i) % 100 > 70 && (time + i) % 100 < 75) {
                y1 *= 1.5; // P波 (小さな山)
            }

            // 線の頂点を定義
            buffer.vertex(matrix, x1, y1, 0).color(color).normal(0,0,1).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(color).normal(0,0,1).endVertex();
        }

        poseStack.popPose();
    }

    private void drawSimpleWave(PoseStack poseStack, MultiBufferSource bufferSource, long time, float speed, int color) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        for (int i = 0; i < 50; i++) {
            float x1 = i; float x2 = i + 1;
            float y1 = (float) (Math.sin((time * 0.1 + i) * speed) * 5.0);
            float y2 = (float) (Math.sin((time * 0.1 + i + 1) * speed) * 5.0);
            buffer.vertex(matrix, x1, y1, 0).color(color).normal(0,0,1).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(color).normal(0,0,1).endVertex();
        }
    }
}