package jp.houlab.mochidsuki.medicalsystemcore.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 医師カードの発行台帳。grant で正規カードを発行するたびに、免許番号 → 持ち主(UUID・名前)・発行日を記録する。
 * ファイルは {@code <world>/data/medicalsystemcore_issued_licenses.dat}。
 *
 * <p>用途は 2 つ: (1) /doctorcard revoke <player> が、カードの置き場所によらず、その人に発行した全免許番号を
 * 失効させる。(2) 免許番号の自動生成が、発行済みの番号と衝突しないようにする。</p>
 *
 * <p>台帳を導入する前に発行されたカードは記録がない。それらは、持ち主がオンラインでインベントリに持っていれば
 * revoke 時に番号を拾い、そうでなければ /doctorcard revoke_license で番号を直接指定して失効させる。</p>
 *
 * <p>スレッド: サーバースレッドからのみ呼ぶ(コマンドから使う)。</p>
 */
public class IssuedLicenseSavedData extends SavedData {

    public static final String DATA_NAME = "medicalsystemcore_issued_licenses";

    private static final String TAG_LIST = "issued";
    private static final String TAG_LICENSE = "license";
    private static final String TAG_OWNER_UUID = "ownerUuid";
    private static final String TAG_OWNER_NAME = "ownerName";
    private static final String TAG_ISSUE_DATE = "issueDate";

    /** 発行記録 1 件。 */
    public record Issue(String licenseNumber, UUID ownerUuid, String ownerName, long issueDate) {
    }

    private final Map<String, Issue> issues = new LinkedHashMap<>();

    public static IssuedLicenseSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(IssuedLicenseSavedData::load, IssuedLicenseSavedData::new, DATA_NAME);
    }

    public static IssuedLicenseSavedData load(CompoundTag tag) {
        IssuedLicenseSavedData data = new IssuedLicenseSavedData();
        ListTag listTag = tag.getList(TAG_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag entry = listTag.getCompound(i);
            Issue issue = new Issue(
                    entry.getString(TAG_LICENSE),
                    entry.getUUID(TAG_OWNER_UUID),
                    entry.getString(TAG_OWNER_NAME),
                    entry.getLong(TAG_ISSUE_DATE));
            data.issues.put(issue.licenseNumber(), issue);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (Issue issue : issues.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_LICENSE, issue.licenseNumber());
            entry.putUUID(TAG_OWNER_UUID, issue.ownerUuid());
            entry.putString(TAG_OWNER_NAME, issue.ownerName());
            entry.putLong(TAG_ISSUE_DATE, issue.issueDate());
            listTag.add(entry);
        }
        tag.put(TAG_LIST, listTag);
        return tag;
    }

    /** 発行を記録する。同じ免許番号が既にあれば上書きせず例外にする(発行側が一意な番号を作る責任を持つ)。 */
    public void record(String licenseNumber, UUID ownerUuid, String ownerName, long issueDate) {
        if (issues.containsKey(licenseNumber)) {
            throw new IllegalStateException("License number already issued: " + licenseNumber);
        }
        issues.put(licenseNumber, new Issue(licenseNumber, ownerUuid, ownerName, issueDate));
        setDirty();
    }

    public boolean isIssued(String licenseNumber) {
        return issues.containsKey(licenseNumber);
    }

    @Nullable
    public Issue find(String licenseNumber) {
        return issues.get(licenseNumber);
    }

    /** そのプレイヤーに発行した全免許番号。 */
    public List<String> licensesOwnedBy(UUID ownerUuid) {
        List<String> result = new ArrayList<>();
        for (Issue issue : issues.values()) {
            if (issue.ownerUuid().equals(ownerUuid)) {
                result.add(issue.licenseNumber());
            }
        }
        return result;
    }
}
