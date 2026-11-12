package jp.houlab.mochidsuki.medicalsystemcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.RescueCategory;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.item.DoctorCardItem;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class EmergencyCallCommand {

    // 通報の種類を定義するEnum
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("119")
                .executes(EmergencyCallCommand::executeDefaultReport)
                .then(Commands.argument("type", StringArgumentType.string())
                        .suggests(EmergencyCallCommand::getCallTypeSuggestions)
                        .executes(EmergencyCallCommand::executeTypeOnlyReport)
                        .then(Commands.argument("content", StringArgumentType.greedyString())
                                .executes(EmergencyCallCommand::executeReport)
                        )
                )
        );
    }

    private static int executeDefaultReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        return processReport(source, player, RescueCategory.SELF, "");
    }

    private static int executeTypeOnlyReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        String typeStr = StringArgumentType.getString(context, "type").replace("\"", "");

        RescueCategory callType;
        try {
            callType = RescueCategory.fromValue(typeStr.toUpperCase());
            if (callType == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§c無効な通報の種類です。"));
            return 0;
        }

        if (!checkPermission(source, player, callType)) {
            return 0;
        }

        return processReport(source, player, callType, "");
    }

    private static int executeReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        String typeStr = StringArgumentType.getString(context, "type").replace("\"", "");
        String content = StringArgumentType.getString(context, "content");

        RescueCategory callType;
        try {
            callType = RescueCategory.fromValue(typeStr.toUpperCase());
            if (callType == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§c無効な通報の種類です。"));
            return 0;
        }

        if (!checkPermission(source, player, callType)) {
            return 0;
        }

        return processReport(source, player, callType, content);
    }

    private static boolean checkPermission(CommandSourceStack source, ServerPlayer player, RescueCategory callType) {
        switch (callType) {
            case SUPPORT:
                // 応援通報は医師だけが送れる
                if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(player)) {
                    source.sendFailure(Component.literal("§c応援通報は医師のみ可能です。"));
                    return false;
                }
                break;
            case POLICE:
                // TODO: 警察官カードを持っているかどうかの判定に置き換える
                if (false) { // 仮の判定
                    // 権限OK
                } else {
                    source.sendFailure(Component.literal("§c警察連携通報は警察官のみ可能です。"));
                    return false;
                }
                break;
            case PREVENTIVE:
                // TODO: 指令本部用の権限を持っているかどうかの判定に置き換える
                if (false) { // 仮の判定
                    // 権限OK
                } else {
                    source.sendFailure(Component.literal("§c予防的医療事案通報は指令本部のみ可能です。"));
                    return false;
                }
                break;
            default:
                // 他の通報は誰でも可能
                break;
        }
        return true;
    }

    private static int processReport(CommandSourceStack source, ServerPlayer player, RescueCategory callType, String content) {
        String reporter = player.getDisplayName().getString();

        // --- 通報処理 ---
        ResourceKey<Level> dimension = player.level().dimension();
        BlockPos reporterPos = player.blockPosition();
        RescueDataManager.createNewRescueData(source.getServer(), player.getUUID(), reporter, callType, content, reporterPos, dimension);

        source.sendSuccess(() -> Component.literal("通報を送信しました。救急隊の到着をお待ちください。").withStyle(ChatFormatting.GREEN), true);

        // ディメンション表示名の取得
        String dimName;
        String dimPath = dimension.identifier().getPath();
        switch (dimPath) {
            case "overworld": dimName = "オーバーワールド"; break;
            case "the_nether": dimName = "ネザー"; break;
            case "the_end": dimName = "ジ・エンド"; break;
            default: dimName = dimension.identifier().toString(); break;
        }

        // --- 医師カード保持者への通知 ---
        ServerPlayer nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            boolean hasValid = DoctorCardItem.hasValidDoctorCard(p);
            MedicalAuthorizationUtil.MedicalCredentials credentials =
                    MedicalAuthorizationUtil.getMedicalCredentials(p);

            if (!hasValid || credentials == null) {
                continue;
            }

            // サウンド通知
            p.connection.send(new ClientboundSoundPacket(
                    SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS,
                    p.getX(), p.getY(), p.getZ(), 1.0f, 1.0f, p.level().getRandom().nextLong()
            ));

            // サブタイトル表示（空タイトルを送信してサブタイトルの表示をトリガー）
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("緊急通報受信: " + callType.getName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            ));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));

            // チャット詳細メッセージ
            MutableComponent divider = Component.literal("========== 緊急通報 ==========").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            MutableComponent reporterLine = Component.literal("通報者: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(reporter).withStyle(ChatFormatting.WHITE));
            MutableComponent categoryLine = Component.literal("種別: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(callType.getName()).withStyle(ChatFormatting.YELLOW));
            MutableComponent locationLine = Component.literal(
                    String.format("座標: X:%d Y:%d Z:%d", reporterPos.getX(), reporterPos.getY(), reporterPos.getZ())
            ).withStyle(ChatFormatting.AQUA);
            MutableComponent dimensionLine = Component.literal("ディメンション: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(dimName).withStyle(ChatFormatting.GREEN));
            MutableComponent dividerEnd = Component.literal("==============================").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

            p.sendSystemMessage(divider);
            p.sendSystemMessage(reporterLine);
            p.sendSystemMessage(categoryLine);
            p.sendSystemMessage(locationLine);
            p.sendSystemMessage(dimensionLine);
            if (!content.isEmpty()) {
                MutableComponent contentLine = Component.literal("内容: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(content).withStyle(ChatFormatting.WHITE));
                p.sendSystemMessage(contentLine);
            }
            p.sendSystemMessage(dividerEnd);

            // 最寄り隊員の判定（通報者本人を除外、クリエイティブ/スペクテイターを除外、同ディメンションのみ）
            if (!p.getUUID().equals(player.getUUID())
                    && p.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                    && p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                    && p.level().dimension().equals(dimension)) {
                double distance = Math.sqrt(p.blockPosition().distSqr(reporterPos));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = p;
                }
            }
        }

        // --- 最寄り隊員への追加通知 ---
        if (nearestPlayer != null) {
            // 同ディメンションに隊員がいる場合: 最も近い1人に通知
            MutableComponent nearestMsg = Component.literal(
                    String.format("あなたが最もダウン者に近いです。距離: %.0fブロック", nearestDistance)
            ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            nearestPlayer.sendSystemMessage(nearestMsg);
            nearestPlayer.connection.send(new ClientboundSoundPacket(
                    SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS,
                    nearestPlayer.getX(), nearestPlayer.getY(), nearestPlayer.getZ(),
                    1.0f, 2.0f, nearestPlayer.level().getRandom().nextLong()
            ));
        } else {
            // 同ディメンションに誰もいない場合: 全救急隊員（通報者本人を除く）に通知
            MutableComponent noDimMsg = Component.literal(
                    "通報者と同じディメンションに救急隊員がいません。全員に通知しています。"
            ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                if (p.getUUID().equals(player.getUUID())) continue;
                boolean hasValid = DoctorCardItem.hasValidDoctorCard(p);
                MedicalAuthorizationUtil.MedicalCredentials credentials =
                        MedicalAuthorizationUtil.getMedicalCredentials(p);
                if (hasValid && credentials != null) {
                    p.sendSystemMessage(noDimMsg);
                }
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