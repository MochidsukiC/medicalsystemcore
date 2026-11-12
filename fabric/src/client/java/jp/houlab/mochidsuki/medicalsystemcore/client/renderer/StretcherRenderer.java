package jp.houlab.mochidsuki.medicalsystemcore.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.model.StretcherModel;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class StretcherRenderer extends EntityRenderer<StretcherEntity, StretcherRenderer.State> {

    // ブロックで使っていたテクスチャをエンティティにも使用します
    private static final Identifier TEXTURE_LOCATION = Medicalsystemcore.id("textures/entity/stretcher_entity.png");
    private final StretcherModel model;

    /** 描画に必要な値の抽出先(26.2 の extract/submit 方式)。Forge の render の entityYaw に相当する yRot を持つ。 */
    public static class State extends EntityRenderState {
        public float yRot;
    }

    public StretcherRenderer(EntityRendererProvider.Context context) {
        super(context);
        // コンテキストからモデルレイヤーをベイク（構築）して、モデルをインスタンス化します
        this.model = new StretcherModel(context.bakeLayer(StretcherModel.LAYER_LOCATION));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StretcherEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // Forge の render(entityYaw) と同じ、前 tick からの補間済みのヨー
        state.yRot = entity.getYRot(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose(); // 現在の描画状態を保存

        // モデルの基準位置を調整
        poseStack.translate(0, 1.35, 0);

        // エンティティの向きに合わせてモデルをY軸回転させる
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));

        // モデルを描画
        submitNodeCollector.submitModel(this.model, state, poseStack, TEXTURE_LOCATION, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

        poseStack.popPose(); // 保存した描画状態に戻す
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
