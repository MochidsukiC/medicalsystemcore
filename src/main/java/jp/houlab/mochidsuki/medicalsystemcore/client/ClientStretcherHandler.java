package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.util.AngleUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity.calculateIdealStretcherPosition;
import static jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity.calculateIdealStretcherYaw;

/**
 * ストレッチャーに関するクライアントサイドイベントハンドラー
 * 滑らかな担架移動とクライアント側位置予測を実装
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientStretcherHandler {

    /**
     * エンティティの乗車・降車イベント
     */
    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (!event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof Player player &&
                event.getEntityBeingMounted() instanceof StretcherEntity) {

            // クライアントサイドで即座に姿勢制御を適用
            if (event.isMounting()) {
                ClientPoseController.setStretcherPose(player, true);
            } else {
                ClientPoseController.setStretcherPose(player, false);
            }
        }
    }


    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // ストレッチャー姿勢管理の更新
        StretcherPoseManager.tick();

        // デバッグ情報を5秒毎に表示
        if (mc.player.tickCount % 100 == 0) {
            if (StretcherPoseManager.isOnStretcher(mc.player)) {
                String debug = StretcherPoseManager.getDebugInfo(mc.player);
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + debug));
            }
        }

        // ストレッチャーエンティティの更新処理
        mc.level.getEntitiesOfClass(StretcherEntity.class, mc.player.getBoundingBox().inflate(50.0))
                .forEach(ClientStretcherHandler::updateStretcherClientSide);

        // プレイヤーのストレッチャー状態チェック
        updatePlayerStretcherStateSimple(mc.player);
    }


    /**
     * StretcherEntityの集約されたメソッドを使用（修正版）
     */
    private static void updateStretcherClientSide(StretcherEntity stretcher) {
        // 運搬者プレイヤーを取得
        Player carrier = getCarrierFromStretcher(stretcher);
        if (carrier == null) return;

        // *** StretcherEntityの修正された集約メソッドを使用 ***
        StretcherEntity.updateStretcherPosition(stretcher, carrier);
    }

    /**
     * 担架位置の更新（修正版）
     * ClientStretcherHandlerから呼び出される
     */
    public static void updateStretcherPosition(StretcherEntity stretcher, Player carrier) {
        if (stretcher == null || carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());  // A°

        // 修正された理想的な位置と角度を計算
        Vec3 idealPos = calculateIdealStretcherPosition(carrierPos, carrierYaw);
        float idealYaw = calculateIdealStretcherYaw(carrierYaw);  // B° = A° + 90°

        // 現在位置との差が大きい場合のみ補間
        Vec3 currentPos = stretcher.position();
        double distance = currentPos.distanceTo(idealPos);

        if (distance > 0.1) { // 10cm以上離れている場合
            // 滑らかな補間（20%ずつ近づける）
            Vec3 lerpedPos = currentPos.lerp(idealPos, 0.2);
            stretcher.setPos(lerpedPos.x, lerpedPos.y, lerpedPos.z);
        }

        // 角度の滑らかな補間
        float currentYaw = AngleUtils.normalizeAngle(stretcher.getYRot());
        if (!AngleUtils.isAngleChangeSmall(currentYaw, idealYaw, 1.0f)) {
            float newYaw = AngleUtils.gradualAngleChange(currentYaw, idealYaw, 5.0f);
            stretcher.setYRot(newYaw);
        }
    }

    /**
     * プレイヤーのストレッチャー状態を更新（完全版）
     */
    private static void updatePlayerStretcherStateSimple(Player player) {
        boolean isOnStretcherEntity = player.getVehicle() instanceof StretcherEntity;

        if (isOnStretcherEntity) {
            StretcherEntity stretcher = (StretcherEntity) player.getVehicle();
            float stretcherYaw = AngleUtils.normalizeAngle(stretcher.getYRot());

            // カスタムレンダリング管理に登録
            StretcherPoseManager.setStretcherPose(player, true, stretcherYaw);
            StretcherPoseManager.updateStretcherYaw(player, stretcherYaw);
        } else {
            // カスタムレンダリングを停止
            StretcherPoseManager.setStretcherPose(player, false, 0.0f);
        }
    }


    /**
     * ストレッチャーの運搬者プレイヤーを取得
     */
    private static Player getCarrierFromStretcher(StretcherEntity stretcher) {
        // まずEntityDataから正確な運搬者を取得を試行
        Player carrier = stretcher.getCarriedByPlayerFromDataPublic();
        if (carrier != null) {
            return carrier;
        }

        // EntityDataが利用できない場合、近くのプレイヤーで担架を持っていそうな人を探す
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        return mc.level.players().stream()
                .filter(player -> player.distanceToSqr(stretcher) < 25.0) // 5ブロック以内
                .filter(player -> !player.isPassenger()) // 乗車していない
                .findFirst()
                .orElse(null);
    }
}