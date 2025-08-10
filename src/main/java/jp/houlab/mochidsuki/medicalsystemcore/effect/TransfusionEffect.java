package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class TransfusionEffect extends MobEffect {
    public TransfusionEffect() {
        // エフェクトの種類（有益）、色（血液なので赤色）を設定
        super(MobEffectCategory.BENEFICIAL, 0xFF0000);
    }

    /**
     * エフェクトが有効な間、毎フレーム呼ばれる
     */
    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        if (!pLivingEntity.level().isClientSide() && pLivingEntity instanceof Player player) {
            // 1分間に2%回復 (1tickあたり 1/20 %)
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                data.setBloodLevel(data.getBloodLevel() + 2f / 20.0f/60);
            });
        }
    }

    /**
     * applyEffectTickを毎フレーム呼び出すかどうか
     */
    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true;
    }
}