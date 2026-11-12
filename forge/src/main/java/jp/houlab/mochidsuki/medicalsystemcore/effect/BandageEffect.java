package jp.houlab.mochidsuki.medicalsystemcore.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class BandageEffect extends MobEffect {
    public BandageEffect() {
        // エフェクトの種類（有益）、色（包帯なので白っぽい色）を設定
        super(MobEffectCategory.BENEFICIAL, 0xF8F8FF);
    }
}