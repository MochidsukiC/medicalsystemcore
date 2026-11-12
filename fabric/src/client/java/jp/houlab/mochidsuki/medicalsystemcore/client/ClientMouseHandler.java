package jp.houlab.mochidsuki.medicalsystemcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * 意識障害時の視点操作をサーバーに送信しないようにするクラス
 *
 * 仕様：
 * - 意識障害のプレイヤーは視点を自由に動かすことができる
 * - 他のプレイヤーから見ると、意識障害のプレイヤーは全く動いていないように見える
 * - これはクライアント側で体と頭の回転を固定し、サーバーに送信しないことで実現
 */
public class ClientMouseHandler {

    private static float fixedYaw = 0.0f;
    private static float fixedPitch = 0.0f;
    private static boolean wasUnconscious = false;

    /**
     * 意識障害時の視点固定処理
     * この処理はクライアント側のティックで呼び出される
     */
    public static void handleUnconsciousView() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        boolean isUnconscious = ClientMedicalDataManager.isPlayerUnconscious(player);

        if (isUnconscious && !wasUnconscious) {
            // 意識を失った瞬間の向きを記録
            fixedYaw = player.getYRot();
            fixedPitch = player.getXRot();
            wasUnconscious = true;
        } else if (!isUnconscious && wasUnconscious) {
            // 意識回復
            wasUnconscious = false;
        }

        if (isUnconscious) {
            // 意識障害時：他のプレイヤーから見える体と頭の向きを固定
            // プレイヤー自身の視点（カメラ）は自由に動かせる
            player.yBodyRot = fixedYaw;
            player.yHeadRot = fixedYaw;
            player.yBodyRotO = fixedYaw;  // 前フレームの値
            player.yHeadRotO = fixedYaw;  // 前フレームの値

            // サーバーに送信される実際の回転値も固定
            // 注意: これだけでは完全ではないため、ネットワークレベルでの
            // インターセプトが必要（NetworkInterceptHandler参照）
        }
    }

    /**
     * 意識不明中の視点固定の状態を消す(切断時。次の接続に古い向きを持ち越さない)
     */
    public static void reset() {
        fixedYaw = 0.0f;
        fixedPitch = 0.0f;
        wasUnconscious = false;
    }

    public static boolean isPlayerVisuallyFixed() {
        return wasUnconscious;
    }

    public static float getFixedYaw() {
        return fixedYaw;
    }

    public static float getFixedPitch() {
        return fixedPitch;
    }
}