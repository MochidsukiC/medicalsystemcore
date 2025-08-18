package jp.houlab.mochidsuki.medicalsystemcore.util;

import jp.houlab.mochidsuki.medicalsystemcore.item.DoctorCardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 医療活動の認可をチェックするユーティリティクラス
 */
public class MedicalAuthorizationUtil {

    /**
     * プレイヤーが医療活動を実行する権限があるかチェック
     * @param player チェック対象のプレイヤー
     * @param activityName 実行しようとしている医療活動の名前（エラーメッセージ用）
     * @return 権限がある場合true、ない場合false
     */
    public static boolean checkMedicalAuthorization(Player player, String activityName) {
        if (DoctorCardItem.hasValidDoctorCard(player)) {
            return true;
        }

        // 権限がない場合のエラーメッセージ
        player.sendSystemMessage(Component.literal("医療活動権限エラー")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(activityName + "を実行するには有効な医師カードが必要です。")
                .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("正規の医師カードをインベントリに所持してください。")
                .withStyle(ChatFormatting.GRAY));

        return false;
    }

    /**
     * プレイヤーが医療活動を実行する権限があるかチェック（サイレント版）
     * エラーメッセージを表示しない
     */
    public static boolean checkMedicalAuthorizationSilent(Player player) {
        return DoctorCardItem.hasValidDoctorCard(player);
    }

    /**
     * プレイヤーの医療資格情報を取得
     * デバッグや管理用途
     */
    public static MedicalCredentials getMedicalCredentials(Player player) {
        // メインハンド、オフハンド、インベントリの順でチェック
        ItemStack validCard = null;

        ItemStack mainHand = player.getMainHandItem();
        if (DoctorCardItem.isDoctorCard(mainHand) && DoctorCardItem.isGenuine(mainHand) && DoctorCardItem.isOwnedBy(mainHand, player)) {
            validCard = mainHand;
        } else {
            ItemStack offHand = player.getOffhandItem();
            if (DoctorCardItem.isDoctorCard(offHand) && DoctorCardItem.isGenuine(offHand) && DoctorCardItem.isOwnedBy(offHand, player)) {
                validCard = offHand;
            } else {
                // インベントリ内をチェック
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (DoctorCardItem.isDoctorCard(stack) && DoctorCardItem.isGenuine(stack) && DoctorCardItem.isOwnedBy(stack, player)) {
                        validCard = stack;
                        break;
                    }
                }
            }
        }

        if (validCard != null) {
            return new MedicalCredentials(validCard);
        }

        return null;
    }

    /**
     * 医療資格情報を格納するクラス
     */
    public static class MedicalCredentials {
        private final String doctorName;
        private final String licenseNumber;
        private final long issueDate;
        private final boolean isValid;

        public MedicalCredentials(ItemStack cardStack) {
            if (cardStack.hasTag()) {
                this.doctorName = cardStack.getTag().getString(DoctorCardItem.TAG_DOCTOR_NAME);
                this.licenseNumber = cardStack.getTag().getString(DoctorCardItem.TAG_LICENSE_NUMBER);
                this.issueDate = cardStack.getTag().getLong(DoctorCardItem.TAG_ISSUE_DATE);
                this.isValid = DoctorCardItem.isGenuine(cardStack);
            } else {
                this.doctorName = "";
                this.licenseNumber = "";
                this.issueDate = 0;
                this.isValid = false;
            }
        }

        public String getDoctorName() { return doctorName; }
        public String getLicenseNumber() { return licenseNumber; }
        public long getIssueDate() { return issueDate; }
        public boolean isValid() { return isValid; }
    }

    /**
     * 管理者用：プレイヤーに正規の医師カードを付与（ライセンス番号自動発行）
     */
    public static void grantDoctorCard(Player player) {
        ItemStack doctorCard = DoctorCardItem.createGenuineCard(player);
        if (!player.getInventory().add(doctorCard)) {
            // インベントリがフルの場合はドロップ
            player.drop(doctorCard, false);
        }

        player.sendSystemMessage(Component.literal("医師カードが付与されました。")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * 管理者用：プレイヤーに正規の医師カードを付与（ライセンス番号指定）
     */
    public static void grantDoctorCard(Player player, String licenseNumber) {
        ItemStack doctorCard = DoctorCardItem.createGenuineCard(player, licenseNumber);
        if (!player.getInventory().add(doctorCard)) {
            // インベントリがフルの場合はドロップ
            player.drop(doctorCard, false);
        }

        player.sendSystemMessage(Component.literal("医師カードが付与されました。")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * 管理者用：偽造カードを作成（テスト用途）
     */
    public static ItemStack createFakeCardForTesting(String doctorName, String licenseNumber) {
        return DoctorCardItem.createFakeCard(doctorName, licenseNumber);
    }
}