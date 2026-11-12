package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアント -> サーバー
 * 通報の削除を要求する。医師として認可されている送信者だけが削除できる。
 */
public class ServerboundDeleteRescueDataPacket {

    private final int rescueId;

    public ServerboundDeleteRescueDataPacket(int rescueId) {
        this.rescueId = rescueId;
    }

    public ServerboundDeleteRescueDataPacket(FriendlyByteBuf buf) {
        this.rescueId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(rescueId);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(sender)) return;

            // 削除できたときだけ、医師へ削除を配信する(RescueDataManager 内)
            RescueDataManager.deleteRescueData(sender.server, rescueId);
        });
        context.setPacketHandled(true);
    }
}
