package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.client.renderer.StretcherPlayerRenderer;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアント側でのストレッチャープレイヤーレンダリングイベント処理
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class StretcherClientEventHandler {

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();

        // ストレッチャーに乗っているかチェック
        if (player.getVehicle() instanceof StretcherEntity) {
            // カスタムレンダリングを適用
            StretcherPlayerRenderer.renderStretcherPlayer(
                    event.getPoseStack(),
                    event.getMultiBufferSource(),
                    player,
                    event.getRenderer(),
                    event.getPackedLight(),
                    event.getPartialTick()
            );
        }
    }
}