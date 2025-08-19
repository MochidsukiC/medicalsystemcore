package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ServerboundRequestRescueListPacket {
    public ServerboundRequestRescueListPacket() {}
    public ServerboundRequestRescueListPacket(FriendlyByteBuf buf) {}
    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // サーバー側で全リストを要求元のクライアントに送り返す
            ModPackets.sendToPlayer(new ClientboundSyncAllRescueDataPacket(RescueDataManager.getRescueDataList()), context.getSender());
        });
        context.setPacketHandled(true);
    }
}