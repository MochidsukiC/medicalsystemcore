package jp.houlab.mochidsuki.medicalsystemcore.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 設定ファイル(JSON)の読み書き。形式は {@code { "<section>": { "<key>": value } }}。
 * 各キーの説明は {@link ConfigKey#description()} が持ち、ファイルにはコメントを含めない。
 *
 * <p>入出力の失敗(読み書きできない等)は {@link UncheckedIOException} で呼び出し元へ伝える。
 * 値の不正・欠落・未知のキーはファイルの通常の扱いとして WARN ログを出して補正する。</p>
 */
public final class MedicalConfigFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(MedicalConfigFile.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** load の結果。 */
    public enum LoadStatus {
        /** ファイルが無かったため、既定値で新規作成した。 */
        CREATED,
        /** 読み込めた(欠けているキーがあれば既定値で補って書き戻した)。 */
        LOADED,
        /** JSON として読めなかった。メモリ上は既定値で動作し、ファイルは上書きしていない。 */
        UNREADABLE
    }

    /** 直前の load で JSON として読めなかったファイル。次の load が成功するまで、このファイルへの save を拒否する。 */
    private static Path unreadablePath;

    private MedicalConfigFile() {
    }

    /**
     * ファイルを読んで {@link MedicalConfig} の全キーへ適用する。
     * <ul>
     *   <li>ファイルが無ければ既定値で作る。</li>
     *   <li>欠けているキーは既定値で補い、ファイルへ書き戻す。</li>
     *   <li>不正な値(型違い・範囲外)はそのキーを既定値にする。キー間制約違反は閾値 4 キーすべてを既定値に戻す。いずれも WARN を出す。</li>
     *   <li>未知のキーは WARN を出して無視する。</li>
     *   <li>JSON として読めない場合は ERROR を出し、既定値で動作する。ユーザーの設定を失わないようファイルは上書きしない。
     *       この状態は保持され、次に load が成功するまで同じファイルへの {@link #save} は IllegalStateException で拒否される。</li>
     * </ul>
     */
    public static synchronized LoadStatus load(Path path) {
        if (!Files.exists(path)) {
            MedicalConfig.setAll(defaultsById());
            unreadablePath = null;
            save(path);
            LOGGER.info("設定ファイルが無いため既定値で作成した: {}", path);
            return LoadStatus.CREATED;
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("最上位が JSON オブジェクトではない");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            LOGGER.error("設定ファイルを JSON として読めない。既定値で動作し、ファイルは上書きしない: {}", path, e);
            MedicalConfig.setAll(defaultsById());
            // 後続の save() がこのファイルを既定値で上書きしないよう、読めなかった状態を保持する
            unreadablePath = path.toAbsolutePath().normalize();
            return LoadStatus.UNREADABLE;
        } catch (IOException e) {
            throw new UncheckedIOException("設定ファイルを読めない: " + path, e);
        }

        warnUnknownKeys(root);

        Map<String, Object> loaded = new LinkedHashMap<>();
        boolean missing = false;
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            JsonElement element = findElement(root, key);
            if (element == null) {
                missing = true;
                loaded.put(key.id(), key.defaultValue());
                continue;
            }
            Object value = toJavaValue(element);
            loaded.put(key.id(), value);
        }

        Map<String, Object> corrected = new LinkedHashMap<>();
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            Object raw = loaded.get(key.id());
            try {
                corrected.put(key.id(), key.validate(raw));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("設定値が不正なため既定値を使う: key={} value={} 理由={} 既定値={}",
                        key.id(), raw, e.getMessage(), key.defaultValue());
                corrected.put(key.id(), key.defaultValue());
            }
        }

        Optional<String> violation = MedicalConfig.checkConstraints(toKeyMap(corrected));
        if (violation.isPresent()) {
            LOGGER.warn("設定値がキー間制約に違反しているため、関連するキーをすべて既定値に戻す: 理由={}", violation.get());
            for (ConfigKey<?> key : new ConfigKey<?>[]{
                    MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_85, MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_70,
                    MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_65, MedicalConfigKeys.BLOOD_DAMAGE_THRESHOLD_60}) {
                corrected.put(key.id(), key.defaultValue());
            }
        }

        MedicalConfig.setAll(corrected);
        unreadablePath = null;
        if (missing) {
            LOGGER.info("設定ファイルに無いキーを既定値で補って書き戻す: {}", path);
            save(path);
        }
        return LoadStatus.LOADED;
    }

    /** 現在の設定値をファイルへ書く(一時ファイルへ書いてから置き換える)。 */
    public static synchronized void save(Path path) {
        if (path.toAbsolutePath().normalize().equals(unreadablePath)) {
            LOGGER.error("直前の load で JSON として読めなかったファイルのため、保存を拒否する: {}", path);
            throw new IllegalStateException("設定ファイルが読めない状態のため保存を拒否した(修正して再読み込みするまで保存できない): " + path);
        }
        Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
        Map<String, Object> snapshot = MedicalConfig.snapshot();
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            sections.computeIfAbsent(key.section(), s -> new LinkedHashMap<>()).put(key.id(), snapshot.get(key.id()));
        }

        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(sections, writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("設定ファイルを書けない: " + path, e);
        }
    }

    private static JsonElement findElement(JsonObject root, ConfigKey<?> key) {
        JsonElement section = root.get(key.section());
        if (section == null || !section.isJsonObject()) {
            return null;
        }
        return section.getAsJsonObject().get(key.id());
    }

    private static void warnUnknownKeys(JsonObject root) {
        for (Map.Entry<String, JsonElement> sectionEntry : root.entrySet()) {
            String sectionName = sectionEntry.getKey();
            if (!sectionEntry.getValue().isJsonObject()) {
                LOGGER.warn("未知の設定(オブジェクトではない)を無視する: {}", sectionName);
                continue;
            }
            for (String keyName : sectionEntry.getValue().getAsJsonObject().keySet()) {
                ConfigKey<?> key = MedicalConfigKeys.byId(keyName);
                if (key == null) {
                    LOGGER.warn("未知の設定キーを無視する: {}.{}", sectionName, keyName);
                } else if (!key.section().equals(sectionName)) {
                    LOGGER.warn("設定キーが違うセクションにあるため無視する: {}.{} (正しい場所: {}.{})",
                            sectionName, keyName, key.section(), key.id());
                }
            }
        }
    }

    /** JSON 値を検証前の Java 値へ変換する。null・配列・オブジェクトは型違いとして検証で弾かれるようにそのまま渡す。 */
    private static Object toJavaValue(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive()) {
            return element;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsNumber();
        }
        return primitive.getAsString();
    }

    private static Map<String, Object> defaultsById() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            map.put(key.id(), key.defaultValue());
        }
        return map;
    }

    private static Map<ConfigKey<?>, Object> toKeyMap(Map<String, Object> byId) {
        Map<ConfigKey<?>, Object> map = new HashMap<>();
        for (ConfigKey<?> key : MedicalConfigKeys.ALL) {
            map.put(key, byId.get(key.id()));
        }
        return map;
    }
}
