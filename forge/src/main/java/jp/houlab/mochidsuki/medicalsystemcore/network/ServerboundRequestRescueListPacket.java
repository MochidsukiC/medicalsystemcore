package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ServerboundRequestRescueListPacket {
    public ServerboundRequestRescueListPacket() {}
    public ServerboundRequestRescueListPacket(FriendlyByteBuf buf) {}
    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            // 通報者の座標などが含まれるため、医師として認可されている送信者にだけ返す
            if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(sender)) return;

            // サーバー側で全リストを要求元のクライアントに送り返す(新しい順。クライアントの一覧は先頭が最新)
            ModPackets.sendToPlayer(new ClientboundSyncAllRescueDataPacket(RescueDataManager.getRescueDataListNewestFirst(sender.server)), sender);
        });
        context.setPacketHandled(true);
    }
}
