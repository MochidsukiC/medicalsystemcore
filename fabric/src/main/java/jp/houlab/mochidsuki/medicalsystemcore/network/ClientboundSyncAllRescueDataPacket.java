package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** サーバー -> クライアント(医師のみ)。通報の全件同期(新しい順)。受信ハンドラはクライアント側(段 5)に置く。 */
public record ClientboundSyncAllRescueDataPacket(List<RescueData> rescueDataList) implements CustomPacketPayload {

    public static final Type<ClientboundSyncAllRescueDataPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_sync_all_rescue_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSyncAllRescueDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundSyncAllRescueDataPacket::write, ClientboundSyncAllRescueDataPacket::read);

    private static ClientboundSyncAllRescueDataPacket read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        // 受信側(RescuePortalScreen)が一覧を書き換えるので、変更できるリストで返す
        List<RescueData> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(new RescueData(buf));
        }
        return new ClientboundSyncAllRescueDataPacket(list);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(rescueDataList.size());
        rescueDataList.forEach(data -> data.toBytes(buf));
    }

    @Override
    public Type<ClientboundSyncAllRescueDataPacket> type() {
        return TYPE;
    }
}
