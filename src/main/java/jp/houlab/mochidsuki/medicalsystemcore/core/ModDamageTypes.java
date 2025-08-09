package jp.houlab.mochidsuki.medicalsystemcore.core;


import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public class ModDamageTypes {
    // bleeding.jsonに対応するキーを作成
    public static final ResourceKey<DamageType> BLEEDING_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(Medicalsystemcore.MODID, "bleeding")
    );

    // キーを使って、いつでもダメージソースを取得できるようにするヘルパーメソッド
    public static net.minecraft.world.damagesource.DamageSource bleeding(Level level) {
        return new net.minecraft.world.damagesource.DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLEEDING_KEY)
        );
    }
}
