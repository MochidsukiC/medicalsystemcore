package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.DefibrillationRules;
import jp.houlab.mochidsuki.medicalsystemcore.core.DefibrillationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
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

            Entity target = healer.level().getEntity(this.targetEntityId);
            if (!(target instanceof ServerPlayer targetPlayer)) return;

            // ヒーラーが手に持っている電極アイテムを取得し、消費する
            ItemStack electrodeStack = healer.getItemInHand(InteractionHand.MAIN_HAND);
            CompoundTag nbt = electrodeStack.getTag();
            if (nbt != null && nbt.contains("DefibrillatorPos")) {
                BlockPos defibrillatorPos = NbtUtils.readBlockPos(nbt.getCompound("DefibrillatorPos"));
                BlockEntity be = healer.level().getBlockEntity(defibrillatorPos);

                // --- 仕様2: 除細動器の状態をリセット ---
                if (be instanceof DefibrillatorBlockEntity defibrillator) {
                    defibrillator.resetCharge(); // 状態をリセットする新しいメソッド（後で作成）
                    defibrillator.startCooldown(); // <<<--- この行を追加！
                }
            }


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