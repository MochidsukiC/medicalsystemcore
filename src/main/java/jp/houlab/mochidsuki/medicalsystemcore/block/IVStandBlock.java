package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class IVStandBlock extends BaseEntityBlock {
    // ブロックが上半身か下半身かを定義するプロパティ
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    // ブロックの当たり判定（細いポール状）
    protected static final VoxelShape SHAPE = Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);

    public IVStandBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.5f)
                .sound(SoundType.METAL)
                .noOcclusion()
        );
        // デフォルトの状態を「下半身」に設定
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    // ブロックの状態定義にHALFプロパティを追加
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(HALF);
    }

    // 当たり判定を返す
    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    // ブロックが設置された時の処理
    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        // 上のブロックを設置
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
            return this.defaultBlockState();
        }
        return null;
    }

    // ブロックが右クリックされた時の処理
    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide) {
            BlockPos bePos = pState.getValue(HALF) == DoubleBlockHalf.LOWER ? pPos : pPos.below();
            BlockEntity entity = pLevel.getBlockEntity(bePos);

            if (entity instanceof IVStandBlockEntity blockEntity) {
                // 右クリック処理は上のブロックでのみ実行
                if (pState.getValue(HALF) == DoubleBlockHalf.UPPER) {
                    ItemStack heldItem = pPlayer.getItemInHand(pHand);

                    // 手にアイテムを持っている場合：設置を試みる
                    if (!heldItem.isEmpty()) {
                        if (heldItem.is(ModTags.Items.BLOOD_PACKS)) {
                            // 輸血パックならスロット0に入れる
                            blockEntity.itemHandler.setStackInSlot(0, heldItem.split(1));
                        } else if (heldItem.is(ModTags.Items.DRUG_PACKS)) {
                            // 薬剤パックなら空いているスロット(1か2)に入れる
                            if (blockEntity.itemHandler.getStackInSlot(1).isEmpty()) {
                                blockEntity.itemHandler.setStackInSlot(1, heldItem.split(1));
                            } else if (blockEntity.itemHandler.getStackInSlot(2).isEmpty()) {
                                blockEntity.itemHandler.setStackInSlot(2, heldItem.split(1));
                            }
                        }
                        return InteractionResult.SUCCESS;
                    }
                    // 手に何も持っていない場合：回収を試みる（一番下のスロットから）
                    else {
                        for (int i = 2; i >= 0; i--) { // 2, 1, 0の順でチェック
                            if (!blockEntity.itemHandler.getStackInSlot(i).isEmpty()) {
                                pPlayer.getInventory().add(blockEntity.itemHandler.extractItem(i, 1, false));
                                return InteractionResult.SUCCESS;
                            }
                        }
                    }
                }
            }
        }
        return InteractionResult.PASS;
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