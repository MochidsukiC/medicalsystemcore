package jp.houlab.mochidsuki.medicalsystemcore.effect;

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
            // 蘇生確率を毎秒0.5%回復 (1tickあたり 0.5/20 %)
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                data.setResuscitationChance(data.getResuscitationChance() + 0.5f / 20.0f);
            });
        }
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true; // 毎フレーム効果を適用
    }
}