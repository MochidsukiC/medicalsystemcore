package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
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
     * Config値を使用した輸血回復処理
     */
    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        if (!pLivingEntity.level().isClientSide() && pLivingEntity instanceof Player player) {
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // Config値を使用した血液回復
                double recoveryAmount = Config.TRANSFUSION_RECOVERY_RATE / 20.0; // 毎tick回復量
                float currentBlood = medicalData.getBloodLevel();
                float newBloodLevel = Math.min(100.0f, currentBlood + (float) recoveryAmount);

                medicalData.setBloodLevel(newBloodLevel);

                // クライアントに状態を同期（毎秒1回程度に制限）
                if (player.level().getGameTime() % 20 == 0 && player instanceof ServerPlayer serverPlayer) {
                    ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                            serverPlayer.getUUID(),
                            medicalData.getHeartStatus(),
                            medicalData
                    ), serverPlayer);
                }
            });
        }
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true;
    }
}