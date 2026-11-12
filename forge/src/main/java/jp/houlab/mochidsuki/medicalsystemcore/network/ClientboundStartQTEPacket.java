package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ClientboundStartQTEPacket {
    private final int targetEntityId;

    public ClientboundStartQTEPacket(int targetEntityId) { this.targetEntityId = targetEntityId; }
    public ClientboundStartQTEPacket(FriendlyByteBuf buf) { this.targetEntityId = buf.readInt(); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeInt(this.targetEntityId); }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ClientPacketHandler.handleStartQTE(this.targetEntityId);
        });
        return true;
    }
}