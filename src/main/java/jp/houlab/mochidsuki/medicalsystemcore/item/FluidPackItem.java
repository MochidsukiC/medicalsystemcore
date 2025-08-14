package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
import net.minecraft.ChatFormatting;
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
        int maxTicks = Config.IV_PACK_DURATION * 20;

        if (nbt != null && nbt.contains("FluidVolumeTicks")) {
            int ticksLeft = nbt.getInt("FluidVolumeTicks");
            int secondsLeft = ticksLeft / 20;
            int maxSeconds = maxTicks / 20;
            float percentage = (float) ticksLeft / maxTicks * 100;

            ChatFormatting color = percentage > 75 ? ChatFormatting.GREEN :
                    percentage > 50 ? ChatFormatting.YELLOW :
                            percentage > 25 ? ChatFormatting.GOLD : ChatFormatting.RED;

            pTooltipComponents.add(Component.literal("残量: " + secondsLeft + "/" + maxSeconds + "秒 ")
                    .withStyle(color)
                    .append(Component.literal("(" + String.format("%.1f", percentage) + "%)")
                            .withStyle(ChatFormatting.GRAY)));
        } else {
            pTooltipComponents.add(Component.literal("残量: 満タン (" + (maxTicks / 20) + "秒)")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public void inventoryTick(ItemStack pStack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        if (!pStack.hasTag()) {
            CompoundTag nbt = pStack.getOrCreateTag();
            nbt.putInt("FluidVolumeTicks", Config.IV_PACK_DURATION * 20);
            // 残量レベルも同時に設定
            updateFluidLevel(pStack);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack pStack) {
        CompoundTag nbt = pStack.getTag();
        if (nbt != null && nbt.contains("FluidVolumeTicks")) {
            int maxTicks = Config.IV_PACK_DURATION * 20;
            int currentTicks = nbt.getInt("FluidVolumeTicks");
            return currentTicks < maxTicks;
        }
        return false;
    }

    @Override
    public int getBarColor(ItemStack pStack) {
        float percentage = getRemainingPercentage(pStack);
        return percentage > 0.75f ? 0x00FF00 :
                percentage > 0.5f ? 0xFFFF00 :
                        percentage > 0.25f ? 0xFF8000 : 0xFF0000;
    }

    @Override
    public int getBarWidth(ItemStack pStack) {
        float percentage = getRemainingPercentage(pStack);
        return Math.round(13.0F * percentage);
    }

    // ========== ヘルパーメソッド ==========

    public static int getRemainingTicks(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains("FluidVolumeTicks")) {
            return nbt.getInt("FluidVolumeTicks");
        }
        return Config.IV_PACK_DURATION * 20;
    }

    public static void setRemainingTicks(ItemStack stack, int ticks) {
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt("FluidVolumeTicks", Math.max(0, ticks));
        updateFluidLevel(stack); // 残量レベルも更新
    }

    public static float getRemainingPercentage(ItemStack stack) {
        int currentTicks = getRemainingTicks(stack);
        int maxTicks = Config.IV_PACK_DURATION * 20;
        return (float) currentTicks / maxTicks;
    }

    public static boolean isEmpty(ItemStack stack) {
        return getRemainingTicks(stack) <= 0;
    }

    public static ItemStack createFullPack(Item packItem) {
        ItemStack stack = new ItemStack(packItem);
        setRemainingTicks(stack, Config.IV_PACK_DURATION * 20);
        return stack;
    }

    /**
     * 残量レベル（1-9）をNBTに保存
     */
    private static void updateFluidLevel(ItemStack stack) {
        float percentage = getRemainingPercentage(stack);
        int level;

        if (percentage <= 0) {
            level = 0; // 空
        } else if (percentage >= 1.0f) {
            level = 9; // 満タン
        } else {
            // 1-8の段階を計算
            level = Math.max(1, (int) Math.ceil(percentage * 8));
        }

        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt("FluidLevel", level);
    }

    /**
     * NBTから残量レベルを取得
     */
    public static int getFluidLevel(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains("FluidLevel")) {
            return nbt.getInt("FluidLevel");
        }
        return 9; // デフォルト満タン
    }
}