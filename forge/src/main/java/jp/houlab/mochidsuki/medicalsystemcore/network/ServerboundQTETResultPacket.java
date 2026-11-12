package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.DefibrillationRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.QteRules;
import jp.houlab.mochidsuki.medicalsystemcore.core.DefibrillationHandler;
import jp.houlab.mochidsuki.medicalsystemcore.core.PendingQteManager;
import jp.houlab.mochidsuki.medicalsystemcore.item.ElectrodeItem;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundQTETResultPacket {
    private final int targetEntityId;
    private final QTEResult result;

    public ServerboundQTETResultPacket(int targetEntityId, QTEResult result) {
        this.targetEntityId = targetEntityId;
        this.result = result;
    }

    public ServerboundQTETResultPacket(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readInt();
        this.result = buf.readEnum(QTEResult.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.targetEntityId);
        buf.writeEnum(this.result);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // この処理はサーバーサイドで実行される
            ServerPlayer healer = context.getSender();
            if (healer == null) return;

            // 1) サーバーが記録した QTE の開始に対する結果であること
            PendingQteManager.Pending pending = PendingQteManager.peek(healer);
            if (pending == null) return;

            Entity target = healer.level().getEntity(this.targetEntityId);
            if (!(target instanceof ServerPlayer targetPlayer) || !targetPlayer.getUUID().equals(pending.targetUuid())) return;

            // 期限切れの記録は破棄し、チャージを解除する(通常は tick の掃除が先に処理する)
            if (pending.isExpired(healer.server.overworld().getGameTime())) {
                PendingQteManager.remove(healer);
                PendingQteManager.expire(healer.server, pending);
                return;
            }

            // 2) 全条件を再検証する。満たさなければ記録には触らない
            //    (残った記録は期限切れで破棄され、そのときチャージが解除されるので、開始した QTE は必ず放電で終わる)
            // 送信者が医師として認可されている(エラーメッセージを送らない Silent 版)
            if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(healer)) return;

            // 送信者に意識があり、心臓が NORMAL
            boolean healerAble = healer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                    .map(data -> data.isConscious() && data.getHeartStatus() == HeartStatus.NORMAL)
                    .orElse(false);
            if (!healerAble) return;

            // 距離
            if (healer.distanceToSqr(targetPlayer) > QteRules.MAX_DISTANCE * QteRules.MAX_DISTANCE) return;

            // 記録した手に電極があり、その DefibrillatorPos が記録と一致する(メインハンド/オフハンドの両方に対応)
            ItemStack electrodeStack = healer.getItemInHand(pending.hand());
            if (!(electrodeStack.getItem() instanceof ElectrodeItem)) return;
            CompoundTag nbt = electrodeStack.getTag();
            if (nbt == null || !nbt.contains("DefibrillatorPos")) return;
            BlockPos electrodePos = NbtUtils.readBlockPos(nbt.getCompound("DefibrillatorPos"));
            if (!electrodePos.equals(pending.defibrillatorPos())) return;

            // その位置の除細動器が CHARGED(ブロックが別物になっていたら受け付けない)
            if (healer.level().dimension() != pending.dimension()) return;
            ServerLevel level = healer.serverLevel();
            if (!PendingQteManager.isChargedDefibrillator(level, pending.defibrillatorPos())) return;

            // 患者が心停止か VF
            boolean targetNeedsShock = targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                    .map(data -> data.getHeartStatus() == HeartStatus.CARDIAC_ARREST || data.getHeartStatus() == HeartStatus.VF)
                    .orElse(false);
            if (!targetNeedsShock) return;

            // 3) 受け付ける。記録を消し、結果が FAIL でもチャージを解除してクールダウンを始める
            PendingQteManager.remove(healer);
            PendingQteManager.releaseCharge(level, pending.defibrillatorPos());

            // 成功判定(大成功/成功/失敗)はクライアントの申告を使う。サーバー側での再計算はしない
            targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // 判定と状態の適用は DefibrillationRules / DefibrillationHandler に集約されている
                // (QTE が失敗範囲のときは判定せず終了する)
                DefibrillationRules.Result shock = DefibrillationHandler.shockWithQte(medicalData, this.result);

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
            });
        });
        return true;
    }
}
