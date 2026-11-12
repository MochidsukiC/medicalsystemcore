package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;

import javax.annotation.Nullable;

public class ClientTrackingManager {

    @Nullable
    private static RescueData trackedData;

    public static void setTrackedData(@Nullable RescueData data) {
        trackedData = data;
    }

    public static void clearTracking() {
        trackedData = null;
    }

    /** 追跡中の通報が指定 ID なら追跡を止める(通報が削除されたとき用)。 */
    public static void clearTrackingIfId(int rescueId) {
        if (trackedData != null && trackedData.getId() == rescueId) {
            trackedData = null;
        }
    }

    public static boolean isTracking() {
        return trackedData != null;
    }

    @Nullable
    public static RescueData getTrackedData() {
        return trackedData;
    }
}
