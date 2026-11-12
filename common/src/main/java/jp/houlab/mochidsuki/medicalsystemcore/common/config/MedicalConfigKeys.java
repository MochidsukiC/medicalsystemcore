package jp.houlab.mochidsuki.medicalsystemcore.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全設定キーの定義。値の範囲・既定値は旧 Forge 版 Config.java を引き継ぐ。
 *
 * <p>{@code clientSynced} のキーはクライアント側コードが参照するため、サーバーからクライアントへ同期する。</p>
 */
public final class MedicalConfigKeys {

    // 以降の static final キー定義より前に初期化されている必要がある(宣言順に初期化される)
    private static final List<ConfigKey<?>> REGISTRY = new ArrayList<>();
    private static final Map<String, ConfigKey<?>> BY_ID = new LinkedHashMap<>();

    // === 血液量(blood_system) ===
    public static final ConfigKey<Double> BLOOD_DAMAGE_THRESHOLD_85 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_threshold_85", 85.0, 0.0, 100.0, "血液量85%の閾値"));
    public static final ConfigKey<Double> BLOOD_DAMAGE_THRESHOLD_70 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_threshold_70", 70.0, 0.0, 100.0, "血液量70%の閾値"));
    public static final ConfigKey<Double> BLOOD_DAMAGE_THRESHOLD_65 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_threshold_65", 65.0, 0.0, 100.0, "血液量65%の閾値"));
    public static final ConfigKey<Double> BLOOD_DAMAGE_THRESHOLD_60 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_threshold_60", 60.0, 0.0, 100.0, "血液量60%の閾値"));
    public static final ConfigKey<Double> BLOOD_DAMAGE_85_70 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_85_70", 4.0, 0.0, 1000.0, "血液量85~70%時のダメージ"));
    public static final ConfigKey<Double> BLOOD_DAMAGE_70_65 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_70_65", 8.0, 0.0, 1000.0, "血液量70~65%時のダメージ"));
    public static final ConfigKey<Double> BLOOD_DAMAGE_65_60 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_65_60", 12.0, 0.0, 1000.0, "血液量65~60%時のダメージ"));
    public static final ConfigKey<Double> BLOOD_DAMAGE_BELOW_60 = register(
            ConfigKey.ofDouble("blood_system", "blood_damage_below_60", 99999.0, 0.0, 999999.0, "血液量60%未満時のダメージ"));
    public static final ConfigKey<Integer> BLOOD_DAMAGE_INTERVAL = register(
            ConfigKey.ofInt("blood_system", "blood_damage_interval", 100, 1, 12000, "血液量ダメージの間隔(tick)"));

    // === 出血(bleeding_system) ===
    public static final ConfigKey<Double> BLEEDING_CHANCE_LOW_DAMAGE = register(
            ConfigKey.ofDouble("bleeding_system", "bleeding_chance_low_damage", 0.10, 0.0, 1.0, "0.1~2ダメージ時の出血発生確率"));
    public static final ConfigKey<Double> BLEEDING_SPEED_INCREASE_LOW = register(
            ConfigKey.ofDouble("bleeding_system", "bleeding_speed_increase_low", 1.0, 0.0, 100.0, "0.1~2ダメージ時の出血速度増加量"));
    public static final ConfigKey<Double> BLEEDING_SPEED_INCREASE_MID = register(
            ConfigKey.ofDouble("bleeding_system", "bleeding_speed_increase_mid", 3.0, 0.0, 100.0, "2~5ダメージ時の出血速度増加量"));
    public static final ConfigKey<Double> BLEEDING_SPEED_INCREASE_HIGH = register(
            ConfigKey.ofDouble("bleeding_system", "bleeding_speed_increase_high", 6.0, 0.0, 100.0, "5~10ダメージ時の出血速度増加量"));
    public static final ConfigKey<Double> BLEEDING_SPEED_INCREASE_EXTREME = register(
            ConfigKey.ofDouble("bleeding_system", "bleeding_speed_increase_extreme", 9.0, 0.0, 100.0, "10ダメージ以上時の出血速度増加量"));
    public static final ConfigKey<Double> BLEEDING_RECOVERY_BASE_RATE = register(
            ConfigKey.ofDouble("bleeding_system", "bleeding_recovery_base_rate", 0.1, 0.0, 10.0, "出血回復の基本レート"));
    public static final ConfigKey<Double> BLEEDING_RECOVERY_BANDAGE_MULTIPLIER = register(
            ConfigKey.ofDouble("bleeding_system", "bleeding_recovery_bandage_multiplier", 0.5, 0.0, 100.0, "包帯エフェクトの出血回復倍率"));

    // === 止血剤(hemostatic_agents) ===
    public static final ConfigKey<Double> FIBRINOGEN_BLEEDING_REDUCTION_RATE = register(
            ConfigKey.ofDouble("hemostatic_agents", "fibrinogen_bleeding_reduction_rate", 2.0, 0.0, 100.0,
                    "フィブリノゲン製剤が出血速度を毎秒この値だけ減らす(絶対値。エフェクトレベルが上がるほど倍になる)"));
    public static final ConfigKey<Double> TRANEXAMIC_ACID_BLEEDING_REDUCTION_RATE = register(
            ConfigKey.ofDouble("hemostatic_agents", "tranexamic_acid_bleeding_reduction_rate", 2.0, 0.0, 100.0,
                    "トラネキサム酸が出血速度を毎秒この値だけ減らす(絶対値。エフェクトレベルが上がるほど倍になる)"));

    // === 血液回復(blood_recovery) ===
    public static final ConfigKey<Double> BLOOD_NATURAL_RECOVERY_RATE = register(
            ConfigKey.ofDouble("blood_recovery", "blood_natural_recovery_rate", 0.5, 0.0, 10.0, "自然回復時の血液回復率(%/秒)"));
    public static final ConfigKey<Double> TRANSFUSION_RECOVERY_RATE = register(
            ConfigKey.ofDouble("blood_recovery", "transfusion_recovery_rate", 1.0, 0.0, 10.0, "輸血時の血液回復率(%/秒)"));

    // === 心停止(cardiac_system) ===
    public static final ConfigKey<Double> RESUSCITATION_CHANCE_DECAY_RATE = register(
            ConfigKey.ofDouble("cardiac_system", "resuscitation_chance_decay_rate", 5.0, 0.0, 100.0, "心停止時の蘇生確率減少率(%/分)"));
    public static final ConfigKey<Integer> CARDIAC_ARREST_DEATH_TIME = register(
            ConfigKey.ofInt("cardiac_system", "cardiac_arrest_death_time", 20, 1, 120, "心停止から強制死亡までの時間(分)"));
    public static final ConfigKey<Double> ADRENALINE_RESUSCITATION_RECOVERY_RATE = register(
            ConfigKey.ofDouble("cardiac_system", "adrenaline_resuscitation_recovery_rate", 0.5, 0.0, 10.0, "アドレナリン投与時の蘇生確率回復率(%/秒)"));

    // === 除細動器(defibrillator) ===
    public static final ConfigKey<Integer> DEFIBRILLATOR_COOLDOWN = register(
            ConfigKey.ofInt("defibrillator", "defibrillator_cooldown", 30, 1, 3600, "除細動器のクールダウン時間(秒)"));
    // 充電の進捗表示と充電音をクライアントのブロックエンティティ tick / レンダラーが参照する
    public static final ConfigKey<Integer> DEFIBRILLATOR_CHARGE_TIME = register(
            ConfigKey.ofInt("defibrillator", "defibrillator_charge_time", 2, 1, 60, "除細動器の充電時間(秒)").clientSynced());
    public static final ConfigKey<Double> DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER = register(
            ConfigKey.ofDouble("defibrillator", "defibrillator_vf_success_multiplier", 2.0, 0.1, 10.0, "VF時の除細動器成功率倍率"));
    public static final ConfigKey<Double> DEFIBRILLATOR_TRANSITION_CHANCE = register(
            ConfigKey.ofDouble("defibrillator", "defibrillator_transition_chance", 0.4, 0.0, 1.0, "除細動器失敗時の状態移行確率"));
    public static final ConfigKey<Double> QTE_GREAT_SUCCESS_BONUS = register(
            ConfigKey.ofDouble("defibrillator", "qte_great_success_bonus", 20.0, -100.0, 100.0,
                    "除細動QTEが大成功のときに蘇生確率(%)へ加える補正値"));
    public static final ConfigKey<Double> QTE_SUCCESS_BONUS = register(
            ConfigKey.ofDouble("defibrillator", "qte_success_bonus", 0.0, -100.0, 100.0,
                    "除細動QTEが成功のときに蘇生確率(%)へ加える補正値"));

    // === 点滴(iv_system) ===
    // パック残量のツールチップ・耐久バーをクライアントのアイテムが参照する
    public static final ConfigKey<Integer> IV_PACK_DURATION = register(
            ConfigKey.ofInt("iv_system", "iv_pack_duration", 120, 1, 3600, "点滴パック1個の持続時間(秒)").clientSynced());
    public static final ConfigKey<Double> IV_RANGE = register(
            ConfigKey.ofDouble("iv_system", "iv_range", 10.0, 1.0, 100.0, "点滴の有効範囲(ブロック)"));

    // === 包帯(bandage_system) ===
    // 自分への使用時間は Item#getUseDuration がクライアントでも呼ばれる
    public static final ConfigKey<Integer> BANDAGE_SELF_USE_DURATION = register(
            ConfigKey.ofInt("bandage_system", "bandage_self_use_duration", 5, 1, 60, "自分への包帯使用時間(秒)").clientSynced());
    public static final ConfigKey<Integer> BANDAGE_OTHER_USE_DURATION = register(
            ConfigKey.ofInt("bandage_system", "bandage_other_use_duration", 3, 1, 60, "他人への包帯使用時間(秒)").clientSynced());
    public static final ConfigKey<Integer> BANDAGE_EFFECT_DURATION = register(
            ConfigKey.ofInt("bandage_system", "bandage_effect_duration", 300, 1, 3600, "包帯エフェクトの持続時間(秒)"));
    public static final ConfigKey<Integer> BANDAGE_SELF_LEVEL_INCREASE = register(
            ConfigKey.ofInt("bandage_system", "bandage_self_level_increase", 1, 1, 10, "自分への包帯使用時のエフェクトレベル増加"));
    public static final ConfigKey<Integer> BANDAGE_OTHER_LEVEL_INCREASE = register(
            ConfigKey.ofInt("bandage_system", "bandage_other_level_increase", 2, 1, 10, "他人への包帯使用時のエフェクトレベル増加"));
    public static final ConfigKey<Integer> BANDAGE_MAX_LEVEL = register(
            ConfigKey.ofInt("bandage_system", "bandage_max_level", 6, 1, 20, "包帯エフェクトの最大レベル"));

    // === 意識不明(unconscious_system) ===
    public static final ConfigKey<Boolean> UNCONSCIOUS_FLOAT_IN_LIQUID = register(
            ConfigKey.ofBoolean("unconscious_system", "unconscious_float_in_liquid", true, "意識不明時に液体中で浮上するか"));
    public static final ConfigKey<Double> UNCONSCIOUS_FLOAT_SPEED = register(
            ConfigKey.ofDouble("unconscious_system", "unconscious_float_speed", 0.05, 0.0, 1.0, "液体中の浮上速度"));
    public static final ConfigKey<Boolean> GIVE_UP_ENABLED = register(
            ConfigKey.ofBoolean("unconscious_system", "give_up_enabled", true, "意識不明時にShift長押しでリスポーンできるか").clientSynced());
    public static final ConfigKey<Integer> GIVE_UP_DURATION = register(
            ConfigKey.ofInt("unconscious_system", "give_up_duration", 5, 1, 60, "確殺に必要なShift長押し時間(秒)").clientSynced());
    public static final ConfigKey<Boolean> TRANSPORT_MODE = register(
            ConfigKey.ofBoolean("unconscious_system", "transport_mode", true, "搬送モード(trueで搬送、falseで自死)").clientSynced());
    public static final ConfigKey<Integer> TRANSPORT_X = register(
            ConfigKey.ofInt("unconscious_system", "transport_x", 0, -30000000, 30000000, "搬送先X座標"));
    public static final ConfigKey<Integer> TRANSPORT_Y = register(
            ConfigKey.ofInt("unconscious_system", "transport_y", 64, -64, 320, "搬送先Y座標"));
    public static final ConfigKey<Integer> TRANSPORT_Z = register(
            ConfigKey.ofInt("unconscious_system", "transport_z", 0, -30000000, 30000000, "搬送先Z座標"));
    public static final ConfigKey<String> TRANSPORT_DIMENSION = register(
            ConfigKey.ofString("unconscious_system", "transport_dimension", "minecraft:overworld", "搬送先ディメンション(例: minecraft:overworld)"));

    /** 全キー(定義順)。 */
    public static final List<ConfigKey<?>> ALL = Collections.unmodifiableList(REGISTRY);

    private MedicalConfigKeys() {
    }

    /** id からキーを引く。未知の id は null。 */
    public static ConfigKey<?> byId(String id) {
        return BY_ID.get(id);
    }

    private static <T> ConfigKey<T> register(ConfigKey<T> key) {
        if (BY_ID.putIfAbsent(key.id(), key) != null) {
            throw new IllegalStateException("設定キーの id が重複している: " + key.id());
        }
        REGISTRY.add(key);
        return key;
    }
}
