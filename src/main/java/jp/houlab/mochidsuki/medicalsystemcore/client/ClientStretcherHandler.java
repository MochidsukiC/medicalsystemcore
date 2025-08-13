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

    /**
     * クライアントティックイベント
     * ストレッチャーエンティティとプレイヤーの処理を分離
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // ストレッチャーエンティティの更新処理
        mc.level.getEntitiesOfClass(StretcherEntity.class, mc.player.getBoundingBox().inflate(50.0))
                .forEach(ClientStretcherHandler::updateStretcherClientSide);

        // プレイヤーのストレッチャー状態チェック
        updatePlayerStretcherState(mc.player);
    }

    /**
     * プレイヤーのストレッチャー状態を更新
     */
    private static void updatePlayerStretcherState(Player player) {
        // ストレッチャーエンティティに乗っているかチェック
        boolean isOnStretcherEntity = player.getVehicle() instanceof StretcherEntity;

        // 現在の制御状態と実際の状態が異なる場合は更新
        boolean isCurrentlyControlled = ClientPoseController.isControlledBy(player, ClientPoseController.PoseReason.STRETCHER);

        if (isOnStretcherEntity != isCurrentlyControlled) {
            ClientPoseController.setStretcherPose(player, isOnStretcherEntity);
        }
    }

    /**
     * クライアントサイドでのストレッチャー位置予測
     */
    private static void updateStretcherClientSide(StretcherEntity stretcher) {
        // 運搬者プレイヤーを取得
        Player carrier = getCarrierFromStretcher(stretcher);
        if (carrier == null) return;

        // 現在の担架位置と運搬者から計算した理想位置を比較
        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());

        double yawRad = Math.toRadians(carrierYaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        // 理想的な担架位置
        Vec3 idealPos = carrierPos.add(lookX * 1.0, 0.0, lookZ * 1.0);

        // 現在位置との差が大きい場合、クライアントサイドで補間
        Vec3 currentPos = stretcher.position();
        double distance = currentPos.distanceTo(idealPos);

        if (distance > 0.1) { // 10cm以上離れている場合
            // 滑らかな補間（20%ずつ近づける）
            Vec3 lerpedPos = currentPos.lerp(idealPos, 0.2);
            stretcher.setPos(lerpedPos.x, lerpedPos.y, lerpedPos.z);
        }

        // 担架の角度も滑らかに補間
        float currentYaw = AngleUtils.normalizeAngle(stretcher.getYRot());
        float targetYaw = carrierYaw;

        if (!AngleUtils.isAngleChangeSmall(currentYaw, targetYaw, 1.0f)) {
            float newYaw = AngleUtils.gradualAngleChange(currentYaw, targetYaw, 5.0f);
            stretcher.setYRot(newYaw);
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