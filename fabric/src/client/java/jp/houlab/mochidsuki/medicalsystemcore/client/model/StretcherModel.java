package jp.houlab.mochidsuki.medicalsystemcore.client.model;
// Made with Blockbench 4.12.6
// Exported for Minecraft version 1.17 or later with Mojang mappings

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * ストレッチャーエンティティのモデル。Forge の StretcherModel と同じ形状。
 * 26.2 では描画は Model#renderToBuffer(root 全体)に統一されるので、Forge の bb_main を直接描く renderToBuffer の override は不要。
 * レンダータイプは EntityModel の既定(entityCutout。26.2 でもカリングなし)のまま。
 */
public class StretcherModel extends EntityModel<EntityRenderState> {
	// Forge のまま。レンダラーが EntityRendererProvider.Context で bake してこのモデルのコンストラクタに渡す
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(Medicalsystemcore.id("textures/entity/stretcher_entity.png"), "main");

	public StretcherModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -2.0F, -16.0F, 16.0F, 1.0F, 32.0F, new CubeDeformation(0.0F))
		.texOffs(0, 33).addBox(6.5F, -1.0F, -20.0F, 1.25F, 1.25F, 40.0F, new CubeDeformation(0.0F))
		.texOffs(0, 74).addBox(-7.75F, -1.0F, -20.0F, 1.25F, 1.25F, 40.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}
}
