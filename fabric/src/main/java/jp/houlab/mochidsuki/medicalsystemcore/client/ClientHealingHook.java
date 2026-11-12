package jp.houlab.mochidsuki.medicalsystemcore.client;

import java.util.function.IntConsumer;

/**
 * BandageItem(main)からクライアント側の治療開始処理(ClientHealingManager#startHealing。client ソースセット)を呼ぶための受け口。
 * main から client のクラスは参照できないので、client 側の初期化(段 4)が {@link #setStartHealing} で処理を差し込む。
 *
 * <p>差し込み前に呼ばれたら例外にする(差し込み忘れを黙って無視しない)。専用サーバーでは BandageItem の
 * クライアント分岐が実行されないので、この受け口は触られない。</p>
 */
public final class ClientHealingHook {
    private static volatile IntConsumer startHealing = targetEntityId -> {
        throw new IllegalStateException("ClientHealingHook.startHealing is not wired yet (client initializer must call setStartHealing)");
    };

    private ClientHealingHook() {
    }

    /** client の初期化から、ClientHealingManager::startHealing を差し込む。 */
    public static void setStartHealing(IntConsumer startHealing) {
        ClientHealingHook.startHealing = startHealing;
    }

    /** 対象エンティティへの治療開始をクライアントで試みる。 */
    public static void startHealing(int targetEntityId) {
        startHealing.accept(targetEntityId);
    }
}
