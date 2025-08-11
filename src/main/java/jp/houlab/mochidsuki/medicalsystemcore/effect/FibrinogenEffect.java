package jp.houlab.mochidsuki.medicalsystemcore.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class FibrinogenEffect extends MobEffect {
    public FibrinogenEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xADD8E6); // 有益、薄い青色
    }
}