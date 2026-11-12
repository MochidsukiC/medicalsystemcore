package jp.houlab.mochidsuki.medicalsystemcore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundOpenRescuePortalPacket {

    public ClientboundOpenRescuePortalPacket() {
    }

    public ClientboundOpenRescuePortalPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientRescuePortalHandler::openScreen);
        });
        ctx.setPacketHandled(true);
    }
}
