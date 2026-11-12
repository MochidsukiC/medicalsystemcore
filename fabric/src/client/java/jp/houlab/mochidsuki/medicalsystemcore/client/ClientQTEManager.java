package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.QteRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundQTETResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class ClientQTEManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    // QTEの状態
    private static boolean isActive = false;
    private static int targetId = -1;
    private static float barPosition = 0.0f; // 0.0 ~ 1.0
    private static final float barSpeed = QteRules.BAR_SPEED_PER_TICK;
    private static int direction = 1;

    // 結果表示(HUD)用。最後の結果と、その終了時刻(clientTicks 基準)。結果が無いときは null。
    /** 結果表示を出し続ける tick 数(1 秒)。 */
    public static final int RESULT_DISPLAY_TICKS = 20;
    private static QTEResult lastResult = null;
    private static int lastResultTick = 0;
    private static int lastResultTargetId = -1; // 結果表示中も患者の心リズムを引けるよう、終了時の対象 ID を残す
    /**
     * このクラスの tick() が呼ばれた回数(クライアントの 1 tick に 1 回)。
     * 波形のスクロールや明滅など時間で変わる表示は、フレーム数ではなくこの値と partialTick から求める。
     */
    private static int clientTicks = 0;

    // スキルチェックのゾーン定義 (0.0 ~ 1.0)
    // 区間の定義と判定は common の QteRules にある


    public static void start(Entity target) {
        if (!isActive) {
            isActive = true;
            targetId = target.getId();
            barPosition = 0.0f;
            direction = 1;
            lastResult = null; // 新しい QTE が始まったら前の結果表示は消す
        }
    }

    public static void stop() {
        if (!isActive) return;

        // 結果を判定
        QTEResult result = QteRules.judge(barPosition);
        switch (result) {
            case GREAT_SUCCESS -> Minecraft.getInstance().player.sendSystemMessage(Component.literal("§6大成功！"));
            case SUCCESS -> Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a成功。"));
            case FAIL -> Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c失敗..."));
        }

        // ▼▼▼ 判定結果をサーバーに送信 ▼▼▼
        ClientPlayNetworking.send(new ServerboundQTETResultPacket(targetId, result));

        recordResult(result);
        isActive = false;
        targetId = -1;
    }

    private static void recordResult(QTEResult result) {
        lastResultTargetId = targetId;
        lastResult = result;
        lastResultTick = clientTicks;
    }

    /**
     * 結果を送らずに QTE の状態だけを消す(切断時)。
     * サーバーは接続の切断で QTE の記録を破棄しチャージを解除するため、ここでは何も送らない。
     */
    public static void reset() {
        isActive = false;
        targetId = -1;
        barPosition = 0.0f;
        direction = 1;
        lastResult = null;
        lastResultTick = 0;
        lastResultTargetId = -1;
        clientTicks = 0;
    }

    public static void tick() {
        clientTicks++;
        if (!isActive) return;

        // バーを動かす
        barPosition += barSpeed * direction;

        // バーが端に達したら反転、または失敗
        if (QteRules.isOvershoot(barPosition)) {
            LOGGER.info("QTE Result: FAILED (Overshot)");
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c失敗..."));
            // サーバーは QTE の開始を記録しているので、失敗も報告して除細動器のチャージを解除させる
            ClientPlayNetworking.send(new ServerboundQTETResultPacket(targetId, QTEResult.FAIL));
            recordResult(QTEResult.FAIL);
            isActive = false; // バーが行き過ぎたら無条件で失敗
            targetId = -1;
        }
        // 反転させたい場合は以下のコメントアウトを解除
        /*
        if (barPosition > 1.0f || barPosition < 0.0f) {
            direction *= -1;
        }
        */
    }

    // HUD描画用のゲッター
    /** HUD が心リズムを引く対象のエンティティ ID。実行中は QTE の対象、結果表示中は終了した QTE の対象。 */
    public static int getDisplayTargetId() { return isActive ? targetId : lastResultTargetId; }
    /** 時間アニメーション用の時刻(tick)。partialTick を足してフレーム間を補間する。 */
    public static float getAnimationTime(float partialTick) { return clientTicks + partialTick; }
    /** 表示中の結果。結果が無い・表示時間を過ぎた・QTE 実行中なら null。 */
    public static QTEResult getDisplayedResult() {
        if (isActive || lastResult == null) return null;
        return clientTicks - lastResultTick < RESULT_DISPLAY_TICKS ? lastResult : null;
    }
    /** 結果が出てからの経過時間(tick)。partialTick を足してフレーム間を補間する。 */
    public static float getResultAge(float partialTick) { return clientTicks - lastResultTick + partialTick; }
    public static boolean isActive() { return isActive; }
    public static float getBarPosition() { return barPosition; }
    public static float getSuccessStart() { return QteRules.SUCCESS_START; }
    public static float getGreatSuccessStart() { return QteRules.GREAT_SUCCESS_START; }
    public static float getGreatSuccessEnd() { return QteRules.GREAT_SUCCESS_END; }
    public static float getSuccessEnd() { return QteRules.SUCCESS_END; }
}