package jp.houlab.mochidsuki.medicalsystemcore.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.houlab.mochidsuki.medicalsystemcore.block.IVStandBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 点滴スタンドに掛かった 3 スロットのパックを表示する。
 * Forge の ItemRenderer#renderStatic は、extract でアイテムの描画状態(ItemStackRenderState)を作り、submit で描く方式になった(CampfireRenderer と同じ)。
 */
public class IVStandBlockEntityRenderer implements BlockEntityRenderer<IVStandBlockEntity, IVStandBlockEntityRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    /** 描画に必要な値の抽出先。blockState は BlockEntityRenderState 側では private なので、必要な値を自前で持つ。 */
    public static class State extends BlockEntityRenderState {
        public boolean lowerHalf;
        public Direction facing = Direction.NORTH;
        /** スロット順。空スロットは null。 */
        public final List<@Nullable ItemStackRenderState> items = new ArrayList<>();
        /** アイテムの明るさ。ブロックの 1 つ上の位置の明るさ(Forge の getLightLevel と同じ)。 */
        public int itemLight;
    }

    public IVStandBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(IVStandBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        // 下半身のブロックでのみ描画する（重複を避けるため）
        state.lowerHalf = blockEntity.getBlockState().getValue(IVStandBlock.HALF) == DoubleBlockHalf.LOWER;
        state.items.clear();
        if (!state.lowerHalf) {
            return;
        }

        state.facing = blockEntity.getBlockState().getValue(IVStandBlock.FACING);
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();
        state.itemLight = getLightLevel(level, pos);

        int seed = (int) pos.asLong();
        // 3つのスロットをループで処理
        for (int i = 0; i < blockEntity.inventory.getContainerSize(); i++) {
            ItemStack itemStack = blockEntity.inventory.getItem(i);
            // アイテムが空でなければ描画
            if (itemStack.isEmpty()) {
                state.items.add(null);
                continue;
            }
            ItemStackRenderState itemState = new ItemStackRenderState();
            this.itemModelResolver.updateForTopItem(itemState, itemStack, ItemDisplayContext.FIXED, level, null, seed);
            state.items.add(itemState);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (!state.lowerHalf) {
            return;
        }

        poseStack.pushPose();

        // ブロック全体の回転（向きに応じて）
        poseStack.translate(0.5, 0, 0.5); // 中心に移動
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot())); // FACINGに応じて回転
        poseStack.translate(-0.5, 0, -0.5); // 元の位置に戻す

        for (int i = 0; i < state.items.size(); i++) {
            ItemStackRenderState itemState = state.items.get(i);
            if (itemState == null || itemState.isEmpty()) {
                continue;
            }

            poseStack.pushPose(); // 各アイテムを描画する前に状態を保存

            // スロット番号に応じて描画位置をずらす
            // 0: 中央, 1: X-方向, 2: X+方向
            double xOffset = (i - 0.5d) / 4d;

            // アイテムの描画位置を調整
            poseStack.translate(0.5d + xOffset, 1.48d, 0.5d);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            poseStack.scale(1f, 1f, 1f); // 少し小さくする

            // アイテムを描画
            itemState.submit(poseStack, submitNodeCollector, state.itemLight, OverlayTexture.NO_OVERLAY, 0);

            poseStack.popPose(); // このアイテムの描画が完了したので状態を元に戻す
        }

        poseStack.popPose(); // ブロック全体の回転を終了
    }

    // ブロックの位置の明るさを取得するヘルパーメソッド
    private static int getLightLevel(Level level, BlockPos pos) {
        int bLight = level.getBrightness(LightLayer.BLOCK, pos.above());
        int sLight = level.getBrightness(LightLayer.SKY, pos.above());
        return LightCoordsUtil.pack(bLight, sLight);
    }
}
