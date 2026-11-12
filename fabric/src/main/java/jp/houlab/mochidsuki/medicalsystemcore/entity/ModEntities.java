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
                    // 乗客の座る高さ(未確定。段 5 でレンダラーと合わせて調整すること)。Forge の getPassengersRidingOffset() は 0.1。
                    // 26.2 では乗客の足元 = 担架の原点 + この値 - 乗客の車両接続点で、プレイヤーの車両接続点は STANDING・CROUCHING が
                    // (0, 0.6, 0)、SWIMMING・SLEEPING などは接続点なし(Avatar.java)。つまり姿勢で最大 0.6 ずれる。
                    // 0.35 は Forge のプレイヤー側オフセット(-0.35、記憶に基づく)を足して「立ち姿勢で Forge と同じ」になるよう
                    // 導いた値。もう一方の候補は 0.1(Forge の値をそのまま移す)。
                    .passengerAttachments(0.35F));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Medicalsystemcore.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
