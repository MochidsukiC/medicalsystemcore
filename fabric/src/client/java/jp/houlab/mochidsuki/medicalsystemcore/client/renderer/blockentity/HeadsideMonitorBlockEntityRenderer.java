package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.block.HeadsideMonitorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class HeadsideMonitorBlockEntityRenderer implements BlockEntityRenderer<HeadsideMonitorBlockEntity, HeadsideMonitorBlockEntityRenderer.State> {
    private final Font font;

    // モニターごとの波形履歴を管理。
    // キーは弱参照(WeakHashMap)にして、BlockEntity が破棄された(チャンクのアンロード、ワールドの切り替え等)ら履歴も解放されるようにする。
    // 値(WaveformHistory)は BlockEntity を参照しないので、値がキーを引き留めることはない。描画スレッドからしか触らない。
    private static final Map<HeadsideMonitorBlockEntity, WaveformHistory> waveformHistories = new WeakHashMap<>();

    private static final int MAX_LIGHT = 15728880;

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

    /** 警告サイン 1 つ分(文字、色、位置)。 */
    private record Warning(String text, int color, int x, int y) {
    }

    /** 描画に必要な値の抽出先。blockState は BlockEntityRenderState 側では private なので、必要な値を自前で持つ。 */
    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        int heartRate;
        float bloodLevel;
        HeartStatus heartStatus = HeartStatus.NORMAL;
        // 波形履歴は描画中に書き換わらないよう、extract 時点の複製を持つ
        float[] lead1History = new float[0];
        float[] lead2History = new float[0];
        float[] lead3History = new float[0];
        int historyIndex;
        boolean blinkState;
        final List<Warning> warnings = new ArrayList<>();
        // 波形の線幅。Forge の RenderType.lines() が使っていた既定の幅(max(2.5, 幅 / 1920 * 2.5))と同じ値
        float lineWidth;
    }

    public HeadsideMonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HeadsideMonitorBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        WaveformHistory history = waveformHistories.computeIfAbsent(blockEntity, k -> new WaveformHistory());

        // ブロックの向きに応じた回転を適用
        state.facing = blockEntity.getBlockState().getValue(HeadsideMonitorBlock.FACING);

        long currentTime = blockEntity.getLevel().getGameTime();
        history.update(blockEntity.leadI, blockEntity.leadII, blockEntity.leadIII, currentTime);

        state.heartRate = blockEntity.heartRate;
        state.bloodLevel = blockEntity.bloodLevel;
        state.heartStatus = blockEntity.heartStatus;
        state.lead1History = history.lead1History.clone();
        state.lead2History = history.lead2History.clone();
        state.lead3History = history.lead3History.clone();
        state.historyIndex = history.historyIndex;
        state.lineWidth = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;

        // 心停止警告サインの表示(表示順は Forge のまま)
        state.warnings.clear();
        if (blockEntity.heartStatus == HeartStatus.CARDIAC_ARREST) {
            state.warnings.add(new Warning("心拍なし", 0xFFFFFF00, 50, -40));
        }
        if (blockEntity.heartStatus == HeartStatus.VF) {
            state.warnings.add(new Warning("VF", 0xFFFF0000, 50, -40));
        }
        if (blockEntity.bloodLevel < 85) {
            state.warnings.add(new Warning("低血液量", 0xFFFFFF00, 50, -30));
        }
        if (blockEntity.heartRate <= 0) {
            state.warnings.add(new Warning("SpO2測定不可", 0xFF00FFFF, 10, -40));
        }
        if (!(blockEntity.heartStatus == HeartStatus.VF || blockEntity.heartStatus == HeartStatus.CARDIAC_ARREST) && blockEntity.heartRate <= 0) {
            state.warnings.add(new Warning("電極はずれ", 0xFFFFFF00, 50, -40));
        }
        // 点滅状態の更新は警告を描くときだけ(Forge は警告を描くたびに更新していた。同じ tick 内の 2 回目以降は何も変わらない)
        if (!state.warnings.isEmpty()) {
            history.updateBlinkState(currentTime);
        }
        state.blinkState = history.blinkState;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();

        // ブロック全体の回転（向きに応じて）
        poseStack.translate(0.5, 0, 0.5); // 中心に移動
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.getOpposite().toYRot())); // FACINGに応じて回転
        poseStack.translate(-0.5, 0, -0.5); // 元の位置に戻す

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 1f / 16f - 0.002D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(0, -1 / 16f, 0);
        float scale = (14f / 16f) / 100f;
        poseStack.scale(scale, -scale, scale);

        // 各誘導の描画
        submitHeartRate(poseStack, collector, state, state.heartRate, state.heartStatus, state.lead2History);
        submitBloodLevel(poseStack, collector, state, state.bloodLevel, state.lead1History);
        submitSpO2(poseStack, collector, state, calculateSpO2(state.heartRate), state.heartRate, state.lead3History);

        for (Warning warning : state.warnings) {
            submitCardiacArrestWarning(poseStack, collector, state, warning);
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * 心停止警告サインを表示
     * 黄色文字で点滅表示（文字色と背景色が反転）
     * 既存の描画方式に合わせて実装
     */
    private void submitCardiacArrestWarning(PoseStack poseStack, SubmitNodeCollector collector, State state, Warning warning) {
        poseStack.pushPose();

        // 警告サインの位置設定（画面上部）
        poseStack.translate(warning.x(), warning.y(), 0);

        // 既存のテキスト描画と同じスケールを使用
        float scale = 0.8f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(warning.text());

        // 背景色と文字色の設定
        int textColor, backgroundColor;
        if (!state.blinkState) {
            // 通常状態：黄色文字、黒背景
            textColor = warning.color(); // 黄色
            backgroundColor = 0xFF000000; // 黒
        } else {
            // 反転状態：黒文字、黄色背景
            textColor = 0xFF000000; // 黒
            backgroundColor = warning.color(); // 黄色
        }

        float bgPadding = 2.0f;
        float x1 = -textWidth - bgPadding;
        float x2 = bgPadding;
        float y1 = -bgPadding;
        float y2 = this.font.lineHeight + bgPadding;
        submitBackground(poseStack, collector, x1, y1, x2, y2, backgroundColor, 200); // 透明度を既存コードに合わせる

        // 警告テキストを描画（既存のコードと同じ方式）
        submitText(poseStack, collector, warning.text(), -textWidth, 0, textColor);

        // スケールを元に戻す（既存コードと同じパターン）
        poseStack.scale(1 / scale, 1 / scale, 1 / scale);

        poseStack.popPose();
    }

    /**
     * 心拍数表示（Z軸重複修正版）
     */
    private void submitHeartRate(PoseStack poseStack, SubmitNodeCollector collector, State state, int heartRate, HeartStatus status, float[] waveformHistory) {
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
            float bgPadding = 2.0f;
            float y1 = -bgPadding;
            float y2 = this.font.lineHeight + bgPadding;

            // 背景色（赤）- 適切なサイズで描画
            submitBackground(poseStack, collector, -20, y1, 0, y2, 0xFFFF0000, 200);

            // 文字を後に描画（手前側。Forge のまま y を 0.001 ずらす）
            submitText(poseStack, collector, text, -textWidth, 0.001f, 0xFF000000);
        } else {
            // 通常表示（緑）
            submitText(poseStack, collector, text, -textWidth, 0, 0xFF00FF00);
        }

        poseStack.scale(1 / scale, 1 / scale, 1 / scale);

        poseStack.translate(0, 15, 0);
        submitWaveform(poseStack, collector, state, waveformHistory, 0xFF00FF00);
        poseStack.popPose();
    }

    /**
     * 血液量表示（Z軸重複修正版）
     */
    private void submitBloodLevel(PoseStack poseStack, SubmitNodeCollector collector, State state, float bloodLevel, float[] waveformData) {
        poseStack.pushPose();
        poseStack.translate(-10, 0, 0);

        String text = String.format("%.0f", bloodLevel) + "%";
        float scale = 1.5f;
        poseStack.scale(scale, scale, scale);
        float textWidth = this.font.width(text);

        boolean shouldHighlight = (bloodLevel < 60.0f);

        if (shouldHighlight) {
            // 背景を先に描画（奥側のZ座標）
            float bgPadding = 2.0f;
            float y1 = -bgPadding;
            float y2 = this.font.lineHeight + bgPadding;

            // 背景色（赤）- 適切なサイズで描画
            submitBackground(poseStack, collector, -30, y1, 0, y2, 0xFFFF0000, 200);

            // 文字を後に描画（手前側。Forge のまま y を 0.001 ずらす）
            submitText(poseStack, collector, text, -textWidth, 0.001f, 0xFF000000);
        } else {
            // 通常表示（マゼンタ）
            submitText(poseStack, collector, text, -textWidth, 0, 0xFFFF00FF);
        }

        poseStack.scale(1 / scale, 1 / scale, 1 / scale);

        poseStack.translate(0, 5, 0);
        submitWaveform(poseStack, collector, state, waveformData, 0xFFFF00FF);
        poseStack.popPose();
    }

    /**
     * SpO2表示
     */
    private void submitSpO2(PoseStack poseStack, SubmitNodeCollector collector, State state, int spo2, int heartRate, float[] waveformData) {
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
        submitText(poseStack, collector, text, -textWidth, 0, 0xFF00FFFF);
        poseStack.scale(1 / scale, 1 / scale, 1 / scale);

        poseStack.translate(0, 5, 0);
        submitWaveform(poseStack, collector, state, waveformData, 0xFF00FFFF);
        poseStack.popPose();
    }

    private int calculateSpO2(int heartRate) {
        if (heartRate <= 0 || heartRate >= 350) {
            return 0;
        }
        return 98 + (heartRate % 3);
    }

    /**
     * 文字を描く。Forge の Font#drawInBatch(影なし、Font.DisplayMode.NORMAL、最大光量)と同じ。
     */
    private void submitText(PoseStack poseStack, SubmitNodeCollector collector, String text, float x, float y, int color) {
        FormattedCharSequence sequence = FormattedCharSequence.forward(text, Style.EMPTY);
        collector.submitText(poseStack, x, y, sequence, false, Font.DisplayMode.NORMAL, MAX_LIGHT, color, 0, 0);
    }

    /**
     * 文字の背景の四角形。Forge の RenderType.gui()(位置+色、半透明)は 26.2 に無いので、同じ頂点形式の debugQuads() で描く。
     * Z は Forge と同じく -0.001(文字より奥)。
     */
    private void submitBackground(PoseStack poseStack, SubmitNodeCollector collector, float x1, float y1, float x2, float y2, int argb, int alpha) {
        final float bgZ = -0.001f;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            buffer.addVertex(pose, x1, y1, bgZ).setColor(r, g, b, alpha);
            buffer.addVertex(pose, x1, y2, bgZ).setColor(r, g, b, alpha);
            buffer.addVertex(pose, x2, y2, bgZ).setColor(r, g, b, alpha);
            buffer.addVertex(pose, x2, y1, bgZ).setColor(r, g, b, alpha);
        });
    }

    private void submitWaveform(PoseStack poseStack, SubmitNodeCollector collector, State state, float[] waveformHistory, int color) {
        final int currentIndex = state.historyIndex;
        final float lineWidth = state.lineWidth;
        poseStack.pushPose();
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
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
                    addLine(buffer, pose, x1, -5, x1, 5, 0x33FFFFFF, lineWidth);
                }

                // 波形データ
                addLine(buffer, pose, x1, y1, x2, y2, color, lineWidth);
            }
        });
        poseStack.popPose();
    }

    /**
     * 線を 1 本足す。26.2 の lines パイプラインは、法線を「線の向き」として太さを展開する(1.20.1 でも同じ計算だが、
     * Forge は法線に固定の (0,0,1) を渡していた)ので、ここでは線の向きそのものを法線に渡す。幅は頂点ごとに指定する。
     */
    private static void addLine(VertexConsumer buffer, PoseStack.Pose pose,
                                float x1, float y1, float x2, float y2, int color, float lineWidth) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = dx / length;
        float ny = dy / length;
        buffer.addVertex(pose, x1, y1, 0).setColor(color).setNormal(pose, nx, ny, 0).setLineWidth(lineWidth);
        buffer.addVertex(pose, x2, y2, 0).setColor(color).setNormal(pose, nx, ny, 0).setLineWidth(lineWidth);
    }

    public static void cleanup(HeadsideMonitorBlockEntity blockEntity) {
        waveformHistories.remove(blockEntity);
    }
}
