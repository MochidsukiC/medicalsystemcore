package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class IVStandBlock extends BaseEntityBlock {
    // ブロックが上半身か下半身かを定義するプロパティ
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    // FACING プロパティを追加
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);

    public IVStandBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH));
    }

    // --- onRemoveメソッドを追加 (破壊時にアイテムをドロップ) ---
    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock())) {
            BlockPos bePos = pState.getValue(HALF) == DoubleBlockHalf.LOWER ? pPos : pPos.below();
            if (pLevel.getBlockEntity(bePos) instanceof IVStandBlockEntity blockEntity) {
                for (int i = 0; i < blockEntity.itemHandler.getSlots(); i++) {
                    ItemStack stack = blockEntity.itemHandler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        pLevel.addFreshEntity(new ItemEntity(pLevel, bePos.getX() + 0.5, bePos.getY() + 0.5, bePos.getZ() + 0.5, stack));
                    }
                }
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // 下半身のブロックエンティティを取得
        BlockPos bePos = pState.getValue(HALF) == DoubleBlockHalf.LOWER ? pPos : pPos.below();
        if (pLevel.getBlockEntity(bePos) instanceof IVStandBlockEntity blockEntity) {
            ItemStack heldItem = pPlayer.getItemInHand(pHand);

            if (heldItem.isEmpty()) {
                // 手ぶらの場合はアイテム取り出し
                handleItemRemoval(pPlayer, blockEntity);
            } else if (heldItem.is(ModTags.Items.BLOOD_PACKS) || heldItem.is(ModTags.Items.DRUG_PACKS)) {
                // パックの場合はアイテム挿入
                handleItemInsertion(pPlayer, blockEntity, heldItem);
            } else {
                // その他のアイテムの場合はGUIを開く
                if (pPlayer instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, blockEntity, bePos);
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
            targetSlot = blockEntity.itemHandler.getStackInSlot(1).isEmpty() ? 1 : 2;
        }

        if (targetSlot != -1) {
            ItemStack currentStack = blockEntity.itemHandler.getStackInSlot(targetSlot);
            if (!currentStack.isEmpty()) {
                player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), currentStack));
            }
            blockEntity.itemHandler.setStackInSlot(targetSlot, heldItem.split(1));
        }
    }

    private void handleItemRemoval(Player player, IVStandBlockEntity blockEntity) {
        for (int i = 2; i >= 0; i--) {
            if (!blockEntity.itemHandler.getStackInSlot(i).isEmpty()) {
                player.getInventory().add(blockEntity.itemHandler.extractItem(i, 1, false));
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
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    // ブロックが設置された時の処理
    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        // 上のブロックを設置（FACINGも引き継ぐ）
        pLevel.setBlock(pPos.above(), pState.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    // プレイヤーによってブロックが破壊される時の処理
    @Override
    public void playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
        if (!pLevel.isClientSide) {
            // 上下どちらかが壊されたら、もう片方も壊す
            DoubleBlockHalf half = pState.getValue(HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pPos.above() : pPos.below();
            BlockState otherState = pLevel.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                // 正しいブロックを壊す（クリエイティブモードでのドロップを防ぐため）
                pLevel.setBlock(otherPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
                pLevel.levelEvent(pPlayer, 2001, otherPos, Block.getId(otherState));
            }
        }
        super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
    }

    // 設置可能かどうかのチェック
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        BlockPos pos = pContext.getClickedPos();
        Level level = pContext.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(pContext)) {
            return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection());
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
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }
}