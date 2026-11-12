package jp.houlab.mochidsuki.medicalsystemcore.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 設定値の保管庫(プロセス内で 1 つ)。スレッドセーフ。
 *
 * <p>読み取りはロック無し(不変マップの参照を volatile で保持)。書き込みは検証してから
 * マップ全体を差し替える(全か無か)ため、他スレッドが途中状態を見ることはない。
 * 値が実際に変わったキーについてだけリスナーへ通知する(値が同じ set は通知しない)。</p>
 *
 * <p>実行中の変更(コマンド・管理パネル)も {@link #set} / {@link #setAll} を使う。</p>
 */
public final class MedicalConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MedicalConfig.class);

    private static final Object WRITE_LOCK = new Object();
    private static final List<Consumer<ConfigKey<?>>> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile Map<ConfigKey<?>, Object> values = defaults();

    private MedicalConfig() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(ConfigKey<T> key) {
        // values は常に全キーを持つ(defaults() から始まり、差し替えも全キー)ため null にならない
        return (T) values.get(key);
    }

    /** 検証して保存し、値が変わっていればリスナーへ通知する。範囲外・型違い・キー間制約違反は IllegalArgumentException。 */
    public static <T> void set(ConfigKey<T> key, T value) {
        Map<String, Object> single = new HashMap<>();
        single.put(key.id(), value);
        setAll(single);
    }

    /**
     * id → 値 の Map をまとめて適用する(全か無か)。Map に無いキーは現在値のまま。
     * 全項目を検証した後、適用後の状態に対してキー間制約も検証する。未知の id は IllegalArgumentException。
     */
    public static void setAll(Map<String, Object> updates) {
        List<ConfigKey<?>> changed = new ArrayList<>();
        synchronized (WRITE_LOCK) {
            Map<ConfigKey<?>, Object> next = new HashMap<>(values);
            for (Map.Entry<String, Object> e : updates.entrySet()) {
                ConfigKey<?> key = MedicalConfigKeys.byId(e.getKey());
                if (key == null) {
                    throw new IllegalArgumentException("未知の設定キー: " + e.getKey());
                }
                next.put(key, key.validate(e.getValue()));
            }
            Optional<String> violation = checkConstraints(next);
            if (violation.isPresent()) {
                throw new IllegalArgumentException(violation.get());
            }
            for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
                if (!next.get(key).equals(values.get(key))) {
                    changed.add(key);
                }
            }
            if (changed.isEmpty()) {
                return;
            }
            values = Collections.unmodifiableMap(next);
        }
        notifyListeners(changed);
    }

    /** 全キーを既定値へ戻す。 */
    public static void resetToDefaults() {
        resetToDefaults(key -> true);
    }

    /** 条件に合うキーだけを既定値へ戻す(例: クライアントが切断時に同期対象キーだけ戻す)。 */
    public static void resetToDefaults(Predicate<ConfigKey<?>> filter) {
        Map<String, Object> updates = new HashMap<>();
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            if (filter.test(key)) {
                updates.put(key.id(), key.defaultValue());
            }
        }
        setAll(updates);
    }

    /** 全キーの現在値(id → 値、定義順、変更不可)。 */
    public static Map<String, Object> snapshot() {
        return snapshot(key -> true);
    }

    /** サーバーからクライアントへ送る同期対象キーだけの現在値。 */
    public static Map<String, Object> clientSyncedSnapshot() {
        return snapshot(ConfigKey::isClientSynced);
    }

    private static Map<String, Object> snapshot(Predicate<ConfigKey<?>> filter) {
        Map<ConfigKey<?>, Object> current = values;
        Map<String, Object> result = new LinkedHashMap<>();
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            if (filter.test(key)) {
                result.put(key.id(), current.get(key));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** 値が変わったキーごとに呼ばれる。set を呼んだスレッドで同期的に実行される。 */
    public static void addListener(Consumer<ConfigKey<?>> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Consumer<ConfigKey<?>> listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(List<ConfigKey<?>> changed) {
        for (ConfigKey<?> key : changed) {
            for (Consumer<ConfigKey<?>> listener : LISTENERS) {
                try {
                    listener.accept(key);
                } catch (RuntimeException e) {
                    // 1 つのリスナーの失敗で、設定の適用と他のリスナーへの通知を止めない
                    LOGGER.error("設定変更リスナーが失敗した: key={}", key, e);
                }
            }
        }
    }

    private static Map<ConfigKey<?>, Object> defaults() {
        Map<ConfigKey<?>, Object> map = new HashMap<>();
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            map.put(key, key.defaultValue());
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * キー間制約を検証する。違反があれば理由を返す。
     * 現在の制約: blood_damage_threshold_85 &gt; _70 &gt; _65 &gt; _60(狭義単調減少)。
     */
    static Optional<String> checkConstraints(Map<ConfigKey<?>, Object> map) {
        double t85 = (Double) map.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_85);
        double t70 = (Double) map.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_70);
        double t65 = (Double) map.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_65);
        double t60 = (Double) map.get(MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_60);
        if (!(t85 > t70 && t70 > t65 && t65 > t60)) {
            return Optional.of("blood_damage_threshold は 85 > 70 > 65 > 60 の順に狭義で大きくする必要がある(現在値: "
                    + t85 + ", " + t70 + ", " + t65 + ", " + t60 + ")");
        }
        return Optional.empty();
    }
}
