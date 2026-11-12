package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.HemostaticRules;
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
                data.setResuscitationChance(HemostaticRules.recoverResuscitationChance(
                        data.getResuscitationChance(),
                        MedicalConfig.get(MedicalConfigKeys.ADRENALINE_RESUSCITATION_RECOVERY_RATE)));
            });
        }
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true; // 毎フレーム効果を適用
    }
}