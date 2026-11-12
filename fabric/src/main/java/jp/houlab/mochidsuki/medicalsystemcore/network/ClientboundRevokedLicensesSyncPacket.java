package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * サーバー -> クライアント
 * 失効済みの免許番号を全件送る(ログイン時と、失効リストが変わったとき)。
 * 受信側は保持分を丸ごと置き換える(ClientRevokedLicenses.replaceAll)。受信ハンドラはクライアント側(段 4)に置く。
 */
public record ClientboundRevokedLicensesSyncPacket(Set<String> licenses) implements CustomPacketPayload {

    public static final Type<ClientboundRevokedLicensesSyncPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_revoked_licenses_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRevokedLicensesSyncPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundRevokedLicensesSyncPacket::write, ClientboundRevokedLicensesSyncPacket::read);

    public ClientboundRevokedLicensesSyncPacket {
        // 送信時点のコピーを持つ(送信はキューを経由するので、元のセットの後続の変更を拾わない)
        licenses = new HashSet<>(licenses);
    }

    private static ClientboundRevokedLicensesSyncPacket read(FriendlyByteBuf buf) {
        List<String> read = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        return new ClientboundRevokedLicensesSyncPacket(new HashSet<>(read));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeCollection(licenses, FriendlyByteBuf::writeUtf);
    }

    @Override
    public Type<ClientboundRevokedLicensesSyncPacket> type() {
        return TYPE;
    }
}
