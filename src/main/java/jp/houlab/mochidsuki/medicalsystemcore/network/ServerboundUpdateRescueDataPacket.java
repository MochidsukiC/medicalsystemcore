package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager; // 後で作成するサーバー側マネージャー
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ServerboundUpdateRescueDataPacket {

    private final int rescueId;
    private final UpdateType type;
    private final boolean value;
    private final String memo; // メモ更新用

    public enum UpdateType { CHECKBOX, MEMO }

    // チェックボックス更新用コンストラクタ
    public ServerboundUpdateRescueDataPacket(int rescueId, boolean isDispatch, boolean isTreatment) {
        this.rescueId = rescueId;
        this.type = UpdateType.CHECKBOX;
        this.value = isDispatch; // valueにdispatchを、memoにtreatmentをエンコードする簡易的な方法
        this.memo = String.valueOf(isTreatment);
    }

    // メモ更新用コンストラクタ
    public ServerboundUpdateRescueDataPacket(int rescueId, String memo) {
        this.rescueId = rescueId;
        this.type = UpdateType.MEMO;
        this.value = false;
        this.memo = memo;
    }


    public ServerboundUpdateRescueDataPacket(FriendlyByteBuf buf) {
        this.rescueId = buf.readInt();
        this.type = buf.readEnum(UpdateType.class);
        this.value = buf.readBoolean();
        this.memo = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(rescueId);
        buf.writeEnum(type);
        buf.writeBoolean(value);
        buf.writeUtf(memo);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // サーバー側での処理
            RescueDataManager.updateRescueData(context.getSender(), rescueId, type, value, memo);
        });
        context.setPacketHandled(true);
    }
}