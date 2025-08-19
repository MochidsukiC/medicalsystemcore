package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundUpdateRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundUpdateRescueDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * サーバーサイドで通報データを一元管理するクラス
 */
public class RescueDataManager {

    // サーバーが保持する唯一の通報リスト。スレッドセーフなリストを使用。
    private static final List<RescueData> RESCUE_DATA_LIST = Collections.synchronizedList(new ArrayList<>());
    // IDをスレッドセーフに採番するためのカウンター
    private static final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * 新しい通報データを作成し、全クライアントに通知する
     * @param reporterName 通報者名
     * @param category 通報カテゴリ
     * @param description 通報内容
     * @param location 通報地点
     */
    public static void createNewRescueData(String reporterName, RescueCategory category, String description, BlockPos location) {
        // 新しいIDを採番してデータを作成
        RescueData newData = new RescueData(reporterName, category, description, location);

        // サーバーのマスターリストに追加
        RESCUE_DATA_LIST.add(newData); // 常にリストの先頭に追加

        // この新しいデータを全クライアントに送信
        ModPackets.sendToAllClients(new ClientboundUpdateRescueDataPacket(newData, true));
    }


    /**
     * チェックボックスの状態を更新し、全クライアントに同期する
     */
    public static void updateRescueDataCheckbox(ServerPlayer player, int rescueId, boolean isDispatch, boolean isTreatment) {
        // TODO: ここに更新を実行できる権限があるかどうかのチェックを入れる
        findRescueDataById(rescueId).ifPresent(data -> {
            // データが実際に変更されたかチェック
            if (data.isDispatch() != isDispatch || data.isTreatment() != isTreatment) {
                data.setDispatch(isDispatch);
                data.setTreatment(isTreatment);
                // 全クライアントに更新をブロードキャスト
                ModPackets.sendToAllClients(new ClientboundUpdateRescueDataPacket(data, false));
            }
        });
    }


    /**
     * メモの内容を更新し、全クライアントに同期する
     */
    public static void updateRescueDataMemo(ServerPlayer player, int rescueId, String memo) {
        // TODO: ここに更新を実行できる権限があるかどうかのチェックを入れる
        findRescueDataById(rescueId).ifPresent(data -> {
            // データが実際に変更されたかチェック
            if (!memo.equals(data.getMemo())) {
                data.setMemo(memo);
                // 全クライアントに更新をブロードキャスト
                ModPackets.sendToAllClients(new ClientboundUpdateRescueDataPacket(data, false));
            }
        });
    }

    /**
     * IDを指定して通報データを検索する
     * @param id 検索するID
     * @return 見つかった場合はOptional<RescueData>、なければOptional.empty()
     */
    public static Optional<RescueData> findRescueDataById(int id) {
        // synchronizedブロックでリストへのアクセスを保護
        synchronized (RESCUE_DATA_LIST) {
            return RESCUE_DATA_LIST.stream().filter(data -> data.getId() == id).findFirst();
        }
    }

    /**
     * 現在の通報リストのコピーを返す
     * @return 通報リストの安全なコピー
     */
    public static List<RescueData> getRescueDataList() {
        synchronized (RESCUE_DATA_LIST) {
            return new ArrayList<>(RESCUE_DATA_LIST);
        }
    }
}