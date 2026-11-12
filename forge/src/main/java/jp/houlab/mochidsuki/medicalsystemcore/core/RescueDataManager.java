package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.RescueCategory;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.RescueRules;

import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundUpdateRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
     * 新しい通報データを作成し、医師として認可されているオンラインのプレイヤーに通知する
     * @param reporterUuid 通報者の UUID(照合用)
     * @param reporterName 通報者名(表示用)
     * @param category 通報カテゴリ
     * @param description 通報内容
     * @param location 通報地点
     * @param dimension ディメンション
     */
    public static void createNewRescueData(UUID reporterUuid, String reporterName, RescueCategory category, String description, BlockPos location, ResourceKey<Level> dimension) {
        // 新しいIDを採番してデータを作成
        RescueData newData = new RescueData(reporterUuid, reporterName, category, description, location, dimension);

        // サーバーのマスターリストに追加
        RESCUE_DATA_LIST.add(newData); // 常にリストの先頭に追加

        // この新しいデータを医師にだけ送信(通報者の座標が一般プレイヤーに漏れないようにする)
        sendToMedicalStaff(new ClientboundUpdateRescueDataPacket(newData, true));
    }

    /**
     * 医師として認可されているオンラインのプレイヤーだけにパケットを送る。
     * 認可の判定は MedicalAuthorizationUtil に従う(エラーメッセージを送らない Silent 版)。
     */
    private static <MSG> void sendToMedicalStaff(MSG message) {
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(player)) {
                ModPackets.sendToPlayer(message, player);
            }
        }
    }


    /**
     * チェックボックスの状態を更新し、医師に同期する。
     * 権限の確認は呼び出し元(ServerboundUpdateRescueDataPacket)が入口で行う。
     */
    public static void updateRescueDataCheckbox(ServerPlayer player, int rescueId, boolean isDispatch, boolean isTreatment) {
        findRescueDataById(rescueId).ifPresent(data -> {
            // データが実際に変更されたかチェック
            if (data.isDispatch() != isDispatch || data.isTreatment() != isTreatment) {
                data.setDispatch(isDispatch);
                data.setTreatment(isTreatment);
                // 医師に更新をブロードキャスト
                sendToMedicalStaff(new ClientboundUpdateRescueDataPacket(data, false));
            }
        });
    }


    /**
     * メモの内容を更新し、医師に同期する。
     * 権限と長さの確認は呼び出し元(ServerboundUpdateRescueDataPacket)が入口で行う。
     * ギブアップの自動追記(患者本人は医師とは限らない)もここを通るため、この中では権限を確認しない。
     */
    public static void updateRescueDataMemo(ServerPlayer player, int rescueId, String memo) {
        findRescueDataById(rescueId).ifPresent(data -> {
            // データが実際に変更されたかチェック
            if (!memo.equals(data.getMemo())) {
                data.setMemo(memo);
                // 医師に更新をブロードキャスト
                sendToMedicalStaff(new ClientboundUpdateRescueDataPacket(data, false));
            }
        });
    }

    /**
     * 通報者(UUID で照合)の最新の通報のメモへ、システムの追記(「死亡」「搬送」)を加える。
     * メモが上限(RescueRules.MEMO_MAX_LENGTH)を超える場合は、追記を残すために既存メモの末尾側を切り詰める。
     */
    public static void appendSystemMemo(ServerPlayer reporter, String tag) {
        List<RescueData> dataList = getRescueDataList();

        // 最新のものから検索（リストの末尾が最新）
        for (int i = dataList.size() - 1; i >= 0; i--) {
            RescueData data = dataList.get(i);
            if (reporter.getUUID().equals(data.getReporterUuid())) {
                String currentMemo = data.getMemo();
                String newMemo = (currentMemo == null || currentMemo.isEmpty()) ? tag : currentMemo + " / " + tag;
                if (newMemo.length() > RescueRules.MEMO_MAX_LENGTH) {
                    String suffix = " / " + tag;
                    newMemo = currentMemo.substring(0, RescueRules.MEMO_MAX_LENGTH - suffix.length()) + suffix;
                }
                updateRescueDataMemo(reporter, data.getId(), newMemo);
                break;
            }
        }
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
