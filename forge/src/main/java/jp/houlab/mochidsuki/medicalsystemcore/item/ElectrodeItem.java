package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
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

public class ElectrodeItem extends Item {
    public ElectrodeItem(Properties pProperties) {
        super(pProperties);
    }

    // isFoilメソッドは不要なので削除

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        // 医師カード認証チェック
        if (!MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "除細動器による治療")) {
            return InteractionResult.FAIL;
        }

        // このメソッドはサーバーサイドでのみ重要な処理を行う
        if (!pPlayer.level().isClientSide && pTarget instanceof ServerPlayer targetPlayer) {
            CompoundTag nbt = pStack.getTag();
            if (nbt == null || !nbt.contains("DefibrillatorPos")) return InteractionResult.FAIL;

            BlockPos defibrillatorPos = NbtUtils.readBlockPos(nbt.getCompound("DefibrillatorPos"));

            // サーバー側で除細動器が充電完了しているかを検証
            if (pPlayer.level().getBlockState(defibrillatorPos).getValue(DefibrillatorBlock.CHARGED)) {
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