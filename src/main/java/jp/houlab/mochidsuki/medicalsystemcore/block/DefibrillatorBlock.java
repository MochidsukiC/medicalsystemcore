package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DefibrillatorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final BooleanProperty HAS_PADS = BooleanProperty.create("has_pads");
    protected static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    public DefibrillatorBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(CHARGED, false)
                .setValue(HAS_PADS, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING, POWERED, CHARGED, HAS_PADS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        boolean isPowered = pContext.getLevel().hasNeighborSignal(pContext.getClickedPos());
        return this.defaultBlockState()
                .setValue(FACING, pContext.getHorizontalDirection())
                .setValue(POWERED, isPowered);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (pLevel.getBlockEntity(pPos) instanceof DefibrillatorBlockEntity defibrillatorEntity) {
            ItemStack heldItem = pPlayer.getItemInHand(pHand);

            // 電源が入っていない場合
            if (!pState.getValue(POWERED)) {
                pPlayer.sendSystemMessage(Component.literal("§c除細動器に電源が供給されていません。"));
                return InteractionResult.FAIL;
            }

            // 電極パッドが取り出されている場合
            if (!pState.getValue(HAS_PADS)) {
                pPlayer.sendSystemMessage(Component.literal("§c電極パッドが取り出されています。"));
                return InteractionResult.FAIL;
            }

            // クールダウン中の場合
            if (defibrillatorEntity.isOnCooldown()) {
                long secondsLeft = defibrillatorEntity.getCooldownSecondsLeft();
                pPlayer.sendSystemMessage(Component.literal("§cクールダウン中です。あと " + secondsLeft + " 秒お待ちください。"));
                return InteractionResult.FAIL;
            }

            // 充電中または充電完了の場合
            if (defibrillatorEntity.isCharging() || defibrillatorEntity.isCharged()) {
                pPlayer.sendSystemMessage(Component.literal("§c既に充電中または充電完了しています。"));
                return InteractionResult.FAIL;
            }

            // 手に何も持っていない場合は充電開始
            if (heldItem.isEmpty()) {
                defibrillatorEntity.startCharge();
                pPlayer.sendSystemMessage(Component.literal("§a除細動器の充電を開始しました。"));
                return InteractionResult.SUCCESS;
            }

            // 電極を持っている場合は電極パッドを取り出す
            if (heldItem.getItem() == Medicalsystemcore.ELECTRODE.get()) {
                ItemStack electrodeStack = new ItemStack(Medicalsystemcore.ELECTRODE.get(), 2);
                CompoundTag nbt = new CompoundTag();
                nbt.put("DefibrillatorPos", NbtUtils.writeBlockPos(pPos));
                electrodeStack.setTag(nbt);

                pPlayer.getInventory().add(electrodeStack);
                pLevel.setBlock(pPos, pState.setValue(HAS_PADS, false), 3);
                defibrillatorEntity.arePadsTaken = true;
                defibrillatorEntity.setChanged();

                pPlayer.sendSystemMessage(Component.literal("§a電極パッドを取り出しました。"));
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock()) && !pLevel.isClientSide) {
            // 電極パッドが取り出されている状態でブロックが破壊された場合の処理
            if (!pState.getValue(HAS_PADS)) {
                for (ServerPlayer player : pLevel.getServer().getPlayerList().getPlayers()) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() == Medicalsystemcore.ELECTRODE.get() && stack.hasTag()) {
                            CompoundTag nbt = stack.getTag();
                            if (nbt.contains("DefibrillatorPos")) {
                                BlockPos linkedPos = NbtUtils.readBlockPos(nbt.getCompound("DefibrillatorPos"));
                                if (linkedPos.equals(pPos)) {
                                    stack.shrink(stack.getCount());
                                    player.sendSystemMessage(Component.literal("§c除細動器が破壊されたため、電極が消失しました。"));
                                }
                            }
                        }
                    }
                }
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        if (!pLevel.isClientSide) {
            boolean isPowered = pLevel.hasNeighborSignal(pPos);
            if (pState.getValue(POWERED) != isPowered) {
                pLevel.setBlock(pPos, pState.setValue(POWERED, isPowered), 2);
            }
        }
    }

    @Override
    public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        if (!pLevel.isClientSide) {
            boolean isPowered = pLevel.hasNeighborSignal(pPos);
            if (pState.getValue(POWERED) != isPowered) {
                pLevel.setBlock(pPos, pState.setValue(POWERED, isPowered), 2);
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return createTickerHelper(pBlockEntityType, Medicalsystemcore.DEFIBRILLATOR_BLOCK_ENTITY.get(), DefibrillatorBlockEntity::tick);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new DefibrillatorBlockEntity(pPos, pState);
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }
}