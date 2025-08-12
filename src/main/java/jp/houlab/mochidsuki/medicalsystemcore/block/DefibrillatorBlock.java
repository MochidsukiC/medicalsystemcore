package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DefibrillatorBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final BooleanProperty HAS_PADS = BooleanProperty.create("has_pads");
    protected static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    public DefibrillatorBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(CHARGED, false).setValue(HAS_PADS, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(POWERED, CHARGED, HAS_PADS);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(pLevel.getBlockEntity(pPos) instanceof DefibrillatorBlockEntity be)) {
            return InteractionResult.FAIL;
        }

        double hitY = pHit.getLocation().y() - pPos.getY();

        if (hitY >= 11.0 / 16.0) {
            handlePadInteraction(pPlayer, pHand, be);
        } else {
            handleMachineInteraction(pPlayer, be);
        }

        return InteractionResult.SUCCESS;
    }

    private void handlePadInteraction(Player player, InteractionHand hand, DefibrillatorBlockEntity be) {
        ItemStack heldItem = player.getItemInHand(hand);
        Level level = player.level();
        BlockPos pos = be.getBlockPos();

        // 電極を戻す - 修正: この除細動器から取り出された電極のみ受け入れる
        if (heldItem.is(Medicalsystemcore.ELECTRODE.get())) {
            CompoundTag nbt = heldItem.getTag();
            if (nbt != null && nbt.contains("DefibrillatorPos")) {
                BlockPos electrodeOrigin = NbtUtils.readBlockPos(nbt.getCompound("DefibrillatorPos"));
                // この除細動器から取り出された電極かチェック
                if (pos.equals(electrodeOrigin)) {
                    be.arePadsTaken = false;
                    heldItem.shrink(1);
                    player.sendSystemMessage(Component.literal("§e電極を収納しました。"));
                    level.setBlock(pos, be.getBlockState().setValue(HAS_PADS, true), 3);
                    be.setChanged();
                } else {
                    player.sendSystemMessage(Component.literal("§cこの電極は別の除細動器のものです。"));
                }
            } else {
                player.sendSystemMessage(Component.literal("§cこの電極の接続先が不明です。"));
            }
        }
        // 電極を取り出す
        else if (heldItem.isEmpty() && !be.arePadsTaken) {
            be.arePadsTaken = true;

            ItemStack electrodeStack = new ItemStack(Medicalsystemcore.ELECTRODE.get());
            CompoundTag nbt = electrodeStack.getOrCreateTag();
            nbt.put("DefibrillatorPos", NbtUtils.writeBlockPos(be.getBlockPos()));

            player.setItemInHand(hand, electrodeStack);
            player.sendSystemMessage(Component.literal("§a電極を準備しました。"));
            level.setBlock(pos, be.getBlockState().setValue(HAS_PADS, false), 3);
            be.setChanged();
        }
        // 電極がすでに取り出されている
        else if (heldItem.isEmpty() && be.arePadsTaken) {
            player.sendSystemMessage(Component.literal("§c電極は既に使用中です。"));
        }
    }

    private void handleMachineInteraction(Player player, DefibrillatorBlockEntity be) {
        Level level = player.level();
        if (level == null) return;

        if (!be.getBlockState().getValue(POWERED)) {
            player.sendSystemMessage(Component.literal("§c電源が入っていません。"));
            return;
        }
        if (be.isCharging()) {
            player.sendSystemMessage(Component.literal("§e現在充電中です..."));
            return;
        }
        if (be.isCharged()) {
            player.sendSystemMessage(Component.literal("§a既に充電完了しています。"));
            return;
        }
        if (be.isOnCooldown()) {
            long secondsLeft = be.getCooldownSecondsLeft();
            player.sendSystemMessage(Component.literal("§cクールダウン中です... 残り" + secondsLeft + "秒"));
            return;
        }

        be.startCharge();
        player.sendSystemMessage(Component.literal("§eチャージを開始します..."));
    }

    // 修正: ブロック破壊時に電極を返却
    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock()) && !pLevel.isClientSide) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof DefibrillatorBlockEntity be && be.arePadsTaken) {
                // 電極が取り出されている場合、ドロップする
                ItemStack electrodeStack = new ItemStack(Medicalsystemcore.ELECTRODE.get());
                CompoundTag nbt = electrodeStack.getOrCreateTag();
                nbt.put("DefibrillatorPos", NbtUtils.writeBlockPos(pPos));

                net.minecraft.world.entity.item.ItemEntity itemEntity =
                        new net.minecraft.world.entity.item.ItemEntity(pLevel, pPos.getX(), pPos.getY(), pPos.getZ(), electrodeStack);
                pLevel.addFreshEntity(itemEntity);
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