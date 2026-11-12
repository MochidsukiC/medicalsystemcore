package jp.houlab.mochidsuki.medicalsystemcore.common.config;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 設定項目 1 つの定義(不変)。
 *
 * <p>id は JSON のキー名、section は JSON のグループ名。型は Integer / Double / Boolean / String のいずれか。
 * インスタンスは {@link MedicalConfigKeys} に 1 つずつ存在し、同一性で比較する。</p>
 *
 * @param <T> 値の型
 */
public final class ConfigKey<T> {
    private final String id;
    private final String section;
    private final Class<T> type;
    private final T defaultValue;
    private final Number min;
    private final Number max;
    private final String description;
    private final boolean clientSynced;

    private ConfigKey(String id, String section, Class<T> type, T defaultValue,
                      Number min, Number max, String description, boolean clientSynced) {
        this.id = Objects.requireNonNull(id, "id");
        this.section = Objects.requireNonNull(section, "section");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.min = min;
        this.max = max;
        this.description = Objects.requireNonNull(description, "description");
        this.clientSynced = clientSynced;
        // 既定値自身が制約を満たしていることを定義時に保証する
        validate(defaultValue);
    }

    public static ConfigKey<Integer> ofInt(String section, String id, int defaultValue, int min, int max, String description) {
        return new ConfigKey<>(id, section, Integer.class, defaultValue, min, max, description, false);
    }

    public static ConfigKey<Double> ofDouble(String section, String id, double defaultValue, double min, double max, String description) {
        return new ConfigKey<>(id, section, Double.class, defaultValue, min, max, description, false);
    }

    public static ConfigKey<Boolean> ofBoolean(String section, String id, boolean defaultValue, String description) {
        return new ConfigKey<>(id, section, Boolean.class, defaultValue, null, null, description, false);
    }

    public static ConfigKey<String> ofString(String section, String id, String defaultValue, String description) {
        return new ConfigKey<>(id, section, String.class, defaultValue, null, null, description, false);
    }

    /** クライアントが参照するキーとして、サーバーからの同期対象にした複製を返す。 */
    public ConfigKey<T> clientSynced() {
        return new ConfigKey<>(id, section, type, defaultValue, min, max, description, true);
    }

    public String id() {
        return id;
    }

    public String section() {
        return section;
    }

    public Class<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** 数値型の下限。数値型でなければ null。 */
    public Number min() {
        return min;
    }

    /** 数値型の上限。数値型でなければ null。 */
    public Number max() {
        return max;
    }

    public String description() {
        return description;
    }

    public boolean isClientSynced() {
        return clientSynced;
    }

    /**
     * 値を検証して、このキーの型に正規化した値を返す。
     *
     * <p>Integer キーは整数として表せる数値のみ受け付ける(5.5 は不正)。Double キーは有限の数値なら整数リテラルも受け付ける。
     * NaN と Infinity は不正。範囲外・型違いは {@link IllegalArgumentException}。</p>
     */
    public T validate(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(id + ": 値が null");
        }
        if (type == Integer.class) {
            return type.cast(validateInt(value));
        }
        if (type == Double.class) {
            return type.cast(validateDouble(value));
        }
        if (type == Boolean.class) {
            if (value instanceof Boolean b) {
                return type.cast(b);
            }
            throw typeMismatch(value);
        }
        if (type == String.class) {
            if (value instanceof String s) {
                return type.cast(s);
            }
            throw typeMismatch(value);
        }
        throw new IllegalStateException(id + ": 未対応の型 " + type.getName());
    }

    private Integer validateInt(Object value) {
        if (!(value instanceof Number n)) {
            throw typeMismatch(value);
        }
        BigDecimal decimal = toBigDecimal(n);
        int result;
        try {
            // 小数部があれば ArithmeticException、int の範囲外でも ArithmeticException
            result = decimal.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(id + ": 整数(int)ではない値 " + value, e);
        }
        if (result < min.intValue() || result > max.intValue()) {
            throw outOfRange(value);
        }
        return result;
    }

    private Double validateDouble(Object value) {
        if (!(value instanceof Number n)) {
            throw typeMismatch(value);
        }
        double result = n.doubleValue();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException(id + ": 有限の数値ではない値 " + value);
        }
        if (result < min.doubleValue() || result > max.doubleValue()) {
            throw outOfRange(value);
        }
        return result;
    }

    private BigDecimal toBigDecimal(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException(id + ": 有限の数値ではない値 " + n);
            }
            return BigDecimal.valueOf(d);
        }
        try {
            return new BigDecimal(n.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(id + ": 数値として解釈できない値 " + n, e);
        }
    }

    private IllegalArgumentException typeMismatch(Object value) {
        return new IllegalArgumentException(id + ": 型が違う(期待 " + type.getSimpleName()
                + ", 実際 " + value.getClass().getSimpleName() + ") 値=" + value);
    }

    private IllegalArgumentException outOfRange(Object value) {
        return new IllegalArgumentException(id + ": 範囲外(" + min + " 〜 " + max + ") 値=" + value);
    }

    @Override
    public String toString() {
        return section + "." + id;
    }
}
