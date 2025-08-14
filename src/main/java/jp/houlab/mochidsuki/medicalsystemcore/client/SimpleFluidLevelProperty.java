package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.item.FluidPackItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SimpleFluidLevelProperty implements ItemPropertyFunction {

    @Override
    public float call(ItemStack pStack, @Nullable ClientLevel pLevel, @Nullable LivingEntity pEntity, int pSeed) {
        if (pStack.getItem() instanceof FluidPackItem) {
            int fluidLevel = FluidPackItem.getFluidLevel(pStack);
            // 0.1-0.9の範囲で返す（0.1刻み）
            return fluidLevel / 10.0f;
        }
        return 0.9f; // デフォルト満タン
    }
}