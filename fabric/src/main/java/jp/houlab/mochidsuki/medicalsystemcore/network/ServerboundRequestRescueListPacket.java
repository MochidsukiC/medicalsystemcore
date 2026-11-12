package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** クライアントからサーバーへ、通報一覧の送信を要求する。中身なし。受信ハンドラは段 3 に置く。 */
public record ServerboundRequestRescueListPacket() implements CustomPacketPayload {

    public static final Type<ServerboundRequestRescueListPacket> TYPE =
            new Type<>(Medicalsystemcore.id("serverbound_request_rescue_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRequestRescueListPacket> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestRescueListPacket());

    @Override
    public Type<ServerboundRequestRescueListPacket> type() {
        return TYPE;
    }
}
