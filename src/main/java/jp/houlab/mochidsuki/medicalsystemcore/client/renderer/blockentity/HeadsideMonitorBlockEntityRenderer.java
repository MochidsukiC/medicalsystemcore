package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class HeadsideMonitorBlockEntityRenderer implements BlockEntityRenderer<HeadsideMonitorBlockEntity> {
    private final Font font;

    // モニターごとの波形履歴を管理
    private static final Map<HeadsideMonitorBlockEntity, WaveformHistory> waveformHistories = new HashMap<>();

    private static class WaveformHistory {
        final float[] lead1History = new float[100];
        final float[] lead2History = new float[100];
        final float[] lead3History = new float[100];
        int historyIndex = 0;
        long lastUpdateTime = 0;

        void update(float lead1, float lead2, float lead3, long currentTime) {
            // 適切な更新頻度を維持（約50ms間隔）
            if (currentTime - lastUpdateTime >= 1) { // 1ティックごと
                historyIndex = (historyIndex + 1) % lead1History.length;
                lead1History[historyIndex] = lead1;
                lead2History[historyIndex] = lead2;
                lead3History[historyIndex] = lead3;
                lastUpdateTime = currentTime;
            }
        }
    }

    public HeadsideMonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(HeadsideMonitorBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        // 波形履歴を取得または作成
        WaveformHistory history = waveformHistories.computeIfAbsent(pBlockEntity, k -> new WaveformHistory());

        // 現在のデータで履歴を更新
        long currentTime = pBlockEntity.getLevel().getGameTime();
        history.update(pBlockEntity.leadI, pBlockEntity.leadII, pBlockEntity.leadIII, currentTime);

        pPoseStack.pushPose();
        pPoseStack.translate(0.5D, 0.5D, 0.001D);
        pPoseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        pPoseStack.translate(0, -1/16f, 0);
        float scale = (14f / 16f) / 100f;
        pPoseStack.scale(scale, -scale, scale);

        int maxLight = 15728880;

        // 各誘導の描画（心停止時でも数値は表示し続ける）
        renderHeartRate(pPoseStack, pBuffer, maxLight, pBlockEntity.heartRate, pBlockEntity.heartStatus, history.lead2History, history.historyIndex);
        renderBloodLevel(pPoseStack, pBuffer, maxLight, pBlockEntity.bloodLevel, history.lead1History, history.historyIndex);
        renderSpO2(pPoseStack, pBuffer, maxLight, calculateSpO2(pBlockEntity.heartRate), pBlockEntity.heartRate, history.lead3History, history.historyIndex);

        pPoseStack.popPose();
    }

    /**
     * 心拍数表示（新仕様対応）
     * - VF、心停止時は背景色付き、文字黒色
     * - 心拍数が0または350以上の場合は"-?-"表示
     */
    private void renderHeartRate(PoseStack poseStack, MultiBufferSource bufferSource, int light, int heartRate, HeartStatus status, float[] waveformHistory, int historyIndex) {
        poseStack.pushPose();
        poseStack.translate(-10, -35, 0);

        // 心拍数の表示文字列を決定
        String text;
        if (heartRate <= 0 || heartRate >= 350) {
            text = "-?-";
        } else {
            text = String.valueOf(heartRate);
        }

        float scale = 3.0f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);

        // 強調表示の判定（VF、心停止時）
        boolean shouldHighlight = (status == HeartStatus.VF || status == HeartStatus.CARDIAC_ARREST);

        if (shouldHighlight) {
            // 背景を描画
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.gui());

            float bgPadding = 2.0f;
            float x1 = -textWidth - bgPadding;
            float y1 = -bgPadding;
            float x2 = bgPadding;
            float y2 = this.font.lineHeight + bgPadding;

            // 背景色（赤）
            buffer.vertex(matrix, x1, y1, 0).color(255, 0, 0, 255).endVertex();
            buffer.vertex(matrix, x1, y2, 0).color(255, 0, 0, 255).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(255, 0, 0, 255).endVertex();
            buffer.vertex(matrix, x2, y1, 0).color(255, 0, 0, 255).endVertex();

            // 文字色（黒）
            this.font.drawInBatch(text, -textWidth, 0, 0xFF000000, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        } else {
            // 通常表示（緑）
            this.font.drawInBatch(text, -textWidth, 0, 0xFF00FF00, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        }

        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(0, 15, 0);
        drawWaveform(poseStack, bufferSource, waveformHistory, historyIndex, 0xFF00FF00);
        poseStack.popPose();
    }

    /**
     * 血液量表示（新仕様対応）
     * - 血液量が60%未満の場合は背景色付き、文字黒色
     */
    private void renderBloodLevel(PoseStack poseStack, MultiBufferSource bufferSource, int light, float bloodLevel, float[] waveformData, int historyIndex) {
        poseStack.pushPose();
        poseStack.translate(-10, 0, 0);

        String text = String.format("%.0f", bloodLevel) + "%";
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);

        // 強調表示の判定（血液量60%未満）
        boolean shouldHighlight = (bloodLevel < 60.0f);

        if (shouldHighlight) {
            // 背景を描画
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.gui());

            float bgPadding = 2.0f;
            float x1 = -textWidth - bgPadding;
            float y1 = -bgPadding;
            float x2 = bgPadding;
            float y2 = this.font.lineHeight + bgPadding;

            // 背景色（赤）
            buffer.vertex(matrix, x1, y1, 0).color(255, 0, 0, 255).endVertex();
            buffer.vertex(matrix, x1, y2, 0).color(255, 0, 0, 255).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(255, 0, 0, 255).endVertex();
            buffer.vertex(matrix, x2, y1, 0).color(255, 0, 0, 255).endVertex();

            // 文字色（黒）
            this.font.drawInBatch(text, -textWidth, 0, 0xFF000000, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        } else {
            // 通常表示（マゼンタ）
            this.font.drawInBatch(text, -textWidth, 0, 0xFFFF00FF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        }

        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(0, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformData, historyIndex, 0xFFFF00FF);
        poseStack.popPose();
    }

    /**
     * SpO2表示（新仕様対応）
     * - 心拍数が0または350以上の場合は"-?-"表示
     */
    private void renderSpO2(PoseStack poseStack, MultiBufferSource bufferSource, int light, int spo2, int heartRate, float[] waveformData, int historyIndex) {
        poseStack.pushPose();
        poseStack.translate(-10, 25, 0);

        // SpO2の表示文字列を決定
        String text;
        if (heartRate <= 0 || heartRate >= 350) {
            text = "-?-";
        } else {
            text = String.valueOf(spo2);
        }

        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);
        this.font.drawInBatch(text, -textWidth, 0, 0xFF00FFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
        poseStack.scale(1/scale, 1/scale, 1/scale);

        poseStack.translate(0, 5, 0);
        drawWaveform(poseStack, bufferSource, waveformData, historyIndex, 0xFF00FFFF);
        poseStack.popPose();
    }

    /**
     * SpO2の値を計算（簡易実装）
     */
    private int calculateSpO2(int heartRate) {
        if (heartRate <= 0 || heartRate >= 350) {
            return 0; // 異常値の場合は0を返す（表示は"-?-"になる）
        }
        return 98 + (heartRate % 3); // 98-100の範囲で変動
    }

    private void drawWaveform(PoseStack poseStack, MultiBufferSource bufferSource, float[] waveformHistory, int currentIndex, int color) {
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        int historyLength = waveformHistory.length;
        float drawWidth = 80f;
        float amplitudeScale = 15f; // 振幅を調整

        // 右から左へ流れる波形を描画
        for (int i = 0; i < historyLength - 1; i++) {
            int currentDataIndex = (currentIndex - i + historyLength) % historyLength;
            int nextDataIndex = (currentIndex - (i + 1) + historyLength) % historyLength;

            float x1 = drawWidth - (i * drawWidth / historyLength);
            float x2 = drawWidth - ((i + 1) * drawWidth / historyLength);

            float y1 = -waveformHistory[currentDataIndex] * amplitudeScale; // Y軸反転修正
            float y2 = -waveformHistory[nextDataIndex] * amplitudeScale;

            // グリッドライン（基準線）
            if (i % 10 == 0) {
                buffer.vertex(matrix, x1, -5, 0).color(0x33FFFFFF).normal(0,0,1).endVertex();
                buffer.vertex(matrix, x1, 5, 0).color(0x33FFFFFF).normal(0,0,1).endVertex();
            }

            // 波形データ
            buffer.vertex(matrix, x1, y1, 0).color(color).normal(0,0,1).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(color).normal(0,0,1).endVertex();
        }

        poseStack.popPose();
    }

    // レンダラーが削除される際に履歴も削除
    public static void cleanup(HeadsideMonitorBlockEntity blockEntity) {
        waveformHistories.remove(blockEntity);
    }
}