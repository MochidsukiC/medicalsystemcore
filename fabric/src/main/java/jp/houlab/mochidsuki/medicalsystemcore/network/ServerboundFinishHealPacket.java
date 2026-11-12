package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** クライアントからサーバーへ、治療が完了したことを通知するパケット。受信ハンドラ(検証と効果の付与)は段 3 に置く。 */
public record ServerboundFinishHealPacket(int targetEntityId) implements CustomPacketPayload {

    public static final Type<ServerboundFinishHealPacket> TYPE =
            new Type<>(Medicalsystemcore.id("serverbound_finish_heal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundFinishHealPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ServerboundFinishHealPacket::write, ServerboundFinishHealPacket::read);

    private static ServerboundFinishHealPacket read(FriendlyByteBuf buf) {
        return new ServerboundFinishHealPacket(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.targetEntityId);
    }

    @Override
    public Type<ServerboundFinishHealPacket> type() {
        return TYPE;
    }
}
