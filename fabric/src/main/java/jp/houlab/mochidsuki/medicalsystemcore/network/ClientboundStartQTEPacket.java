package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** サーバーからクライアントへ、QTE の開始を通知する。受信ハンドラはクライアント側(段 4)に置く。 */
public record ClientboundStartQTEPacket(int targetEntityId) implements CustomPacketPayload {

    public static final Type<ClientboundStartQTEPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_start_qte"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundStartQTEPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundStartQTEPacket::write, ClientboundStartQTEPacket::read);

    private static ClientboundStartQTEPacket read(FriendlyByteBuf buf) {
        return new ClientboundStartQTEPacket(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.targetEntityId);
    }

    @Override
    public Type<ClientboundStartQTEPacket> type() {
        return TYPE;
    }
}
