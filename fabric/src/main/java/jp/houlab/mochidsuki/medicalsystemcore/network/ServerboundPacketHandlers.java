package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.capability.ModAttachments;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BandageRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.DefibrillationRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.QteRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.RescueRules;
import jp.houlab.mochidsuki.medicalsystemcore.core.DefibrillationHandler;
import jp.houlab.mochidsuki.medicalsystemcore.core.GiveUpGuard;
import jp.houlab.mochidsuki.medicalsystemcore.core.PendingHealManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.PendingQteManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.effect.ModEffects;
import jp.houlab.mochidsuki.medicalsystemcore.item.BandageItem;
import jp.houlab.mochidsuki.medicalsystemcore.item.DoctorCardItem;
import jp.houlab.mochidsuki.medicalsystemcore.item.ElectrodeItem;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * クライアント -> サーバーの 6 payload の受信ハンドラ(Forge では各 Serverbound*Packet#handle にあった)。
 * ハンドラは Fabric がサーバーのメインスレッドで呼ぶ(Forge の enqueueWork の中に相当)。
 * 送信者は {@code context.player()}(Forge の context.getSender())。検証はすべて Forge 現行のまま。
 */
public final class ServerboundPacketHandlers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerboundPacketHandlers.class);

    /**
     * 経過時間の許容(tick)。クライアントは右クリックの押下から数えるが、サーバーの記録は
     * 開始パケットの到着時刻なので、通信の揺らぎぶんだけ経過がクライアントより短く見える。
     * 5 tick(0.25 秒)あれば通常の回線の揺らぎを吸収できる。
     */
    private static final int HEAL_ELAPSED_TOLERANCE_TICKS = 5;

    private ServerboundPacketHandlers() {
    }

    /** 6 payload の受信ハンドラを登録する。MOD 初期化で 1 回だけ呼ぶ(payload 型の登録 ModPackets#register の後)。 */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ServerboundFinishHealPacket.TYPE, ServerboundPacketHandlers::handleFinishHeal);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundQTETResultPacket.TYPE, ServerboundPacketHandlers::handleQteResult);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundGiveUpPacket.TYPE, ServerboundPacketHandlers::handleGiveUp);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRequestRescueListPacket.TYPE, ServerboundPacketHandlers::handleRequestRescueList);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundUpdateRescueDataPacket.TYPE, ServerboundPacketHandlers::handleUpdateRescueData);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundDeleteRescueDataPacket.TYPE, ServerboundPacketHandlers::handleDeleteRescueData);
    }

    // ========== ServerboundFinishHealPacket ==========

    private static void handleFinishHeal(ServerboundFinishHealPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer healer = context.player();

        // サーバーが記録した治療の開始に対する完了であること
        PendingHealManager.Pending pending = PendingHealManager.peek(healer);
        if (pending == null) return;

        Entity target = healer.level().getEntity(payload.targetEntityId());
        if (!(target instanceof ServerPlayer targetPlayer) || !targetPlayer.getUUID().equals(pending.targetUuid())) return;

        // 自分自身には使えない
        if (targetPlayer == healer) return;

        // 距離（5ブロック以内）
        if (healer.distanceToSqr(targetPlayer) >= 25.0) return;

        // 治療に必要な時間が経過していること
        int requiredTicks = BandageRules.durationTicks(MedicalConfig.get(MedicalConfigKeys.BANDAGE_OTHER_USE_DURATION));
        long elapsed = healer.level().getServer().overworld().getGameTime() - pending.startGameTime();
        if (elapsed < requiredTicks - HEAL_ELAPSED_TOLERANCE_TICKS) return;

        // 送信者に意識があり、メインハンドに包帯を持っていること
        boolean healerConscious = healer.getAttachedOrCreate(ModAttachments.MEDICAL_DATA).isConscious();
        if (!healerConscious) return;
        ItemStack bandage = healer.getMainHandItem();
        if (!(bandage.getItem() instanceof BandageItem)) return;

        // 受理: 記録を消し、包帯を 1 個消費する（クリエイティブは消費しない）
        PendingHealManager.remove(healer);
        if (!healer.getAbilities().instabuild) {
            bandage.shrink(1);
        }

        MobEffectInstance existingEffect = targetPlayer.getEffect(ModEffects.BANDAGE_EFFECT);
        int currentLevel = 0;
        if (existingEffect != null) {
            currentLevel = BandageRules.levelFromAmplifier(existingEffect.getAmplifier());
        }

        int newLevel = BandageRules.nextLevel(currentLevel,
                MedicalConfig.get(MedicalConfigKeys.BANDAGE_OTHER_LEVEL_INCREASE),
                MedicalConfig.get(MedicalConfigKeys.BANDAGE_MAX_LEVEL));
        int duration = BandageRules.durationTicks(MedicalConfig.get(MedicalConfigKeys.BANDAGE_EFFECT_DURATION));

        targetPlayer.addEffect(new MobEffectInstance(
                ModEffects.BANDAGE_EFFECT,
                duration,
                BandageRules.amplifierForLevel(newLevel),
                true,
                true
        ));

        healer.level().playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    // ========== ServerboundQTETResultPacket ==========

    private static void handleQteResult(ServerboundQTETResultPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer healer = context.player();

        // 1) サーバーが記録した QTE の開始に対する結果であること
        PendingQteManager.Pending pending = PendingQteManager.peek(healer);
        if (pending == null) return;

        Entity target = healer.level().getEntity(payload.targetEntityId());
        if (!(target instanceof ServerPlayer targetPlayer) || !targetPlayer.getUUID().equals(pending.targetUuid())) return;

        // 期限切れの記録は破棄し、チャージを解除する(通常は tick の掃除が先に処理する)
        MinecraftServer server = healer.level().getServer();
        if (pending.isExpired(server.overworld().getGameTime())) {
            PendingQteManager.remove(healer);
            PendingQteManager.expire(server, pending);
            return;
        }

        // 2) 全条件を再検証する。満たさなければ記録には触らない
        //    (残った記録は期限切れで破棄され、そのときチャージが解除されるので、開始した QTE は必ず放電で終わる)
        // 送信者が医師として認可されている(エラーメッセージを送らない Silent 版)
        if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(healer)) return;

        // 送信者に意識があり、心臓が NORMAL
        IPlayerMedicalData healerData = healer.getAttachedOrCreate(ModAttachments.MEDICAL_DATA);
        boolean healerAble = healerData.isConscious() && healerData.getHeartStatus() == HeartStatus.NORMAL;
        if (!healerAble) return;

        // 距離
        if (healer.distanceToSqr(targetPlayer) > QteRules.MAX_DISTANCE * QteRules.MAX_DISTANCE) return;

        // 記録した手に電極があり、その DefibrillatorPos が記録と一致する(メインハンド/オフハンドの両方に対応)
        ItemStack electrodeStack = healer.getItemInHand(pending.hand());
        if (!(electrodeStack.getItem() instanceof ElectrodeItem)) return;
        BlockPos electrodePos = ElectrodeItem.getLinkedPos(electrodeStack);
        if (electrodePos == null) return;
        if (!electrodePos.equals(pending.defibrillatorPos())) return;

        // その位置の除細動器が CHARGED(ブロックが別物になっていたら受け付けない)
        if (healer.level().dimension() != pending.dimension()) return;
        ServerLevel level = healer.level();
        if (!PendingQteManager.isChargedDefibrillator(level, pending.defibrillatorPos())) return;

        // 手の電極の PadsId が、除細動器が現在発行中のものと一致する(QTE 中に再発行されて無効になった電極は受け付けない)
        if (!(level.getBlockEntity(pending.defibrillatorPos()) instanceof DefibrillatorBlockEntity defibrillator)
                || !ElectrodeItem.isLinkedTo(electrodeStack, defibrillator)) return;

        // 患者が心停止か VF
        IPlayerMedicalData medicalData = targetPlayer.getAttachedOrCreate(ModAttachments.MEDICAL_DATA);
        boolean targetNeedsShock = medicalData.getHeartStatus() == HeartStatus.CARDIAC_ARREST
                || medicalData.getHeartStatus() == HeartStatus.VF;
        if (!targetNeedsShock) return;

        // 3) 受け付ける。記録を消し、結果が FAIL でもチャージを解除してクールダウンを始める
        PendingQteManager.remove(healer);
        PendingQteManager.releaseCharge(level, pending.defibrillatorPos());

        // 成功判定(大成功/成功/失敗)はクライアントの申告を使う。サーバー側での再計算はしない
        // 判定と状態の適用は DefibrillationRules / DefibrillationHandler に集約されている
        // (QTE が失敗範囲のときは判定せず終了する)
        DefibrillationRules.Result shock = DefibrillationHandler.shockWithQte(medicalData, payload.result());

        switch (shock.outcome()) {
            case QTE_FAILED -> {
                healer.sendSystemMessage(Component.literal("§c電気ショック失敗..."), true);
                // 状態は変わらないので同期もしない
                return;
            }
            case SUCCESS -> {
                healer.sendSystemMessage(Component.literal("§a蘇生成功！"), true);
                targetPlayer.sendSystemMessage(Component.literal("§a蘇生された！"));

                // TODO: 仕様にある「1分間低血液量ダメージ無効」エフェクトをここで付与
            }
            case FAILED_TRANSITIONED -> {
                if (shock.newStatus() == HeartStatus.VF) {
                    healer.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心室細動に移行した。"), true);
                } else {
                    healer.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心停止に移行した。"), true);
                }
            }
            case FAILED, NOT_APPLICABLE -> healer.sendSystemMessage(Component.literal("§c蘇生失敗。"), true);
        }

        // 状態が変化したので、全クライアントに同期
        ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                targetPlayer.getUUID(),
                medicalData.getBloodLevel(),
                medicalData.getHeartStatus(),
                medicalData.getBleedingSpeed(),
                medicalData.getResuscitationChance(),
                medicalData.isConscious()
        ), targetPlayer);
    }

    // ========== ServerboundGiveUpPacket ==========

    private static void handleGiveUp(ServerboundGiveUpPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        IPlayerMedicalData medicalData = player.getAttachedOrCreate(ModAttachments.MEDICAL_DATA);

        // ギブアップが無効な設定では受け付けない
        if (!MedicalConfig.get(MedicalConfigKeys.GIVE_UP_ENABLED)) return;
        // 長押し時間(give_up_duration)の検証はクライアント側の判定のまま。サーバーでは検証しない。
        // 時間を偽って早く送れても、得をするのは本人のギブアップが早まるだけで、他者への影響はない。
        // 意識不明でなければ不正パケットとして無視（クライアントがギブアップを出す条件と同じ）
        if (medicalData.isConscious()) return;
        // 同じ意識不明の状態の間は 1 回しか受け付けない（意識が戻るまで 2 回目以降は無視）
        if (GiveUpGuard.hasUsed(player)) return;

        MinecraftServer server = player.level().getServer();
        if (MedicalConfig.get(MedicalConfigKeys.TRANSPORT_MODE)) {
            // === 搬送モード ===
            int transportX = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_X);
            int transportY = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_Y);
            int transportZ = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_Z);
            String transportDimension = MedicalConfig.get(MedicalConfigKeys.TRANSPORT_DIMENSION);
            LOGGER.info("Transport mode: destination=({}, {}, {}) dimension={}",
                    transportX, transportY, transportZ, transportDimension);
            Identifier dimId = Identifier.tryParse(transportDimension);
            if (dimId == null) {
                LOGGER.warn("Invalid transport dimension: {}", transportDimension);
                return;
            }
            ResourceKey<Level> targetDimension = ResourceKey.create(Registries.DIMENSION, dimId);
            ServerLevel targetLevel = server.getLevel(targetDimension);
            if (targetLevel == null) {
                LOGGER.warn("Transport dimension not found: {}", transportDimension);
                return;
            }

            // ストレッチャー等から下車
            player.stopRiding();

            // 搬送先にテレポート（意識不明状態は維持）
            player.teleportTo(targetLevel, transportX + 0.5, transportY, transportZ + 0.5, Set.of(),
                    player.getYRot(), player.getXRot(), false);

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

            // player.kill() → GENERIC_KILL → ALLOW_DEATH で heartStatus != NORMAL → 死亡成立
            GiveUpGuard.markUsed(player);
            player.kill(player.level());
        }
    }

    /**
     * 搬送/死亡の状態変更を対象プレイヤーに通知する
     * - 搬送: 全プレイヤーに通知
     * - 死亡: ドクターカード所持者のみに通知
     */
    private static void notifyStatusChange(ServerPlayer patient, String status) {
        String patientName = patient.getGameProfile().name();
        boolean isTransport = "搬送".equals(status);
        ChatFormatting statusColor = isTransport ? ChatFormatting.YELLOW : ChatFormatting.RED;

        for (ServerPlayer p : patient.level().getServer().getPlayerList().getPlayers()) {
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

    // ========== ServerboundRequestRescueListPacket ==========

    private static void handleRequestRescueList(ServerboundRequestRescueListPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        // 通報者の座標などが含まれるため、医師として認可されている送信者にだけ返す
        if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(sender)) return;

        // サーバー側で全リストを要求元のクライアントに送り返す(新しい順。クライアントの一覧は先頭が最新)
        ModPackets.sendToPlayer(new ClientboundSyncAllRescueDataPacket(
                RescueDataManager.getRescueDataListNewestFirst(sender.level().getServer())), sender);
    }

    // ========== ServerboundUpdateRescueDataPacket ==========

    private static void handleUpdateRescueData(ServerboundUpdateRescueDataPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();

        // 通報の更新は医師として認可されている送信者だけが行える
        if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(player)) return;

        // メモの長さ上限(デコード側の上限とは別に、サーバーでも検証する)
        if (payload.updateType() == ServerboundUpdateRescueDataPacket.UpdateType.MEMO
                && payload.memo().length() > RescueRules.MEMO_MAX_LENGTH) return;

        // 新しいRescueDataManagerのメソッドを呼び出す
        if (payload.updateType() == ServerboundUpdateRescueDataPacket.UpdateType.CHECKBOX) {
            RescueDataManager.updateRescueDataCheckbox(player, payload.rescueId(), payload.isDispatch(), payload.isTreatment());
        } else { // MEMO
            RescueDataManager.updateRescueDataMemo(player, payload.rescueId(), payload.memo());
        }
    }

    // ========== ServerboundDeleteRescueDataPacket ==========

    private static void handleDeleteRescueData(ServerboundDeleteRescueDataPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(sender)) return;

        // 削除できたときだけ、医師へ削除を配信する(RescueDataManager 内)
        RescueDataManager.deleteRescueData(sender.level().getServer(), payload.rescueId());
    }
}
