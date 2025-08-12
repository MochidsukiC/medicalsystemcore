package jp.houlab.mochidsuki.medicalsystemcore.effect;

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
        // エフェクトの種類（有益）、色（血液なので赤色）を設定
        super(MobEffectCategory.BENEFICIAL, 0xFF0000);
    }

    /**
     * エフェクトが有効な間、毎フレーム呼ばれる
     * 仕様: 輸血中、血液量を毎秒1%回復する
     */
    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        if (!pLivingEntity.level().isClientSide() && pLivingEntity instanceof Player player) {
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // 毎秒1%回復 = 毎tick 1/20 %回復
                float recoveryAmount = 1.0f / 20.0f;
                float currentBlood = medicalData.getBloodLevel();
                float newBloodLevel = Math.min(100.0f, currentBlood + recoveryAmount);

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

    /**
     * applyEffectTickを毎フレーム呼び出すかどうか
     */
    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true;
    }
}