package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.blockentity.HeadsideMonitorBlockEntity;
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

import java.util.Optional;

public class HeadsideCableItem extends Item {
    public HeadsideCableItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        Level level = pPlayer.level();
        if (level.isClientSide() || !(pTarget instanceof Player targetPlayer)) {
            return InteractionResult.PASS;
        }

        CompoundTag nbt = pStack.getTag();
        if (nbt == null || !nbt.contains("MonitorPos")) {
            return InteractionResult.FAIL;
        }

        BlockPos monitorPos = NbtUtils.readBlockPos(nbt.getCompound("MonitorPos"));
        BlockEntity be = level.getBlockEntity(monitorPos);

        if (be instanceof HeadsideMonitorBlockEntity monitor) {
            // モニターに監視対象のUUIDを設定
            monitor.setMonitoredPlayerUUID(Optional.of(targetPlayer.getUUID()));
            // ケーブルを返却した扱いにする
            monitor.setCableTaken(false);

            pPlayer.sendSystemMessage(Component.literal("§a" + targetPlayer.getName().getString() + " をモニターに接続しました。"));
            pStack.shrink(1); // ケーブルを消費
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }
}