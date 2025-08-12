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
    public static void onRenderDebugScreen(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ClientMedicalDataManager.getPlayerData(mc.player).ifPresent(data -> {
                List<String> leftStrings = event.getLeft();

                leftStrings.add("");
                leftStrings.add("§c[Medical System Core]");
                leftStrings.add(String.format("Blood Level: %.1f%%", data.bloodLevel));
                leftStrings.add("Heart Status: " + data.heartStatus.name());
                leftStrings.add(String.format("Bleeding Speed: %.2f", data.bleedingSpeed));
                leftStrings.add(String.format("Resuscitation Chance: %.1f%%", data.resuscitationChance));
                leftStrings.add("Conscious: " + (data.isConscious ? "Yes" : "No")); // 追加
            });
        }
    }
}