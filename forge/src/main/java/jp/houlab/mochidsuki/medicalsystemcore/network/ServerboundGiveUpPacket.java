package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.core.GiveUpGuard;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.item.DoctorCardItem;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * クライアントからサーバーへ、ギブアップ（確殺リスポーン）または搬送を通知するパケット
 */
public class ServerboundGiveUpPacket {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerboundGiveUpPacket.class);

    public ServerboundGiveUpPacket() {
    }

    public ServerboundGiveUpPacket(FriendlyByteBuf buf) {
        // データなし
    }

    public void toBytes(FriendlyByteBuf buf) {
        // データなし
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // ギブアップが無効な設定では受け付けない
                if (!MedicalConfig.get(MedicalConfigKeys.GIVE_UP_ENABLED)) return;
                // 長押し時間(give_up_duration)の検証はクライアント側の判定のまま。サーバーでは検証しない。
                // 時間を偽って早く送れても、得をするのは本人のギブアップが早まるだけで、他者への影響はない。
                // 意識不明でなければ不正パケットとして無視（クライアントがギブアップを出す条件と同じ）
                if (medicalData.isConscious()) return;
                // 同じ意識不明の状態の間は 1 回しか受け付けない（意識が戻るまで 2 回目以降は無視）
                if (GiveUpGuard.hasUsed(player)) return;

                if (MedicalConfig.get(MedicalConfigKeys.TRANSPORT_MODE)) {
                    // === 搬送モード ===
                    int transportX = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_X);
                    int transportY = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_Y);
                    int transportZ = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_Z);
                    String transportDimension = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_DIMENSION);
                    LOGGER.info("Transport mode: destination=({}, {}, {}) dimension={}",
                            transportX, transportY, transportZ, transportDimension);
                    ResourceLocation dimRL = ResourceLocation.tryParse(transportDimension);
                    if (dimRL == null) {
                        LOGGER.warn("Invalid transport dimension: {}", transportDimension);
                        return;
                    }
                    ResourceKey<Level> targetDimension = ResourceKey.create(Registries.DIMENSION, dimRL);
                    ServerLevel targetLevel = player.server.getLevel(targetDimension);
                    if (targetLevel == null) {
                        LOGGER.warn("Transport dimension not found: {}", transportDimension);
                        return;
                    }

                    // ストレッチャー等から下車
                    player.stopRiding();

                    // 搬送先にテレポート（意識不明状態は維持）
                    player.teleportTo(targetLevel, transportX + 0.5, transportY, transportZ + 0.5, player.getYRot(), player.getXRot());

                    // 通報データのメモに「搬送」を追記
                    RescueDataManager.appendSystemMemo(player, "搬送");
                    GiveUpGuard.markUsed(player);

                    // 全プレイヤーに搬送通知
                    notifyStatusChange(player, "搬送");

                    // 医療データをクライアントに再同期
                    ModPackets.sendToAllTracking(
                            new ClientboundCoreStatsPacket(player.getUUID(), medicalData.getHeartStatus(), medicalData),
                            player
                    );
                } else {
                    // === 自死モード（既存動作） ===
                    // heartStatusをCARDIAC_ARRESTに設定（NORMALの場合のみ変更）
                    if (medicalData.getHeartStatus() == HeartStatus.NORMAL) {
                        medicalData.setHeartStatus(HeartStatus.CARDIAC_ARREST);
                    }

                    // ダメージ免疫を解除
                    medicalData.setDamageImmune(false);

                    // 通報データのメモに「死亡」を追記
                    RescueDataManager.appendSystemMemo(player, "死亡");

                    // ドクターカード所持者に死亡通知
                    notifyStatusChange(player, "死亡");

                    // player.kill() → GENERIC_KILL → onLivingDeath で heartStatus != NORMAL → 死亡成立
                    GiveUpGuard.markUsed(player);
                    player.kill();
                }
            });
        });
        return true;
    }

    /**
     * 搬送/死亡の状態変更を対象プレイヤーに通知する
     * - 搬送: 全プレイヤーに通知
     * - 死亡: ドクターカード所持者のみに通知
     */
    private static void notifyStatusChange(ServerPlayer patient, String status) {
        String patientName = patient.getGameProfile().getName();
        boolean isTransport = "搬送".equals(status);
        ChatFormatting statusColor = isTransport ? ChatFormatting.YELLOW : ChatFormatting.RED;

        for (ServerPlayer p : patient.server.getPlayerList().getPlayers()) {
            // 死亡の場合はドクターカード所持者のみ
            if (!isTransport) {
                boolean hasValid = DoctorCardItem.hasValidDoctorCard(p);
                MedicalAuthorizationUtil.MedicalCredentials credentials =
                        MedicalAuthorizationUtil.getMedicalCredentials(p);
                if (!hasValid || credentials == null) {
                    continue;
                }
            }

            // サウンド通知
            p.connection.send(new ClientboundSoundPacket(
                    SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS,
                    p.getX(), p.getY(), p.getZ(), 1.0f, 1.0f, p.level().getRandom().nextLong()
            ));

            // チャットメッセージ
            MutableComponent divider = Component.literal("========== 状態通知 ==========").withStyle(statusColor, ChatFormatting.BOLD);
            MutableComponent patientLine = Component.literal("患者: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(patientName).withStyle(ChatFormatting.WHITE));
            MutableComponent statusLine = Component.literal("ステータス: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(status).withStyle(statusColor));
            MutableComponent dividerEnd = Component.literal("==============================").withStyle(statusColor, ChatFormatting.BOLD);

            p.sendSystemMessage(divider);
            p.sendSystemMessage(patientLine);
            p.sendSystemMessage(statusLine);
            p.sendSystemMessage(dividerEnd);
        }
    }
}
