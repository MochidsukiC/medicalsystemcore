package jp.houlab.mochidsuki.medicalsystemcore.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
     * 正規の医師カードを作成（ライセンス番号自動発行）
     */
    public static ItemStack createGenuineCard(Player player) {
        return createGenuineCard(player, generateLicenseNumber(player));
    }

    /**
     * 正規の医師カードを作成（ライセンス番号指定）
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
     */
    private static String generateLicenseNumber(Player player) {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
        String dateStr = dateFormat.format(new java.util.Date());
        String uuidSuffix = player.getUUID().toString().replaceAll("-", "").substring(28).toUpperCase();
        return "MC-" + dateStr + "-" + uuidSuffix;
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
        ItemStack mainHand = player.getMainHandItem();
        if (isDoctorCard(mainHand) && isGenuine(mainHand) && isOwnedBy(mainHand, player)) {
            return true;
        }

        ItemStack offHand = player.getOffhandItem();
        if (isDoctorCard(offHand) && isGenuine(offHand) && isOwnedBy(offHand, player)) {
            return true;
        }

        // インベントリ内をチェック
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isDoctorCard(stack) && isGenuine(stack) && isOwnedBy(stack, player)) {
                return true;
            }
        }

        return false;
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