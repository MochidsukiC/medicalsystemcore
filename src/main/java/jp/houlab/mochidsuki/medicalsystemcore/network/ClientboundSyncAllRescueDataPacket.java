package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.client.screen.RescuePortalScreen;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ClientboundSyncAllRescueDataPacket {

    private final List<RescueData> rescueDataList;

    public ClientboundSyncAllRescueDataPacket(List<RescueData> list) {
        this.rescueDataList = list;
    }

    public ClientboundSyncAllRescueDataPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.rescueDataList = IntStream.range(0, size).mapToObj(i -> new RescueData(buf)).collect(Collectors.toList());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(rescueDataList.size());
        rescueDataList.forEach(data -> data.toBytes(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().screen instanceof RescuePortalScreen screen) {
                    screen.updateRescueDataList(this.rescueDataList);
                }
            });
        });
        context.setPacketHandled(true);
    }
}