package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.capability.ModAttachments;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BloodRules;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class TransfusionEffect extends MobEffect {
    public TransfusionEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF0000);
    }

    /**
     * エフェクトが有効な間、毎フレーム呼ばれる
     * Config値を使用した輸血回復処理。ServerLevel を受け取る(= サーバー側でだけ呼ばれる)。戻り値 true は効果を継続する。
     */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            var medicalData = player.getAttachedOrCreate(ModAttachments.MEDICAL_DATA);
            medicalData.setBloodLevel(BloodRules.transfuse(
                    medicalData.getBloodLevel(),
                    MedicalConfig.get(MedicalConfigKeys.TRANSFUSION_RECOVERY_RATE)));

            // クライアントに状態を同期（毎秒1回程度に制限）
            if (level.getGameTime() % 20 == 0 && player instanceof ServerPlayer serverPlayer) {
                ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                        serverPlayer.getUUID(),
                        medicalData.getHeartStatus(),
                        medicalData
                ), serverPlayer);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
