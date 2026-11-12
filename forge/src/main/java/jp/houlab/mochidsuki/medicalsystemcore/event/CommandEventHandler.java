package jp.houlab.mochidsuki.medicalsystemcore.event;

import jp.houlab.mochidsuki.medicalsystemcore.command.DoctorCardCommand;
import jp.houlab.mochidsuki.medicalsystemcore.command.EmergencyCallCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "medicalsystemcore")
public class CommandEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DoctorCardCommand.register(event.getDispatcher());
        EmergencyCallCommand.register(event.getDispatcher()); // 追加

    }
}