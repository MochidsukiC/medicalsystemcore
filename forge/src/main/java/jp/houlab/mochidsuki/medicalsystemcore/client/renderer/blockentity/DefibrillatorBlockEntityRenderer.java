package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

public class DefibrillatorBlockEntityRenderer implements BlockEntityRenderer<DefibrillatorBlockEntity> {
    private final Font font;

    public DefibrillatorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(DefibrillatorBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        boolean isPowered = pBlockEntity.getBlockState().getValue(DefibrillatorBlock.POWERED);
        boolean isCharged = pBlockEntity.getBlockState().getValue(DefibrillatorBlock.CHARGED);
        Direction facing = pBlockEntity.getBlockState().getValue(DefibrillatorBlock.FACING);

        // ブロックの向きに応じた回転を適用
        pPoseStack.pushPose();
        pPoseStack.translate(0.5, 0, 0.5); // 中心に移動
        pPoseStack.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot())); // FACINGに応じて回転
        pPoseStack.translate(-0.5, 0, -0.5); // 元の位置に戻す

        // 描画が成功したデバッグ用のRenderTypeを使用
        VertexConsumer buffer = pBuffer.getBuffer(RenderType.debugQuads());

        // 1. 電源ランプ (赤)
        if (isPowered) {
            // JSON from: [2, 5, 3], to: [4, 7, 5], rotation: {angle: 22.5, axis: "x", origin: [1, 8, 3]}
            renderRotatedCube(pPoseStack, buffer, 2, 5, 3, 4, 7, 5, 22.5f, 1, 8, 3, 255, 0, 0, 100);
        }
        // 2. 充電完了ランプ (黄)
        if (isCharged) {
            // JSON from: [2, 8, 3], to: [4, 10, 5], rotation: {angle: 22.5, axis: "x", origin: [1, 8, 3]}
            renderRotatedCube(pPoseStack, buffer, 2, 8, 3, 4, 10, 5, 22.5f, 1, 8, 3, 255, 255, 0, 100);

            if(pBlockEntity.getLevel().getGameTime() % 20 >10){
                renderRotatedCube(pPoseStack, buffer, 5, 4, 3, 6, 10, 5, 22.5f, 1, 8, 3, 255, 165, 0, 150);
            }
        }
        // 3. チャージゲージ (オレンジ)
        if (pBlockEntity.isCharging()) {
            // getChargeProgress()を呼び出して、0.0～1.0の進捗を取得
            float chargeProgress = pBlockEntity.getChargeProgress();

            // JSONの from: [5, 4, 3], to: [6, 10, 5] に対応
            float totalBarHeight = 10f - 4f;
            int currentBarHeight = (int) (totalBarHeight * chargeProgress);
            renderRotatedCube(pPoseStack, buffer, 5, 4, 3, 6, 4 + currentBarHeight, 5, 22.5f, 1, 8, 3, 255, 165, 0, 150);
        }

        // 4. クールダウン
        if (pBlockEntity.isOnCooldown()) {
            long secondsLeft = pBlockEntity.getCooldownSecondsLeft();
            String text = String.valueOf(secondsLeft);

            // 専用メソッドを呼び出してテキストを描画
            renderTextOnDisplay(pPoseStack, pBuffer, text);
        }

        pPoseStack.popPose(); // ブロック全体の回転を終了
    }

    /**
     * JSONのエレメント定義に合わせて、回転した立方体を描画するヘルパーメソッド
     */
    private void renderRotatedCube(PoseStack poseStack, VertexConsumer buffer,
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
        float f_fromX = new_fromX / 16f; float f_fromY = new_fromY / 16f; float f_fromZ = new_fromZ / 16f;
        float f_toX = new_toX / 16f; float f_toY = new_toY / 16f; float f_toZ = new_toZ / 16f;
        float f_rotOriginX = rotOriginX / 16f; float f_rotOriginY = rotOriginY / 16f; float f_rotOriginZ = rotOriginZ / 16f;

        // 1. JSONで定義された回転軸の中心に移動
        poseStack.translate(f_rotOriginX, f_rotOriginY, f_rotOriginZ);
        // 2. JSONで定義された角度でX軸中心に回転
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        // 3. 回転軸から元の位置に戻す
        poseStack.translate(-f_rotOriginX, -f_rotOriginY, -f_rotOriginZ);

        Matrix4f matrix = poseStack.last().pose();

        // renderCubeのロジック
        // 下面 (Y-)
        buffer.vertex(matrix, f_fromX, f_fromY, f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_fromY, f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_fromY, f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_fromY, f_toZ  ).color(r, g, b, a).endVertex();
        // 上面 (Y+)
        buffer.vertex(matrix, f_fromX, f_toY,   f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_toY,   f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_toY,   f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_toY,   f_fromZ).color(r, g, b, a).endVertex();
        // 北面 (Z-)
        buffer.vertex(matrix, f_fromX, f_fromY, f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_toY,   f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_toY,   f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_fromY, f_fromZ).color(r, g, b, a).endVertex();
        // 南面 (Z+)
        buffer.vertex(matrix, f_toX,   f_fromY, f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_toY,   f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_toY,   f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_fromY, f_toZ  ).color(r, g, b, a).endVertex();
        // 西面 (X-)
        buffer.vertex(matrix, f_fromX, f_fromY, f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_toY,   f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_toY,   f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_fromX, f_fromY, f_fromZ).color(r, g, b, a).endVertex();
        // 東面 (X+)
        buffer.vertex(matrix, f_toX,   f_fromY, f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_toY,   f_fromZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_toY,   f_toZ  ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, f_toX,   f_fromY, f_toZ  ).color(r, g, b, a).endVertex();

        poseStack.popPose(); // この描画専用の状態を終了
    }

    private void renderTextOnDisplay(PoseStack poseStack, MultiBufferSource pBuffer, String text) {
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
        Matrix4f matrix = poseStack.last().pose();

        // 基準点がディスプレイの中心になったため、文字の幅と高さの半分だけオフセットすれば中央揃えになる
        float xOffset = - (this.font.width(text) / 2f);
        float yOffset = - (this.font.lineHeight / 2f);
        int color = 0xFFFFFFFF; // 白色
        int maxLight = 15728880; // 最大光量

        this.font.drawInBatch(text, xOffset, yOffset, color, false, matrix, pBuffer, Font.DisplayMode.NORMAL, 0, maxLight);

        poseStack.popPose(); // テキスト描画の座標変換を終了
    }
}