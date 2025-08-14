package jp.houlab.mochidsuki.medicalsystemcore.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.model.StretcherModel;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class StretcherRenderer extends EntityRenderer<StretcherEntity> {

    // ブロックで使っていたテクスチャをエンティティにも使用します
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(Medicalsystemcore.MODID, "textures/entity/stretcher_entity.png");
    private final StretcherModel model;

    public StretcherRenderer(EntityRendererProvider.Context context) {
        super(context);
        // コンテキストからモデルレイヤーをベイク（構築）して、モデルをインスタンス化します
        this.model = new StretcherModel(context.bakeLayer(StretcherModel.LAYER_LOCATION));
    }

    @Override
    public void render(StretcherEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose(); // 現在の描画状態を保存

        // モデルの基準位置を調整
        poseStack.translate(0, 0.1, 0);

        // エンティティの向きに合わせてモデルをY軸回転させる
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));

        // モデルを描画
        this.model.renderToBuffer(poseStack, buffer.getBuffer(this.model.renderType(getTextureLocation(entity))), packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose(); // 保存した描画状態に戻す
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(StretcherEntity entity) {
        return TEXTURE_LOCATION;
    }
}