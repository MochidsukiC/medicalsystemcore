package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRevokedLicensesSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 失効した医師カードの免許番号を、ワールド(オーバーワールドのデータストレージ)に保存する。
 * ファイルは {@code <world>/data/medicalsystemcore_revoked_licenses.dat}。
 * カードの現物は残り、権限判定(DoctorCardItem)がこの一覧と照合して無効にする。
 * 失効の取り消しは用意していない。
 *
 * <p>スレッド: サーバースレッドからのみ呼ぶ(コマンド・ログインイベント・権限判定はいずれもサーバースレッド)。</p>
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID)
public class RevokedLicenseSavedData extends SavedData {

    public static final String DATA_NAME = "medicalsystemcore_revoked_licenses";
    private static final String TAG_LIST = "licenses";

    private final Set<String> licenses = new LinkedHashSet<>();

    public static RevokedLicenseSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RevokedLicenseSavedData::load, RevokedLicenseSavedData::new, DATA_NAME);
    }

    public static RevokedLicenseSavedData load(CompoundTag tag) {
        RevokedLicenseSavedData data = new RevokedLicenseSavedData();
        ListTag listTag = tag.getList(TAG_LIST, Tag.TAG_STRING);
        for (int i = 0; i < listTag.size(); i++) {
            data.licenses.add(listTag.getString(i));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (String license : licenses) {
            listTag.add(StringTag.valueOf(license));
        }
        tag.put(TAG_LIST, listTag);
        return tag;
    }

    public boolean isRevoked(String licenseNumber) {
        return licenses.contains(licenseNumber);
    }

    /**
     * 免許番号をまとめて失効に加え、新たに加わった件数を返す(すでに失効済みのものは数えない)。
     * 1 件でも加わったら保存対象にし、全員へ 1 回だけ再同期する。
     */
    public int revokeAll(Collection<String> licenseNumbers) {
        int added = 0;
        for (String license : licenseNumbers) {
            if (licenses.add(license)) {
                added++;
            }
        }
        if (added > 0) {
            setDirty();
            ModPackets.sendToAllClients(new ClientboundRevokedLicensesSyncPacket(licenses));
        }
        return added;
    }

    /** ログイン時に、そのプレイヤーへ失効リスト全件を送る。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModPackets.sendToPlayer(new ClientboundRevokedLicensesSyncPacket(get(player.server).licenses), player);
        }
    }
}
