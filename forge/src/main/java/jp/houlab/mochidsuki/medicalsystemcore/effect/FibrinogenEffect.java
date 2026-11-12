package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.HemostaticRules;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class FibrinogenEffect extends MobEffect {
    public FibrinogenEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xADD8E6); // 有益、薄い青色
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        // サーバーサイドのプレイヤーにのみ効果を適用
        if (!pLivingEntity.level().isClientSide() && pLivingEntity instanceof Player player) {
            // Config値を使用した出血速度減少
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                data.setBleedingSpeed(HemostaticRules.reduceBleeding(
                        data.getBleedingSpeed(),
                        MedicalConfig.get(MedicalConfigKeys.FIBRINOGEN_BLEEDING_REDUCTION_RATE),
                        pAmplifier));
            });
        }
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true; // 毎フレーム効果を適用
    }
}