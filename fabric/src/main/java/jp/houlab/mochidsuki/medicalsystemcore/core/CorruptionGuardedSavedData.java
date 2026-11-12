package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 壊れた .dat を空データで上書きしないための SavedData 共通基底。
 *
 * <p>バニラの SavedDataStorage は、.dat が読めないとエラーログを出して null を返し、computeIfAbsent が
 * 新しい空データを作って dirty にする。次の保存で壊れたファイルが空データに置き換わってしまう。
 * 失効リストが消えると失効した医師カードが有効に戻るため、読めなかったファイルには書き込まない。</p>
 *
 * <p>破損を検出したら、「保存しない」印の付いた空データをストレージに登録する(以後のプレイは空データで動く)。
 * 印の付いたデータは isDirty() が常に false なので保存対象に拾われない。変更が加わるたびにエラーログを出す。
 * 管理者がファイルを直すか消してサーバーを再起動すれば、通常の動作に戻る。</p>
 */
public abstract class CorruptionGuardedSavedData extends SavedData {

    /** 読めなかった .dat のパス。null なら正常なデータ。 */
    private @Nullable Path corruptFile;

    /**
     * ストレージからデータを取得する。無ければ新規に作る。
     * ファイルが存在するのに読めなかった場合は、保存しない印の付いた空データを登録して返す。
     */
    protected static <T extends CorruptionGuardedSavedData> T getOrCreate(MinecraftServer server, SavedDataType<T> type) {
        SavedDataStorage storage = server.getDataStorage();
        T loaded = storage.get(type);
        if (loaded != null) {
            return loaded;
        }

        // バニラの SavedDataStorage#getDataFile と同じ規則でデータファイルを求める。
        Path file = type.id().withSuffix(".dat").resolveAgainst(server.getWorldPath(LevelResource.DATA));
        if (!Files.exists(file)) {
            return storage.computeIfAbsent(type);
        }

        // ファイルはあるのに読めなかった(バニラ側がエラーを出して null を返した)。
        T placeholder = type.constructor().get();
        // set() は setDirty() を呼ぶので、印を付ける前に登録し、そのあと印と同時に dirty を落とす。
        storage.set(type, placeholder);
        placeholder.markCorrupt(file);
        Medicalsystemcore.LOGGER.error(
                "Saved data file {} could not be read (it is corrupt). This mod will NOT write to this file, "
                        + "so the existing file is left untouched and everything in it is treated as empty. "
                        + "Changes made now will not be saved until an administrator repairs or deletes the file "
                        + "and restarts the server.", file);
        return placeholder;
    }

    final void markCorrupt(Path file) {
        super.setDirty(false);
        this.corruptFile = file;
    }

    @Override
    public void setDirty(boolean dirty) {
        if (corruptFile == null) {
            super.setDirty(dirty);
            return;
        }
        // 破損データは dirty にしない。変更が失われることを管理者に知らせるため、変更のたびにログを出す。
        if (dirty) {
            Medicalsystemcore.LOGGER.error(
                    "Saved data file {} is corrupt, so this change is NOT saved and will be lost on restart. "
                            + "Repair or delete the file and restart the server.", corruptFile);
        }
    }

    @Override
    public boolean isDirty() {
        return corruptFile == null && super.isDirty();
    }
}
