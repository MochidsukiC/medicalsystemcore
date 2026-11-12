package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアントサイドでのストレッチャーエンティティの追加処理
 * ネットワークラグを補償するためのリアルタイム位置更新
 */
@Mod.EventBusSubscriber(modid = "medicalsystemcore", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class ClientStretcherHandler {

    /**
     * クライアントティック処理
     * ストレッチャーエンティティの追加更新を行い、ラグを最小化
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // クライアント世界の全ストレッチャーエンティティを取得
        mc.level.getEntitiesOfClass(StretcherEntity.class, mc.player.getBoundingBox().inflate(100.0))
                .forEach(ClientStretcherHandler::updateStretcherClientSide);
    }

    /**
     * クライアントサイドでのストレッチャー追加更新
     * サーバーからの更新を待たずに、ローカルで位置を計算
     */
    private static void updateStretcherClientSide(StretcherEntity stretcher) {
        Player carrier = stretcher.getCarrier();
        if (carrier == null || !carrier.isAlive()) return;

        // 注意: この処理はStretcherEntity.tick()内のクライアントサイド処理に統合されているため、
        // 実際には追加の処理は不要。ただし、将来的な拡張のために残しておく。

        // 必要に応じて追加のクライアントサイド処理をここに実装
    }

    /**
     * プレイヤーがストレッチャーに乗っている際の追加クライアント処理
     */
    public static void handleStretcherRiding(Player player) {
        if (!(player.getVehicle() instanceof StretcherEntity stretcher)) return;

        // クライアントサイドでの追加の乗車処理
        // 例：特殊なカメラワーク、エフェクト等
    }
}