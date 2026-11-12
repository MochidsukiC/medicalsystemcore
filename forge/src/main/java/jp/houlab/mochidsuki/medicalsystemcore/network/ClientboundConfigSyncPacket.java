package jp.houlab.mochidsuki.medicalsystemcore.network;

import jp.houlab.mochidsuki.medicalsystemcore.common.config.ConfigKey;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * サーバーからクライアントへ、clientSynced の設定値を送る。
 * 各項目は「キー名 + 型タグ + 値」。受信側は {@link MedicalConfig#setAll} で検証して適用する。
 */
public class ClientboundConfigSyncPacket {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientboundConfigSyncPacket.class);

    private static final byte TYPE_INT = 0;
    private static final byte TYPE_DOUBLE = 1;
    private static final byte TYPE_BOOLEAN = 2;
    private static final byte TYPE_STRING = 3;

    /** id → 検証前の値(Integer / Double / Boolean / String)。 */
    private final Map<String, Object> values;

    public ClientboundConfigSyncPacket(Map<String, Object> values) {
        this.values = values;
    }

    /** 現在のサーバー設定のうち、クライアントが参照するキーすべてを送るパケット。 */
    public static ClientboundConfigSyncPacket ofCurrent() {
        return new ClientboundConfigSyncPacket(MedicalConfig.clientSyncedSnapshot());
    }

    public ClientboundConfigSyncPacket(FriendlyByteBuf buf) {
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
        this.values = read;
    }

    public void toBytes(FriendlyByteBuf buf) {
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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            Map<String, Object> accepted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                ConfigKey<?> key = MedicalConfigKeys.byId(entry.getKey());
                if (key == null) {
                    // サーバーの方が新しく、このクライアントが知らないキーを送ってきた場合
                    LOGGER.warn("サーバーから未知の設定キーが送られたため無視する: {}", entry.getKey());
                } else if (!key.isClientSynced()) {
                    LOGGER.warn("サーバーから同期対象ではない設定キーが送られたため無視する: {}", entry.getKey());
                } else {
                    accepted.put(entry.getKey(), entry.getValue());
                }
            }
            try {
                MedicalConfig.setAll(accepted);
            } catch (IllegalArgumentException e) {
                // サーバーが送った値が検証に通らない。一部だけ適用せず、全体を捨てる(setAll は全か無か)
                LOGGER.error("サーバーから送られた設定値が不正なため適用しない", e);
            }
        });
        return true;
    }
}
