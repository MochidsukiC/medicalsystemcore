package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BandageRules;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.core.PendingHealManager;
import jp.houlab.mochidsuki.medicalsystemcore.item.BandageItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアントからサーバーへ、治療が完了したことを通知するパケット（Config値使用版）
 */
public class ServerboundFinishHealPacket {
    /**
     * 経過時間の許容(tick)。クライアントは右クリックの押下から数えるが、サーバーの記録は
     * 開始パケットの到着時刻なので、通信の揺らぎぶんだけ経過がクライアントより短く見える。
     * 5 tick(0.25 秒)あれば通常の回線の揺らぎを吸収できる。
     */
    private static final int ELAPSED_TOLERANCE_TICKS = 5;

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

            // サーバーが記録した治療の開始に対する完了であること
            PendingHealManager.Pending pending = PendingHealManager.peek(healer);
            if (pending == null) return;

            Entity target = healer.level().getEntity(this.targetEntityId);
            if (!(target instanceof ServerPlayer targetPlayer) || !targetPlayer.getUUID().equals(pending.targetUuid())) return;

            // 自分自身には使えない
            if (targetPlayer == healer) return;

            // 距離（5ブロック以内）
            if (healer.distanceToSqr(targetPlayer) >= 25.0) return;

            // 治療に必要な時間が経過していること
            int requiredTicks = BandageRules.durationTicks(MedicalConfig.get(MedicalConfigKeys.BANDAGE_OTHER_USE_DURATION));
            long elapsed = healer.server.overworld().getGameTime() - pending.startGameTime();
            if (elapsed < requiredTicks - ELAPSED_TOLERANCE_TICKS) return;

            // 送信者に意識があり、メインハンドに包帯を持っていること
            boolean healerConscious = healer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                    .map(data -> data.isConscious())
                    .orElse(false);
            if (!healerConscious) return;
            ItemStack bandage = healer.getMainHandItem();
            if (!(bandage.getItem() instanceof BandageItem)) return;

            // 受理: 記録を消し、包帯を 1 個消費する（クリエイティブは消費しない）
            PendingHealManager.remove(healer);
            if (!healer.getAbilities().instabuild) {
                bandage.shrink(1);
            }

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
        });
        return true;
    }
}
