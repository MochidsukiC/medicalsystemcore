
package jp.houlab.mochidsuki.medicalsystemcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.util.AngleUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアント側でのストレッチャープレイヤーレンダリングイベント処理
 * 完全なレンダリング置き換え版
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class StretcherClientEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();

        // ストレッチャーに乗っているかチェック
        if (player.getVehicle() instanceof StretcherEntity stretcher) {
            // デフォルトのレンダリングをキャンセル
            event.setCanceled(true);

            // カスタムレンダリングを実行
            renderStretcherPlayerComplete(
                    event.getPoseStack(),
                    event.getMultiBufferSource(),
                    player,
                    event.getRenderer(),
                    event.getPackedLight(),
                    event.getPartialTick(),
                    stretcher
            );
        }
    }

    /**
     * ストレッチャー用の完全なプレイヤーレンダリング
     */
    private static void renderStretcherPlayerComplete(PoseStack poseStack, MultiBufferSource bufferSource,
                                                      AbstractClientPlayer player, PlayerRenderer renderer,
                                                      int packedLight, float partialTick, StretcherEntity stretcher) {

        float stretcherYaw = AngleUtils.normalizeAngle(stretcher.getYRot());

        poseStack.pushPose();

        // デバッグ：プレイヤーにレンダリング情報を表示
        if (player.tickCount % 20 == 0) { // 1秒毎
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    String.format("§6レンダリング - 担架角度: %.1f°", stretcherYaw)
            ));
        }

        // ストレッチャー用の完全な座標変換
        applyCompleteStretcherTransform(poseStack, stretcherYaw, partialTick);

        // プレイヤーモデルを取得
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        // ストレッチャー用のポーズを完全に設定
        setupStretcherPose(model, stretcherYaw, player.tickCount + partialTick);

        // プレイヤーテクスチャを取得
        ResourceLocation texture = renderer.getTextureLocation(player);

        // モデルを実際に描画
        renderPlayerModel(poseStack, bufferSource, model, texture, packedLight, player);

        poseStack.popPose();
    }

    /**
     * ストレッチャー用の完全な座標変換
     */
    private static void applyCompleteStretcherTransform(PoseStack poseStack, float stretcherYaw, float partialTick) {
        // 1. 最初にプレイヤーを中心に移動
        poseStack.translate(0.0, 1.5, 0.0);

        // 2. 担架の向きに合わせてY軸回転（まず向きを設定）
        poseStack.mulPose(Axis.YP.rotationDegrees(stretcherYaw));

        // 3. プレイヤーを横向きに寝かせる（Z軸で90度回転）
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));

        // 4. 位置の微調整
        poseStack.translate(0.0, -0.8, 0.0);

        // 5. スケール調整
        poseStack.scale(1.0f, 1.0f, 1.0f);
    }

    /**
     * ストレッチャー用のポーズを完全設定
     */
    private static void setupStretcherPose(PlayerModel<AbstractClientPlayer> model, float stretcherYaw, float animationTime) {
        // 全ての部位をリセット
        model.head.setRotation(0, 0, 0);
        model.body.setRotation(0, 0, 0);
        model.rightArm.setRotation(0, 0, 0);
        model.leftArm.setRotation(0, 0, 0);
        model.rightLeg.setRotation(0, 0, 0);
        model.leftLeg.setRotation(0, 0, 0);

        // 横たわりポーズの設定

        // 頭：少し上を向く
        model.head.xRot = -0.2f;
        model.head.yRot = 0.0f;
        model.head.zRot = 0.0f;

        // 体：まっすぐ
        model.body.xRot = 0.0f;
        model.body.yRot = 0.0f;
        model.body.zRot = 0.0f;

        // 右腕：体の横に自然に
        model.rightArm.xRot = 0.0f;
        model.rightArm.yRot = 0.0f;
        model.rightArm.zRot = 0.3f;

        // 左腕：体の横に自然に
        model.leftArm.xRot = 0.0f;
        model.leftArm.yRot = 0.0f;
        model.leftArm.zRot = -0.3f;

        // 右脚：まっすぐ
        model.rightLeg.xRot = 0.0f;
        model.rightLeg.yRot = 0.0f;
        model.rightLeg.zRot = 0.05f;

        // 左脚：まっすぐ
        model.leftLeg.xRot = 0.0f;
        model.leftLeg.yRot = 0.0f;
        model.leftLeg.zRot = -0.05f;

        // 微細な呼吸アニメーション
        float breathing = Mth.sin(animationTime * 0.05f) * 0.01f;
        model.body.yRot = breathing;
        model.head.yRot = breathing * 0.5f;
    }

    /**
     * プレイヤーモデルを実際に描画
     */
    private static void renderPlayerModel(PoseStack poseStack, MultiBufferSource bufferSource,
                                          PlayerModel<AbstractClientPlayer> model, ResourceLocation texture,
                                          int packedLight, AbstractClientPlayer player) {

        // レンダータイプを取得
        RenderType renderType = model.renderType(texture);
        var vertexConsumer = bufferSource.getBuffer(renderType);

        // モデルを描画
        model.renderToBuffer(
                poseStack,
                vertexConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f  // RGBA
        );

        // 追加の描画（アーマー、アイテムなど）は省略
        // 必要に応じて後で追加
    }
}