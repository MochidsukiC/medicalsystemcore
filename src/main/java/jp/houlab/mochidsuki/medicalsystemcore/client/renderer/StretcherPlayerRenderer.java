package jp.houlab.mochidsuki.medicalsystemcore.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.util.AngleUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * ストレッチャー用カスタムプレイヤーレンダラー
 * プレイヤーがストレッチャーに乗っている時に横たわりポーズを描画
 */
public class StretcherPlayerRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * プレイヤーの描画をカスタマイズ（ストレッチャー用）
     */
    public static void renderStretcherPlayer(PoseStack poseStack, MultiBufferSource bufferSource,
                                             AbstractClientPlayer player, PlayerRenderer renderer,
                                             int packedLight, float partialTick) {

        if (!(player.getVehicle() instanceof StretcherEntity stretcher)) {
            return; // ストレッチャーに乗っていない場合は何もしない
        }

        float stretcherYaw = AngleUtils.normalizeAngle(stretcher.getYRot());

        poseStack.pushPose();

        // ストレッチャー用の変形を適用
        applyStretcherTransform(poseStack, stretcherYaw, partialTick);

        // プレイヤーモデルを取得してポーズを調整
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        customizeStretcherPose(model, stretcherYaw, player.tickCount + partialTick);

        poseStack.popPose();
    }

    /**
     * ストレッチャー用の座標変換を適用
     */
    private static void applyStretcherTransform(PoseStack poseStack, float stretcherYaw, float partialTick) {
        // プレイヤーを寝かせるための回転
        // X軸で90度回転させて横向きに
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));

        // 担架の向きに合わせてY軸回転
        poseStack.mulPose(Axis.YP.rotationDegrees(stretcherYaw));

        // 位置の微調整
        poseStack.translate(0.0, -0.3, 0.0);
    }

    /**
     * ストレッチャー用のカスタムポーズを適用
     */
    private static void customizeStretcherPose(PlayerModel<AbstractClientPlayer> model, float stretcherYaw, float animationTime) {
        // 基本的な横たわりポーズを設定

        // 頭の角度（少し横向き）
        model.head.xRot = 0.0f;
        model.head.yRot = 0.0f;
        model.head.zRot = 0.0f;

        // 体の角度
        model.body.xRot = 0.0f;
        model.body.yRot = 0.0f;
        model.body.zRot = 0.0f;

        // 右腕（体の横に）
        model.rightArm.xRot = 0.0f;
        model.rightArm.yRot = 0.0f;
        model.rightArm.zRot = 0.3f; // 少し外に開く

        // 左腕（体の横に）
        model.leftArm.xRot = 0.0f;
        model.leftArm.yRot = 0.0f;
        model.leftArm.zRot = -0.3f; // 少し外に開く

        // 右脚（まっすぐ）
        model.rightLeg.xRot = 0.0f;
        model.rightLeg.yRot = 0.0f;
        model.rightLeg.zRot = 0.05f;

        // 左脚（まっすぐ）
        model.leftLeg.xRot = 0.0f;
        model.leftLeg.yRot = 0.0f;
        model.leftLeg.zRot = -0.05f;

        // 微細な呼吸アニメーション（オプション）
        float breathingOffset = Mth.sin(animationTime * 0.1f) * 0.02f;
        model.body.yRot = breathingOffset;
    }
}