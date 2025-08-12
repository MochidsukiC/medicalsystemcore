package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;

public class IVStandBlockEntityRenderer implements BlockEntityRenderer<IVStandBlockEntity> {

    public IVStandBlockEntityRenderer(BlockEntityRendererProvider.Context pContext) {

    }

    @Override
    public void render(IVStandBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBufferSource, int pPackedLight, int pPackedOverlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        // itemHandlerを直接参照するように変更
        ItemStackHandler itemHandler = pBlockEntity.itemHandler;

        // 3つのスロットをループで処理
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack itemStack = itemHandler.getStackInSlot(i);

            // アイテムが空でなければ描画
            if (!itemStack.isEmpty()) {
                pPoseStack.pushPose(); // 各アイテムを描画する前に状態を保存

                // スロット番号に応じて描画位置をずらす
                // 0: 中央, 1: X-方向, 2: X+方向
                double xOffset = (i-0.5d)/4d;

                // アイテムの描画位置を調整
                pPoseStack.translate(0.5d + xOffset, 1.48d, 0.5d);
                pPoseStack.mulPose(Axis.YP.rotationDegrees(-90));
                pPoseStack.scale(1f, 1f, 1f); // 少し小さくする

                // アイテムを描画
                itemRenderer.renderStatic(
                        itemStack,
                        ItemDisplayContext.FIXED,
                        getLightLevel(pBlockEntity.getLevel(), pBlockEntity.getBlockPos()),
                        pPackedOverlay,
                        pPoseStack,
                        pBufferSource,
                        pBlockEntity.getLevel(),
                        (int) pBlockEntity.getBlockPos().asLong()
                );

                pPoseStack.popPose(); // このアイテムの描画が完了したので状態を元に戻す
            }
        }
    }

    // ブロックの位置の明るさを取得するヘルパーメソッド
    private int getLightLevel(Level pLevel, BlockPos pPos) {
        int bLight = pLevel.getBrightness(LightLayer.BLOCK, pPos.above());
        int sLight = pLevel.getBrightness(LightLayer.SKY, pPos.above());
        return LightTexture.pack(bLight, sLight);
    }
}
