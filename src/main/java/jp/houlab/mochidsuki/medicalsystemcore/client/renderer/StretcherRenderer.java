package jp.houlab.mochidsuki.medicalsystemcore.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 超単純化されたストレッチャーレンダリング
 * プレイヤーを横向きに寝かせるだけ
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class StretcherRenderer {

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();

        // ストレッチャーに乗っているかチェック
        if (!(player.getVehicle() instanceof StretcherEntity)) {
            return; // 通常のレンダリング
        }

        // ストレッチャー用の変形を適用
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // プレイヤーを横向きに寝かせる
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));

        // プレイヤーモデルを横たわりポーズに設定
        if (event.getRenderer().getModel() instanceof PlayerModel<?>) {
            PlayerModel<?> model = event.getRenderer().getModel();
            setLyingPose(model);
        }

        poseStack.popPose();
    }

    /**
     * 横たわりポーズを設定（最小限）
     */
    private static void setLyingPose(PlayerModel<?> model) {
        // すべての部位をリセット
        model.head.setRotation(0, 0, 0);
        model.body.setRotation(0, 0, 0);
        model.rightArm.setRotation(0, 0, 0.3f);  // 腕を少し開く
        model.leftArm.setRotation(0, 0, -0.3f);
        model.rightLeg.setRotation(0, 0, 0);
        model.leftLeg.setRotation(0, 0, 0);
    }
}