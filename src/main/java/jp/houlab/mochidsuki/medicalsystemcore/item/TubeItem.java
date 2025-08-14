package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.IVStandBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Optional;
import java.util.UUID;

public class TubeItem extends Item {
    private static final String TAG_START_POS = "StartPos";

    public TubeItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        BlockPos clickedPos = pContext.getClickedPos();
        Player player = pContext.getPlayer();
        ItemStack heldItem = pContext.getItemInHand();


        if (!level.isClientSide && player != null) {
            if (level.getBlockState(clickedPos).getBlock() instanceof IVStandBlock) {
                CompoundTag nbt = heldItem.getOrCreateTag();
                BlockPos startPos = level.getBlockState(clickedPos).getValue(IVStandBlock.HALF) == DoubleBlockHalf.LOWER
                        ? clickedPos
                        : clickedPos.below();

                nbt.put(TAG_START_POS, NbtUtils.writeBlockPos(startPos));
                player.sendSystemMessage(Component.literal("§a始点を設定しました。終点となるプレイヤーを右クリックしてください。"));
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        Level level = pPlayer.level();
        if (!level.isClientSide && pTarget instanceof Player targetPlayer) {
            CompoundTag nbt = pStack.getTag();

            if (nbt != null && nbt.contains(TAG_START_POS)) {
                BlockPos startPos = NbtUtils.readBlockPos(nbt.getCompound(TAG_START_POS));
                BlockEntity be = level.getBlockEntity(startPos);

                if (be instanceof IVStandBlockEntity standEntity) {
                    // 修正: 既に他のプレイヤーが接続している場合は切断する
                    disconnectExistingConnections(level, startPos, targetPlayer.getUUID());

                    boolean hasAnyPack = false;
                    for (int i = 0; i < standEntity.itemHandler.getSlots(); i++) {
                        if (!standEntity.itemHandler.getStackInSlot(i).isEmpty()) {
                            hasAnyPack = true;
                            break;
                        }
                    }

                    // 修正: パックがなくても接続可能
                    targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                        data.setTransfusingFromStandPos(Optional.of(startPos));
                    });

                    if (hasAnyPack) {
                        pPlayer.sendSystemMessage(Component.literal("§a" + targetPlayer.getName().getString() + " への点滴を開始しました。"));
                        targetPlayer.sendSystemMessage(Component.literal("§a点滴が開始されました。"));
                    } else {
                        pPlayer.sendSystemMessage(Component.literal("§e" + targetPlayer.getName().getString() + " にチューブを接続しました。パックを補充してください。"));
                        targetPlayer.sendSystemMessage(Component.literal("§eチューブが接続されました。"));
                    }

                    nbt.remove(TAG_START_POS);
                    return InteractionResult.SUCCESS;
                }
                pPlayer.sendSystemMessage(Component.literal("§cエラー: 点滴スタンドが見つかりません。"));
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * 既存の接続を切断するメソッド
     */
    private void disconnectExistingConnections(Level level, BlockPos standPos, UUID newPlayerUUID) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 全てのプレイヤーをチェックして、この点滴スタンドに接続しているプレイヤーを見つける
        for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
            if (player.getUUID().equals(newPlayerUUID)) continue; // 新しく接続するプレイヤーはスキップ

            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                Optional<BlockPos> connectedStand = data.getTransfusingFromStandPos();
                if (connectedStand.isPresent() && connectedStand.get().equals(standPos)) {
                    // 既存の接続を切断
                    data.setTransfusingFromStandPos(Optional.empty());
                    player.sendSystemMessage(Component.literal("§e点滴が切断されました。（新しいプレイヤーが接続されました）"));
                }
            });
        }
    }
}