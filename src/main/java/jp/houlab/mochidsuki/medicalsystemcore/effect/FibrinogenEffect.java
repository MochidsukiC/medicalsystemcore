package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
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
                double reductionPerSecond = Config.FIBRINOGEN_BLEEDING_REDUCTION_RATE;
                double reductionPerTick = reductionPerSecond / 20.0; // 毎tick減少量
                float currentSpeed = data.getBleedingSpeed();

                // エフェクトレベルによって効果を調整（レベル1 = amplifier 0）
                double adjustedReduction = reductionPerTick * (pAmplifier + 1);

                float newSpeed = Math.max(0.0f, currentSpeed - (float) adjustedReduction);
                data.setBleedingSpeed(newSpeed);
            });
        }
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true; // 毎フレーム効果を適用
    }
}