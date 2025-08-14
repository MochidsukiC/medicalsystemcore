package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * クライアントサイドの入力を監視し、必要に応じてブロックするクラス
 *
 * クライアントサイドでも姿勢制御を行い、バニラの姿勢制御を確実に上書きする
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
                    // 意識障害時の視点固定処理を呼び出し
                    ClientMouseHandler.handleUnconsciousView();
                    ClientPoseController.maintainPoseControl(player);
                    enforceUnconsciousRestrictions(player);

                }
            }
        }
    }

    /**
      * 意識不明時の追加制限を強制適用
     */
    private static void enforceUnconsciousRestrictions(Player player) {
        // 移動の完全停止（追加の安全策）
        player.setDeltaMovement(player.getDeltaMovement().multiply(0, 1, 0)); // Y軸（重力）は維持

        // ジャンプ状態の強制解除
        if (player.onGround()) {
            player.setOnGround(true);
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
     * 意識不明時のマウスクリック制限
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        // QTE用の右クリック処理は特別に許可
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_RELEASE && ClientQTEManager.isActive()) {
            ClientQTEManager.stop();
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();


        if (mc.screen != null) {
            return;
        }

        // 意識不明時は全てのマウスクリックを無効化
        if (mc.player != null && ClientMedicalDataManager.isPlayerUnconscious(mc.player)) {
            event.setCanceled(true);
        }
    }




    /**
     * 意識不明時のマウススクロール制限
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && ClientMedicalDataManager.isPlayerUnconscious(mc.player)) {
            event.setCanceled(true);
        }
    }
}