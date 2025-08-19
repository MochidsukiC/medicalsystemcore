package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.client.screen.RescuePortalScreen;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ClientboundUpdateRescueDataPacket {

    private final RescueData data;
    private final boolean isNew;

    public ClientboundUpdateRescueDataPacket(RescueData data, boolean isNew) {
        this.data = data;
        this.isNew = isNew;
    }

    public ClientboundUpdateRescueDataPacket(FriendlyByteBuf buf) {
        this.isNew = buf.readBoolean();
        this.data = new RescueData(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isNew);
        data.toBytes(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // クライアント側のデータを更新または追加
                if (isNew) {
                    RescueData.RESCUE_DATA_LIST.add(0, data);
                } else {
                    RescueData.RESCUE_DATA_LIST.stream()
                            .filter(d -> d.getId() == data.getId())
                            .findFirst()
                            .ifPresent(d -> {
                                d.setDispatch(data.isDispatch());
                                d.setTreatment(data.isTreatment());
                                d.setMemo(data.getMemo());
                            });
                }

                // GUIが開いていれば更新
                if (Minecraft.getInstance().screen instanceof RescuePortalScreen screen) {
                    screen.onRescueDataUpdated(data);
                }
            });
        });
        context.setPacketHandled(true);
    }
}