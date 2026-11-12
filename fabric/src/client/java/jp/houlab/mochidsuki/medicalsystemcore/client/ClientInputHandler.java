package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.client.mixin.ClientInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;

/**
 * クライアントサイドの入力を監視し、必要に応じてブロックするクラス
 *
 * クライアントサイドでも姿勢制御を行い、バニラの姿勢制御を確実に上書きする
 *
 * <p>Forge ではイベント購読だったものを、Fabric では次の形で呼ぶ(MedicalsystemcoreClient と client 用 Mixin から)。</p>
 * <ul>
 *   <li>{@link #onClientTick}: ClientTickEvents.END_CLIENT_TICK(Forge の TickEvent.PlayerTickEvent クライアント END)</li>
 *   <li>{@link #onMovementInputUpdate}: LocalPlayerMixin(Forge の MovementInputUpdateEvent)</li>
 *   <li>{@link #onMouseButton}: MouseHandlerMixin(Forge の InputEvent.MouseButton.Pre)</li>
 * </ul>
 */
public class ClientInputHandler {

    /**
     * 毎クライアント tick の終わりに呼ぶ。
     *
     * <p>Forge の PlayerTickEvent(クライアント側)は、レベルのエンティティ tick の中でプレイヤーごとに 1 回ずつ発火していた。
     * END_CLIENT_TICK は {@code Minecraft#tick} の末尾で、レベルが無いとき(タイトル画面)や
     * シングルプレイの一時停止中(エンティティ tick が止まる)にも発火する。Forge の発火条件に合わせるため、
     * レベルが無い・一時停止中は何もしない(QTE のバーが一時停止中に進んで失敗にならないように)。</p>
     */
    public static void onClientTick(Minecraft mc) {
        if (mc.level == null || mc.isPaused()) {
            return;
        }

        // ローカルプレイヤーの場合のみタイマーを更新
        if (mc.player != null) {
            ClientQTEManager.tick();
            ClientHealingManager.tick();
            ClientGiveUpManager.tick();
        }

        // クライアント上の全プレイヤー(ローカル含む)
        for (Player player : mc.level.players()) {
            boolean isUnconscious = ClientMedicalDataManager.isPlayerUnconscious(player);

            if (isUnconscious) {
                // 意識障害時の視点固定処理を呼び出し
                ClientMouseHandler.handleUnconsciousView();
                ClientPoseController.maintainPoseControl(player);
                enforceUnconsciousRestrictions(player);
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

    /**
     * 移動入力の更新直後に呼ぶ(LocalPlayer#aiStep の ClientInput#tick の直後)。
     * 意識障害状態の場合、前後左右の移動量・ジャンプ・しゃがみの入力を 0 にする。
     *
     * <p>Forge は forwardImpulse / leftImpulse / jumping / shiftKeyDown だけを 0 にしていた(上下左右のキー状態と
     * ダッシュのキー状態は触らない)。26.2 の対応は moveVector(移動量)と keyPresses の jump / shift。</p>
     */
    public static void onMovementInputUpdate(Player player, ClientInput input) {
        if (player == null) {
            return;
        }

        if (ClientMedicalDataManager.isPlayerUnconscious(player)) {
            ((ClientInputAccessor) input).medicalsystemcore$setMoveVector(Vec2.ZERO);
            Input keys = input.keyPresses;
            input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(),
                    false, // ジャンプ
                    false, // しゃがみも無効にする
                    keys.sprint());
        }
    }

    /**
     * 意識不明時のマウスクリック制限。MouseHandler#onButton の先頭で呼ぶ。
     *
     * @param button GLFW のマウスボタン番号
     * @param action GLFW のアクション(GLFW_PRESS / GLFW_RELEASE)
     * @return true ならそのクリックを無かったことにする(Forge の event.setCanceled(true))
     */
    public static boolean onMouseButton(int button, int action) {
        // QTE用の右クリック処理は特別に許可
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_RELEASE && ClientQTEManager.isActive()) {
            ClientQTEManager.stop();
            return false;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.gui.screen() != null) {
            return false;
        }

        // 意識不明時は全てのマウスクリックを無効化
        return mc.player != null && ClientMedicalDataManager.isPlayerUnconscious(mc.player);
    }
}
