package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.RescueRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * クライアント -> サーバー
 * RescueDataの更新を要求するパケット。型(CHECKBOX / MEMO)で中身が変わる。
 * CHECKBOX のとき memo は "" 、MEMO のとき isDispatch / isTreatment は false。
 * 受信ハンドラ(認可の検証と更新)は段 3 に置く。
 * Forge 版のフィールド type は、CustomPacketPayload#type() と衝突するため updateType に改名した。
 */
public record ServerboundUpdateRescueDataPacket(int rescueId, UpdateType updateType, boolean isDispatch, boolean isTreatment,
                                                String memo) implements CustomPacketPayload {

    public enum UpdateType { CHECKBOX, MEMO }

    public static final Type<ServerboundUpdateRescueDataPacket> TYPE =
            new Type<>(Medicalsystemcore.id("serverbound_update_rescue_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundUpdateRescueDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ServerboundUpdateRescueDataPacket::write, ServerboundUpdateRescueDataPacket::read);

    /**
     * チェックボックス更新用のコンストラクタ(Forge 版と同じ引数)
     */
    public ServerboundUpdateRescueDataPacket(int rescueId, boolean isDispatch, boolean isTreatment) {
        this(rescueId, UpdateType.CHECKBOX, isDispatch, isTreatment, ""); // memo はこの型では使用しない
    }

    /**
     * メモ更新用のコンストラクタ(Forge 版と同じ引数)
     */
    public ServerboundUpdateRescueDataPacket(int rescueId, String memo) {
        this(rescueId, UpdateType.MEMO, false, false, memo); // isDispatch / isTreatment はこの型では使用しない
    }

    /**
     * バイトデータからのデコード
     */
    private static ServerboundUpdateRescueDataPacket read(FriendlyByteBuf buf) {
        int rescueId = buf.readInt();
        UpdateType updateType = buf.readEnum(UpdateType.class);

        if (updateType == UpdateType.CHECKBOX) {
            boolean isDispatch = buf.readBoolean();
            boolean isTreatment = buf.readBoolean();
            return new ServerboundUpdateRescueDataPacket(rescueId, isDispatch, isTreatment);
        } else { // MEMO
            // 巨大な文字列をそもそも読まないよう、デコード時に上限をかける
            String memo = buf.readUtf(RescueRules.MEMO_MAX_LENGTH);
            return new ServerboundUpdateRescueDataPacket(rescueId, memo);
        }
    }

    /**
     * バイトデータへのエンコード
     */
    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.rescueId);
        buf.writeEnum(this.updateType);
        if (this.updateType == UpdateType.CHECKBOX) {
            buf.writeBoolean(this.isDispatch);
            buf.writeBoolean(this.isTreatment);
        } else { // MEMO
            buf.writeUtf(this.memo, RescueRules.MEMO_MAX_LENGTH);
        }
    }

    @Override
    public Type<ServerboundUpdateRescueDataPacket> type() {
        return TYPE;
    }
}
