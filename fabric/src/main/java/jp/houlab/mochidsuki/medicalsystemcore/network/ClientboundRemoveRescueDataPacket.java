package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * サーバー -> クライアント(医師のみ)
 * 通報が削除されたことを通知する。受信ハンドラはクライアント側(段 5)に置く。
 */
public record ClientboundRemoveRescueDataPacket(int rescueId) implements CustomPacketPayload {

    public static final Type<ClientboundRemoveRescueDataPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_remove_rescue_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRemoveRescueDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundRemoveRescueDataPacket::write, ClientboundRemoveRescueDataPacket::read);

    private static ClientboundRemoveRescueDataPacket read(FriendlyByteBuf buf) {
        return new ClientboundRemoveRescueDataPacket(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(rescueId);
    }

    @Override
    public Type<ClientboundRemoveRescueDataPacket> type() {
        return TYPE;
    }
}
