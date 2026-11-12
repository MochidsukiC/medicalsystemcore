package jp.houlab.mochidsuki.medicalsystemcore.core;

import com.mojang.serialization.Codec;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRevokedLicensesSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 失効した医師カードの免許番号を、ワールド全体のデータストレージ(MinecraftServer#getDataStorage)に保存する。
 * ファイルは {@code <world>/data/medicalsystemcore/revoked_licenses.dat}。
 * カードの現物は残り、権限判定(DoctorCardItem)がこの一覧と照合して無効にする。
 * 失効の取り消しは用意していない。
 *
 * <p>スレッド: サーバースレッドからのみ呼ぶ(コマンド・ログインイベント・権限判定はいずれもサーバースレッド)。</p>
 */
public class RevokedLicenseSavedData extends CorruptionGuardedSavedData {

    private static final String TAG_LIST = "licenses";

    public static final Codec<RevokedLicenseSavedData> CODEC = Codec.STRING.listOf().fieldOf(TAG_LIST).xmap(
            RevokedLicenseSavedData::new,
            data -> new ArrayList<>(data.licenses)).codec();

    /** dataFixType は null(MOD 独自のデータにバニラの DataFixer を通さない)。 */
    public static final SavedDataType<RevokedLicenseSavedData> TYPE = new SavedDataType<>(
            Medicalsystemcore.id("revoked_licenses"), RevokedLicenseSavedData::new, CODEC, null);

    private final Set<String> licenses = new LinkedHashSet<>();

    public RevokedLicenseSavedData() {
    }

    /** Codec の読み込み用。Codec が返すリストは変更できないので、変更できる集合にコピーして持つ。 */
    private RevokedLicenseSavedData(Collection<String> licenses) {
        this.licenses.addAll(licenses);
    }

    public static RevokedLicenseSavedData get(MinecraftServer server) {
        return getOrCreate(server, TYPE);
    }

    public boolean isRevoked(String licenseNumber) {
        return licenses.contains(licenseNumber);
    }

    /**
     * 免許番号をまとめて失効に加え、新たに加わった件数を返す(すでに失効済みのものは数えない)。
     * 1 件でも加わったら保存対象にし、全員へ 1 回だけ再同期する。
     * Forge 版の revokeAll(Collection) に server 引数を足した形(Fabric の送信は接続中のサーバーを明示する必要があるため)。
     */
    public int revokeAll(MinecraftServer server, Collection<String> licenseNumbers) {
        int added = 0;
        for (String license : licenseNumbers) {
            if (licenses.add(license)) {
                added++;
            }
        }
        if (added > 0) {
            setDirty();
            ModPackets.sendToAllClients(new ClientboundRevokedLicensesSyncPacket(licenses), server);
        }
        return added;
    }

    /** ログイン時に、そのプレイヤーへ失効リスト全件を送る。MOD 初期化で 1 回だけ呼ぶ。 */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ModPackets.sendToPlayer(new ClientboundRevokedLicensesSyncPacket(get(server).licenses), handler.player));
    }
}
