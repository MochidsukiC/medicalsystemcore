package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.core.QTEResult;
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
    private static float barSpeed = 0.02f; // 1秒でバーの半分を進む速さ
    private static int direction = 1;

    // スキルチェックのゾーン定義 (0.0 ~ 1.0)
    private static final float SUCCESS_START = 0.6f;
    private static final float GREAT_SUCCESS_START = 0.75f;
    private static final float GREAT_SUCCESS_END = 0.85f;
    private static final float SUCCESS_END = 1.0f;


    public static void start(Entity target) {
        if (!isActive) {
            isActive = true;
            targetId = target.getId();
            barPosition = 0.0f;
            direction = 1;
            LOGGER.info("QTE Minigame Started for target: {}", targetId);
        }
    }

    public static void stop() {
        if (!isActive) return;

        QTEResult result;
        // 結果を判定
        if (barPosition >= GREAT_SUCCESS_START && barPosition <= GREAT_SUCCESS_END) {
            result = QTEResult.GREAT_SUCCESS;
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§6大成功！"));
        } else if (barPosition >= SUCCESS_START && barPosition <= SUCCESS_END) {
            result = QTEResult.SUCCESS;
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a成功。"));
        } else {
            result = QTEResult.FAIL;
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c失敗..."));
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
        if (barPosition > 1.0f) {
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
    public static float getSuccessStart() { return SUCCESS_START; }
    public static float getGreatSuccessStart() { return GREAT_SUCCESS_START; }
    public static float getGreatSuccessEnd() { return GREAT_SUCCESS_END; }
    public static float getSuccessEnd() { return SUCCESS_END; }
}