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

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            reset();
            return;
        }

        if (!MedicalConfig.get(MedicalConfigKeys.GIVE_UP_ENABLED)) {
            reset();
            return;
        }

        // 意識不明でなければ何もしない
        if (!ClientMedicalDataManager.isPlayerUnconscious(mc.player)) {
            reset();
            return;
        }

        // GUIが開いている場合はタイマーを進めない
        if (mc.screen != null) {
            reset();
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

        if (shiftDown) {
            holdTicks++;
            holding = true;

            int requiredTicks = MedicalConfig.get(MedicalConfigKeys.GIVE_UP_DURATION) * 20;
            if (holdTicks >= requiredTicks) {
                // サーバーにギブアップパケット送信
                ModPackets.sendToServer(new ServerboundGiveUpPacket());
                reset();
            }
        } else {
            reset();
        }
    }

    public static void reset() {
        holdTicks = 0;
        holding = false;
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
