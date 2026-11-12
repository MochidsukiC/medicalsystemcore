package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.client.screen.RescuePortalScreen;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientTrackingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * サーバー -> クライアント(医師のみ)
 * 通報が削除されたことを通知する。
 */
public class ClientboundRemoveRescueDataPacket {

    private final int rescueId;

    public ClientboundRemoveRescueDataPacket(int rescueId) {
        this.rescueId = rescueId;
    }

    public ClientboundRemoveRescueDataPacket(FriendlyByteBuf buf) {
        this.rescueId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(rescueId);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // 追跡中の通報が消えたら、画面が開いていなくても追跡を止める
                ClientTrackingManager.clearTrackingIfId(rescueId);
                if (Minecraft.getInstance().screen instanceof RescuePortalScreen screen) {
                    screen.onRescueDataRemoved(rescueId);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
