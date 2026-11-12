package jp.houlab.mochidsuki.medicalsystemcore.entity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * EntityType の登録ホルダ(Forge の Medicalsystemcore.STRETCHER_ENTITY に相当)。ID は Forge と同じ。
 */
public final class ModEntities {
    private ModEntities() {
    }

    public static final EntityType<StretcherEntity> STRETCHER_ENTITY = register("stretcher_entity",
            EntityType.Builder.<StretcherEntity>of(StretcherEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F)
                    .clientTrackingRange(64)      // Forge のまま。単位はチャンク(Forge のコメントの「64ブロック」は誤り)
                    .updateInterval(1)            // 毎ティック更新（20→1）で滑らかな動作
                    .alwaysUpdateVelocity(true)   // 速度更新を受信(Forge の setShouldReceiveVelocityUpdates)
                    // 乗客の座る高さ。Forge は乗客の足元 = 担架の Y + getPassengersRidingOffset()(0.1) + Player#getMyRidingOffset()(-0.35、
                    // 1.20.1 の Player バイトコードで確認) = 担架の Y - 0.25 で、姿勢によらない。
                    // 26.2 は乗客の足元 = 担架の原点 + この値 - 乗客の車両接続点。プレイヤーの車両接続点は STANDING・CROUCHING が (0, 0.6, 0)、
                    // SWIMMING・SLEEPING は接続点なし(原点 = 足元)で、姿勢により 0.6 ずれる(Avatar.java)。
                    // この値を足元の高さ(-0.25)とし、姿勢による車両接続点の差は StretcherEntity#getPassengerAttachmentPoint で打ち消す。
                    .passengerAttachments(-0.25F));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Medicalsystemcore.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
