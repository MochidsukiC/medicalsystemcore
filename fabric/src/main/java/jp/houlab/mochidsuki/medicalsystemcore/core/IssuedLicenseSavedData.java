package jp.houlab.mochidsuki.medicalsystemcore.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 医師カードの発行台帳。grant で正規カードを発行するたびに、免許番号 → 持ち主(UUID・名前)・発行日を記録する。
 * ファイルは {@code <world>/data/medicalsystemcore/issued_licenses.dat}。
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

    /** 発行記録 1 件。 */
    public record Issue(String licenseNumber, UUID ownerUuid, String ownerName, long issueDate) {
        public static final Codec<Issue> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("license").forGetter(Issue::licenseNumber),
                UUIDUtil.CODEC.fieldOf("ownerUuid").forGetter(Issue::ownerUuid),
                Codec.STRING.fieldOf("ownerName").forGetter(Issue::ownerName),
                Codec.LONG.fieldOf("issueDate").forGetter(Issue::issueDate)
        ).apply(instance, Issue::new));
    }

    public static final Codec<IssuedLicenseSavedData> CODEC = Issue.CODEC.listOf().fieldOf("issued").xmap(
            IssuedLicenseSavedData::new,
            data -> new ArrayList<>(data.issues.values())).codec();

    /** dataFixType は null(MOD 独自のデータにバニラの DataFixer を通さない)。 */
    public static final SavedDataType<IssuedLicenseSavedData> TYPE = new SavedDataType<>(
            Medicalsystemcore.id("issued_licenses"), IssuedLicenseSavedData::new, CODEC, null);

    private final Map<String, Issue> issues = new LinkedHashMap<>();

    public IssuedLicenseSavedData() {
    }

    /** Codec の読み込み用。Codec が返すリストは変更できないので、変更できるマップにコピーして持つ。 */
    private IssuedLicenseSavedData(List<Issue> loaded) {
        for (Issue issue : loaded) {
            this.issues.put(issue.licenseNumber(), issue);
        }
    }

    public static IssuedLicenseSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
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
