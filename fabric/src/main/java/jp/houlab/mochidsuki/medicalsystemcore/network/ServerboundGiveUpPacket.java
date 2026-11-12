package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** クライアントからサーバーへ、ギブアップ（確殺リスポーン）または搬送を通知するパケット。中身なし。受信ハンドラは段 3 に置く。 */
public record ServerboundGiveUpPacket() implements CustomPacketPayload {

    public static final Type<ServerboundGiveUpPacket> TYPE =
            new Type<>(Medicalsystemcore.id("serverbound_give_up"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundGiveUpPacket> STREAM_CODEC =
            StreamCodec.unit(new ServerboundGiveUpPacket());

    @Override
    public Type<ServerboundGiveUpPacket> type() {
        return TYPE;
    }
}
