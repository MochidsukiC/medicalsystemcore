package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * サーバーからクライアントへ、プレイヤーのHeartStatusを同期するためのパケット。
 * 受信ハンドラ(ClientMedicalData などの更新)はクライアント側(段 4)に置く。
 */
public record ClientboundCoreStatsPacket(UUID playerUUID, float bloodLevel, HeartStatus heartStatus,
                                         float bleedingSpeed, float resuscitationChance, boolean isConscious)
        implements CustomPacketPayload {

    public static final Type<ClientboundCoreStatsPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_core_stats"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCoreStatsPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundCoreStatsPacket::write, ClientboundCoreStatsPacket::read);

    /** 医療データから作る。heartStatus は呼び出し側が指定する(Forge の同名コンストラクタと同じ)。 */
    public ClientboundCoreStatsPacket(UUID playerUUID, HeartStatus heartStatus, IPlayerMedicalData medicalData) {
        this(playerUUID, medicalData.getBloodLevel(), heartStatus, medicalData.getBleedingSpeed(),
                medicalData.getResuscitationChance(), medicalData.isConscious());
    }

    private static ClientboundCoreStatsPacket read(FriendlyByteBuf buf) {
        UUID playerUUID = buf.readUUID();
        float bloodLevel = buf.readFloat();
        HeartStatus heartStatus = buf.readEnum(HeartStatus.class);
        float bleedingSpeed = buf.readFloat();
        float resuscitationChance = buf.readFloat();
        boolean isConscious = buf.readBoolean();
        return new ClientboundCoreStatsPacket(playerUUID, bloodLevel, heartStatus, bleedingSpeed, resuscitationChance, isConscious);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        buf.writeFloat(bloodLevel);
        buf.writeEnum(heartStatus);
        buf.writeFloat(bleedingSpeed);
        buf.writeFloat(resuscitationChance);
        buf.writeBoolean(isConscious);
    }

    @Override
    public Type<ClientboundCoreStatsPacket> type() {
        return TYPE;
    }
}
