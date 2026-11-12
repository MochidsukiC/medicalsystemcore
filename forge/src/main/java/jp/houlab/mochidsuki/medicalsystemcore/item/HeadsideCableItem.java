package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class HeadsideCableItem extends Item {
    public static final String TAG_MONITOR_POS = "MonitorPos";
    public static final String TAG_CABLE_ID = "CableId";

    public HeadsideCableItem(Properties pProperties) {
        super(pProperties);
    }

    /** ケーブルが接続されているモニターの座標。NBT がなければ null。 */
    @Nullable
    public static BlockPos getLinkedPos(ItemStack cable) {
        CompoundTag nbt = cable.getTag();
        if (nbt == null || !nbt.contains(TAG_MONITOR_POS)) return null;
        return NbtUtils.readBlockPos(nbt.getCompound(TAG_MONITOR_POS));
    }

    @Nullable
    public static UUID getCableId(ItemStack cable) {
        CompoundTag nbt = cable.getTag();
        return nbt != null && nbt.hasUUID(TAG_CABLE_ID) ? nbt.getUUID(TAG_CABLE_ID) : null;
    }

    /** このケーブルが、指定のモニターが現在発行中のケーブル(座標と識別子が一致)か。 */
    public static boolean isLinkedTo(ItemStack cable, HeadsideMonitorBlockEntity monitor) {
        return monitor.getBlockPos().equals(getLinkedPos(cable)) && monitor.isCableLinked(getCableId(cable));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        Level level = pPlayer.level();
        if (level.isClientSide() || !(pTarget instanceof Player targetPlayer)) {
            return InteractionResult.PASS;
        }

        BlockPos monitorPos = getLinkedPos(pStack);
        if (monitorPos == null) {
            pPlayer.sendSystemMessage(Component.translatable("message.medicalsystemcore.cable.not_linked").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        BlockEntity be = level.getBlockEntity(monitorPos);

        if (be instanceof HeadsideMonitorBlockEntity monitor && isLinkedTo(pStack, monitor)) {
            // モニターに監視対象のUUIDを設定
            monitor.setMonitoredPlayerUUID(Optional.of(targetPlayer.getUUID()));
            // ケーブルを消費するので、モニターは新しいケーブルを出せる状態に戻す
            monitor.returnCable();

            pPlayer.sendSystemMessage(Component.literal("§a" + targetPlayer.getName().getString() + " をモニターに接続しました。"));
            // ケーブルを消費(クリエイティブでは pStack が Player.interactOn の作ったコピーなので、手持ちの実物を減らす)
            pPlayer.getItemInHand(pHand).shrink(1);
            return InteractionResult.SUCCESS;
        }

        pPlayer.sendSystemMessage(Component.translatable("message.medicalsystemcore.cable.invalid").withStyle(ChatFormatting.RED));
        return InteractionResult.FAIL;
    }
}