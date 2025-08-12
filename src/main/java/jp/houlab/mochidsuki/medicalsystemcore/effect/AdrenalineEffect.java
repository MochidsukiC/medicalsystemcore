package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class AdrenalineEffect extends MobEffect {
    public AdrenalineEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF7F50); // 有益な効果、色をオレンジ系に
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        // サーバーサイドのプレイヤーにのみ効果を適用
        if (!pLivingEntity.level().isClientSide() && pLivingEntity instanceof Player player) {
            // Config値を使用した蘇生確率回復
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                double recoveryAmount = Config.ADRENALINE_RESUSCITATION_RECOVERY_RATE / 20.0; // 毎tick回復量
                float currentChance = data.getResuscitationChance();
                float newChance = Math.min(100.0f, currentChance + (float) recoveryAmount);
                data.setResuscitationChance(newChance);
            });
        }
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true; // 毎フレーム効果を適用
    }
}