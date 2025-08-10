package jp.houlab.mochidsuki.medicalsystemcore.client;

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
    private static final int HEAL_DURATION_ON_OTHER = 4 * 20; // 他人への治療時間: 4秒

    // 治療の開始
    public static void startHealing(int entityId) {
        // 既に他の誰かを治療中の場合は何もしない
        if (targetId == -1) {
            targetId = entityId;
            healingTicks = 0;
        }
    }

    // 治療の中断
    public static void stopHealing() {
        if (targetId != -1) {
            targetId = -1;
            healingTicks = 0;
            // 中断した音などを鳴らしても良い
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

            System.out.println("Healing Ticks: " + healingTicks);


            // 治療完了
            if (healingTicks >= HEAL_DURATION_ON_OTHER) {
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
        if (!isHealing()) return 0;
        return (float) healingTicks / HEAL_DURATION_ON_OTHER;
    }
}