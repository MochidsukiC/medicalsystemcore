package jp.houlab.mochidsuki.medicalsystemcore.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public enum RescueCategory {
    SELF("本人緊急通報"),
    OTHER("他者緊急通報"),
    SUPPORT("応援通報"),
    POLICE("警察連携通報"),
    PREVENTIVE("予防的医療事案通報");

    final private String name;

    RescueCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Set<String> getNamesForCommand() {
        Set<String> names = new HashSet<>();
        for(RescueCategory c : RescueCategory.values()) {
            names.add("\""+c.getName()+"\"");
        }
        return names;
    }

    /**
     * 文字列から対応するEnum定数を探す静的メソッド
     * @param value 検索したい文字列
     * @return 対応するEnum定数 (見つからない場合はnullやOptional.empty())
     */
    public static RescueCategory fromValue(String value) {
        return Arrays.stream(values()) // Status.values() で全定数の配列を取得
                .filter(status -> status.getName().equalsIgnoreCase(value)) // 大文字・小文字を無視して比較
                .findFirst().orElse(null);
    }
}

