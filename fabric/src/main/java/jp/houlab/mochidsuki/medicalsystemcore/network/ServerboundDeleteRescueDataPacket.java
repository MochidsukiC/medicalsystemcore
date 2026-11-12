package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * クライアント -> サーバー
 * 通報の削除を要求する。医師として認可されている送信者だけが削除できる(受信ハンドラは段 3 で認可を検証する)。
 */
public record ServerboundDeleteRescueDataPacket(int rescueId) implements CustomPacketPayload {

    public static final Type<ServerboundDeleteRescueDataPacket> TYPE =
            new Type<>(Medicalsystemcore.id("serverbound_delete_rescue_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundDeleteRescueDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ServerboundDeleteRescueDataPacket::write, ServerboundDeleteRescueDataPacket::read);

    private static ServerboundDeleteRescueDataPacket read(FriendlyByteBuf buf) {
        return new ServerboundDeleteRescueDataPacket(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(rescueId);
    }

    @Override
    public Type<ServerboundDeleteRescueDataPacket> type() {
        return TYPE;
    }
}
