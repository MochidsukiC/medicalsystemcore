package jp.houlab.mochidsuki.medicalsystemcore.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class StretcherRenderer extends EntityRenderer<StretcherEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation("medicalsystemcore", "textures/entity/stretcher.png");
    private final StretcherModel model;

    public StretcherRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new StretcherModel(context.bakeLayer(StretcherModel.LAYER_LOCATION));
    }

    @Override
    public void render(StretcherEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        pPoseStack.pushPose();

        // モデルがエンティティの進行方向を向くように回転を設定
        pPoseStack.mulPose(Axis.YP.rotationDegrees(90.0F - pEntity.getYRot()));

        VertexConsumer vertexConsumer = pBuffer.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(pEntity)));
        this.model.renderToBuffer(pPoseStack, vertexConsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        pPoseStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pPoseStack, pBuffer, pPackedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(StretcherEntity pEntity) {
        return TEXTURE_LOCATION;
    }

    public static class StretcherModel extends EntityModel<StretcherEntity> {
        public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("medicalsystemcore", "stretcher"), "main");

        private final ModelPart root;

        public StretcherModel(ModelPart root) {
            this.root = root;
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition meshdefinition = new MeshDefinition();
            PartDefinition partdefinition = meshdefinition.getRoot();

            // 本体: 幅16、高さ2、長さ32の直方体
            partdefinition.addOrReplaceChild("main_body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 22.0F, -16.0F, 16.0F, 2.0F, 32.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

            // 脚1
            partdefinition.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(0, 34).addBox(-7.75F, 19.75F, -16.0F, 1.25F, 2.5F, 32.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

            // 脚2
            partdefinition.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(0, 34).addBox(6.5F, 19.75F, -16.0F, 1.25F, 2.5F, 32.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

            return LayerDefinition.create(meshdefinition, 64, 64);
        }

        @Override
        public void setupAnim(StretcherEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            // ストレッチャーは静的なモデルなので、アニメーションの設定は不要
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}