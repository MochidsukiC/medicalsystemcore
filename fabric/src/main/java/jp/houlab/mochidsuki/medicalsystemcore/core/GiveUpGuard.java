package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.capability.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ギブアップ(搬送/自死)を「意識不明の 1 回の状態につき 1 回」に制限する。メモリ上のみで保存しない。
 * 意識が戻った時点(毎 tick 確認)、ログアウト、サーバー停止で解除する。
 * 死亡後のリスポーンでは医療データが引き継がれず意識ありに戻るため、同じ経路で解除される。
 */
public final class GiveUpGuard {
    private static final Set<UUID> USED = new HashSet<>();

    private GiveUpGuard() {
    }

    public static boolean hasUsed(ServerPlayer player) {
        return USED.contains(player.getUUID());
    }

    /** ギブアップを実際に実行した時点で呼ぶ。 */
    public static void markUsed(ServerPlayer player) {
        USED.add(player.getUUID());
    }

    /** tick・ログアウト・サーバー停止のイベントを登録する。MOD 初期化で 1 回だけ呼ぶ。 */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GiveUpGuard::onEndServerTick);
        ServerPlayerEvents.LEAVE.register(player -> USED.remove(player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> USED.clear());
    }

    /** Forge の PlayerTickEvent(END)に相当。プレイヤー単位の tick イベントが無いので、全プレイヤーを回す。 */
    private static void onEndServerTick(MinecraftServer server) {
        if (USED.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!USED.contains(player.getUUID())) continue;
            if (player.getAttachedOrCreate(ModAttachments.MEDICAL_DATA).isConscious()) {
                USED.remove(player.getUUID());
            }
        }
    }
}
