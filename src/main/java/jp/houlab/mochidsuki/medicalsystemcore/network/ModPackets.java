package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModPackets {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(Medicalsystemcore.MODID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;


        net.messageBuilder(ClientboundMedicalDataSyncPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ClientboundMedicalDataSyncPacket::new)
                .encoder(ClientboundMedicalDataSyncPacket::toBytes)
                .consumerMainThread(ClientboundMedicalDataSyncPacket::handle)
                .add();

        net.messageBuilder(ClientboundStartQTEPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ClientboundStartQTEPacket::new)
                .encoder(ClientboundStartQTEPacket::toBytes)
                .consumerMainThread(ClientboundStartQTEPacket::handle)
                .add();


        net.messageBuilder(ServerboundFinishHealPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ServerboundFinishHealPacket::new)
                .encoder(ServerboundFinishHealPacket::toBytes)
                .consumerMainThread(ServerboundFinishHealPacket::handle)
                .add();

        net.messageBuilder(ServerboundQTETResultPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ServerboundQTETResultPacket::new)
                .encoder(ServerboundQTETResultPacket::toBytes)
                .consumerMainThread(ServerboundQTETResultPacket::handle)
                .add();

    }

    // 特定のプレイヤーにパケットを送信するヘルパーメソッド
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    /**
     * 指定されたエンティティを追跡している全てのプレイヤーにパケットを送信する
     */
    public static <MSG> void sendToAllTracking(MSG message, Entity entity) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
    }
}
