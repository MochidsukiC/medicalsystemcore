package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.PendingQteManager;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundStartQTEPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

public class ElectrodeItem extends Item {
    public static final String TAG_DEFIBRILLATOR_POS = "DefibrillatorPos";
    public static final String TAG_PADS_ID = "PadsId";

    public ElectrodeItem(Properties pProperties) {
        super(pProperties);
    }

    /** 電極が紐づいている除細動器の座標。NBT がなければ null。 */
    @Nullable
    public static BlockPos getLinkedPos(ItemStack electrode) {
        CompoundTag nbt = electrode.getTag();
        if (nbt == null || !nbt.contains(TAG_DEFIBRILLATOR_POS)) return null;
        return NbtUtils.readBlockPos(nbt.getCompound(TAG_DEFIBRILLATOR_POS));
    }

    /** 電極の識別子。識別子導入前に取り出された旧電極は null。 */
    @Nullable
    public static UUID getPadsId(ItemStack electrode) {
        CompoundTag nbt = electrode.getTag();
        return nbt != null && nbt.hasUUID(TAG_PADS_ID) ? nbt.getUUID(TAG_PADS_ID) : null;
    }

    /** この電極が、指定の除細動器が現在発行中の電極(座標と識別子が一致)か。 */
    public static boolean isLinkedTo(ItemStack electrode, DefibrillatorBlockEntity defibrillator) {
        return defibrillator.getBlockPos().equals(getLinkedPos(electrode))
                && defibrillator.isPadsLinked(getPadsId(electrode));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        // 医師カード認証チェック
        if (!MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "除細動器による治療")) {
            return InteractionResult.FAIL;
        }

        // このメソッドはサーバーサイドでのみ重要な処理を行う
        if (!pPlayer.level().isClientSide && pTarget instanceof ServerPlayer targetPlayer) {
            BlockPos defibrillatorPos = getLinkedPos(pStack);
            if (defibrillatorPos == null) {
                pPlayer.sendSystemMessage(Component.literal("§cこの電極はどの除細動器にも接続されていません。"));
                return InteractionResult.FAIL;
            }

            // 接続先が除細動器であり、この電極が現在発行中のものであることを確認する
            // (除細動器が撤去・置換された、電極が再発行で無効になった、別ディメンションの同座標、などは無効)
            if (!(pPlayer.level().getBlockEntity(defibrillatorPos) instanceof DefibrillatorBlockEntity defibrillator)
                    || !isLinkedTo(pStack, defibrillator)) {
                pPlayer.sendSystemMessage(Component.literal("§cこの電極は無効です。接続先の除細動器が見つからないか、再発行された古い電極です。"));
                return InteractionResult.FAIL;
            }

            // サーバー側で除細動器が充電完了しているかを検証
            if (defibrillator.getBlockState().getValue(DefibrillatorBlock.CHARGED)) {
                ServerPlayer healer = (ServerPlayer) pPlayer;
                // 右クリックを押し続けている間はこのメソッドが繰り返し呼ばれる。
                // 期限内の QTE が既にあるなら、開始時刻を上書きせず、開始パケットも再送しない(クライアントも進行中は無視する)
                if (!PendingQteManager.hasLive(healer)) {
                    // サーバーが QTE の開始を記録する。結果パケットはこの記録に対して 1 回だけ受け付ける
                    PendingQteManager.begin(healer, targetPlayer, defibrillatorPos, pHand);
                    // 検証成功！使用者のクライアントにQTE開始を命令する
                    ModPackets.sendToPlayer(new ClientboundStartQTEPacket(pTarget.getId()), healer);
                }
            } else {
                pPlayer.sendSystemMessage(Component.literal("§c除細動器のチャージが完了していません！"));
            }
        }
        return InteractionResult.CONSUME;
    }
}