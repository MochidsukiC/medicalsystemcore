package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ギブアップ(搬送/自死)を「意識不明の 1 回の状態につき 1 回」に制限する。メモリ上のみで保存しない。
 * 意識が戻った時点(毎 tick 確認)、ログアウト、サーバー停止で解除する。
 * 死亡後のリスポーンでは医療データが引き継がれず意識ありに戻るため、同じ経路で解除される。
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID)
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

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || USED.isEmpty()) return;
        if (!(event.player instanceof ServerPlayer player) || !USED.contains(player.getUUID())) return;
        player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
            if (data.isConscious()) {
                USED.remove(player.getUUID());
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        USED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        USED.clear();
    }
}
