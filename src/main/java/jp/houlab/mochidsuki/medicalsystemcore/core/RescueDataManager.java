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
        RESCUE_DATA_LIST.add(0, newData); // 常にリストの先頭に追加

        // この新しいデータを全クライアントに送信
        ModPackets.sendToAllClients(new ClientboundUpdateRescueDataPacket(newData, true));
    }

    /**
     * クライアントからの更新要求を処理する
     * @param player 更新を要求したプレイヤー
     * @param rescueId 更新対象の通報ID
     * @param type 更新の種類 (CHECKBOX or MEMO)
     * @param value チェックボックスの値 (isDispatch)
     * @param memo メモの内容 or isTreatmentの値
     */
    public static void updateRescueData(ServerPlayer player, int rescueId, ServerboundUpdateRescueDataPacket.UpdateType type, boolean value, String memo) {
        // TODO: ここに更新を実行できる権限があるかどうかのチェックを入れる

        findRescueDataById(rescueId).ifPresent(data -> {
            boolean updated = false;
            if (type == ServerboundUpdateRescueDataPacket.UpdateType.CHECKBOX) {
                boolean isTreatment = Boolean.parseBoolean(memo);
                if (data.isDispatch() != value || data.isTreatment() != isTreatment) {
                    data.setDispatch(value);
                    data.setTreatment(isTreatment);
                    updated = true;
                }
            } else if (type == ServerboundUpdateRescueDataPacket.UpdateType.MEMO) {
                if (!data.getMemo().equals(memo)) {
                    data.setMemo(memo);
                    updated = true;
                }
            }

            // データが実際に変更された場合のみ、全クライアントに同期パケットを送信
            if (updated) {
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