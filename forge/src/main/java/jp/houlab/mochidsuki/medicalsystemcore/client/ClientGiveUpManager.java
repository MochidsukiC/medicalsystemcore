package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundGiveUpPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ClientGiveUpManager {
    private static int holdTicks = 0;
    private static boolean holding = false;
    /**
     * 今回の押下ですでにギブアップを送ったか。
     * Shift を離す(または意識不明でなくなる・切断する)まで true のままにし、押しっぱなしで繰り返し送らない。
     * 進捗のリセット(reset)とは別に持つ: reset は GUI を開いたときなどにも呼ばれ、押下の途中でも進捗だけを戻すため。
     */
    private static boolean sentThisPress = false;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            resetAll();
            return;
        }

        if (!MedicalConfig.get(MedicalConfigKeys.GIVE_UP_ENABLED)) {
            resetAll();
            return;
        }

        // 意識不明でなければ何もしない
        if (!ClientMedicalDataManager.isPlayerUnconscious(mc.player)) {
            resetAll();
            return;
        }

        // Shiftキーの生の状態をGLFWから直接検出（KeyMapping状態管理の影響を回避）
        boolean shiftDown;
        long windowHandle = mc.getWindow().getWindow();
        InputConstants.Key key = mc.options.keyShift.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            shiftDown = InputConstants.isKeyDown(windowHandle, key.getValue());
        } else if (key.getType() == InputConstants.Type.MOUSE) {
            shiftDown = GLFW.glfwGetMouseButton(windowHandle, key.getValue()) == GLFW.GLFW_PRESS;
        } else {
            shiftDown = mc.options.keyShift.isDown();
        }

        // 離したら送信済みを解除する(GUI が開いている間の押し直しも見逃さないよう、GUI の判定より前に行う)
        if (!shiftDown) {
            sentThisPress = false;
        }

        // GUIが開いている場合はタイマーを進めない
        if (mc.screen != null) {
            reset();
            return;
        }

        if (shiftDown) {
            // 送信済みの押下では進捗も進めない(押し直すまで何もしない)
            if (sentThisPress) {
                reset();
                return;
            }

            holdTicks++;
            holding = true;

            int requiredTicks = MedicalConfig.get(MedicalConfigKeys.GIVE_UP_DURATION) * 20;
            if (holdTicks >= requiredTicks) {
                // サーバーにギブアップパケット送信(1 回の押下につき 1 回だけ)
                ModPackets.sendToServer(new ServerboundGiveUpPacket());
                sentThisPress = true;
                reset();
            }
        } else {
            reset();
        }
    }

    /** 押下の進捗だけを戻す。送信済みの印は残す。 */
    public static void reset() {
        holdTicks = 0;
        holding = false;
    }

    /** 進捗も送信済みの印も消す(プレイヤーなし・機能無効・意識あり・切断)。 */
    public static void resetAll() {
        reset();
        sentThisPress = false;
    }

    public static boolean isHolding() {
        return holding;
    }

    public static float getProgress() {
        if (!holding) return 0;
        int requiredTicks = MedicalConfig.get(MedicalConfigKeys.GIVE_UP_DURATION) * 20;
        if (requiredTicks == 0) return 0;
        return (float) holdTicks / requiredTicks;
    }
}
