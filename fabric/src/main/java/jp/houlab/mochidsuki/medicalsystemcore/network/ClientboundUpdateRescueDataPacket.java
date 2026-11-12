package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** サーバー -> クライアント(医師のみ)。通報 1 件の追加または更新。受信ハンドラはクライアント側(段 5)に置く。 */
public record ClientboundUpdateRescueDataPacket(RescueData data, boolean isNew) implements CustomPacketPayload {

    public static final Type<ClientboundUpdateRescueDataPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_update_rescue_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateRescueDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundUpdateRescueDataPacket::write, ClientboundUpdateRescueDataPacket::read);

    private static ClientboundUpdateRescueDataPacket read(FriendlyByteBuf buf) {
        // 書き出し順は isNew → data(コンストラクタ引数の順とは逆)
        boolean isNew = buf.readBoolean();
        RescueData data = new RescueData(buf);
        return new ClientboundUpdateRescueDataPacket(data, isNew);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(isNew);
        data.toBytes(buf);
    }

    @Override
    public Type<ClientboundUpdateRescueDataPacket> type() {
        return TYPE;
    }
}
