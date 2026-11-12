package jp.houlab.mochidsuki.medicalsystemcore.block;

import com.mojang.serialization.MapCodec;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModTags;
import jp.houlab.mochidsuki.medicalsystemcore.item.ModItems;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class IVStandBlock extends BaseEntityBlock {
    public static final MapCodec<IVStandBlock> CODEC = simpleCodec(IVStandBlock::new);
    // ブロックが上半身か下半身かを定義するプロパティ
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    // FACING プロパティを追加
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);

    public IVStandBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // 破壊時に中身をドロップする処理(Forge では onRemove)は、26.2 の onRemove 廃止に伴い
    // IVStandBlockEntity#preRemoveSideEffects に移した(BE を持つ下半身だけが 1 回だけ行う)。

    // Forge の use は手を問わず 1 つのメソッドだったので、26.2 では手を受け取る useItemOn に全体を置く。
    @Override
    protected InteractionResult useItemOn(ItemStack pStack, BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "点滴スタンドの操作")) {
            return InteractionResult.FAIL;
        }

        // 下半身のブロックエンティティを取得
        BlockPos bePos = pState.getValue(HALF) == DoubleBlockHalf.LOWER ? pPos : pPos.below();
        if (pLevel.getBlockEntity(bePos) instanceof IVStandBlockEntity blockEntity) {
            ItemStack heldItem = pPlayer.getItemInHand(pHand);

            if (heldItem.isEmpty() && !pPlayer.isShiftKeyDown()) {
                // 手ぶらの場合はアイテム取り出し
                handleItemRemoval(pPlayer, blockEntity);
            } else if ((heldItem.is(ModTags.Items.BLOOD_PACKS) || heldItem.is(ModTags.Items.DRUG_PACKS)) && !pPlayer.isShiftKeyDown()) {
                // パックの場合はアイテム挿入
                handleItemInsertion(pPlayer, blockEntity, heldItem);
            } else if (heldItem.is(ModItems.TUBE)) {
                return InteractionResult.FAIL;
            } else {
                // その他のアイテムの場合はGUIを開く
                if (pPlayer instanceof ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(blockEntity);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void handleItemInsertion(Player player, IVStandBlockEntity blockEntity, ItemStack heldItem) {
        int targetSlot = -1;

        if (heldItem.is(ModTags.Items.BLOOD_PACKS)) {
            targetSlot = 0;
        } else if (heldItem.is(ModTags.Items.DRUG_PACKS)) {
            targetSlot = blockEntity.inventory.getItem(1).isEmpty() ? 1 : 2;
        }

        if (targetSlot != -1) {
            ItemStack currentStack = blockEntity.inventory.getItem(targetSlot);
            if (!currentStack.isEmpty()) {
                player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), currentStack));
            }
            blockEntity.inventory.setItem(targetSlot, heldItem.split(1));
        }
    }

    private void handleItemRemoval(Player player, IVStandBlockEntity blockEntity) {
        for (int i = 2; i >= 0; i--) {
            if (!blockEntity.inventory.getItem(i).isEmpty()) {
                // 満杯の場合は足元にドロップされ、パックが消えない
                player.handleExtraItemsCreatedOnUse(blockEntity.inventory.removeItem(i, 1));
                break;
            }
        }
    }

    // ブロックの状態定義にHALFプロパティとFACINGプロパティを追加
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(HALF, FACING);
    }

    // 当たり判定を返す
    @Override
    protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    // ブロックが設置された時の処理
    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        // 上のブロックを設置（FACINGも引き継ぐ）
        pLevel.setBlock(pPos.above(), pState.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    // 上下の片割れが失われたら、もう片方も消える（バニラのドアと同じ方式）。
    // 消えた側は Block.updateOrDestroy 経由で destroyBlock(drop=true) となり、loot table（下半身のみ）で 1 台だけドロップする。
    @Override
    protected BlockState updateShape(BlockState pState, LevelReader pLevel, ScheduledTickAccess pTicks, BlockPos pPos, Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource pRandom) {
        DoubleBlockHalf half = pState.getValue(HALF);
        if (pDirection.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (pDirection == Direction.UP)) {
            return pNeighborState.is(this) && pNeighborState.getValue(HALF) != half
                    ? pState
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(pState, pLevel, pTicks, pPos, pDirection, pNeighborPos, pNeighborState, pRandom);
    }

    // クリエイティブで上半身を壊したとき、下半身経由で台がドロップしないようにする（バニラの DoublePlantBlock と同じ）
    @Override
    public BlockState playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
        if (!pLevel.isClientSide() && pPlayer.isCreative() && pState.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pPos.below();
            BlockState lowerState = pLevel.getBlockState(lowerPos);
            if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                pLevel.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 35);
                pLevel.levelEvent(pPlayer, 2001, lowerPos, Block.getId(lowerState));
            }
        }
        return super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
    }

    // 設置可能かどうかのチェック
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        BlockPos pos = pContext.getClickedPos();
        Level level = pContext.getLevel();
        // Forge の getMaxBuildHeight は上限の 1 つ上(排他的)で、条件は「< getMaxBuildHeight() - 1」。26.2 の getMaxY は最上段の Y(包含的)
        if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(pContext)) {
            // プレイヤーの向きの逆方向を設定（プレイヤーと向き合うように）
            return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
        }
        return null;
    }

    // --- Block Entity関連の必須メソッド ---
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        // ブロックエンティティは下半身のブロックにのみ紐づく
        return pState.getValue(HALF) == DoubleBlockHalf.LOWER ? new IVStandBlockEntity(pPos, pState) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }
}
