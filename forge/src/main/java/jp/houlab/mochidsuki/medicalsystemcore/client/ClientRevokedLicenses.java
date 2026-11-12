package jp.houlab.mochidsuki.medicalsystemcore.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * クライアントが知っている失効済み免許番号。サーバーが全件同期で送ってくる(ログイン時と変更時)。
 * ツールチップの「失効」表示にだけ使う。権限の判定はサーバーが行う。
 *
 * <p>DoctorCardItem は専用サーバーでもロードされるため、このクラスは net.minecraft.client.* を参照しない。</p>
 */
public class ClientRevokedLicenses {

    private static volatile Set<String> revoked = Collections.emptySet();

    public static void replaceAll(Set<String> licenses) {
        revoked = Collections.unmodifiableSet(new HashSet<>(licenses));
    }

    public static void clear() {
        revoked = Collections.emptySet();
    }

    public static boolean isRevoked(String licenseNumber) {
        return revoked.contains(licenseNumber);
    }
}
