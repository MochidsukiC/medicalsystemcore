package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.capability.ModAttachments;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.HemostaticRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class TranexamicAcidEffect extends MobEffect {
    public TranexamicAcidEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x90EE90); // 有益、薄い緑色
    }

    /** ServerLevel を受け取る(= サーバー側でだけ呼ばれる)。戻り値 true は効果を継続する。 */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        // プレイヤーにのみ効果を適用
        if (entity instanceof Player player) {
            // Config値を使用した出血速度減少
            var data = player.getAttachedOrCreate(ModAttachments.MEDICAL_DATA);
            data.setBleedingSpeed(HemostaticRules.reduceBleeding(
                    data.getBleedingSpeed(),
                    MedicalConfig.get(MedicalConfigKeys.TRANEXAMIC_ACID_BLEEDING_REDUCTION_RATE),
                    amplifier));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // 毎フレーム効果を適用
    }
}
