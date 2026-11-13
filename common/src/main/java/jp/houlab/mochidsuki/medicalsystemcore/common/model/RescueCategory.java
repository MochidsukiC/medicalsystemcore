package jp.houlab.mochidsuki.medicalsystemcore.common.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 通報の種類。
 * name は受信側(救急隊のチャット・救急ポータル)に出す分類名、commandName は通報者が /119 で打つ言葉(通報者の目線の言い方)。
 * 保存データは enum の定数名で持つため、どちらの文言を変えても既存の保存データには影響しない。
 */
public enum RescueCategory {
    SELF("本人緊急通報", "助けて（自分）"),
    OTHER("他者緊急通報", "けが人発見"),
    SUPPORT("応援通報", "応援要請"),
    POLICE("警察連携通報", "警察から要請"),
    PREVENTIVE("予防的医療事案通報", "事前待機要請");

    private final String name;
    private final String commandName;

    RescueCategory(String name, String commandName) {
        this.name = name;
        this.commandName = commandName;
    }

    /** 受信側に表示する分類名。 */
    public String getName() {
        return name;
    }

    /** 通報者が /119 で打つ言葉。 */
    public String getCommandName() {
        return commandName;
    }

    /** /119 の入力候補(日本語は引用符なしでは打てないため、引用符で囲んで出す)。 */
    public static Set<String> getNamesForCommand() {
        Set<String> names = new HashSet<>();
        for (RescueCategory c : RescueCategory.values()) {
            names.add("\"" + c.getCommandName() + "\"");
        }
        return names;
    }

    /**
     * /119 で打たれた言葉から種類を探す。
     * @param value 通報者が打った言葉
     * @return 対応する種類。見つからなければ null
     */
    public static RescueCategory fromValue(String value) {
        return Arrays.stream(values())
                .filter(c -> c.getCommandName().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}
