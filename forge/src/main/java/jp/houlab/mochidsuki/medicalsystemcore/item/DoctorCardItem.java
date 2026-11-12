package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientRevokedLicenses;
import jp.houlab.mochidsuki.medicalsystemcore.core.IssuedLicenseSavedData;
import jp.houlab.mochidsuki.medicalsystemcore.core.RevokedLicenseSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 医師カード・偽造医師カードアイテム
 * NBTタグで正規/偽造を判別し、正規カードは所有者UUIDを保持する
 */
public class DoctorCardItem extends Item {

    // NBTタグキー
    public static final String TAG_IS_GENUINE = "isGenuine";
    public static final String TAG_OWNER_UUID = "ownerUUID";
    public static final String TAG_DOCTOR_NAME = "doctorName";
    public static final String TAG_LICENSE_NUMBER = "licenseNumber";
    public static final String TAG_ISSUE_DATE = "issueDate";

    public DoctorCardItem(Properties properties) {
        super(properties);
    }

    /**
     * 正規の医師カードを作成（ライセンス番号指定）。
     * 自動採番は generateUniqueLicenseNumber を使う。発行の記録は MedicalAuthorizationUtil.grantDoctorCard が行う。
     */
    public static ItemStack createGenuineCard(Player player, String licenseNumber) {
        ItemStack stack = new ItemStack(jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore.DOCTOR_CARD.get());
        CompoundTag nbt = stack.getOrCreateTag();

        nbt.putBoolean(TAG_IS_GENUINE, true);
        nbt.putString(TAG_OWNER_UUID, player.getUUID().toString());
        nbt.putString(TAG_DOCTOR_NAME, player.getName().getString());
        nbt.putString(TAG_LICENSE_NUMBER, licenseNumber);
        nbt.putLong(TAG_ISSUE_DATE, System.currentTimeMillis());

        return stack;
    }

    /**
     * ライセンス番号を自動生成
     * 形式: MC-YYYYMMDD-XXXX (MCは接頭辞、日付、プレイヤーUUIDの末尾4桁)
     * 基本形は「日付 + UUID 末尾」で決まるため、同じ人に同日に再発行すると衝突する。
     * 発行台帳か失効リストに既にある番号なら、末尾に -2, -3, ... を付けて一意にする(基本形の規則は変えていない)。
     */
    public static String generateUniqueLicenseNumber(ServerPlayer player) {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
        String dateStr = dateFormat.format(new java.util.Date());
        String uuidSuffix = player.getUUID().toString().replaceAll("-", "").substring(28).toUpperCase();
        String base = "MC-" + dateStr + "-" + uuidSuffix;

        IssuedLicenseSavedData issued = IssuedLicenseSavedData.get(player.server);
        RevokedLicenseSavedData revoked = RevokedLicenseSavedData.get(player.server);
        String candidate = base;
        for (int sequence = 2; issued.isIssued(candidate) || revoked.isRevoked(candidate); sequence++) {
            candidate = base + "-" + sequence;
        }
        return candidate;
    }

    /**
     * 偽造医師カードを作成
     */
    public static ItemStack createFakeCard(String doctorName, String licenseNumber) {
        ItemStack stack = new ItemStack(jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore.DOCTOR_CARD.get());
        CompoundTag nbt = stack.getOrCreateTag();

        nbt.putBoolean(TAG_IS_GENUINE, false);
        nbt.putString(TAG_DOCTOR_NAME, doctorName);
        nbt.putString(TAG_LICENSE_NUMBER, licenseNumber);
        nbt.putLong(TAG_ISSUE_DATE, System.currentTimeMillis());

        return stack;
    }

    /**
     * カードが正規かどうかをチェック
     */
    public static boolean isGenuine(ItemStack stack) {
        if (stack.isEmpty() || !isDoctorCard(stack)) {
            return false;
        }
        CompoundTag nbt = stack.getTag();
        return nbt != null && nbt.getBoolean(TAG_IS_GENUINE);
    }

    /**
     * カードが医師カードかどうかをチェック
     */
    public static boolean isDoctorCard(ItemStack stack) {
        return stack.getItem() instanceof DoctorCardItem;
    }

    /**
     * カードが指定したプレイヤーの所有物かどうかをチェック
     */
    public static boolean isOwnedBy(ItemStack stack, Player player) {
        if (!isGenuine(stack)) {
            return false;
        }

        CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains(TAG_OWNER_UUID)) {
            return false;
        }

        try {
            UUID ownerUUID = UUID.fromString(nbt.getString(TAG_OWNER_UUID));
            return ownerUUID.equals(player.getUUID());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * プレイヤーが有効な医師カードを所持しているかチェック
     */
    public static boolean hasValidDoctorCard(Player player) {
        // メインハンド、オフハンド、インベントリの順でチェック
        if (isValidCardFor(player.getMainHandItem(), player)) {
            return true;
        }

        if (isValidCardFor(player.getOffhandItem(), player)) {
            return true;
        }

        // インベントリ内をチェック
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isValidCardFor(player.getInventory().getItem(i), player)) {
                return true;
            }
        }

        return false;
    }

    /**
     * このスタックが、そのプレイヤーの権限として使える医師カードかどうか。
     * 正規で、所有者が本人で、免許番号が失効していないこと。権限判定はすべてここを通る
     * (hasValidDoctorCard と MedicalAuthorizationUtil.getMedicalCredentials)。
     */
    public static boolean isValidCardFor(ItemStack stack, Player player) {
        return isDoctorCard(stack) && isGenuine(stack) && isOwnedBy(stack, player) && !isRevoked(stack, player);
    }

    /** カードの免許番号。タグが無ければ空文字。 */
    public static String getLicenseNumber(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt == null ? "" : nbt.getString(TAG_LICENSE_NUMBER);
    }

    /**
     * カードの免許番号が失効リストにあるか。
     * サーバーではワールドのセーブデータを、クライアントではサーバーから同期された写しを参照する。
     */
    private static boolean isRevoked(ItemStack stack, Player player) {
        String license = getLicenseNumber(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            return RevokedLicenseSavedData.get(serverPlayer.server).isRevoked(license);
        }
        return ClientRevokedLicenses.isRevoked(license);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null) return;

        if (nbt.contains(TAG_DOCTOR_NAME)) {
            tooltip.add(Component.literal("医師名: " + nbt.getString(TAG_DOCTOR_NAME))
                    .withStyle(ChatFormatting.BLUE));
        }

        if (nbt.contains(TAG_LICENSE_NUMBER)) {
            tooltip.add(Component.literal("免許番号: " + nbt.getString(TAG_LICENSE_NUMBER))
                    .withStyle(ChatFormatting.GRAY));

            // 失効済みの正規カードには赤字で「失効」を出す(失効リストはサーバーから同期された写し)
            if (isGenuine(stack) && ClientRevokedLicenses.isRevoked(nbt.getString(TAG_LICENSE_NUMBER))) {
                tooltip.add(Component.translatable("tooltip.medicalsystemcore.doctor_card.revoked")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
        }

        if (nbt.contains(TAG_ISSUE_DATE)) {
            long issueDate = nbt.getLong(TAG_ISSUE_DATE);
            tooltip.add(Component.literal("発行日: " + new java.text.SimpleDateFormat("yyyy/MM/dd").format(new java.util.Date(issueDate)))
                    .withStyle(ChatFormatting.GRAY));
        }

        // デバッグ用（通常時は表示されない）
        if (flag.isAdvanced() && nbt.contains(TAG_IS_GENUINE)) {
            tooltip.add(Component.literal(nbt.getBoolean(TAG_IS_GENUINE) ? "正規" : "偽造")
                    .withStyle(nbt.getBoolean(TAG_IS_GENUINE) ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        tooltip.add(Component.literal("医療活動に必要")
                .withStyle(ChatFormatting.YELLOW));
    }
}