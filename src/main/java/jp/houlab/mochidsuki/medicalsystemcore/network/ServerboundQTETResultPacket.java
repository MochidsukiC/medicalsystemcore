package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.core.QTEResult;
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


            // --- QTEが「失敗」範囲だった場合 ---
            if (this.result == QTEResult.FAIL) {
                healer.sendSystemMessage(Component.literal("§c電気ショック失敗..."), true);
                // ここで除細動器のクールダウンを開始させるなどの処理を追加可能
                return;
            }

            // --- QTEが「成功」または「大成功」だった場合 ---
            targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                float chance = medicalData.getResuscitationChance();
                HeartStatus currentStatus = medicalData.getHeartStatus();
                boolean success = false;

                // 「大成功」の場合、蘇生確率にボーナス
                if (this.result == QTEResult.GREAT_SUCCESS) {
                    chance += 20.0f;
                }

                // 蘇生判定
                if (currentStatus == HeartStatus.CARDIAC_ARREST) {
                    if (Math.random() < chance / 100.0) success = true;
                } else if (currentStatus == HeartStatus.VF) {
                    if (Math.random() < (chance * 2) / 100.0) success = true;
                }

                // --- 蘇生成功時の処理 ---
                if (success) {
                    medicalData.setHeartStatus(HeartStatus.NORMAL);
                    medicalData.setResuscitationChance(100.0f);
                    medicalData.setCardiacArrestTimer(0);
                    medicalData.setDamageImmune(false); // ダメージ無効を解除

                    healer.sendSystemMessage(Component.literal("§a蘇生成功！"), true);
                    targetPlayer.sendSystemMessage(Component.literal("§a蘇生された！"));

                    // TODO: 仕様にある「1分間低血液量ダメージ無効」エフェクトをここで付与

                }
                // --- 蘇生失敗時の処理 ---
                else {
                    if (currentStatus == HeartStatus.CARDIAC_ARREST && Math.random() < 0.4) {
                        medicalData.setHeartStatus(HeartStatus.VF);
                        healer.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心室細動に移行した。"), true);
                    } else if (currentStatus == HeartStatus.VF && Math.random() < 0.4) {
                        medicalData.setHeartStatus(HeartStatus.CARDIAC_ARREST);
                        healer.sendSystemMessage(Component.literal("§c蘇生失敗。対象は心停止に移行した。"), true);
                    } else {
                        healer.sendSystemMessage(Component.literal("§c蘇生失敗。"), true);
                    }
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