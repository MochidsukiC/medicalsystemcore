package jp.houlab.mochidsuki.medicalsystemcore;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // === 血液量関連設定 ===
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_THRESHOLD_85_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_THRESHOLD_70_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_THRESHOLD_65_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_THRESHOLD_60_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_85_70_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_70_65_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_65_60_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DAMAGE_BELOW_60_SPEC;
    private static final ForgeConfigSpec.IntValue BLOOD_DAMAGE_INTERVAL_SPEC;

    // === 出血関連設定 ===
    private static final ForgeConfigSpec.DoubleValue BLEEDING_CHANCE_LOW_DAMAGE_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_SPEED_INCREASE_LOW_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_SPEED_INCREASE_MID_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_SPEED_INCREASE_HIGH_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_SPEED_INCREASE_EXTREME_SPEC;

    // === 出血回復関連設定 ===
    private static final ForgeConfigSpec.DoubleValue BLEEDING_RECOVERY_BASE_RATE_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_RECOVERY_BANDAGE_MULTIPLIER_SPEC;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_RECOVERY_PLATELET_MULTIPLIER_SPEC;

    // === 血液回復関連設定 ===
    private static final ForgeConfigSpec.DoubleValue BLOOD_NATURAL_RECOVERY_RATE_SPEC;
    private static final ForgeConfigSpec.DoubleValue TRANSFUSION_RECOVERY_RATE_SPEC;

    // === 心停止関連設定 ===
    private static final ForgeConfigSpec.DoubleValue RESUSCITATION_CHANCE_DECAY_RATE_SPEC;
    private static final ForgeConfigSpec.IntValue CARDIAC_ARREST_DEATH_TIME_SPEC;
    private static final ForgeConfigSpec.DoubleValue ADRENALINE_RESUSCITATION_RECOVERY_RATE_SPEC;

    // === 除細動器関連設定 ===
    private static final ForgeConfigSpec.IntValue DEFIBRILLATOR_COOLDOWN_SPEC;
    private static final ForgeConfigSpec.IntValue DEFIBRILLATOR_CHARGE_TIME_SPEC;
    private static final ForgeConfigSpec.DoubleValue DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER_SPEC;
    private static final ForgeConfigSpec.DoubleValue DEFIBRILLATOR_TRANSITION_CHANCE_SPEC;

    // === 点滴関連設定 ===
    private static final ForgeConfigSpec.IntValue IV_PACK_DURATION_SPEC;
    private static final ForgeConfigSpec.DoubleValue IV_RANGE_SPEC;

    // === 包帯関連設定 ===
    private static final ForgeConfigSpec.IntValue BANDAGE_SELF_USE_DURATION_SPEC;
    private static final ForgeConfigSpec.IntValue BANDAGE_OTHER_USE_DURATION_SPEC;
    private static final ForgeConfigSpec.IntValue BANDAGE_EFFECT_DURATION_SPEC;
    private static final ForgeConfigSpec.IntValue BANDAGE_SELF_LEVEL_INCREASE_SPEC;
    private static final ForgeConfigSpec.IntValue BANDAGE_OTHER_LEVEL_INCREASE_SPEC;
    private static final ForgeConfigSpec.IntValue BANDAGE_MAX_LEVEL_SPEC;

    static {
        BUILDER.push("Blood System");
        BUILDER.comment("血液量に関する設定");

        BLOOD_DAMAGE_THRESHOLD_85_SPEC = BUILDER.comment("血液量85%の閾値")
                .defineInRange("blood_damage_threshold_85", 85.0, 0.0, 100.0);
        BLOOD_DAMAGE_THRESHOLD_70_SPEC = BUILDER.comment("血液量70%の閾値")
                .defineInRange("blood_damage_threshold_70", 70.0, 0.0, 100.0);
        BLOOD_DAMAGE_THRESHOLD_65_SPEC = BUILDER.comment("血液量65%の閾値")
                .defineInRange("blood_damage_threshold_65", 65.0, 0.0, 100.0);
        BLOOD_DAMAGE_THRESHOLD_60_SPEC = BUILDER.comment("血液量60%の閾値")
                .defineInRange("blood_damage_threshold_60", 60.0, 0.0, 100.0);

        BLOOD_DAMAGE_85_70_SPEC = BUILDER.comment("血液量85~70%時のダメージ")
                .defineInRange("blood_damage_85_70", 4.0, 0.0, 1000.0);
        BLOOD_DAMAGE_70_65_SPEC = BUILDER.comment("血液量70~65%時のダメージ")
                .defineInRange("blood_damage_70_65", 8.0, 0.0, 1000.0);
        BLOOD_DAMAGE_65_60_SPEC = BUILDER.comment("血液量65~60%時のダメージ")
                .defineInRange("blood_damage_65_60", 12.0, 0.0, 1000.0);
        BLOOD_DAMAGE_BELOW_60_SPEC = BUILDER.comment("血液量60%未満時のダメージ")
                .defineInRange("blood_damage_below_60", 99999.0, 0.0, 999999.0);

        BLOOD_DAMAGE_INTERVAL_SPEC = BUILDER.comment("血液量ダメージの間隔（tick）")
                .defineInRange("blood_damage_interval", 100, 1, 12000);

        BUILDER.pop();

        BUILDER.push("Bleeding System");
        BUILDER.comment("出血システムに関する設定");

        BLEEDING_CHANCE_LOW_DAMAGE_SPEC = BUILDER.comment("0.1~2ダメージ時の出血発生確率")
                .defineInRange("bleeding_chance_low_damage", 0.10, 0.0, 1.0);
        BLEEDING_SPEED_INCREASE_LOW_SPEC = BUILDER.comment("0.1~2ダメージ時の出血速度増加量")
                .defineInRange("bleeding_speed_increase_low", 1.0, 0.0, 100.0);
        BLEEDING_SPEED_INCREASE_MID_SPEC = BUILDER.comment("2~5ダメージ時の出血速度増加量")
                .defineInRange("bleeding_speed_increase_mid", 3.0, 0.0, 100.0);
        BLEEDING_SPEED_INCREASE_HIGH_SPEC = BUILDER.comment("5~10ダメージ時の出血速度増加量")
                .defineInRange("bleeding_speed_increase_high", 6.0, 0.0, 100.0);
        BLEEDING_SPEED_INCREASE_EXTREME_SPEC = BUILDER.comment("10ダメージ以上時の出血速度増加量")
                .defineInRange("bleeding_speed_increase_extreme", 9.0, 0.0, 100.0);

        BLEEDING_RECOVERY_BASE_RATE_SPEC = BUILDER.comment("出血回復の基本レート")
                .defineInRange("bleeding_recovery_base_rate", 0.1, 0.0, 10.0);
        BLEEDING_RECOVERY_BANDAGE_MULTIPLIER_SPEC = BUILDER.comment("包帯エフェクトの出血回復倍率")
                .defineInRange("bleeding_recovery_bandage_multiplier", 1.0, 0.0, 100.0);
        BLEEDING_RECOVERY_PLATELET_MULTIPLIER_SPEC = BUILDER.comment("血小板エフェクトの出血回復倍率")
                .defineInRange("bleeding_recovery_platelet_multiplier", 5.0, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.push("Blood Recovery");
        BUILDER.comment("血液回復に関する設定");

        BLOOD_NATURAL_RECOVERY_RATE_SPEC = BUILDER.comment("自然回復時の血液回復率（%/秒）")
                .defineInRange("blood_natural_recovery_rate", 0.5, 0.0, 10.0);
        TRANSFUSION_RECOVERY_RATE_SPEC = BUILDER.comment("輸血時の血液回復率（%/秒）")
                .defineInRange("transfusion_recovery_rate", 1.0, 0.0, 10.0);

        BUILDER.pop();

        BUILDER.push("Cardiac System");
        BUILDER.comment("心停止・蘇生システムに関する設定");

        RESUSCITATION_CHANCE_DECAY_RATE_SPEC = BUILDER.comment("心停止時の蘇生確率減少率（%/分）")
                .defineInRange("resuscitation_chance_decay_rate", 5.0, 0.0, 100.0);
        CARDIAC_ARREST_DEATH_TIME_SPEC = BUILDER.comment("心停止から強制死亡までの時間（分）")
                .defineInRange("cardiac_arrest_death_time", 20, 1, 120);
        ADRENALINE_RESUSCITATION_RECOVERY_RATE_SPEC = BUILDER.comment("アドレナリン投与時の蘇生確率回復率（%/秒）")
                .defineInRange("adrenaline_resuscitation_recovery_rate", 0.5, 0.0, 10.0);

        BUILDER.pop();

        BUILDER.push("Defibrillator");
        BUILDER.comment("除細動器に関する設定");

        DEFIBRILLATOR_COOLDOWN_SPEC = BUILDER.comment("除細動器のクールダウン時間（秒）")
                .defineInRange("defibrillator_cooldown", 30, 1, 3600);
        DEFIBRILLATOR_CHARGE_TIME_SPEC = BUILDER.comment("除細動器の充電時間（秒）")
                .defineInRange("defibrillator_charge_time", 2, 1, 60);
        DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER_SPEC = BUILDER.comment("VF時の除細動器成功率倍率")
                .defineInRange("defibrillator_vf_success_multiplier", 2.0, 0.1, 10.0);
        DEFIBRILLATOR_TRANSITION_CHANCE_SPEC = BUILDER.comment("除細動器失敗時の状態移行確率")
                .defineInRange("defibrillator_transition_chance", 0.4, 0.0, 1.0);

        BUILDER.pop();

        BUILDER.push("IV System");
        BUILDER.comment("点滴システムに関する設定");

        IV_PACK_DURATION_SPEC = BUILDER.comment("点滴パック1個の持続時間（秒）")
                .defineInRange("iv_pack_duration", 60, 1, 3600);
        IV_RANGE_SPEC = BUILDER.comment("点滴の有効範囲（ブロック）")
                .defineInRange("iv_range", 10.0, 1.0, 100.0);

        BUILDER.pop();

        BUILDER.push("Bandage System");
        BUILDER.comment("包帯システムに関する設定");

        BANDAGE_SELF_USE_DURATION_SPEC = BUILDER.comment("自分への包帯使用時間（秒）")
                .defineInRange("bandage_self_use_duration", 6, 1, 60);
        BANDAGE_OTHER_USE_DURATION_SPEC = BUILDER.comment("他人への包帯使用時間（秒）")
                .defineInRange("bandage_other_use_duration", 4, 1, 60);
        BANDAGE_EFFECT_DURATION_SPEC = BUILDER.comment("包帯エフェクトの持続時間（秒）")
                .defineInRange("bandage_effect_duration", 60, 1, 3600);
        BANDAGE_SELF_LEVEL_INCREASE_SPEC = BUILDER.comment("自分への包帯使用時のエフェクトレベル増加")
                .defineInRange("bandage_self_level_increase", 1, 1, 10);
        BANDAGE_OTHER_LEVEL_INCREASE_SPEC = BUILDER.comment("他人への包帯使用時のエフェクトレベル増加")
                .defineInRange("bandage_other_level_increase", 2, 1, 10);
        BANDAGE_MAX_LEVEL_SPEC = BUILDER.comment("包帯エフェクトの最大レベル")
                .defineInRange("bandage_max_level", 6, 1, 20);

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // === 実際に使用される設定値 ===
    // 血液量関連
    public static double BLOOD_DAMAGE_THRESHOLD_85;
    public static double BLOOD_DAMAGE_THRESHOLD_70;
    public static double BLOOD_DAMAGE_THRESHOLD_65;
    public static double BLOOD_DAMAGE_THRESHOLD_60;
    public static double BLOOD_DAMAGE_85_70;
    public static double BLOOD_DAMAGE_70_65;
    public static double BLOOD_DAMAGE_65_60;
    public static double BLOOD_DAMAGE_BELOW_60;
    public static int BLOOD_DAMAGE_INTERVAL;

    // 出血関連
    public static double BLEEDING_CHANCE_LOW_DAMAGE;
    public static double BLEEDING_SPEED_INCREASE_LOW;
    public static double BLEEDING_SPEED_INCREASE_MID;
    public static double BLEEDING_SPEED_INCREASE_HIGH;
    public static double BLEEDING_SPEED_INCREASE_EXTREME;
    public static double BLEEDING_RECOVERY_BASE_RATE;
    public static double BLEEDING_RECOVERY_BANDAGE_MULTIPLIER;
    public static double BLEEDING_RECOVERY_PLATELET_MULTIPLIER;

    // 血液回復関連
    public static double BLOOD_NATURAL_RECOVERY_RATE;
    public static double TRANSFUSION_RECOVERY_RATE;

    // 心停止関連
    public static double RESUSCITATION_CHANCE_DECAY_RATE;
    public static int CARDIAC_ARREST_DEATH_TIME;
    public static double ADRENALINE_RESUSCITATION_RECOVERY_RATE;

    // 除細動器関連
    public static int DEFIBRILLATOR_COOLDOWN;
    public static int DEFIBRILLATOR_CHARGE_TIME;
    public static double DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER;
    public static double DEFIBRILLATOR_TRANSITION_CHANCE;

    // 点滴関連
    public static int IV_PACK_DURATION;
    public static double IV_RANGE;

    // 包帯関連
    public static int BANDAGE_SELF_USE_DURATION;
    public static int BANDAGE_OTHER_USE_DURATION;
    public static int BANDAGE_EFFECT_DURATION;
    public static int BANDAGE_SELF_LEVEL_INCREASE;
    public static int BANDAGE_OTHER_LEVEL_INCREASE;
    public static int BANDAGE_MAX_LEVEL;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        // 血液量関連
        BLOOD_DAMAGE_THRESHOLD_85 = BLOOD_DAMAGE_THRESHOLD_85_SPEC.get();
        BLOOD_DAMAGE_THRESHOLD_70 = BLOOD_DAMAGE_THRESHOLD_70_SPEC.get();
        BLOOD_DAMAGE_THRESHOLD_65 = BLOOD_DAMAGE_THRESHOLD_65_SPEC.get();
        BLOOD_DAMAGE_THRESHOLD_60 = BLOOD_DAMAGE_THRESHOLD_60_SPEC.get();
        BLOOD_DAMAGE_85_70 = BLOOD_DAMAGE_85_70_SPEC.get();
        BLOOD_DAMAGE_70_65 = BLOOD_DAMAGE_70_65_SPEC.get();
        BLOOD_DAMAGE_65_60 = BLOOD_DAMAGE_65_60_SPEC.get();
        BLOOD_DAMAGE_BELOW_60 = BLOOD_DAMAGE_BELOW_60_SPEC.get();
        BLOOD_DAMAGE_INTERVAL = BLOOD_DAMAGE_INTERVAL_SPEC.get();

        // 出血関連
        BLEEDING_CHANCE_LOW_DAMAGE = BLEEDING_CHANCE_LOW_DAMAGE_SPEC.get();
        BLEEDING_SPEED_INCREASE_LOW = BLEEDING_SPEED_INCREASE_LOW_SPEC.get();
        BLEEDING_SPEED_INCREASE_MID = BLEEDING_SPEED_INCREASE_MID_SPEC.get();
        BLEEDING_SPEED_INCREASE_HIGH = BLEEDING_SPEED_INCREASE_HIGH_SPEC.get();
        BLEEDING_SPEED_INCREASE_EXTREME = BLEEDING_SPEED_INCREASE_EXTREME_SPEC.get();
        BLEEDING_RECOVERY_BASE_RATE = BLEEDING_RECOVERY_BASE_RATE_SPEC.get();
        BLEEDING_RECOVERY_BANDAGE_MULTIPLIER = BLEEDING_RECOVERY_BANDAGE_MULTIPLIER_SPEC.get();
        BLEEDING_RECOVERY_PLATELET_MULTIPLIER = BLEEDING_RECOVERY_PLATELET_MULTIPLIER_SPEC.get();

        // 血液回復関連
        BLOOD_NATURAL_RECOVERY_RATE = BLOOD_NATURAL_RECOVERY_RATE_SPEC.get();
        TRANSFUSION_RECOVERY_RATE = TRANSFUSION_RECOVERY_RATE_SPEC.get();

        // 心停止関連
        RESUSCITATION_CHANCE_DECAY_RATE = RESUSCITATION_CHANCE_DECAY_RATE_SPEC.get();
        CARDIAC_ARREST_DEATH_TIME = CARDIAC_ARREST_DEATH_TIME_SPEC.get();
        ADRENALINE_RESUSCITATION_RECOVERY_RATE = ADRENALINE_RESUSCITATION_RECOVERY_RATE_SPEC.get();

        // 除細動器関連
        DEFIBRILLATOR_COOLDOWN = DEFIBRILLATOR_COOLDOWN_SPEC.get();
        DEFIBRILLATOR_CHARGE_TIME = DEFIBRILLATOR_CHARGE_TIME_SPEC.get();
        DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER = DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER_SPEC.get();
        DEFIBRILLATOR_TRANSITION_CHANCE = DEFIBRILLATOR_TRANSITION_CHANCE_SPEC.get();

        // 点滴関連
        IV_PACK_DURATION = IV_PACK_DURATION_SPEC.get();
        IV_RANGE = IV_RANGE_SPEC.get();

        // 包帯関連
        BANDAGE_SELF_USE_DURATION = BANDAGE_SELF_USE_DURATION_SPEC.get();
        BANDAGE_OTHER_USE_DURATION = BANDAGE_OTHER_USE_DURATION_SPEC.get();
        BANDAGE_EFFECT_DURATION = BANDAGE_EFFECT_DURATION_SPEC.get();
        BANDAGE_SELF_LEVEL_INCREASE = BANDAGE_SELF_LEVEL_INCREASE_SPEC.get();
        BANDAGE_OTHER_LEVEL_INCREASE = BANDAGE_OTHER_LEVEL_INCREASE_SPEC.get();
        BANDAGE_MAX_LEVEL = BANDAGE_MAX_LEVEL_SPEC.get();
    }
}