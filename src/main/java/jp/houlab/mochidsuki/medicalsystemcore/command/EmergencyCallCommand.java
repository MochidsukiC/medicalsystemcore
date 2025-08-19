package jp.houlab.mochidsuki.medicalsystemcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueCategory;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.item.DoctorCardItem;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class EmergencyCallCommand {

    // 通報の種類を定義するEnum
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("119")
                        .then(Commands.argument("type", StringArgumentType.string())
                                .suggests(EmergencyCallCommand::getCallTypeSuggestions) // 入力候補を提示
                                .then(Commands.argument("content", StringArgumentType.greedyString())
                                        .executes(EmergencyCallCommand::executeReport)
                                )
                        )
        );
    }

    private static int executeReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        String reporter = player.getDisplayName().getString();
        String typeStr = StringArgumentType.getString(context, "type").replace("\"", "");
        String content = StringArgumentType.getString(context, "content");



        RescueCategory callType;
        try {
            // 入力された文字列をEnumに変換
            callType = RescueCategory.fromValue(typeStr.toUpperCase());
            if(callType == null)throw new IllegalArgumentException();

        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§c無効な通報の種類です。"));
            return 0;
        }

        // --- 権限チェック ---
        switch (callType) {
            case SUPPORT:
                if (!DoctorCardItem.hasValidDoctorCard(player)) {
                    // 権限OK
                } else {
                    source.sendFailure(Component.literal("§c応援通報は医師のみ可能です。"));
                    return 0;
                }
                break;
            case POLICE:
                // TODO: 警察官カードを持っているかどうかの判定に置き換える
                if (false) { // 仮の判定
                    // 権限OK
                } else {
                    source.sendFailure(Component.literal("§c警察連携通報は警察官のみ可能です。"));
                    return 0;
                }
                break;
            case PREVENTIVE:
                // TODO: 指令本部用の権限を持っているかどうかの判定に置き換える
                if (false) { // 仮の判定
                    // 権限OK
                } else {
                    source.sendFailure(Component.literal("§c予防的医療事案通報は指令本部のみ可能です。"));
                    return 0;
                }
                break;
            default:
                // 他の通報は誰でも可能
                break;
        }

        // --- 通報処理 ---
        // TODO: この通報データをEmergencyCallManagerのような管理クラスに渡してリストに追加する
        RescueDataManager.createNewRescueData(reporter, callType, content, player.blockPosition());

        System.out.println("通報を受信しました:");
        System.out.println("  通報者: " + reporter);
        System.out.println("  種類: " + callType.getName());
        System.out.println("  内容: " + content);
        System.out.println("  地点: " + player.blockPosition());

        source.sendSuccess(() -> Component.literal("通報を送信しました。救急隊の到着をお待ちください。").withStyle(ChatFormatting.GREEN), true);

        // TODO: オンラインの救急隊員（と指令本部）に通報があったことを通知する
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            boolean hasValid = DoctorCardItem.hasValidDoctorCard(p);
            MedicalAuthorizationUtil.MedicalCredentials credentials =
                    MedicalAuthorizationUtil.getMedicalCredentials(p);

            if (hasValid && credentials != null) {
                p.sendSystemMessage(Component.literal("受信"));
            } else {

            }
        }
        return 1;
    }

    // コマンド入力時に通報の種類の候補を提示するメソッド
    private static CompletableFuture<Suggestions> getCallTypeSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                RescueCategory.getNamesForCommand(),
                builder
        );
    }
}