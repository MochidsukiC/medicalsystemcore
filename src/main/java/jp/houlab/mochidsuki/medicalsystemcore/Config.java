package jp.houlab.mochidsuki.medicalsystemcore;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // --- ここからが設定項目の定義です ---

    // 設定の骨格（SPEC）を定義します。_SPECという接尾辞をつけて、実際に使う変数と区別します。
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL1_HP_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL1_BLOOD_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL2_HP_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL2_BLOOD_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL3_HP_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL3_BLOOD_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL4_HP_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_LVL4_BLOOD_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_RECOVERY_INTERVAL_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_CHANCE_LOW_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_CHANCE_MID_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_CHANCE_HIGH_LVL1_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEED_CHANCE_HIGH_LVL2_SPEC;


    // staticイニシャライザを使って、設定ファイルの内容を構築します。
    static {
        BUILDER.push("Bleeding Intervals (in seconds)");
        BUILDER.comment("各出血レベルでのHP(体力)とBlood(血液量)が1ずつ減少する間隔（秒）");

        BLEED_LVL1_HP_INTERVAL_SPEC = BUILDER.defineInRange("lvl1_hp_interval", 30.0, 0.1, 3600.0);
        BLEED_LVL1_BLOOD_INTERVAL_SPEC = BUILDER.defineInRange("lvl1_blood_interval", 30.0, 0.1, 3600.0);

        BLEED_LVL2_HP_INTERVAL_SPEC = BUILDER.defineInRange("lvl2_hp_interval", 15.0, 0.1, 3600.0);
        BLEED_LVL2_BLOOD_INTERVAL_SPEC = BUILDER.defineInRange("lvl2_blood_interval", 10.0, 0.1, 3600.0);

        BLEED_LVL3_HP_INTERVAL_SPEC = BUILDER.defineInRange("lvl3_hp_interval", 10.0, 0.1, 3600.0);
        BLEED_LVL3_BLOOD_INTERVAL_SPEC = BUILDER.defineInRange("lvl3_blood_interval", 2.0, 0.1, 3600.0);

        BLEED_LVL4_HP_INTERVAL_SPEC = BUILDER.defineInRange("lvl4_hp_interval", 4.0, 0.1, 3600.0);
        BLEED_LVL4_BLOOD_INTERVAL_SPEC = BUILDER.defineInRange("lvl4_blood_interval", 0.1, 0.1, 3600.0);

        BUILDER.pop();

        BUILDER.push("Recovery Settings");
        BLOOD_RECOVERY_INTERVAL_SPEC = BUILDER.comment("出血していない時に血液量が1%回復する間隔（秒）")
                .defineInRange("blood_recovery_interval", 2.0, 0.1, 3600.0);
        BUILDER.pop();

        BUILDER.push("Damage to Bleed Chances (0.0 to 1.0)");
        BUILDER.comment("ダメージ量に応じた出血発生確率");

        BLEED_CHANCE_LOW_SPEC = BUILDER.comment("1-5ダメージを受けた時の出血発生確率")
                .defineInRange("chance_low_damage", 0.1, 0.0, 1.0);
        BLEED_CHANCE_MID_SPEC = BUILDER.comment("6-10ダメージを受けた時の出血発生確率")
                .defineInRange("chance_mid_damage", 0.5, 0.0, 1.0);
        BLEED_CHANCE_HIGH_LVL1_SPEC = BUILDER.comment("10以上のダメージを受けた時に出血レベルが1上がる確率")
                .defineInRange("chance_high_damage_lvl1", 0.5, 0.0, 1.0);
        BLEED_CHANCE_HIGH_LVL2_SPEC = BUILDER.comment("10以上のダメージを受けた時に出血レベルが2上がる確率（lvl1の確率と合わせて1.0になるように調整してください）")
                .defineInRange("chance_high_damage_lvl2", 0.5, 0.0, 1.0);

        BUILDER.pop();

        // 最後にビルドしてSPECを完成させます
    }
    public final static ForgeConfigSpec SPEC = BUILDER.build();


    // --- ここから下は、ロードした設定値を保持するための変数です ---
    public static double BLEED_LVL1_HP_INTERVAL, BLEED_LVL1_BLOOD_INTERVAL;
    public static double BLEED_LVL2_HP_INTERVAL, BLEED_LVL2_BLOOD_INTERVAL;
    public static double BLEED_LVL3_HP_INTERVAL, BLEED_LVL3_BLOOD_INTERVAL;
    public static double BLEED_LVL4_HP_INTERVAL, BLEED_LVL4_BLOOD_INTERVAL;
    public static double BLOOD_RECOVERY_INTERVAL;
    public static double BLEED_CHANCE_LOW;
    public static double BLEED_CHANCE_MID;
    public static double BLEED_CHANCE_HIGH_LVL1;
    public static double BLEED_CHANCE_HIGH_LVL2;


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ロードされたコンフィグがこのクラスのものでなければ何もしない
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        // SPECから値を取得し、扱いやすい変数に格納する
        BLEED_LVL1_HP_INTERVAL = BLEED_LVL1_HP_INTERVAL_SPEC.get();
        BLEED_LVL1_BLOOD_INTERVAL = BLEED_LVL1_BLOOD_INTERVAL_SPEC.get();
        BLEED_LVL2_HP_INTERVAL = BLEED_LVL2_HP_INTERVAL_SPEC.get();
        BLEED_LVL2_BLOOD_INTERVAL = BLEED_LVL2_BLOOD_INTERVAL_SPEC.get();
        BLEED_LVL3_HP_INTERVAL = BLEED_LVL3_HP_INTERVAL_SPEC.get();
        BLEED_LVL3_BLOOD_INTERVAL = BLEED_LVL3_BLOOD_INTERVAL_SPEC.get();
        BLEED_LVL4_HP_INTERVAL = BLEED_LVL4_HP_INTERVAL_SPEC.get();
        BLEED_LVL4_BLOOD_INTERVAL = BLEED_LVL4_BLOOD_INTERVAL_SPEC.get();
        BLOOD_RECOVERY_INTERVAL = BLOOD_RECOVERY_INTERVAL_SPEC.get();
        BLEED_CHANCE_LOW = BLEED_CHANCE_LOW_SPEC.get();
        BLEED_CHANCE_MID = BLEED_CHANCE_MID_SPEC.get();
        BLEED_CHANCE_HIGH_LVL1 = BLEED_CHANCE_HIGH_LVL1_SPEC.get();
        BLEED_CHANCE_HIGH_LVL2 = BLEED_CHANCE_HIGH_LVL2_SPEC.get();
    }
}
