package jp.houlab.mochidsuki.medicalsystemcore.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.RescueCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通報の一覧と次の ID を、ワールド全体のデータストレージ(MinecraftServer#getDataStorage)に保存する。
 * ファイルは {@code <world>/data/medicalsystemcore/rescue.dat}。ワールドごとに別で、サーバー再起動後も残る。
 *
 * <p>スレッド: 呼び出しはサーバースレッドからのみ行う前提。パケットはメインスレッドで処理され、
 * コマンドはサーバースレッドで実行されるため、通常の経路はすべて満たす。念のため各メソッドは
 * synchronized にしてあるが、取得した RescueData の setter 呼び出しまでは保護しない
 * (変更は必ず {@link #markChanged()} とセットで行うこと。RescueDataManager が担う)。</p>
 *
 * <p>ID 採番はここ 1 か所に集約している。削除しても nextId は戻さないため、ID は再利用されない
 * (クライアントは ID で追跡・選択を管理している)。</p>
 *
 * <p>保存順は追加順(先頭が最古、末尾が最新)。クライアントへの全件同期だけ新しい順へ並べ替える。</p>
 */
public class RescueSavedData extends SavedData {

    public static final Codec<RescueSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("nextId").forGetter(RescueSavedData::getNextId),
            RescueData.CODEC.listOf().fieldOf("rescues").forGetter(RescueSavedData::getOldestFirst)
    ).apply(instance, RescueSavedData::new));

    /** dataFixType は null(MOD 独自のデータにバニラの DataFixer を通さない)。 */
    public static final SavedDataType<RescueSavedData> TYPE = new SavedDataType<>(
            Medicalsystemcore.id("rescue"), RescueSavedData::new, CODEC, null);

    private final List<RescueData> list = new ArrayList<>();
    private int nextId = 1;

    public RescueSavedData() {
    }

    /** Codec の読み込み用。Codec が返すリストは変更できないので、コピーして持つ。 */
    private RescueSavedData(int nextId, List<RescueData> rescues) {
        this.nextId = nextId;
        this.list.addAll(rescues);
    }

    public static RescueSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private synchronized int getNextId() {
        return nextId;
    }

    /** 新しい通報を ID 採番して末尾(最新)に追加する。 */
    public synchronized RescueData create(UUID reporterUuid, String reporterName, RescueCategory category, String description,
                                          BlockPos location, ResourceKey<Level> dimension) {
        RescueData rescue = new RescueData(nextId++, reporterUuid, reporterName, category, description, location, dimension);
        list.add(rescue);
        setDirty();
        return rescue;
    }

    public synchronized Optional<RescueData> findById(int id) {
        return list.stream().filter(data -> data.getId() == id).findFirst();
    }

    /** 削除できたら true。 */
    public synchronized boolean remove(int id) {
        boolean removed = list.removeIf(data -> data.getId() == id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** 保存順(古い順)のコピー。 */
    public synchronized List<RescueData> getOldestFirst() {
        return new ArrayList<>(list);
    }

    /** 新しい順のコピー。クライアントの一覧は新着を先頭に入れるため、全件同期はこの順で送る。 */
    public synchronized List<RescueData> getNewestFirst() {
        List<RescueData> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    /** 要素の setter で内容を書き換えたあとに呼ぶ(保存対象としてマークする)。 */
    public synchronized void markChanged() {
        setDirty();
    }
}
