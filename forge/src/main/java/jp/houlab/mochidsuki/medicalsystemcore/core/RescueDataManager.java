package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.RescueCategory;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.RescueRules;

import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRemoveRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundUpdateRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * サーバーサイドで通報データを操作するクラス。
 * 通報の実体はワールドのセーブデータ({@link RescueSavedData})にあり、ここは更新と医師への配信を担う。
 * すべてサーバースレッドから呼ぶこと。
 */
public class RescueDataManager {

    /**
     * 新しい通報データを作成し、医師として認可されているオンラインのプレイヤーに通知する
     * @param reporterUuid 通報者の UUID(照合用)
     * @param reporterName 通報者名(表示用)
     * @param category 通報カテゴリ
     * @param description 通報内容
     * @param location 通報地点
     * @param dimension ディメンション
     */
    public static void createNewRescueData(MinecraftServer server, UUID reporterUuid, String reporterName, RescueCategory category, String description, BlockPos location, ResourceKey<Level> dimension) {
        // ID の採番と保存は RescueSavedData が行う
        RescueData newData = RescueSavedData.get(server).create(reporterUuid, reporterName, category, description, location, dimension);

        // この新しいデータを医師にだけ送信(通報者の座標が一般プレイヤーに漏れないようにする)
        sendToMedicalStaff(server, new ClientboundUpdateRescueDataPacket(newData, true));
    }

    /**
     * 医師として認可されているオンラインのプレイヤーだけにパケットを送る。
     * 認可の判定は MedicalAuthorizationUtil に従う(エラーメッセージを送らない Silent 版)。
     */
    public static <MSG> void sendToMedicalStaff(MinecraftServer server, MSG message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
        RescueSavedData saved = RescueSavedData.get(player.server);
        saved.findById(rescueId).ifPresent(data -> {
            // データが実際に変更されたかチェック
            if (data.isDispatch() != isDispatch || data.isTreatment() != isTreatment) {
                data.setDispatch(isDispatch);
                data.setTreatment(isTreatment);
                saved.markChanged();
                // 医師に更新をブロードキャスト
                sendToMedicalStaff(player.server, new ClientboundUpdateRescueDataPacket(data, false));
            }
        });
    }


    /**
     * メモの内容を更新し、医師に同期する。
     * 権限と長さの確認は呼び出し元(ServerboundUpdateRescueDataPacket)が入口で行う。
     * ギブアップの自動追記(患者本人は医師とは限らない)もここを通るため、この中では権限を確認しない。
     */
    public static void updateRescueDataMemo(ServerPlayer player, int rescueId, String memo) {
        RescueSavedData saved = RescueSavedData.get(player.server);
        saved.findById(rescueId).ifPresent(data -> {
            // データが実際に変更されたかチェック
            if (!memo.equals(data.getMemo())) {
                data.setMemo(memo);
                saved.markChanged();
                // 医師に更新をブロードキャスト
                sendToMedicalStaff(player.server, new ClientboundUpdateRescueDataPacket(data, false));
            }
        });
    }

    /**
     * 通報を削除し、医師に削除を通知する。
     * 権限の確認は呼び出し元(ServerboundDeleteRescueDataPacket)が入口で行う。
     * @return 実際に削除したら true(存在しない ID なら false。通知は送らない)
     */
    public static boolean deleteRescueData(MinecraftServer server, int rescueId) {
        if (!RescueSavedData.get(server).remove(rescueId)) {
            return false;
        }
        sendToMedicalStaff(server, new ClientboundRemoveRescueDataPacket(rescueId));
        return true;
    }

    /**
     * 通報者(UUID で照合)の最新の通報のメモへ、システムの追記(「死亡」「搬送」)を加える。
     * メモが上限(RescueRules.MEMO_MAX_LENGTH)を超える場合は、追記を残すために既存メモの末尾側を切り詰める。
     */
    public static void appendSystemMemo(ServerPlayer reporter, String tag) {
        // 保存順は古い順(末尾が最新)なので、最新のものから検索する
        List<RescueData> dataList = RescueSavedData.get(reporter.server).getOldestFirst();

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
    public static Optional<RescueData> findRescueDataById(MinecraftServer server, int id) {
        return RescueSavedData.get(server).findById(id);
    }

    /**
     * クライアントへ全件同期するための通報リスト(新しい順)。
     * クライアントの一覧は先頭が最新(新着は先頭へ挿入、ウィジェットは index 0 が最上段)なので、それに揃える。
     */
    public static List<RescueData> getRescueDataListNewestFirst(MinecraftServer server) {
        return RescueSavedData.get(server).getNewestFirst();
    }
}
