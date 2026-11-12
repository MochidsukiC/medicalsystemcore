package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** サーバーからクライアントへ、救急ポータル画面を開かせる。中身なし。受信ハンドラはクライアント側(段 5)に置く。 */
public record ClientboundOpenRescuePortalPacket() implements CustomPacketPayload {

    public static final Type<ClientboundOpenRescuePortalPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_open_rescue_portal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenRescuePortalPacket> STREAM_CODEC =
            StreamCodec.unit(new ClientboundOpenRescuePortalPacket());

    @Override
    public Type<ClientboundOpenRescuePortalPacket> type() {
        return TYPE;
    }
}
