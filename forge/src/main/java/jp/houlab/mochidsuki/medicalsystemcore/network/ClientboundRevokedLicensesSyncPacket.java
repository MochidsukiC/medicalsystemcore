package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientRevokedLicenses;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * サーバー -> クライアント
 * 失効済みの免許番号を全件送る(ログイン時と、失効リストが変わったとき)。
 * 受信側は保持分を丸ごと置き換える。
 */
public class ClientboundRevokedLicensesSyncPacket {


    private final Set<String> licenses;

    public ClientboundRevokedLicensesSyncPacket(Collection<String> licenses) {
        // 送信時点のコピーを持つ(送信はキューを経由するので、元のセットの後続の変更を拾わない)
        this.licenses = new HashSet<>(licenses);
    }

    public ClientboundRevokedLicensesSyncPacket(FriendlyByteBuf buf) {
        this.licenses = new HashSet<>(buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(licenses, FriendlyByteBuf::writeUtf);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRevokedLicenses.replaceAll(licenses)));
        context.setPacketHandled(true);
    }
}
