package jp.houlab.mochidsuki.medicalsystemcore.capability;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Data Attachment の登録。Forge の PlayerMedicalDataProvider(capability)に相当する。
 *
 * <p>使い方: {@code player.getAttachedOrCreate(ModAttachments.MEDICAL_DATA)}。attachment は常に存在する。</p>
 *
 * <ul>
 *   <li>{@code copyOnDeath()} は付けない。死亡後のリスポーンでは引き継がず(初期値に戻る)、
 *       End からの帰還では Fabric API が AFTER_RESPAWN で全 attachment をコピーする(docs/fabric-26.2-api-mapping.md 2.1 節)。</li>
 *   <li>{@code syncWith} は付けない。現行の医療データは毎 tick その場で書き換えるため setAttached 経路の同期に乗らない。
 *       クライアントへの同期は ClientboundCoreStatsPacket が担う。</li>
 * </ul>
 */
public final class ModAttachments {
    private ModAttachments() {
    }

    public static final AttachmentType<PlayerMedicalData> MEDICAL_DATA = AttachmentRegistry.create(
            Medicalsystemcore.id("medical_data"),
            builder -> builder
                    .persistent(PlayerMedicalData.CODEC)
                    .initializer(PlayerMedicalData::new));

    /** クラスのロード(= attachment の登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
