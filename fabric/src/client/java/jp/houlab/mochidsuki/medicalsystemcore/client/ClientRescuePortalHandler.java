package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.client.screen.RescuePortalScreen;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundOpenRescuePortalPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundRemoveRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundSyncAllRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundUpdateRescueDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 救急ポータル画面に触る受信ハンドラ(Forge では各 Clientbound パケットの handle と network/ClientRescuePortalHandler にあった)。
 * 呼び出しはクライアントのメインスレッド(Fabric の受信ハンドラが execute 済み)。
 */
public class ClientRescuePortalHandler {

    public static void openScreen(ClientboundOpenRescuePortalPacket packet) {
        Minecraft.getInstance().gui.setScreen(new RescuePortalScreen(
                Component.translatable("gui.medicalsystemcore.rescue_portal.title")));
    }

    public static void handleSyncAll(ClientboundSyncAllRescueDataPacket packet) {
        if (Minecraft.getInstance().gui.screen() instanceof RescuePortalScreen screen) {
            screen.updateRescueDataList(packet.rescueDataList());
        }
    }

    public static void handleUpdate(ClientboundUpdateRescueDataPacket packet) {
        // クライアント側のデータを更新または追加
        if (Minecraft.getInstance().gui.screen() instanceof RescuePortalScreen screen) {
            if (packet.isNew()) {
                // 一覧の並びは新しい順(先頭が最新)。全件同期もサーバー側で新しい順にして送る
                screen.getRescueDataList().add(0, packet.data());
            } else {
                screen.getRescueDataList().stream()
                        .filter(d -> d.getId() == packet.data().getId())
                        .findFirst()
                        .ifPresent(d -> {
                            d.setDispatch(packet.data().isDispatch());
                            d.setTreatment(packet.data().isTreatment());
                            d.setMemo(packet.data().getMemo());
                        });
            }

            // GUIが開いていれば更新
            screen.onRescueDataUpdated(packet.data());
        }
    }

    public static void handleRemove(ClientboundRemoveRescueDataPacket packet) {
        // 追跡中の通報が消えたら、画面が開いていなくても追跡を止める
        ClientTrackingManager.clearTrackingIfId(packet.rescueId());
        if (Minecraft.getInstance().gui.screen() instanceof RescuePortalScreen screen) {
            screen.onRescueDataRemoved(packet.rescueId());
        }
    }
}
