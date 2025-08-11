package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.IVStandBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
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

public class TubeItem extends Item {
    private static final String TAG_START_POS = "StartPos";

    public TubeItem(Properties pProperties) {
        super(pProperties);
    }

    /**
     * ブロックを右クリックした時 (始点の設定)
     */
    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        BlockPos clickedPos = pContext.getClickedPos();
        Player player = pContext.getPlayer();
        ItemStack heldItem = pContext.getItemInHand();

        if (!level.isClientSide && player != null) {
            // クリックしたブロックが点滴スタンドか確認
            if (level.getBlockState(clickedPos).getBlock() instanceof IVStandBlock) {
                // NBTタグに始点の座標を書き込む
                CompoundTag nbt = heldItem.getOrCreateTag();
                // 必ず下のブロックの座標を記録する
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

    /**
     * 他のエンティティを右クリックした時 (終点の設定)
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        Level level = pPlayer.level();
        if (!level.isClientSide && pTarget instanceof Player targetPlayer) {
            CompoundTag nbt = pStack.getTag();

            // ▼▼▼ ここのキー名を修正 ▼▼▼
            if (nbt != null && nbt.contains(TAG_START_POS)) {
                // ▼▼▼ ここのキー名を修正 ▼▼▼
                BlockPos startPos = NbtUtils.readBlockPos(nbt.getCompound(TAG_START_POS));
                BlockEntity be = level.getBlockEntity(startPos);

                if (be instanceof IVStandBlockEntity standEntity) {
                    boolean hasAnyPack = false;
                    for (int i = 0; i < standEntity.itemHandler.getSlots(); i++) {
                        if (!standEntity.itemHandler.getStackInSlot(i).isEmpty()) {
                            hasAnyPack = true;
                            break;
                        }
                    }

                    if (hasAnyPack) {
                        targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                            data.setTransfusingFromStandPos(Optional.of(startPos));
                        });

                        pPlayer.sendSystemMessage(Component.literal("§a" + targetPlayer.getName().getString() + " への点滴を開始しました。"));
                        targetPlayer.sendSystemMessage(Component.literal("§a点滴が開始されました。"));

                        // ▼▼▼ ここのキー名を修正 ▼▼▼
                        nbt.remove(TAG_START_POS);
                        return InteractionResult.SUCCESS;
                    }
                }
                pPlayer.sendSystemMessage(Component.literal("§cエラー: 点滴スタンドに有効なパックがありません。"));
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.PASS;
    }
}