package jp.houlab.mochidsuki.medicalsystemcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 全 14 payload の型登録と、サーバー側の送信ヘルパー。
 * クライアント → サーバーの送信は client ソースセット側(ClientPlayNetworking.send)で行う(main からは参照できない)。
 * 受信ハンドラはここでは登録しない(サーバー側は段 3、クライアント側は段 4/5)。
 */
public class ModPackets {

    /** 全 payload の型(record + StreamCodec)を登録する。サーバー・クライアントの両方で必要なので ModInitializer から呼ぶ。 */
    public static void register() {
        // --- サーバー -> クライアント ---
        PayloadTypeRegistry.clientboundPlay().register(ClientboundCoreStatsPacket.TYPE, ClientboundCoreStatsPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundStartQTEPacket.TYPE, ClientboundStartQTEPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundSyncAllRescueDataPacket.TYPE, ClientboundSyncAllRescueDataPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundUpdateRescueDataPacket.TYPE, ClientboundUpdateRescueDataPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundOpenRescuePortalPacket.TYPE, ClientboundOpenRescuePortalPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundConfigSyncPacket.TYPE, ClientboundConfigSyncPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundRemoveRescueDataPacket.TYPE, ClientboundRemoveRescueDataPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundRevokedLicensesSyncPacket.TYPE, ClientboundRevokedLicensesSyncPacket.STREAM_CODEC);

        // --- クライアント -> サーバー ---
        PayloadTypeRegistry.serverboundPlay().register(ServerboundFinishHealPacket.TYPE, ServerboundFinishHealPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundQTETResultPacket.TYPE, ServerboundQTETResultPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundUpdateRescueDataPacket.TYPE, ServerboundUpdateRescueDataPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundRequestRescueListPacket.TYPE, ServerboundRequestRescueListPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundGiveUpPacket.TYPE, ServerboundGiveUpPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundDeleteRescueDataPacket.TYPE, ServerboundDeleteRescueDataPacket.STREAM_CODEC);
    }

    // 特定のプレイヤーにパケットを送信するヘルパーメソッド
    public static void sendToPlayer(CustomPacketPayload payload, ServerPlayer player) {
        ServerPlayNetworking.send(player, payload);
    }

    /**
     * 指定されたエンティティを追跡している全てのプレイヤーと、エンティティ自身(プレイヤーの場合)にパケットを送信する。
     * Forge の TRACKING_ENTITY_AND_SELF に相当。PlayerLookup.tracking は自分自身を含まないので足す。
     */
    public static void sendToAllTracking(CustomPacketPayload payload, Entity entity) {
        for (ServerPlayer tracker : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(tracker, payload);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    /**
     * サーバーに接続している全プレイヤーにパケットを送信する。
     * Forge 版と違い、送り先のサーバーを引数で受け取る(Fabric にはチャンネル経由の暗黙の宛先が無い)。
     */
    public static void sendToAllClients(CustomPacketPayload payload, MinecraftServer server) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
