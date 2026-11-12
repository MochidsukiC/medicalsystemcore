package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.QteRules;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
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

    // スキルチェックのゾーン定義 (0.0 ~ 1.0)
    // 区間の定義と判定は common の QteRules にある


    public static void start(Entity target) {
        if (!isActive) {
            isActive = true;
            targetId = target.getId();
            barPosition = 0.0f;
            direction = 1;
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
        ModPackets.sendToServer(new ServerboundQTETResultPacket(targetId, result));

        isActive = false;
        targetId = -1;
    }

    public static void tick() {
        if (!isActive) return;

        // バーを動かす
        barPosition += barSpeed * direction;

        // バーが端に達したら反転、または失敗
        if (QteRules.isOvershoot(barPosition)) {
            LOGGER.info("QTE Result: FAILED (Overshot)");
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c失敗..."));
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
    public static boolean isActive() { return isActive; }
    public static float getBarPosition() { return barPosition; }
    public static float getSuccessStart() { return QteRules.SUCCESS_START; }
    public static float getGreatSuccessStart() { return QteRules.GREAT_SUCCESS_START; }
    public static float getGreatSuccessEnd() { return QteRules.GREAT_SUCCESS_END; }
    public static float getSuccessEnd() { return QteRules.SUCCESS_END; }
}