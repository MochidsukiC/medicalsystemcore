package jp.houlab.mochidsuki.medicalsystemcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * サーバーからクライアントへ送られるパケットの処理を専門に行うクラス
 */
public class ClientPacketHandler {

    /**
     * サーバーからのQTE開始命令を受け取り、ClientQTEManagerを開始させる
     * @param targetId QTEの対象となるエンティティのID
     */
    public static void handleStartQTE(int targetId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity target = mc.level.getEntity(targetId);
        if (target != null) {
            ClientQTEManager.start(target);
        }
    }

    // 他のサーバー→クライアントへのパケット処理も、将来的にここに追加していきます
}