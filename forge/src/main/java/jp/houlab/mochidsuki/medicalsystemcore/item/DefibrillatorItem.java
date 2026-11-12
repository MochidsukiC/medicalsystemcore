package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.DefibrillationRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.Ticks;
import jp.houlab.mochidsuki.medicalsystemcore.core.DefibrillationHandler;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DefibrillatorItem extends Item {
    public DefibrillatorItem(Properties properties) {
        super(properties);
    }

    /**
     * このアイテムでエンティティを右クリックした時に呼び出されるメソッド
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }

        // サーバーサイドでのみ実行し、対象がプレイヤーでなければ何もしない
        if (player.level().isClientSide || !(target instanceof ServerPlayer targetPlayer)) {
            return InteractionResult.PASS;
        }

        // 対象プレイヤーのCapabilityを取得
        targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            HeartStatus currentStatus = medicalData.getHeartStatus();

            // 正常なプレイヤーには使用できない
            if (currentStatus == HeartStatus.NORMAL) {
                player.sendSystemMessage(Component.literal("§e対象は正常です。"));
                return;
            }

            // アイテムにクールダウンを設定
            player.getCooldowns().addCooldown(this, Ticks.fromSeconds(MedicalConfig.get(MedicalConfigKeys.DEFIBRILLATOR_COOLDOWN)));
            Level level = player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 判定と状態の適用は QTE 経路と共通(DefibrillationRules / DefibrillationHandler)。QTE 補正なし
            DefibrillationRules.Result shock = DefibrillationHandler.shockWithoutQte(medicalData);

            switch (shock.outcome()) {
                case SUCCESS -> player.sendSystemMessage(Component.literal("§a蘇生成功！"));
                case FAILED_TRANSITIONED -> {
                    if (shock.newStatus() == HeartStatus.VF) {
                        player.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心室細動に移行した。"));
                    } else {
                        player.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心停止に移行した。"));
                    }
                }
                // NORMAL は冒頭で除外済み。QTE_FAILED は QTE を経ないこの経路では返らない
                default -> player.sendSystemMessage(Component.literal("§c蘇生失敗。"));
            }

            // 成功・失敗(状態移行を含む)のいずれでも状態が変化した可能性があるので対象へ同期
            ModPackets.sendToPlayer(new ClientboundCoreStatsPacket(targetPlayer.getUUID(), medicalData.getHeartStatus(), medicalData), targetPlayer);
        });

        return InteractionResult.SUCCESS;
    }
}