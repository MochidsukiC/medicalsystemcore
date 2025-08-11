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
            int color = 0xFFFFFFFF; // 白色

            // 専用メソッドを呼び出してテキストを描画
            renderTextOnDisplay(pPoseStack, pBuffer, 0xF000F0, text, color);
        }
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
    /**
     * モデルのディスプレイ部分にテキストを描画する専用メソッド
     */
    private void renderTextOnDisplay(PoseStack poseStack, MultiBufferSource buffer, int light, String text, int color) {
        poseStack.pushPose(); // テキスト描画専用の状態を開始

        // --- Step 1: モデル全体の回転に合わせる ---
        // あなたのモデルの主要なエレメントと同じ回転を適用し、描画空間を傾ける
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(22.5f));
        poseStack.translate(-0.5, -0.5, -0.5);

        // --- Step 2: ディスプレイの位置へ移動 ---
        // JSONの黒い画面エレメント from:[7, 4, 3.5] に基づき、描画の基準点を移動
        // Z座標を少し手前に出す(0.001f)ことで、モデルとの表示のチラつきを防ぐ
        poseStack.translate(7f/16f, 4f/16f, 3.5f/16f + 0.001f);

        // --- Step 3: テキスト描画用の設定 ---
        // 描画の向きを調整
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180));

        // スケールを調整 (フォントはピクセル単位なので非常に小さくする)
        float scale = 0.005f;
        poseStack.scale(scale, scale, scale);

        // --- Step 4: テキストの描画 ---
        Matrix4f matrix = poseStack.last().pose();

        // テキストを中央に配置するためのオフセットを計算
        // ディスプレイの幅 (14-7=7) をスケールで割ってピクセル幅に変換
        float displayPixelWidth = (7f / 16f) / scale;
        float textWidth = this.font.width(text);
        float xOffset = (displayPixelWidth - textWidth) / 2f;

        // 実際にテキストを描画
        this.font.drawInBatch(text, xOffset, 0, color, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);

        poseStack.popPose(); // テキスト描画の状態を終了
    }
}