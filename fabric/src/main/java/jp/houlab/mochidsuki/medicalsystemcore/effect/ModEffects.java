package jp.houlab.mochidsuki.medicalsystemcore.effect;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

/**
 * MobEffect の登録ホルダ(Forge の Medicalsystemcore.TRANSFUSION などに相当)。ID は Forge と同じ。
 * MobEffectInstance は Holder を受け取るので、Holder のまま公開する。
 */
public final class ModEffects {
    private ModEffects() {
    }

    public static final Holder<MobEffect> TRANSFUSION = register("transfusion", new TransfusionEffect());
    public static final Holder<MobEffect> BANDAGE_EFFECT = register("bandage_effect", new BandageEffect());
    public static final Holder<MobEffect> ADRENALINE_EFFECT = register("adrenaline_effect", new AdrenalineEffect());
    public static final Holder<MobEffect> FIBRINOGEN_EFFECT = register("fibrinogen_effect", new FibrinogenEffect());
    public static final Holder<MobEffect> TRANEXAMIC_ACID_EFFECT = register("tranexamic_acid_effect", new TranexamicAcidEffect());
    public static final Holder<MobEffect> BLEEDING_EFFECT = register("bleeding", new BleedingEffect());

    private static Holder<MobEffect> register(String name, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Medicalsystemcore.id(name), effect);
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
