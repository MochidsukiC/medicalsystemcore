package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * クライアントサイドの入力を監視し、必要に応じてブロックするクラス
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientInputHandler {

    @SubscribeEvent
    public static void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() && event.phase == TickEvent.Phase.END) {
            Player player = event.player;

            ClientQTEManager.tick();
            ClientHealingManager.tick();

            if (player != null) {
                boolean isUnconscious = ClientMedicalDataManager.isPlayerUnconscious(player);

                if (isUnconscious) {
                    // 意識障害状態の処理
                    if (player.getPose() != Pose.SWIMMING) {
                        player.setPose(Pose.SWIMMING);
                    }

                    // 意識障害時の視点固定処理を呼び出し
                    ClientMouseHandler.handleUnconsciousView();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        // 意識障害状態の場合、全ての移動入力をゼロにする
        if (ClientMedicalDataManager.isPlayerUnconscious(player)) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false; // しゃがみも無効にする
        }
    }

    /**
     * マウスボタンの入力イベント
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Post event) {
        // 右クリックが「離された」時、かつQTEがアクティブな場合
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_RELEASE && ClientQTEManager.isActive()) {
            ClientQTEManager.stop();
        }
    }
}