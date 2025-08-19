// C:/Users/dora2/IdeaProjects/medicalsystemcore/src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundUpdateRescueDataPacket.java

package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアント -> サーバー
 * RescueDataの更新を要求するパケット（改善版）
 */
public class ServerboundUpdateRescueDataPacket {

    private final int rescueId;
    private final UpdateType type;

    // チェックボックス更新用のフィールド
    private final boolean isDispatch;
    private final boolean isTreatment;

    // メモ更新用のフィールド
    private final String memo;

    public enum UpdateType { CHECKBOX, MEMO }

    /**
     * チェックボックス更新用のコンストラクタ
     */
    public ServerboundUpdateRescueDataPacket(int rescueId, boolean isDispatch, boolean isTreatment) {
        this.rescueId = rescueId;
        this.type = UpdateType.CHECKBOX;
        this.isDispatch = isDispatch;
        this.isTreatment = isTreatment;
        this.memo = ""; // このコンストラクタでは使用しない
    }

    /**
     * メモ更新用のコンストラクタ
     */
    public ServerboundUpdateRescueDataPacket(int rescueId, String memo) {
        this.rescueId = rescueId;
        this.type = UpdateType.MEMO;
        this.isDispatch = false; // このコンストラクタでは使用しない
        this.isTreatment = false; // このコンストラクタでは使用しない
        this.memo = memo;
    }

    /**
     * バイトデータからのデコード
     */
    public ServerboundUpdateRescueDataPacket(FriendlyByteBuf buf) {
        this.rescueId = buf.readInt();
        this.type = buf.readEnum(UpdateType.class);

        if (this.type == UpdateType.CHECKBOX) {
            this.isDispatch = buf.readBoolean();
            this.isTreatment = buf.readBoolean();
            this.memo = "";
        } else { // MEMO
            this.isDispatch = false;
            this.isTreatment = false;
            this.memo = buf.readUtf();
        }
    }

    /**
     * バイトデータへのエンコード
     */
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.rescueId);
        buf.writeEnum(this.type);
        if (this.type == UpdateType.CHECKBOX) {
            buf.writeBoolean(this.isDispatch);
            buf.writeBoolean(this.isTreatment);
        } else { // MEMO
            buf.writeUtf(this.memo);
        }
    }

    /**
     * サーバーサイドでの処理
     */
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            // 新しいRescueDataManagerのメソッドを呼び出す
            if (this.type == UpdateType.CHECKBOX) {
                RescueDataManager.updateRescueDataCheckbox(player, this.rescueId, this.isDispatch, this.isTreatment);
            } else { // MEMO
                RescueDataManager.updateRescueDataMemo(player, this.rescueId, this.memo);
            }
        });
        context.setPacketHandled(true);
    }
}