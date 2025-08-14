package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class StretcherBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> PART = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // ベッドのような低い当たり判定
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public StretcherBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, DoubleBlockHalf.LOWER));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        Direction direction = pContext.getHorizontalDirection();
        BlockPos blockpos = pContext.getClickedPos();
        BlockPos blockpos1 = blockpos.relative(direction);
        Level level = pContext.getLevel();

        // プレイヤーが向いている方向に2ブロック分のスペースがあるかチェック
        if (blockpos1.getY() < level.getMaxBuildHeight() &&
                level.getBlockState(blockpos1).canBeReplaced(pContext)) {
            return this.defaultBlockState().setValue(FACING, direction);
        } else {
            return null;
        }
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (!pLevel.isClientSide) {
            Direction direction = pState.getValue(FACING);
            BlockPos headPos = pPos.relative(direction);
            pLevel.setBlock(headPos, pState.setValue(PART, DoubleBlockHalf.UPPER), 3);
            pLevel.blockUpdated(pPos, Blocks.AIR);
            pState.updateNeighbourShapes(pLevel, pPos, 3);
        }
    }

    /**
     * 手動でブロックを設置する際に呼び出すヘルパーメソッド
     * StretcherPlacementHandlerから使用される
     */
    public static void placeDoubleBlock(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            Direction direction = state.getValue(FACING);
            BlockPos headPos = pos.relative(direction);

            // 足パーツを設置
            level.setBlock(pos, state.setValue(PART, DoubleBlockHalf.LOWER), 3);
            // 頭パーツを設置
            level.setBlock(headPos, state.setValue(PART, DoubleBlockHalf.UPPER), 3);

            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // 足パーツのBlockEntityのみを使用
        BlockPos footPos = getFootPosition(pPos, pState);
        if (!(pLevel.getBlockEntity(footPos) instanceof StretcherBlockEntity stretcherBE)) {
            return InteractionResult.FAIL;
        }

        // 新仕様: Shiftクリックで担架をエンティティ化（アイテムは取得/ドロップしない）
        if (pPlayer.isShiftKeyDown()) {
            return handleStretcherCollection(pLevel, pPos, pState, pPlayer, stretcherBE);
        }

        // 通常のクリックでプレイヤーを乗せる
        if (pPlayer instanceof ServerPlayer serverPlayer) {
            ServerPlayer occupyingPlayer = stretcherBE.getOccupyingPlayer();

            if (occupyingPlayer != null) {
                pPlayer.sendSystemMessage(Component.literal("§c担架は既に使用されています。"));
                return InteractionResult.FAIL;
            }

            // プレイヤーを担架に乗せる（足パーツの位置を使用）
            stretcherBE.setOccupyingPlayer(serverPlayer);
            serverPlayer.teleportTo(footPos.getX() + 0.5, footPos.getY() + 0.3, footPos.getZ() + 0.5);

            // 体の向きをブロックの向きに合わせる
            float blockYaw = pState.getValue(FACING).toYRot();
            serverPlayer.setYRot(blockYaw);
            serverPlayer.setXRot(0);
            serverPlayer.yBodyRot = blockYaw;
            serverPlayer.yBodyRotO = blockYaw;
            serverPlayer.setYHeadRot(blockYaw);
            serverPlayer.xRotO = 0;

            serverPlayer.sendSystemMessage(Component.literal("§e担架に乗りました。"));
        }

        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleStretcherCollection(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer, StretcherBlockEntity stretcherBE) {
        ServerPlayer occupyingPlayer = stretcherBE.getOccupyingPlayer();
        BlockPos footPos = getFootPosition(pPos, pState);

        // 重要: 回収フラグを先に設定してonRemoveでのアイテムドロップを防ぐ
        stretcherBE.setBeingCollected(true);

        if (occupyingPlayer != null) {
            // プレイヤーがストレッチャーの近くにいるかチェック
            double distance = occupyingPlayer.distanceToSqr(footPos.getX() + 0.5, footPos.getY() + 0.3, footPos.getZ() + 0.5);

            if (distance > 9.0) { // 3ブロック^2
                // プレイヤーが遠くにいる場合は警告してエンティティ作成をスキップ
                pPlayer.sendSystemMessage(Component.literal("§c登録されたプレイヤーがストレッチャーから離れています。"));
                stretcherBE.setBeingCollected(false); // フラグをリセット
                return InteractionResult.FAIL;
            } else {
                // プレイヤーが近くにいる場合はエンティティ化
                StretcherEntity stretcherEntity = StretcherEntity.create(
                        pLevel, pPlayer, occupyingPlayer
                );

                pPlayer.sendSystemMessage(Component.literal("§a担架を回収しました。"));
                occupyingPlayer.sendSystemMessage(Component.literal("§e担架が移動式になりました。"));
            }
        } else {
            // プレイヤーが乗っていない場合でも、空のエンティティとして回収
            StretcherEntity stretcherEntity = StretcherEntity.create(
                    pLevel, pPlayer, null
            );

            pPlayer.sendSystemMessage(Component.literal("§a担架を回収しました。"));
        }

        // 両方のブロックを削除（新仕様：アイテムは取得しない）
        removeBothBlocks(pLevel, pPos, pState);
        return InteractionResult.SUCCESS;
    }

    private BlockPos getFootPosition(BlockPos pPos, BlockState pState) {
        DoubleBlockHalf part = pState.getValue(PART);
        if (part == DoubleBlockHalf.LOWER) {
            return pPos; // 既に足パーツ
        } else {
            Direction direction = pState.getValue(FACING);
            return pPos.relative(direction.getOpposite()); // 頭パーツから足パーツへ
        }
    }

    private void removeBothBlocks(Level pLevel, BlockPos pPos, BlockState pState) {
        BlockPos otherPos = getOtherBlockPos(pPos, pState);

        // 両方のブロックを削除
        pLevel.removeBlock(pPos, false);
        if (pLevel.getBlockState(otherPos).is(this)) {
            pLevel.removeBlock(otherPos, false);
        }
    }

    @Override
    public void playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
        if (!pLevel.isClientSide && (pPlayer.isCreative() || !pPlayer.isCreative())) {
            DoubleBlockHalf part = pState.getValue(PART);
            BlockPos otherPos = getOtherBlockPos(pPos, pState);
            BlockState otherState = pLevel.getBlockState(otherPos);

            if (otherState.is(this) && otherState.getValue(PART) != part) {
                pLevel.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                pLevel.levelEvent(pPlayer, 2001, otherPos, Block.getId(otherState));
                if (!pPlayer.isCreative()) {
                    dropResources(pState, pLevel, pPos, null, pPlayer, pPlayer.getMainHandItem());
                    dropResources(otherState, pLevel, otherPos, null, pPlayer, pPlayer.getMainHandItem());
                }
            }
        }
        super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
    }

    private BlockPos getOtherBlockPos(BlockPos pPos, BlockState pState) {
        DoubleBlockHalf part = pState.getValue(PART);
        Direction direction = pState.getValue(FACING);

        if (part == DoubleBlockHalf.LOWER) {
            // 足パーツの場合、頭パーツの位置を返す
            return pPos.relative(direction);
        } else {
            // 頭パーツの場合、足パーツの位置を返す
            return pPos.relative(direction.getOpposite());
        }
    }

    @Override
    public BlockState updateShape(BlockState pState, Direction pDirection, BlockState pNeighborState, LevelAccessor pLevel, BlockPos pPos, BlockPos pNeighborPos) {
        DoubleBlockHalf part = pState.getValue(PART);
        Direction facing = pState.getValue(FACING);

        // 隣接するブロックが正しいかチェック
        if (part == DoubleBlockHalf.LOWER && pDirection == facing) {
            // 足パーツの場合、頭の方向をチェック
            return pNeighborState.is(this) && pNeighborState.getValue(PART) == DoubleBlockHalf.UPPER
                    ? pState : Blocks.AIR.defaultBlockState();
        } else if (part == DoubleBlockHalf.UPPER && pDirection == facing.getOpposite()) {
            // 頭パーツの場合、足の方向をチェック
            return pNeighborState.is(this) && pNeighborState.getValue(PART) == DoubleBlockHalf.LOWER
                    ? pState : Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(pState, pDirection, pNeighborState, pLevel, pPos, pNeighborPos);
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        DoubleBlockHalf part = pState.getValue(PART);
        Direction facing = pState.getValue(FACING);

        if (part == DoubleBlockHalf.LOWER) {
            // 足パーツの場合、頭パーツの位置がブロックを置けるかチェック
            BlockPos headPos = pPos.relative(facing);
            return pLevel.getBlockState(headPos).canBeReplaced();
        } else {
            // 頭パーツの場合、足パーツが存在するかチェック
            BlockPos footPos = pPos.relative(facing.getOpposite());
            BlockState footState = pLevel.getBlockState(footPos);
            return footState.is(this) && footState.getValue(PART) == DoubleBlockHalf.LOWER;
        }
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING, PART);
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        // 足パーツのみにBlockEntityを配置
        if (pState.getValue(PART) == DoubleBlockHalf.LOWER) {
            return new StretcherBlockEntity(pPos, pState);
        }
        return null;
    }

    /**
     * ブロックが破壊された時の処理
     */
    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock()) && !pLevel.isClientSide()) {
            // 足パーツのBlockEntityのみを処理
            BlockPos footPos = getFootPosition(pPos, pState);
            if (pLevel.getBlockEntity(footPos) instanceof StretcherBlockEntity stretcherBE) {
                ServerPlayer occupyingPlayer = stretcherBE.getOccupyingPlayer();

                // プレイヤーが乗っている場合は姿勢制御を解除
                if (occupyingPlayer != null) {
                    occupyingPlayer.sendSystemMessage(Component.literal("§e担架が破壊されました。"));
                }

                // 修正: シフトクリックによる回収の場合はアイテムをドロップしない
                if (!stretcherBE.isBeingCollected()) {
                    // ストレッチャーアイテムをドロップ（通常の破壊時のみ）
                    ItemStack stretcherItem = new ItemStack(Medicalsystemcore.STRETCHER.get());
                    popResource(pLevel, footPos, stretcherItem);
                }
            }
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }
}