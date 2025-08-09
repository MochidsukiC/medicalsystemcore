package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * サーバーからクライアントへ、プレイヤーのHeartStatusを同期するためのパケット
 */
public class ClientboundMedicalDataSyncPacket {

    private final UUID playerUUID;
    private final HeartStatus heartStatus;

    public ClientboundMedicalDataSyncPacket(UUID playerUUID, HeartStatus heartStatus) {
        this.playerUUID = playerUUID;
        this.heartStatus = heartStatus;
    }

    public ClientboundMedicalDataSyncPacket(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.heartStatus = buf.readEnum(HeartStatus.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        buf.writeEnum(heartStatus);
    }
    /**
     * パケット受信時の処理 (最終修正版)
     */
    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // このパケットの役割は、クライアント側のデータマネージャーを更新すること『だけ』にする。
            // これで処理の衝突が完全になくなる。
            ClientMedicalDataManager.setPlayerHeartStatus(this.playerUUID, this.heartStatus);
        });
        return true;
    }
}