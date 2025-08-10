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
import org.jetbrains.annotations.Nullable;

public class DefibrillatorBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final BooleanProperty HAS_PADS = BooleanProperty.create("has_pads");


    public DefibrillatorBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(CHARGED, false).setValue(HAS_PADS, true));

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(POWERED, CHARGED,HAS_PADS);
    }

    // 右クリック処理
    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS; // クライアント側では常に成功を返し、腕振りモーションを再生
        }

        if (!(pLevel.getBlockEntity(pPos) instanceof DefibrillatorBlockEntity be)) {
            return InteractionResult.FAIL;
        }

        // --- クリックされたY座標で処理を分岐 ---
        // pHit.getLocation()はワールド座標なので、ブロックのローカル座標に変換
        double hitY = pHit.getLocation().y() - pPos.getY();

        // ブロックの上部 (11/16以上) をクリックした場合 -> 電極の処理
        if (hitY >= 11.0 / 16.0) {
            handlePadInteraction(pPlayer, pHand, be);
        }
        // ブロックの下部をクリックした場合 -> 機械の操作
        else {
            handleMachineInteraction(pPlayer, be);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 電極の出し入れを担当するメソッド
     */
    private void handlePadInteraction(Player player, InteractionHand hand, DefibrillatorBlockEntity be) {
        ItemStack heldItem = player.getItemInHand(hand);
        Level level = player.level();
        BlockPos pos = be.getBlockPos();

        // 電極を戻す
        if (heldItem.is(Medicalsystemcore.ELECTRODE.get())) {
            be.arePadsTaken = false;
            heldItem.shrink(1);
            player.sendSystemMessage(Component.literal("§e電極を収納しました。"));
            // ▼▼▼ ブロックの状態を「パッドあり」に更新 ▼▼▼
            level.setBlock(pos, be.getBlockState().setValue(HAS_PADS, true), 3);
            be.setChanged();
        }
        // 電極を取り出す
        else if (heldItem.isEmpty() && !be.arePadsTaken) {
            be.arePadsTaken = true;

            ItemStack electrodeStack = new ItemStack(Medicalsystemcore.ELECTRODE.get());
            CompoundTag nbt = electrodeStack.getOrCreateTag();
            nbt.put("DefibrillatorPos", NbtUtils.writeBlockPos(be.getBlockPos()));

            player.setItemInHand(hand, electrodeStack);
            player.sendSystemMessage(Component.literal("§a電極を準備しました。"));
            // ▼▼▼ ブロックの状態を「パッドなし」に更新 ▼▼▼
            level.setBlock(pos, be.getBlockState().setValue(HAS_PADS, false), 3);
            be.setChanged();
        }
        // 電極がすでに取り出されている
        else if (heldItem.isEmpty() && be.arePadsTaken) {
            player.sendSystemMessage(Component.literal("§c電極は既に使用中です。"));
        }
    }

    /**
     * チャージ操作を担当するメソッド
     */
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

        // 全ての条件をクリアした場合、チャージを開始
        be.startCharge();
        player.sendSystemMessage(Component.literal("§eチャージを開始します..."));
    }

    @Override
    public void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        if (!pLevel.isClientSide) {
            // 設置された瞬間に、隣接するレッドストーン信号があるかチェック
            boolean isPowered = pLevel.hasNeighborSignal(pPos);
            if (pState.getValue(POWERED) != isPowered) {
                pLevel.setBlock(pPos, pState.setValue(POWERED, isPowered), 2);
            }
        }
    }

    // レッドストーン信号の処理
    @Override
    public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        if (!pLevel.isClientSide) {
            boolean isPowered = pLevel.hasNeighborSignal(pPos);
            if (pState.getValue(POWERED) != isPowered) {
                pLevel.setBlock(pPos, pState.setValue(POWERED, isPowered), 2);
            }
        }
    }

    // Tick処理を有効にする
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
}