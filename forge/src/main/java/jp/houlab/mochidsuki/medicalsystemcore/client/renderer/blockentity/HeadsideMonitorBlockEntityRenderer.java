package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.block.HeadsideMonitorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
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

        private long lastBlinkTime = 0;
        private boolean blinkState = false;

        void update(float lead1, float lead2, float lead3, long currentTime) {
            if (currentTime - lastUpdateTime >= 1) {
                historyIndex = (historyIndex + 1) % lead1History.length;
                lead1History[historyIndex] = lead1;
                lead2History[historyIndex] = lead2;
                lead3History[historyIndex] = lead3;
                lastUpdateTime = currentTime;
            }
        }

        void updateBlinkState(long currentTime) {
            if (currentTime - lastBlinkTime >= 20) { // 20ティック = 1秒
                blinkState = !blinkState;
                lastBlinkTime = currentTime;
            }
        }
    }

    public HeadsideMonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(HeadsideMonitorBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        WaveformHistory history = waveformHistories.computeIfAbsent(pBlockEntity, k -> new WaveformHistory());


        // ブロックの向きに応じた回転を適用
        Direction facing = pBlockEntity.getBlockState().getValue(HeadsideMonitorBlock.FACING);

        pPoseStack.pushPose();

        // ブロック全体の回転（向きに応じて）
        pPoseStack.translate(0.5, 0, 0.5); // 中心に移動
        pPoseStack.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot())); // FACINGに応じて回転
        pPoseStack.translate(-0.5, 0, -0.5); // 元の位置に戻す


        long currentTime = pBlockEntity.getLevel().getGameTime();
        history.update(pBlockEntity.leadI, pBlockEntity.leadII, pBlockEntity.leadIII, currentTime);

        pPoseStack.pushPose();
        pPoseStack.translate(0.5D, 0.5D, 1f/16f-0.002D);
        pPoseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        pPoseStack.translate(0, -1/16f, 0);
        float scale = (14f / 16f) / 100f;
        pPoseStack.scale(scale, -scale, scale);

        int maxLight = 15728880;

        // 各誘導の描画
        renderHeartRate(pPoseStack, pBuffer, maxLight, pBlockEntity.heartRate, pBlockEntity.heartStatus, history.lead2History, history.historyIndex);
        renderBloodLevel(pPoseStack, pBuffer, maxLight, pBlockEntity.bloodLevel, history.lead1History, history.historyIndex);
        renderSpO2(pPoseStack, pBuffer, maxLight, calculateSpO2(pBlockEntity.heartRate), pBlockEntity.heartRate, history.lead3History, history.historyIndex);

        // 心停止警告サインの表示
        if (pBlockEntity.heartStatus == HeartStatus.CARDIAC_ARREST) {
            renderCardiacArrestWarning(pPoseStack, pBuffer, "心拍なし" , 0xFFFFFF00 ,50,-40, maxLight,history,currentTime);
        }
        if (pBlockEntity.heartStatus == HeartStatus.VF) {
            renderCardiacArrestWarning(pPoseStack, pBuffer, "VF" , 0xFFFF0000 ,50,-40, maxLight,history,currentTime);
        }
        if (pBlockEntity.bloodLevel < 85) {
            renderCardiacArrestWarning(pPoseStack, pBuffer, "低血液量" , 0xFFFFFF00 ,50,-30, maxLight,history,currentTime);
        }
        if(pBlockEntity.heartRate <= 0){
            renderCardiacArrestWarning(pPoseStack, pBuffer, "SpO2測定不可" , 0xFF00FFFF ,10,-40, maxLight,history,currentTime);
        }

        if(!(pBlockEntity.heartStatus == HeartStatus.VF || pBlockEntity.heartStatus == HeartStatus.CARDIAC_ARREST) && pBlockEntity.heartRate <= 0){
            renderCardiacArrestWarning(pPoseStack, pBuffer, "電極はずれ" , 0xFFFFFF00 ,50,-40, maxLight,history,currentTime);
        }
        pPoseStack.popPose();
        pPoseStack.popPose();
    }




    /**
     * 心停止警告サインを表示
     * 黄色文字で点滅表示（文字色と背景色が反転）
     * 既存の描画方式に合わせて実装
     */
    private void renderCardiacArrestWarning(PoseStack pPoseStack, MultiBufferSource pBuffer, String warningText , int color , int x , int y ,int light, WaveformHistory history, long currentTime) {
        // 各モニター個別の点滅状態を更新
        history.updateBlinkState(currentTime);

        pPoseStack.pushPose();

        // 警告サインの位置設定（画面上部）
        // 既存の心拍数表示(-10, -30, 0)より上に配置
        pPoseStack.translate(x, y, 0); // Y座標-50は仮の値


        // 既存のテキスト描画と同じスケールを使用
        float scale = 0.8f;
        pPoseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(warningText);

        // 背景色と文字色の設定
        int textColor, backgroundColor;
        if (!history.blinkState) {
            // 通常状態：黄色文字、黒背景
            textColor = color; // 黄色
            backgroundColor = 0xFF000000; // 黒
        } else {
            // 反転状態：黒文字、黄色背景
            textColor = 0xFF000000; // 黒
            backgroundColor = color; // 黄色
        }

        // 背景描画（既存のコードと同じ方式）
        Matrix4f matrix = pPoseStack.last().pose();
        VertexConsumer buffer = pBuffer.getBuffer(RenderType.gui());

        float bgPadding = 2.0f;
        float x1 = -textWidth - bgPadding;
        float x2 = bgPadding;
        float y1 = -bgPadding;
        float y2 = this.font.lineHeight + bgPadding;
        float bgZ = -0.001f;

        // 背景の四角形を描画
        int bgR = (backgroundColor >> 16) & 0xFF;
        int bgG = (backgroundColor >> 8) & 0xFF;
        int bgB = backgroundColor & 0xFF;
        int bgA = 200; // 透明度を既存コードに合わせる

        buffer.vertex(matrix, x1, y1, bgZ).color(bgR, bgG, bgB, bgA).endVertex();
        buffer.vertex(matrix, x1, y2, bgZ).color(bgR, bgG, bgB, bgA).endVertex();
        buffer.vertex(matrix, x2, y2, bgZ).color(bgR, bgG, bgB, bgA).endVertex();
        buffer.vertex(matrix, x2, y1, bgZ).color(bgR, bgG, bgB, bgA).endVertex();


        // 警告テキストを描画（既存のコードと同じ方式）
        this.font.drawInBatch(
                warningText,
                -textWidth,
                0,
                textColor,
                false,
                pPoseStack.last().pose(),
                pBuffer,
                Font.DisplayMode.NORMAL,
                0,
                light
        );

        // スケールを元に戻す（既存コードと同じパターン）
        pPoseStack.scale(1/scale, 1/scale, 1/scale);

        pPoseStack.popPose();
    }

    /**
     * 心拍数表示（Z軸重複修正版）
     */
    private void renderHeartRate(PoseStack poseStack, MultiBufferSource bufferSource, int light, int heartRate, HeartStatus status, float[] waveformHistory, int historyIndex) {
        poseStack.pushPose();
        poseStack.translate(-10, -30, 0);

        String text;
        if (heartRate <= 0 || heartRate >= 350) {
            text = "-?-";
        } else {
            text = String.valueOf(heartRate);
        }

        float scale = 2.0f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);

        boolean shouldHighlight = (status == HeartStatus.VF || status == HeartStatus.CARDIAC_ARREST);

        if (shouldHighlight) {
            // 背景を先に描画（奥側のZ座標）
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.gui());

            float bgPadding = 2.0f;
            float y1 = -bgPadding;
            float y2 = this.font.lineHeight + bgPadding;

            // 背景のZ座標を少し奥に配置（-0.001f）
            float bgZ = -0.001f;

            // 背景色（赤）- 適切なサイズで描画
            buffer.vertex(matrix, -20, y1, bgZ).color(255, 0, 0, 200).endVertex();
            buffer.vertex(matrix, -20, y2, bgZ).color(255, 0, 0, 200).endVertex();
            buffer.vertex(matrix, 0, y2, bgZ).color(255, 0, 0, 200).endVertex();
            buffer.vertex(matrix, 0, y1, bgZ).color(255, 0, 0, 200).endVertex();

            // 文字を後に描画（手前側のZ座標）
            this.font.drawInBatch(text, -textWidth, 0.001f, 0xFF000000, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
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
     * 血液量表示（Z軸重複修正版）
     */
    private void renderBloodLevel(PoseStack poseStack, MultiBufferSource bufferSource, int light, float bloodLevel, float[] waveformData, int historyIndex) {
        poseStack.pushPose();
        poseStack.translate(-10, 0, 0);

        String text = String.format("%.0f", bloodLevel) + "%";
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);

        boolean shouldHighlight = (bloodLevel < 60.0f);

        if (shouldHighlight) {
            // 背景を先に描画（奥側のZ座標）
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.gui());

            float bgPadding = 2.0f;
            float y1 = -bgPadding;
            float y2 = this.font.lineHeight + bgPadding;

            // 背景のZ座標を少し奥に配置
            float bgZ = -0.001f;

            // 背景色（赤）- 適切なサイズで描画
            buffer.vertex(matrix, -30, y1, bgZ).color(255, 0, 0, 200).endVertex();
            buffer.vertex(matrix, -30, y2, bgZ).color(255, 0, 0, 200).endVertex();
            buffer.vertex(matrix, 0, y2, bgZ).color(255, 0, 0, 200).endVertex();
            buffer.vertex(matrix, 0, y1, bgZ).color(255, 0, 0, 200).endVertex();

            // 文字を後に描画（手前側のZ座標）
            this.font.drawInBatch(text, -textWidth, 0.001f, 0xFF000000, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
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
     * SpO2表示
     */
    private void renderSpO2(PoseStack poseStack, MultiBufferSource bufferSource, int light, int spo2, int heartRate, float[] waveformData, int historyIndex) {
        poseStack.pushPose();
        poseStack.translate(-10, 25, 0);

        String text;
        if (heartRate <= 0) {
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

    private int calculateSpO2(int heartRate) {
        if (heartRate <= 0 || heartRate >= 350) {
            return 0;
        }
        return 98 + (heartRate % 3);
    }

    private void drawWaveform(PoseStack poseStack, MultiBufferSource bufferSource, float[] waveformHistory, int currentIndex, int color) {
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        RenderSystem.lineWidth(2.0f);

        int historyLength = waveformHistory.length;
        float drawWidth = 60f;
        float amplitudeScale = 15f;

        for (int i = 0; i < historyLength - 1; i++) {
            int currentDataIndex = (currentIndex - i + historyLength) % historyLength;
            int nextDataIndex = (currentIndex - (i + 1) + historyLength) % historyLength;

            float x1 = drawWidth - (i * drawWidth / historyLength);
            float x2 = drawWidth - ((i + 1) * drawWidth / historyLength);

            float y1 = -waveformHistory[currentDataIndex] * amplitudeScale;
            float y2 = -waveformHistory[nextDataIndex] * amplitudeScale;

            // グリッドライン
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

    public static void cleanup(HeadsideMonitorBlockEntity blockEntity) {
        waveformHistories.remove(blockEntity);
    }
}