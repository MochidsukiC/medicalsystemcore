package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.client.screen.RescuePortalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ClientRescuePortalHandler {

    public static void openScreen() {
        Minecraft.getInstance().setScreen(new RescuePortalScreen(
                Component.translatable("gui.medicalsystemcore.rescue_portal.title")));
    }
}
