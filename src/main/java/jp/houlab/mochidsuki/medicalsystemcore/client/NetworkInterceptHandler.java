package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * 意識障害時のネットワークパケット送信をインターセプトするクラス
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NetworkInterceptHandler {

    private static float fixedYaw = 0.0f;
    private static float fixedPitch = 0.0f;
    private static boolean hasStoredRotation = false;

    private static Field yRotField;
    private static Field xRotField;

    static {
        try {
            // ServerboundMovePlayerPacketのフィールドにアクセスするためのReflection
            yRotField = ServerboundMovePlayerPacket.class.getDeclaredField("yRot");
            xRotField = ServerboundMovePlayerPacket.class.getDeclaredField("xRot");
            yRotField.setAccessible(true);
            xRotField.setAccessible(true);
        } catch (Exception e) {
            // フィールド名が変更されている場合のフォールバック
            try {
                // Mappingが変わった場合の別名を試す
                yRotField = ServerboundMovePlayerPacket.class.getDeclaredField("f_134033_"); // obfuscated name例
                xRotField = ServerboundMovePlayerPacket.class.getDeclaredField("f_134032_"); // obfuscated name例
                yRotField.setAccessible(true);
                xRotField.setAccessible(true);
            } catch (Exception ex) {
                Medicalsystemcore.LOGGER.error("Failed to access rotation fields in ServerboundMovePlayerPacket", ex);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        boolean isUnconscious = ClientMedicalDataManager.isPlayerUnconscious(player);

        if (isUnconscious) {
            if (!hasStoredRotation) {
                // 意識を失った瞬間の向きを保存
                fixedYaw = player.getYRot();
                fixedPitch = player.getXRot();
                hasStoredRotation = true;
            }

            // プレイヤーの見た目の向きを固定（他のプレイヤーから見える部分）
            // ただし、カメラの向きは自由
            player.yBodyRot = fixedYaw;
            player.yHeadRot = fixedYaw;
            player.yBodyRotO = fixedYaw;
            player.yHeadRotO = fixedYaw;

        } else {
            hasStoredRotation = false;
        }
    }

    /**
     * パケット送信をインターセプトして回転値を固定する
     * 注意: この方法はより高度なMixinアプローチが理想的ですが、
     * Forgeの制約下での代替案として提供
     */
    public static void interceptMovePacket(ServerboundMovePlayerPacket packet) {
        if (!hasStoredRotation) return;

        try {
            if (yRotField != null && xRotField != null) {
                yRotField.setFloat(packet, fixedYaw);
                xRotField.setFloat(packet, fixedPitch);
            }
        } catch (Exception e) {
            Medicalsystemcore.LOGGER.error("Failed to intercept move packet", e);
        }
    }
}