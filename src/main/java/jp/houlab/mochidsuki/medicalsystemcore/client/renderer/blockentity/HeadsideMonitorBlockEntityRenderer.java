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
    private final float[] lead1History = new float[80];
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
        float leadI = pBlockEntity.leadI;
        float leadII = pBlockEntity.leadII;
        float leadIII = pBlockEntity.leadIII;

        // --- 波形の履歴データを更新 ---
        // ゲーム時間ではなく、描画フレームごとに更新することで、より滑らかに見える
        historyIndex = (historyIndex + 1) % lead1History.length;
        lead1History[historyIndex] = leadI;
        lead2History[historyIndex] = leadII;
        lead3History[historyIndex] = leadIII;


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
        renderHeartRate(pPoseStack, pBuffer, maxLight, heartRate, lead2History);
        // 2. 血液量(Blood)と波形 (マゼンタ) - 誘導I
        renderBloodLevel(pPoseStack, pBuffer, maxLight, bloodLevel, lead1History);
        // 3. SpO2と波形 (シアン) - 誘導III
        renderSpO2(pPoseStack, pBuffer, maxLight, 99, lead3History);

        pPoseStack.popPose();
    }

    // --- ヘルパーメソッド群を、新しいデータ構造に合わせて修正 ---

    private void renderHeartRate(PoseStack poseStack, MultiBufferSource bufferSource, int light, int heartRate, float[] waveformHistory) {
        poseStack.pushPose();
        poseStack.translate(-10, -35, 0);

        String text = String.valueOf(heartRate);
        float scale = 3.0f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);
        this.font.drawInBatch(text, -textWidth, 0, 0xFF00FF00, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformHistory, 0xFF00FF00); // 新しい描画メソッドを呼び出し
        poseStack.popPose();
    }

    private void renderBloodLevel(PoseStack poseStack, MultiBufferSource bufferSource, int light, float bloodLevel, float[] waveformHistory) {
        poseStack.pushPose();
        poseStack.translate(-10, 0, 0);

        String text = String.format("%.0f", bloodLevel) + "%";
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);
        this.font.drawInBatch(text, -textWidth, 0, 0xFFFF00FF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformHistory, 0xFFFF00FF);
        poseStack.popPose();
    }

    private void renderSpO2(PoseStack poseStack, MultiBufferSource bufferSource, int light, int spo2, float[] waveformHistory) {
        poseStack.pushPose();
        poseStack.translate(-10, 25, 0);

        String text = String.valueOf(spo2);
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);
        this.font.drawInBatch(text, -textWidth, 0, 0xFF00FFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(35, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformHistory, 0xFF00FFFF);
        poseStack.popPose();
    }

    /**
     * 同期された誘導値の履歴を元に、滑らかな波形を描画する
     * @param waveformHistory 波形のY座標の履歴データ
     */
    private void drawWaveform(PoseStack poseStack, MultiBufferSource bufferSource, float[] waveformHistory, int color) {
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        int historyLength = waveformHistory.length;
        // 履歴を元に線分を描画
        for (int i = 0; i < historyLength - 1; i++) {
            // 現在のフレームから過去に遡ってデータを取得
            int currentIndex = (this.historyIndex - i + historyLength) % historyLength;
            int prevIndex = (this.historyIndex - (i + 1) + historyLength) % historyLength;

            float x1 = 80 - (i * 1.0f); // 右から左へ流れるようにX座標を設定
            float x2 = 80 - ((i + 1) * 1.0f);

            float y1 = waveformHistory[currentIndex] * -10; // Yスケールと向きを調整
            float y2 = waveformHistory[prevIndex] * -10;

            buffer.vertex(matrix, x1, y1, 0).color(color).normal(0,0,1).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(color).normal(0,0,1).endVertex();
        }
        poseStack.popPose();
    }
}