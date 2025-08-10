package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアントからサーバーへ、治療が完了したことを通知するパケット
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
                // サーバー側で最終的な検証を行う (例: 距離が近いか、など)
                if (healer.distanceToSqr(targetPlayer) < 25.0) { // 5ブロック以内
                    targetPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                        int newBleedingLevel = Math.max(0, data.getBleedingLevel() - 2);
                        data.setBleedingLevel(newBleedingLevel);

                        // 治療者の包帯を1つ消費
                        // healer.getMainHandItem().shrink(1); // 注: 安全のためサーバー側でのアイテム消費は慎重に
                        healer.level().playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
                    });
                }
            }
        });
        return true;
    }
}