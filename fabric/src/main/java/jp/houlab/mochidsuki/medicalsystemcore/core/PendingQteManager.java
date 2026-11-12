package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.QteRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * サーバーが「除細動 QTE の開始」を記録する。メモリ上のみで保存しない。
 * 結果パケット(ServerboundQTETResultPacket)はこの記録に対して 1 回だけ受け付ける。
 * 記録は {@link QteRules#RESULT_TIMEOUT_TICKS} を過ぎるか healer がログアウトすると破棄され、そのとき除細動器のチャージを解除してクールダウンを始める。
 * すべてサーバースレッド上で触るため同期は不要。
 */
public final class PendingQteManager {
    /** QTE 開始の記録。 */
    public record Pending(UUID targetUuid, ResourceKey<Level> dimension, BlockPos defibrillatorPos,
                          InteractionHand hand, long startGameTime) {
        public boolean isExpired(long nowGameTime) {
            return nowGameTime - startGameTime > QteRules.RESULT_TIMEOUT_TICKS;
        }
    }

    /** healer の UUID -> 保留中の QTE。 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private PendingQteManager() {
    }

    /** healer に期限内の記録があるか。 */
    public static boolean hasLive(ServerPlayer healer) {
        Pending pending = PENDING.get(healer.getUUID());
        return pending != null && !pending.isExpired(healer.level().getServer().overworld().getGameTime());
    }

    /** QTE の開始を記録する。 */
    public static void begin(ServerPlayer healer, ServerPlayer target, BlockPos defibrillatorPos, InteractionHand hand) {
        PENDING.put(healer.getUUID(), new Pending(target.getUUID(), healer.level().dimension(),
                defibrillatorPos.immutable(), hand, healer.level().getServer().overworld().getGameTime()));
    }

    /** healer の記録を取り出す(消さない)。無ければ null。 */
    public static Pending peek(ServerPlayer healer) {
        return PENDING.get(healer.getUUID());
    }

    /** healer の記録を消す。 */
    public static void remove(ServerPlayer healer) {
        PENDING.remove(healer.getUUID());
    }

    /** 指定位置が CHARGED の除細動器ブロックか。別のブロックに置き換わっていれば false。 */
    public static boolean isChargedDefibrillator(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DefibrillatorBlock && state.getValue(DefibrillatorBlock.CHARGED);
    }

    /**
     * CHARGED の除細動器のチャージを解除してクールダウンを始める。
     * 結果の受理時とタイムアウト時の両方がこれを使う。
     * ブロックが別物になっている、または CHARGED でない場合は何もしない。
     */
    public static void releaseCharge(ServerLevel level, BlockPos pos) {
        if (!isChargedDefibrillator(level, pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DefibrillatorBlockEntity defibrillator) {
            defibrillator.resetCharge();
            defibrillator.startCooldown();
        }
    }

    /** 期限切れの記録の後始末。未ロードのチャンクは強制ロードしない。 */
    public static void expire(MinecraftServer server, Pending pending) {
        ServerLevel level = server.getLevel(pending.dimension());
        if (level != null && level.isLoaded(pending.defibrillatorPos())) {
            releaseCharge(level, pending.defibrillatorPos());
        }
    }

    /** 期限切れの QTE 記録を破棄し、除細動器のチャージを解除する(サーバー tick の末尾)。 */
    private static void onServerTick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Iterator<Pending> it = PENDING.values().iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (pending.isExpired(now)) {
                it.remove();
                expire(server, pending);
            }
        }
    }

    /**
     * ログアウト時の後始末。
     * 開始した QTE は必ず放電(チャージ解除とクールダウン)で終わる。ログアウトも期限切れと同じ後始末をする。
     * 記録を消すだけだと除細動器が CHARGED のまま残る。
     */
    private static void onPlayerLeave(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            expire(player.level().getServer(), pending);
        }
    }

    /** tick・ログアウト・サーバー停止のイベントを登録する。MOD 初期化で 1 回だけ呼ぶ。 */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PendingQteManager::onServerTick);
        ServerPlayerEvents.LEAVE.register(PendingQteManager::onPlayerLeave);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }
}
