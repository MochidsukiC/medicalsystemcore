package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundMedicalDataSyncPacket;
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

            // アイテムにクールダウンを設定 (30秒)
            player.getCooldowns().addCooldown(this, 30 * 20);
            Level level = player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 蘇生確率を取得
            float chance = medicalData.getResuscitationChance();
            boolean success = false;

            if (currentStatus == HeartStatus.CARDIAC_ARREST) {
                // 心停止(Asystole)の場合：蘇生確率 = chance
                if (Math.random() < chance / 100.0) {
                    success = true;
                } else {
                    // 失敗時、40%の確率で心室細動(VF)に移行
                    if (Math.random() < 0.4) {
                        medicalData.setHeartStatus(HeartStatus.VF);
                        player.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心室細動に移行した。"));
                    } else {
                        player.sendSystemMessage(Component.literal("§c蘇生失敗。"));
                    }
                }
            } else if (currentStatus == HeartStatus.VF) {
                // 心室細動(VF)の場合：蘇生確率 = chance * 2
                if (Math.random() < (chance * 2) / 100.0) {
                    success = true;
                } else {
                    // 失敗時、40%の確率で心停止(Asystole)に移行
                    if (Math.random() < 0.4) {
                        medicalData.setHeartStatus(HeartStatus.CARDIAC_ARREST);
                        player.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心停止に移行した。"));
                    } else {
                        player.sendSystemMessage(Component.literal("§c蘇生失敗。"));
                    }
                }
            }

            // 蘇生に成功した場合の処理
            if (success) {
                medicalData.setHeartStatus(HeartStatus.NORMAL);
                medicalData.setResuscitationChance(100.0f);
                medicalData.setCardiacArrestTimer(0);

                player.sendSystemMessage(Component.literal("§a蘇生成功！"));

                // 全てのクライアントに状態の更新とポーズ解除を通知
                ModPackets.sendToPlayer(new ClientboundMedicalDataSyncPacket(targetPlayer.getUUID(), HeartStatus.NORMAL), targetPlayer);
            } else {
                // 失敗した場合も、状態が変化した可能性があるのでデータを同期
                ModPackets.sendToPlayer(new ClientboundMedicalDataSyncPacket(targetPlayer.getUUID(), medicalData.getHeartStatus()), targetPlayer);
            }
        });

        return InteractionResult.SUCCESS;
    }
}