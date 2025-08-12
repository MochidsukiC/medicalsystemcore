package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * サーバーからクライアントへ、プレイヤーのHeartStatusを同期するためのパケット
 */
public class ClientboundCoreStatsPacket {
    // 送るデータを追加
    private final UUID playerUUID;
    private final float bloodLevel;
    private final HeartStatus heartStatus;
    private final float bleedingSpeed;
    private final float resuscitationChance;
    private final boolean isConscious;

    // コンストラクタを更新
    public ClientboundCoreStatsPacket(UUID playerUUID, float bloodLevel, HeartStatus heartStatus, float bleedingSpeed , float resuscitationChance,boolean conscious) {
        this.playerUUID = playerUUID;
        this.bloodLevel = bloodLevel;
        this.heartStatus = heartStatus;
        this.bleedingSpeed = bleedingSpeed;
        this.resuscitationChance = resuscitationChance;
        this.isConscious = conscious;
    }

    public ClientboundCoreStatsPacket(UUID playerUUID, HeartStatus heartStatus, IPlayerMedicalData medicalData) {
        this.playerUUID = playerUUID;
        this.bloodLevel = medicalData.getBloodLevel();
        this.heartStatus = heartStatus;
        this.bleedingSpeed = medicalData.getBleedingSpeed();
        this.resuscitationChance = medicalData.getResuscitationChance();
        this.isConscious = medicalData.isConscious();
    }

    // バイトデータからの復元処理を更新
    public ClientboundCoreStatsPacket(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.bloodLevel = buf.readFloat();
        this.heartStatus = buf.readEnum(HeartStatus.class);
        this.bleedingSpeed = buf.readFloat();
        this.resuscitationChance = buf.readFloat();
        this.isConscious = buf.readBoolean(); //
    }

    // バイトデータへの書き込み処理を更新
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        buf.writeFloat(bloodLevel);
        buf.writeEnum(heartStatus);
        buf.writeFloat(bleedingSpeed);
        buf.writeFloat(resuscitationChance);
        buf.writeBoolean(isConscious);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // 受け取ったデータで、クライアント側のデータを丸ごと更新
            ClientMedicalData data = ClientMedicalDataManager.getPlayerData(this.playerUUID);
            data.bloodLevel = this.bloodLevel;
            data.heartStatus = this.heartStatus;
            data.bleedingSpeed = this.bleedingSpeed;
            data.resuscitationChance = this.resuscitationChance;
            data.isConscious = this.isConscious;
        });
        return true;
    }
}