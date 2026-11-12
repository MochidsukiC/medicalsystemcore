package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
import jp.houlab.mochidsuki.medicalsystemcore.item.BandageItem;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundFinishHealPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public class ClientHealingManager {
    private static int targetId = -1;
    private static int healingTicks = 0;
    private static int healDurationTicks = 0; // Config値から設定される治療時間

    // 治療の開始（Config値使用版）
    public static void startHealing(int entityId) {
        // 既に他の誰かを治療中の場合は何もしない
        if (targetId == -1) {
            targetId = entityId;
            healingTicks = 0;
            // Config値から他人への治療時間を取得
            healDurationTicks = Config.BANDAGE_OTHER_USE_DURATION * 20; // 秒からtickに変換
        }
    }

    // 治療の中断
    public static void stopHealing() {
        if (targetId != -1) {
            targetId = -1;
            healingTicks = 0;
            healDurationTicks = 0;
        }
    }

    // 毎フレームの更新処理
    public static void tick() {
        if (targetId == -1) return; // 誰も治療していないなら終了

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stopHealing();
            return;
        }

        // --- 治療継続の条件チェック ---
        boolean isHoldingBandage = mc.player.getMainHandItem().getItem() instanceof BandageItem;
        boolean isLookingAtTarget = (mc.hitResult instanceof EntityHitResult ehr) && (ehr.getEntity().getId() == targetId);
        boolean isRightMouseDown = mc.options.keyUse.isDown();

        if (isHoldingBandage && isLookingAtTarget && isRightMouseDown) {
            healingTicks++;

            // 治療完了
            if (healingTicks >= healDurationTicks) {
                ModPackets.sendToServer(new ServerboundFinishHealPacket(targetId));
                mc.player.getMainHandItem().shrink(1); // クライアント側でもアイテムを減らす
                mc.player.level().playSound(mc.player, mc.player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
                stopHealing();
            }
        } else {
            // 条件が満たされなくなったら中断
            stopHealing();
        }
    }

    public static boolean isHealing() {
        return targetId != -1;
    }

    public static float getProgress() {
        if (!isHealing() || healDurationTicks == 0) return 0;
        return (float) healingTicks / healDurationTicks;
    }
}