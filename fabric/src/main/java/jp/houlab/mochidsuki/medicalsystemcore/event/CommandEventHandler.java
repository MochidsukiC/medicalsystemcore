package jp.houlab.mochidsuki.medicalsystemcore.event;

import jp.houlab.mochidsuki.medicalsystemcore.command.DoctorCardCommand;
import jp.houlab.mochidsuki.medicalsystemcore.command.EmergencyCallCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class CommandEventHandler {

    /** コマンド登録のイベントを登録する。MOD 初期化で 1 回だけ呼ぶ。 */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DoctorCardCommand.register(dispatcher);
            EmergencyCallCommand.register(dispatcher); // 追加
        });
    }
}
