package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent; // <--- 修正点1: 正しいイベントをimport
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DebugOverlayHandler {

    @SubscribeEvent
    public static void onRenderDebugScreen(CustomizeGuiOverlayEvent.DebugText event) { // <--- 修正点2: 正しいイベントクラスを使用
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ClientMedicalDataManager.getPlayerData(mc.player).ifPresent(data -> {
                // このイベントでは、直接getLeft()でリストを取得できます
                List<String> leftStrings = event.getLeft();

                // 区切りのための空白行とヘッダーを追加
                leftStrings.add("");
                leftStrings.add("§c[Medical System Core]"); // §cで赤色に

                // 各ステータスをフォーマットして追加
                leftStrings.add(String.format("Blood Level: %.1f%%", data.bloodLevel));
                leftStrings.add("Heart Status: " + data.heartStatus.name());
                leftStrings.add(String.format("Bleeding Speed: %.2f", data.bleedingSpeed));
                leftStrings.add(String.format("Resuscitation Chance: %.1f%%", data.resuscitationChance));
            });
        }
    }
}