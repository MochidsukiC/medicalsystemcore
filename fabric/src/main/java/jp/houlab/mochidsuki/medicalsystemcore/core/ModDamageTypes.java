package jp.houlab.mochidsuki.medicalsystemcore.core;


import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public class ModDamageTypes {
    // bleeding.jsonに対応するキーを作成
    public static final ResourceKey<DamageType> BLEEDING_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Medicalsystemcore.id("bleeding")
    );

    // キーを使って、いつでもダメージソースを取得できるようにするヘルパーメソッド
    public static DamageSource bleeding(Level level) {
        return new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BLEEDING_KEY)
        );
    }
}
