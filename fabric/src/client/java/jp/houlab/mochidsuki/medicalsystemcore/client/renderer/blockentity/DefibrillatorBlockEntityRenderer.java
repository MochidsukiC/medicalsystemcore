package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
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

public class DefibrillatorBlockEntityRenderer implements BlockEntityRenderer<DefibrillatorBlockEntity, DefibrillatorBlockEntityRenderer.State> {
    private final Font font;

    /** 描画に必要な値の抽出先。blockState は BlockEntityRenderState 側では private なので、必要な値を自前で持つ。 */
    public static class State extends BlockEntityRenderState {
        public boolean powered;
        public boolean charged;
        public Direction facing = Direction.NORTH;
        /** 充電完了ランプの横のゲージを点滅させる位相(ゲーム時間 % 20 > 10)。 */
        public boolean blinkOn;
        public boolean charging;
        public float chargeProgress;
        public boolean onCooldown;
        public long cooldownSecondsLeft;
    }

    public DefibrillatorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DefibrillatorBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.powered = blockEntity.getBlockState().getValue(DefibrillatorBlock.POWERED);
        state.charged = blockEntity.getBlockState().getValue(DefibrillatorBlock.CHARGED);
        state.facing = blockEntity.getBlockState().getValue(DefibrillatorBlock.FACING);
        state.blinkOn = blockEntity.getLevel().getGameTime() % 20 > 10;
        state.charging = blockEntity.isCharging();
        // getChargeProgress()を呼び出して、0.0～1.0の進捗を取得
        state.chargeProgress = blockEntity.getChargeProgress();
        state.onCooldown = blockEntity.isOnCooldown();
        state.cooldownSecondsLeft = state.onCooldown ? blockEntity.getCooldownSecondsLeft() : 0;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // ブロックの向きに応じた回転を適用
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5); // 中心に移動
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.getOpposite().toYRot())); // FACINGに応じて回転
        poseStack.translate(-0.5, 0, -0.5); // 元の位置に戻す

        // 1. 電源ランプ (赤)
        if (state.powered) {
            // JSON from: [2, 5, 3], to: [4, 7, 5], rotation: {angle: 22.5, axis: "x", origin: [1, 8, 3]}
            submitRotatedCube(poseStack, collector, 2, 5, 3, 4, 7, 5, 22.5f, 1, 8, 3, 255, 0, 0, 100);
        }
        // 2. 充電完了ランプ (黄)
        if (state.charged) {
            // JSON from: [2, 8, 3], to: [4, 10, 5], rotation: {angle: 22.5, axis: "x", origin: [1, 8, 3]}
            submitRotatedCube(poseStack, collector, 2, 8, 3, 4, 10, 5, 22.5f, 1, 8, 3, 255, 255, 0, 100);

            if (state.blinkOn) {
                submitRotatedCube(poseStack, collector, 5, 4, 3, 6, 10, 5, 22.5f, 1, 8, 3, 255, 165, 0, 150);
            }
        }
        // 3. チャージゲージ (オレンジ)
        if (state.charging) {
            // JSONの from: [5, 4, 3], to: [6, 10, 5] に対応
            float totalBarHeight = 10f - 4f;
            int currentBarHeight = (int) (totalBarHeight * state.chargeProgress);
            submitRotatedCube(poseStack, collector, 5, 4, 3, 6, 4 + currentBarHeight, 5, 22.5f, 1, 8, 3, 255, 165, 0, 150);
        }

        // 4. クールダウン
        if (state.onCooldown) {
            // 専用メソッドを呼び出してテキストを描画
            submitTextOnDisplay(poseStack, collector, String.valueOf(state.cooldownSecondsLeft));
        }

        poseStack.popPose(); // ブロック全体の回転を終了
    }

    /**
     * JSONのエレメント定義に合わせて、回転した立方体を描画するヘルパーメソッド。
     * Forge は RenderType.debugQuads() に直接頂点を書いていた。26.2 では submitCustomGeometry で同じ RenderType に書く。
     */
    private void submitRotatedCube(PoseStack poseStack, SubmitNodeCollector collector,
                                   float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                                   float angle, float rotOriginX, float rotOriginY, float rotOriginZ,
                                   int r, int g, int b, int a) {

        poseStack.pushPose(); // この描画専用の状態を開始

        // Blockbenchの座標(0-16)をMinecraftの座標(0.0-1.0)に変換
        // --- 中心点を計算 ---
        float centerX = (fromX + toX) / 2f;
        float centerY = (fromY + toY) / 2f;
        float centerZ = (fromZ + toZ) / 2f;

        // --- サイズを計算 ---
        float sizeX = toX - fromX;
        float sizeY = toY - fromY;
        float sizeZ = toZ - fromZ;

        // --- 1.5倍に拡大 ---
        float scale = 1.1f;
        float scaledSizeX = sizeX * scale;
        float scaledSizeY = sizeY * scale;
        float scaledSizeZ = sizeZ * scale;

        // --- 拡大後の新しいfromとtoを計算 ---
        float new_fromX = centerX - (scaledSizeX / 2f);
        float new_fromY = centerY - (scaledSizeY / 2f);
        float new_fromZ = centerZ - (scaledSizeZ / 2f);
        float new_toX = centerX + (scaledSizeX / 2f);
        float new_toY = centerY + (scaledSizeY / 2f);
        float new_toZ = centerZ + (scaledSizeZ / 2f);

        // Blockbenchの座標(0-16)をMinecraftの座標(0.0-1.0)に変換
        final float f_fromX = new_fromX / 16f; final float f_fromY = new_fromY / 16f; final float f_fromZ = new_fromZ / 16f;
        final float f_toX = new_toX / 16f; final float f_toY = new_toY / 16f; final float f_toZ = new_toZ / 16f;
        float f_rotOriginX = rotOriginX / 16f; float f_rotOriginY = rotOriginY / 16f; float f_rotOriginZ = rotOriginZ / 16f;

        // 1. JSONで定義された回転軸の中心に移動
        poseStack.translate(f_rotOriginX, f_rotOriginY, f_rotOriginZ);
        // 2. JSONで定義された角度でX軸中心に回転
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        // 3. 回転軸から元の位置に戻す
        poseStack.translate(-f_rotOriginX, -f_rotOriginY, -f_rotOriginZ);

        // renderCubeのロジック
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            // 下面 (Y-)
            buffer.addVertex(pose, f_fromX, f_fromY, f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_fromY, f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_fromY, f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_fromY, f_toZ  ).setColor(r, g, b, a);
            // 上面 (Y+)
            buffer.addVertex(pose, f_fromX, f_toY,   f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_toY,   f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_toY,   f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_toY,   f_fromZ).setColor(r, g, b, a);
            // 北面 (Z-)
            buffer.addVertex(pose, f_fromX, f_fromY, f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_toY,   f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_toY,   f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_fromY, f_fromZ).setColor(r, g, b, a);
            // 南面 (Z+)
            buffer.addVertex(pose, f_toX,   f_fromY, f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_toY,   f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_toY,   f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_fromY, f_toZ  ).setColor(r, g, b, a);
            // 西面 (X-)
            buffer.addVertex(pose, f_fromX, f_fromY, f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_toY,   f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_toY,   f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_fromX, f_fromY, f_fromZ).setColor(r, g, b, a);
            // 東面 (X+)
            buffer.addVertex(pose, f_toX,   f_fromY, f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_toY,   f_fromZ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_toY,   f_toZ  ).setColor(r, g, b, a);
            buffer.addVertex(pose, f_toX,   f_fromY, f_toZ  ).setColor(r, g, b, a);
        });

        poseStack.popPose(); // この描画専用の状態を終了
    }

    private void submitTextOnDisplay(PoseStack poseStack, SubmitNodeCollector collector, String text) {
        poseStack.pushPose(); // テキスト描画専用の座標変換を開始

        // --- Step 1: モデルの傾き(22.5度)に、描画空間全体を合わせる ---
        // JSONの主要な回転基点 origin:[0, 2, 0] を反映
        poseStack.translate(0.0/16.0, 2.0/16.0, 0.0/16.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(22.5f));
        poseStack.translate(0.0/16.0, -2.0/16.0, 0.0/16.0);

        // --- Step 2: 傾いた空間の中で、ディスプレイの「中心点」に移動 ---
        // JSONの黒い画面エレメント from:[7, 4, 3.5] to:[14, 10, 4.5]
        float centerX = (7f + 14f) / 2f / 16f; // = 10.5 / 16
        float centerY = (4f + 11f) / 2f / 16f; // = 7.0 / 16
        float centerZ = (3.5f) / 2f / 16f; // = 4.0 / 16
        // Zを少し手前に出して表示のチラつきを防ぐ
        poseStack.translate(centerX, centerY, centerZ - 0.801f/16f);

        // --- Step 3: テキスト描画のために、空間を2Dスクリーンに変換 ---
        // Y軸で180度回転させ、こちらを向ける
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        // スケールを調整し、Yをマイナスにすることで文字の上下を正しくする
        float scale = 0.02f;
        poseStack.scale(scale, -scale, scale);

        // --- Step 4: テキストを描画 ---
        // 基準点がディスプレイの中心になったため、文字の幅と高さの半分だけオフセットすれば中央揃えになる
        float xOffset = - (this.font.width(text) / 2f);
        float yOffset = - (this.font.lineHeight / 2f);
        int color = 0xFFFFFFFF; // 白色
        int maxLight = 15728880; // 最大光量

        FormattedCharSequence sequence = FormattedCharSequence.forward(text, Style.EMPTY);
        collector.submitText(poseStack, xOffset, yOffset, sequence, false, Font.DisplayMode.NORMAL, maxLight, color, 0, 0);

        poseStack.popPose(); // テキスト描画の座標変換を終了
    }
}
