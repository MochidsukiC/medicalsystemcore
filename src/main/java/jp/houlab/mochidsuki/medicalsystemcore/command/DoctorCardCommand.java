package jp.houlab.mochidsuki.medicalsystemcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import jp.houlab.mochidsuki.medicalsystemcore.item.DoctorCardItem;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;

/**
 * 医師カード管理用のコマンドクラス
 * /doctorcard grant <player> - 正規カード付与（ライセンス番号自動発行）
 * /doctorcard grant <player> <license_number> - 正規カード付与（ライセンス番号指定）
 * /doctorcard revoke <player> - カード無効化
 * /doctorcard check <player> - 認証状態確認
 * /doctorcard fake <fake_name> <license_number> - テスト用偽造カード作成
 */
public class DoctorCardCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("doctorcard")
                .requires(source -> source.hasPermission(2)); // OP権限が必要

        // /doctorcard grant <player> - 自動発行
        command.then(Commands.literal("grant")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(DoctorCardCommand::grantCardAuto)
                        .then(Commands.argument("license_number", StringArgumentType.string())
                                .executes(DoctorCardCommand::grantCardManual))));

        // /doctorcard revoke <player>
        command.then(Commands.literal("revoke")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(DoctorCardCommand::revokeCard)));

        // /doctorcard check <player>
        command.then(Commands.literal("check")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(DoctorCardCommand::checkCard)));

        // /doctorcard fake <fake_name> <license_number>
        command.then(Commands.literal("fake")
                .then(Commands.argument("fake_name", StringArgumentType.string())
                        .then(Commands.argument("license_number", StringArgumentType.string())
                                .executes(DoctorCardCommand::createFakeCard))));

        // /doctorcard list - 全プレイヤーの認証状態を表示
        command.then(Commands.literal("list")
                .executes(DoctorCardCommand::listAllCards));

        dispatcher.register(command);
    }

    /**
     * 正規の医師カードを付与（ライセンス番号自動発行）
     */
    private static int grantCardAuto(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();

        // 既存の正規カードがあるかチェック
        if (DoctorCardItem.hasValidDoctorCard(targetPlayer)) {
            source.sendFailure(Component.literal("対象プレイヤーは既に有効な医師カードを所持しています。"));
            return 0;
        }

        MedicalAuthorizationUtil.grantDoctorCard(targetPlayer);

        // 発行されたライセンス番号を取得して表示
        MedicalAuthorizationUtil.MedicalCredentials credentials =
                MedicalAuthorizationUtil.getMedicalCredentials(targetPlayer);
        String licenseNumber = credentials != null ? credentials.getLicenseNumber() : "不明";

        source.sendSuccess(() -> Component.literal("プレイヤー " + targetPlayer.getName().getString() +
                        " に医師カード（免許番号: " + licenseNumber + "）を付与しました。")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    /**
     * 正規の医師カードを付与（ライセンス番号指定）
     */
    private static int grantCardManual(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        String licenseNumber = StringArgumentType.getString(context, "license_number");
        CommandSourceStack source = context.getSource();

        // 既存の正規カードがあるかチェック
        if (DoctorCardItem.hasValidDoctorCard(targetPlayer)) {
            source.sendFailure(Component.literal("対象プレイヤーは既に有効な医師カードを所持しています。"));
            return 0;
        }

        MedicalAuthorizationUtil.grantDoctorCard(targetPlayer, licenseNumber);

        source.sendSuccess(() -> Component.literal("プレイヤー " + targetPlayer.getName().getString() +
                        " に医師カード（免許番号: " + licenseNumber + "）を付与しました。")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    /**
     * プレイヤーの医師カードを無効化（削除）
     */
    private static int revokeCard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();

        boolean removed = false;

        // インベントリ内の正規医師カードを削除
        for (int i = 0; i < targetPlayer.getInventory().getContainerSize(); i++) {
            ItemStack stack = targetPlayer.getInventory().getItem(i);
            if (DoctorCardItem.isDoctorCard(stack) &&
                    DoctorCardItem.isGenuine(stack) &&
                    DoctorCardItem.isOwnedBy(stack, targetPlayer)) {
                targetPlayer.getInventory().removeItem(i, stack.getCount());
                removed = true;
            }
        }

        if (removed) {
            source.sendSuccess(() -> Component.literal("プレイヤー " + targetPlayer.getName().getString() +
                    " の医師カードを無効化しました。").withStyle(ChatFormatting.YELLOW), true);
            targetPlayer.sendSystemMessage(Component.literal("あなたの医師カードが無効化されました。")
                    .withStyle(ChatFormatting.RED));
        } else {
            source.sendFailure(Component.literal("対象プレイヤーは有効な医師カードを所持していません。"));
        }

        return removed ? 1 : 0;
    }

    /**
     * プレイヤーの医師カード認証状態を確認
     */
    private static int checkCard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();

        MedicalAuthorizationUtil.MedicalCredentials credentials =
                MedicalAuthorizationUtil.getMedicalCredentials(targetPlayer);

        source.sendSuccess(() -> Component.literal("=== " + targetPlayer.getName().getString() + " の認証状態 ===")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);

        if (credentials != null && credentials.isValid()) {
            source.sendSuccess(() -> Component.literal("認証状態: 有効 ✓")
                    .withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> Component.literal("医師名: " + credentials.getDoctorName())
                    .withStyle(ChatFormatting.WHITE), false);
            source.sendSuccess(() -> Component.literal("免許番号: " + credentials.getLicenseNumber())
                    .withStyle(ChatFormatting.WHITE), false);
            source.sendSuccess(() -> Component.literal("発行日: " +
                            new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm").format(
                                    new java.util.Date(credentials.getIssueDate())))
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("認証状態: 無効 ✗")
                    .withStyle(ChatFormatting.RED), false);
            source.sendSuccess(() -> Component.literal("有効な医師カードを所持していません。")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }

    /**
     * テスト用の偽造カードを作成
     */
    private static int createFakeCard(CommandContext<CommandSourceStack> context) {
        String fakeName = StringArgumentType.getString(context, "fake_name");
        String licenseNumber = StringArgumentType.getString(context, "license_number");
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer executor = source.getPlayerOrException();
            ItemStack fakeCard = MedicalAuthorizationUtil.createFakeCardForTesting(fakeName, licenseNumber);

            if (!executor.getInventory().add(fakeCard)) {
                executor.drop(fakeCard, false);
            }

            source.sendSuccess(() -> Component.literal("偽造カード（名前: " + fakeName +
                            ", 免許番号: " + licenseNumber + "）を作成しました。")
                    .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("注意: これはテスト用の偽造カードです。")
                    .withStyle(ChatFormatting.RED), false);

            return 1;
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("プレイヤーのみ実行可能なコマンドです。"));
            return 0;
        }
    }

    /**
     * 全プレイヤーの認証状態をリスト表示
     */
    private static int listAllCards(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();

        source.sendSuccess(() -> Component.literal("=== 全プレイヤーの医療認証状態 ===")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);

        for (ServerPlayer player : players) {
            boolean hasValid = DoctorCardItem.hasValidDoctorCard(player);
            MedicalAuthorizationUtil.MedicalCredentials credentials =
                    MedicalAuthorizationUtil.getMedicalCredentials(player);

            if (hasValid && credentials != null) {
                source.sendSuccess(() -> Component.literal("✓ " + player.getName().getString() +
                                " (" + credentials.getLicenseNumber() + ")")
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.literal("✗ " + player.getName().getString() +
                        " (認証なし)").withStyle(ChatFormatting.RED), false);
            }
        }

        return 1;
    }
}