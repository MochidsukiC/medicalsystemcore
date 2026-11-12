package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * サーバーからクライアントへ、clientSynced の設定値を送る。
 * 各項目は「キー名 + 型タグ + 値」。受信側(クライアント側、段 4)は {@link MedicalConfig#setAll} で検証して適用する。
 *
 * @param values id → 検証前の値(Integer / Double / Boolean / String)
 */
public record ClientboundConfigSyncPacket(Map<String, Object> values) implements CustomPacketPayload {

    public static final Type<ClientboundConfigSyncPacket> TYPE =
            new Type<>(Medicalsystemcore.id("clientbound_config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundConfigSyncPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundConfigSyncPacket::write, ClientboundConfigSyncPacket::read);

    private static final byte TYPE_INT = 0;
    private static final byte TYPE_DOUBLE = 1;
    private static final byte TYPE_BOOLEAN = 2;
    private static final byte TYPE_STRING = 3;

    /** 現在のサーバー設定のうち、クライアントが参照するキーすべてを送るパケット。 */
    public static ClientboundConfigSyncPacket ofCurrent() {
        return new ClientboundConfigSyncPacket(MedicalConfig.clientSyncedSnapshot());
    }

    private static ClientboundConfigSyncPacket read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<String, Object> read = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            byte type = buf.readByte();
            read.put(id, switch (type) {
                case TYPE_INT -> buf.readInt();
                case TYPE_DOUBLE -> buf.readDouble();
                case TYPE_BOOLEAN -> buf.readBoolean();
                case TYPE_STRING -> buf.readUtf();
                // 型タグが分からないと以降のバイト列を読み進められないため、続行せず失敗させる
                default -> throw new IllegalStateException("設定同期パケットの型タグが不正: id=" + id + " type=" + type);
            });
        }
        return new ClientboundConfigSyncPacket(read);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            buf.writeUtf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Integer i) {
                buf.writeByte(TYPE_INT);
                buf.writeInt(i);
            } else if (value instanceof Double d) {
                buf.writeByte(TYPE_DOUBLE);
                buf.writeDouble(d);
            } else if (value instanceof Boolean b) {
                buf.writeByte(TYPE_BOOLEAN);
                buf.writeBoolean(b);
            } else if (value instanceof String s) {
                buf.writeByte(TYPE_STRING);
                buf.writeUtf(s);
            } else {
                throw new IllegalStateException("設定同期パケットに載せられない型: id=" + entry.getKey() + " value=" + value);
            }
        }
    }

    @Override
    public Type<ClientboundConfigSyncPacket> type() {
        return TYPE;
    }
}
