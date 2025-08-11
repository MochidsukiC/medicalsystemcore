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
    // 波形の履歴を保持するための配列。クラスのフィールドとして持つことで、描画がスムーズになる
    private final float[] lead1History = new float[80]; // 80フレーム分の履歴
    private final float[] lead2History = new float[80];
    private final float[] lead3History = new float[80];
    private int historyIndex = 0;

    public HeadsideMonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(HeadsideMonitorBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        // --- サーバーから同期された最新のデータを取得 ---
        int heartRate = pBlockEntity.heartRate;
        float bloodLevel = pBlockEntity.bloodLevel;
        // BEに保存された波形データを取得 (これは1周期分の完全なデータ)
        float[] lead1Waveform = pBlockEntity.lead1Waveform;
        float[] lead2Waveform = pBlockEntity.lead2Waveform;
        float[] lead3Waveform = pBlockEntity.lead3Waveform;

        pPoseStack.pushPose();
        // (あなたの座標変換ロジックはそのまま使用)
        pPoseStack.translate(0.5D, 0.5D, 0.001D);
        pPoseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        pPoseStack.translate(0, -1/16f, 0);
        float scale = (14f / 16f) / 100f;
        pPoseStack.scale(scale, -scale, scale);

        int maxLight = 15728880;

        // --- 各要素の描画 ---
        // 1. 心拍数(HR)と心電図 (緑) - 誘導II
        renderHeartRate(pPoseStack, pBuffer, maxLight, heartRate, lead2Waveform);
        // 2. 血液量(Blood)と波形 (マゼンタ) - 誘導I
        renderBloodLevel(pPoseStack, pBuffer, maxLight, bloodLevel, lead1Waveform);
        // 3. SpO2と波形 (シアン) - 誘導III
        renderSpO2(pPoseStack, pBuffer, maxLight, 99, lead3Waveform);

        pPoseStack.popPose();
    }

    // --- ヘルパーメソッド群 ---

    private void renderHeartRate(PoseStack poseStack, MultiBufferSource bufferSource, int light, int heartRate, float[] waveformData) {
        poseStack.pushPose();
        poseStack.translate(-10, -35, 0);

        String text = String.valueOf(heartRate);
        float scale = 3.0f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);
        this.font.drawInBatch(text, -textWidth, 0, 0xFF00FF00, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformData, 0xFF00FF00); // 描画メソッドを統一
        poseStack.popPose();
    }

    private void renderBloodLevel(PoseStack poseStack, MultiBufferSource bufferSource, int light, float bloodLevel, float[] waveformData) {
        poseStack.pushPose();
        poseStack.translate(-10, 0, 0);

        String text = String.format("%.0f", bloodLevel) + "%";
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);
        this.font.drawInBatch(text, -textWidth, 0, 0xFFFF00FF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformData, 0xFFFF00FF);
        poseStack.popPose();
    }

    private void renderSpO2(PoseStack poseStack, MultiBufferSource bufferSource, int light, int spo2, float[] waveformData) {
        poseStack.pushPose();
        poseStack.translate(-10, 25, 0);

        String text = String.valueOf(spo2);
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);
        this.font.drawInBatch(text, -textWidth, 0, 0xFF00FFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformData, 0xFF00FFFF);
        poseStack.popPose();
    }

    /**
     * 同期された波形データ配列を元に、線を描画する新しいメソッド
     */
    private void drawWaveform(PoseStack poseStack, MultiBufferSource bufferSource, float[] waveformData, int color) {
        if (waveformData == null || waveformData.length == 0) return;

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        int dataLength = waveformData.length;
        float drawWidth = 80f; // 波形を描画する幅

        // 1周期分の波形データを、描画幅に合わせて線で結んで描画
        for (int i = 0; i < dataLength - 1; i++) {
            float x1 = (float) i / dataLength * drawWidth;
            float x2 = (float) (i + 1) / dataLength * drawWidth;

            float y1 = waveformData[i] * -10; // Yスケールと向きを調整
            float y2 = waveformData[i + 1] * -10;

            buffer.vertex(matrix, x1, y1, 0).color(color).normal(0,0,1).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(color).normal(0,0,1).endVertex();
        }
        poseStack.popPose();
    }
}