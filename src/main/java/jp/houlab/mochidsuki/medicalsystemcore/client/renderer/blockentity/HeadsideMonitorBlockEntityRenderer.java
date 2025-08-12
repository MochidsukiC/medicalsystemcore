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
    // 波形の履歴を保持するための配列 (あなたのコードにあったものを正しく活用します)
    private final float[] lead1History = new float[80];
    private final float[] lead2History = new float[80];
    private final float[] lead3History = new float[80];
    private int historyIndex = 0;


    public HeadsideMonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(HeadsideMonitorBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        // --- 1. サーバーから同期された最新の「点」のデータを取得 ---
        float currentLeadI = pBlockEntity.leadI;
        float currentLeadII = pBlockEntity.leadII;
        float currentLeadIII = pBlockEntity.leadIII;

        // --- 2. 履歴データを更新 ---
        historyIndex = (historyIndex + 1) % lead1History.length;
        lead1History[historyIndex] = currentLeadI;
        lead2History[historyIndex] = currentLeadII;
        lead3History[historyIndex] = currentLeadIII;

        // --- 3. 描画処理 ---
        pPoseStack.pushPose();
        // (あなたの座標変換ロジックはそのまま使用)
        pPoseStack.translate(0.5D, 0.5D, 0.001D);
        pPoseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        pPoseStack.translate(0, -1/16f, 0);
        float scale = (14f / 16f) / 100f;
        pPoseStack.scale(scale, -scale, scale);

        int maxLight = 15728880;

        // --- 各要素の描画 ---
        // 各描画メソッドに、更新された「履歴」配列を渡す
        renderHeartRate(pPoseStack, pBuffer, maxLight, pBlockEntity.heartRate, lead2History);
        renderBloodLevel(pPoseStack, pBuffer, maxLight, pBlockEntity.bloodLevel, lead1History);
        renderSpO2(pPoseStack, pBuffer, maxLight, 99, lead3History);

        pPoseStack.popPose();
    }

    // --- ヘルパーメソッド群 (テキスト描画部分は変更なし) ---

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
        drawWaveform(poseStack, bufferSource, waveformHistory, 0xFF00FF00);
        poseStack.popPose();
    }
    // (renderBloodLevel, renderSpO2も同様)


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
     * 履歴データを元に、滑らかな波形を描画する
     */
    private void drawWaveform(PoseStack poseStack, MultiBufferSource bufferSource, float[] waveformHistory, int color) {
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        int historyLength = waveformHistory.length;
        float drawWidth = 80f; // 波形を描画する幅

        // 履歴を元に線分を描画
        for (int i = 0; i < historyLength - 1; i++) {
            // 現在のフレームから過去に遡ってデータを取得し、線で結ぶ
            int currentIndex = (this.historyIndex - i + historyLength) % historyLength;
            int prevIndex = (this.historyIndex - (i + 1) + historyLength) % historyLength;

            float x1 = drawWidth - (i * 1.0f); // 右から左へ流れるようにX座標を設定
            float x2 = drawWidth - ((i + 1) * 1.0f);

            float y1 = waveformHistory[currentIndex] * 10; // Yスケールを調整
            float y2 = waveformHistory[prevIndex] * 10;

            buffer.vertex(matrix, x1, y1, 0).color(color).normal(0,0,1).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(color).normal(0,0,1).endVertex();
        }
        poseStack.popPose();
    }
}