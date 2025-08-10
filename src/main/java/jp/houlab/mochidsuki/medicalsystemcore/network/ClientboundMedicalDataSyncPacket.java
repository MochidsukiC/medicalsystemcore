package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalData;
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
    // 送るデータを追加
    private final UUID playerUUID;
    private final float bloodLevel;
    private final HeartStatus heartStatus;
    private final int bleedingLevel;
    private final float resuscitationChance;

    // コンストラクタを更新
    public ClientboundMedicalDataSyncPacket(UUID playerUUID, float bloodLevel, HeartStatus heartStatus, int bleedingLevel, float resuscitationChance) {
        this.playerUUID = playerUUID;
        this.bloodLevel = bloodLevel;
        this.heartStatus = heartStatus;
        this.bleedingLevel = bleedingLevel;
        this.resuscitationChance = resuscitationChance;
    }

    public ClientboundMedicalDataSyncPacket(UUID playerUUID, HeartStatus heartStatus, IPlayerMedicalData medicalData) {
        this.playerUUID = playerUUID;
        this.bloodLevel = medicalData.getBloodLevel();
        this.heartStatus = heartStatus;
        this.bleedingLevel = medicalData.getBleedingLevel();
        this.resuscitationChance = medicalData.getResuscitationChance();
    }

    // バイトデータからの復元処理を更新
    public ClientboundMedicalDataSyncPacket(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.bloodLevel = buf.readFloat();
        this.heartStatus = buf.readEnum(HeartStatus.class);
        this.bleedingLevel = buf.readInt();
        this.resuscitationChance = buf.readFloat();
    }

    // バイトデータへの書き込み処理を更新
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        buf.writeFloat(bloodLevel);
        buf.writeEnum(heartStatus);
        buf.writeInt(bleedingLevel);
        buf.writeFloat(resuscitationChance);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // 受け取ったデータで、クライアント側のデータを丸ごと更新
            ClientMedicalData data = ClientMedicalDataManager.getPlayerData(this.playerUUID);
            data.bloodLevel = this.bloodLevel;
            data.heartStatus = this.heartStatus;
            data.bleedingLevel = this.bleedingLevel;
            data.resuscitationChance = this.resuscitationChance;
        });
        return true;
    }
}