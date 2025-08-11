package jp.houlab.mochidsuki.medicalsystemcore.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FluidPackItem extends Item {
    public FluidPackItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        CompoundTag nbt = pStack.getTag();
        if (nbt != null && nbt.contains("FluidVolumeTicks")) {
            int ticksLeft = nbt.getInt("FluidVolumeTicks");
            int secondsLeft = ticksLeft / 20;
            pTooltipComponents.add(Component.literal("残量: " + secondsLeft + "秒"));
        } else {
            pTooltipComponents.add(Component.literal("残量: 満タン (60秒)"));
        }
    }

    @Override
    public void inventoryTick(ItemStack pStack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        // NBTタグが存在しない場合のみ、デフォルト値を書き込む
        if (!pStack.hasTag()) {
            pStack.getOrCreateTag().putInt("FluidVolumeTicks", 60 * 20);
        }
    }
}