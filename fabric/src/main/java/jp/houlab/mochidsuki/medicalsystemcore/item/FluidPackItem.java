package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.IvRules;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 輸液パック。残量(tick)と残量レベルは ModDataComponents.FLUID_VOLUME_TICKS / FLUID_LEVEL に持つ
 * (Forge の ItemStack NBT "FluidVolumeTicks" / "FluidLevel" に相当)。
 */
public class FluidPackItem extends Item {

    public FluidPackItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(ItemStack pStack, Item.TooltipContext pContext, TooltipDisplay pDisplay, Consumer<Component> pTooltip, TooltipFlag pFlag) {
        super.appendHoverText(pStack, pContext, pDisplay, pTooltip, pFlag);

        int maxTicks = getMaxTicks();

        if (pStack.has(ModDataComponents.FLUID_VOLUME_TICKS)) {
            int ticksLeft = pStack.getOrDefault(ModDataComponents.FLUID_VOLUME_TICKS, 0);
            int secondsLeft = ticksLeft / 20;
            int maxSeconds = maxTicks / 20;
            float percentage = (float) ticksLeft / maxTicks * 100;

            ChatFormatting color = percentage > 75 ? ChatFormatting.GREEN :
                    percentage > 50 ? ChatFormatting.YELLOW :
                            percentage > 25 ? ChatFormatting.GOLD : ChatFormatting.RED;

            pTooltip.accept(Component.literal("残量: " + secondsLeft + "/" + maxSeconds + "秒 ")
                    .withStyle(color)
                    .append(Component.literal("(" + String.format("%.1f", percentage) + "%)")
                            .withStyle(ChatFormatting.GRAY)));
        } else {
            pTooltip.accept(Component.literal("残量: 満タン (" + (maxTicks / 20) + "秒)")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public void inventoryTick(ItemStack pStack, ServerLevel pLevel, Entity pOwner, @Nullable EquipmentSlot pSlot) {
        // Forge の `!pStack.hasTag()`(タグが何も無い)に相当: コンポーネントを何も付けていないスタックだけ初期化する
        if (pStack.getComponentsPatch().isEmpty()) {
            pStack.set(ModDataComponents.FLUID_VOLUME_TICKS, getMaxTicks());
            // 残量レベルも同時に設定
            updateFluidLevel(pStack);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack pStack) {
        if (pStack.has(ModDataComponents.FLUID_VOLUME_TICKS)) {
            int maxTicks = getMaxTicks();
            int currentTicks = pStack.getOrDefault(ModDataComponents.FLUID_VOLUME_TICKS, 0);
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

    /** パック 1 個ぶんの満タン容量(tick)。 */
    public static int getMaxTicks() {
        return IvRules.packCapacityTicks(MedicalConfig.get(MedicalConfigKeys.IV_PACK_DURATION));
    }

    public static int getRemainingTicks(ItemStack stack) {
        // コンポーネントが無ければ満タン(Forge と同じ)
        return stack.getOrDefault(ModDataComponents.FLUID_VOLUME_TICKS, getMaxTicks());
    }

    public static void setRemainingTicks(ItemStack stack, int ticks) {
        stack.set(ModDataComponents.FLUID_VOLUME_TICKS, Math.max(0, ticks));
        updateFluidLevel(stack); // 残量レベルも更新
    }

    public static float getRemainingPercentage(ItemStack stack) {
        int currentTicks = getRemainingTicks(stack);
        int maxTicks = getMaxTicks();
        return (float) currentTicks / maxTicks;
    }

    public static boolean isEmpty(ItemStack stack) {
        return getRemainingTicks(stack) <= 0;
    }

    public static ItemStack createFullPack(Item packItem) {
        ItemStack stack = new ItemStack(packItem);
        setRemainingTicks(stack, getMaxTicks());
        return stack;
    }

    /**
     * 残量レベル（0-9）をデータコンポーネントに保存(items JSON の見た目の切替に使う)
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

        stack.set(ModDataComponents.FLUID_LEVEL, level);
    }

    /**
     * データコンポーネントから残量レベルを取得
     */
    public static int getFluidLevel(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FLUID_LEVEL, 9); // デフォルト満タン
    }
}
