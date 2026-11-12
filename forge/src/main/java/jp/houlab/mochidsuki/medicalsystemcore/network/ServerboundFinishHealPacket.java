package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BandageRules;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアントからサーバーへ、治療が完了したことを通知するパケット（Config値使用版）
 */
public class ServerboundFinishHealPacket {
    private final int targetEntityId;

    public ServerboundFinishHealPacket(int targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public ServerboundFinishHealPacket(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.targetEntityId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // この処理はサーバーサイドで実行される
            ServerPlayer healer = context.getSender();
            if (healer == null) return;

            Entity target = healer.level().getEntity(this.targetEntityId);
            if (target instanceof Player targetPlayer) {
                // サーバー側で最終的な検証を行う
                if (healer.distanceToSqr(targetPlayer) < 25.0) { // 5ブロック以内
                    targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                        MobEffectInstance existingEffect = targetPlayer.getEffect(Medicalsystemcore.BANDAGE_EFFECT.get());
                        int currentLevel = 0;
                        if (existingEffect != null) {
                            currentLevel = BandageRules.levelFromAmplifier(existingEffect.getAmplifier());
                        }

                        int newLevel = BandageRules.nextLevel(currentLevel,
                                MedicalConfig.get(MedicalConfigKeys.BANDAGE_OTHER_LEVEL_INCREASE),
                                MedicalConfig.get(MedicalConfigKeys.BANDAGE_MAX_LEVEL));
                        int duration = BandageRules.durationTicks(MedicalConfig.get(MedicalConfigKeys.BANDAGE_EFFECT_DURATION));

                        targetPlayer.addEffect(new MobEffectInstance(
                                Medicalsystemcore.BANDAGE_EFFECT.get(),
                                duration,
                                BandageRules.amplifierForLevel(newLevel),
                                true,
                                true
                        ));

                        healer.level().playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
                    });
                }
            }
        });
        return true;
    }
}