package jp.houlab.mochidsuki.medicalsystemcore.core;

import com.mojang.serialization.Codec;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * アイテムのデータコンポーネント型(Forge 時代の ItemStack NBT のキーに相当)。
 * 各型は Forge のキーと 1 対 1 に対応させる。
 * 既定値のコンポーネントは付けない(「キーが無い = 満タン」などの扱いは各アイテム側のコードが決める)。
 * 医療バッグの中身は DataComponents.CONTAINER を使うので、ここには無い。
 */
public final class ModDataComponents {
    private ModDataComponents() {
    }

    // --- 電極(ElectrodeItem) ---
    /** 電極が紐づいている除細動器の座標(Forge: "DefibrillatorPos")。 */
    public static final DataComponentType<BlockPos> DEFIBRILLATOR_POS =
            register("defibrillator_pos", BlockPos.CODEC, BlockPos.STREAM_CODEC);
    /** 電極に発行された識別子(Forge: "PadsId")。除細動器が現在発行中のものと一致する電極だけが有効。 */
    public static final DataComponentType<UUID> PADS_ID =
            register("pads_id", UUIDUtil.CODEC, UUIDUtil.STREAM_CODEC);

    // --- ベッドサイドケーブル(HeadsideCableItem) ---
    /** ケーブルが接続されているモニターの座標(Forge: "MonitorPos")。 */
    public static final DataComponentType<BlockPos> MONITOR_POS =
            register("monitor_pos", BlockPos.CODEC, BlockPos.STREAM_CODEC);
    /** ケーブルに発行された識別子(Forge: "CableId")。 */
    public static final DataComponentType<UUID> CABLE_ID =
            register("cable_id", UUIDUtil.CODEC, UUIDUtil.STREAM_CODEC);

    // --- チューブ(TubeItem) ---
    /** チューブの接続元(点滴スタンド)の座標(Forge: "StartPos")。 */
    public static final DataComponentType<BlockPos> START_POS =
            register("start_pos", BlockPos.CODEC, BlockPos.STREAM_CODEC);

    // --- 輸液パック(FluidPackItem) ---
    /** パックの残量(tick)(Forge: "FluidVolumeTicks")。 */
    public static final DataComponentType<Integer> FLUID_VOLUME_TICKS =
            register("fluid_volume_ticks", Codec.INT, ByteBufCodecs.INT);
    /** パックの残量レベル 0〜9(Forge: "FluidLevel")。items JSON の見た目の切替に使う。 */
    public static final DataComponentType<Integer> FLUID_LEVEL =
            register("fluid_level", Codec.INT, ByteBufCodecs.INT);

    // --- 医師カード(DoctorCardItem) ---
    /** 正規カードか(Forge: "isGenuine")。 */
    public static final DataComponentType<Boolean> IS_GENUINE =
            register("is_genuine", Codec.BOOL, ByteBufCodecs.BOOL);
    /** 持ち主の UUID(Forge: "ownerUUID"。Forge と同じく文字列で保持する)。偽造カードには無い。 */
    public static final DataComponentType<String> OWNER_UUID =
            register("owner_uuid", Codec.STRING, ByteBufCodecs.STRING_UTF8);
    /** 医師名(Forge: "doctorName")。 */
    public static final DataComponentType<String> DOCTOR_NAME =
            register("doctor_name", Codec.STRING, ByteBufCodecs.STRING_UTF8);
    /** 免許番号(Forge: "licenseNumber")。 */
    public static final DataComponentType<String> LICENSE_NUMBER =
            register("license_number", Codec.STRING, ByteBufCodecs.STRING_UTF8);
    /** 発行日時(ミリ秒)(Forge: "issueDate")。 */
    public static final DataComponentType<Long> ISSUE_DATE =
            register("issue_date", Codec.LONG, ByteBufCodecs.LONG);

    private static <T> DataComponentType<T> register(String name, Codec<T> codec, StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec) {
        return Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Medicalsystemcore.id(name),
                DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
