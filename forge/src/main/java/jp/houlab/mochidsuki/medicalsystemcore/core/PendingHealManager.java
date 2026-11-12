package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 他人への包帯の開始をサーバーが記録する。メモリ上のみで保存しない。
 * 完了パケット(ServerboundFinishHealPacket)は、この記録からの経過時間を検証して受け付ける。
 * すべてサーバースレッド上で触るため同期は不要。
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID)
public final class PendingHealManager {
    /** 包帯の開始の記録。 */
    public record Pending(UUID targetUuid, long startGameTime) {
    }

    /** healer の UUID -> 治療中の記録。 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private PendingHealManager() {
    }

    /**
     * 開始を記録する。右クリックを押し続けている間 interactLivingEntity は繰り返し呼ばれるため、
     * 同じ対象の記録があれば開始時刻を保持する(上書きすると経過時間が永久に足りなくなる)。
     * 対象が変わったときだけ置き換える。
     */
    public static void begin(ServerPlayer healer, ServerPlayer target) {
        Pending existing = PENDING.get(healer.getUUID());
        if (existing != null && existing.targetUuid().equals(target.getUUID())) return;
        PENDING.put(healer.getUUID(), new Pending(target.getUUID(), healer.server.overworld().getGameTime()));
    }

    /** healer の記録を取得する(消さない)。無ければ null。 */
    public static Pending peek(ServerPlayer healer) {
        return PENDING.get(healer.getUUID());
    }

    /** healer の記録を消す。 */
    public static void remove(ServerPlayer healer) {
        PENDING.remove(healer.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
