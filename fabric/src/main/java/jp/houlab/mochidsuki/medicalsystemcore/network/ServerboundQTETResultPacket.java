package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * クライアントからサーバーへ、QTE の結果を通知する。
 * 位置はサーバーが保持する PendingQteManager の記録から取る(パケットに含めない)。受信ハンドラ(再検証と除細動)は段 3 に置く。
 */
public record ServerboundQTETResultPacket(int targetEntityId, QTEResult result) implements CustomPacketPayload {

    public static final Type<ServerboundQTETResultPacket> TYPE =
            new Type<>(Medicalsystemcore.id("serverbound_qte_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundQTETResultPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ServerboundQTETResultPacket::write, ServerboundQTETResultPacket::read);

    private static ServerboundQTETResultPacket read(FriendlyByteBuf buf) {
        int targetEntityId = buf.readInt();
        QTEResult result = buf.readEnum(QTEResult.class);
        return new ServerboundQTETResultPacket(targetEntityId, result);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.targetEntityId);
        buf.writeEnum(this.result);
    }

    @Override
    public Type<ServerboundQTETResultPacket> type() {
        return TYPE;
    }
}
