package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientQTEManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundMedicalDataSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundStartQTEPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ElectrodeItem extends Item {
    public ElectrodeItem(Properties pProperties) {
        super(pProperties);
    }

    // isFoilメソッドは不要なので削除

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        // このメソッドはサーバーサイドでのみ重要な処理を行う
        if (!pPlayer.level().isClientSide && pTarget instanceof Player) {
            CompoundTag nbt = pStack.getTag();
            if (nbt == null || !nbt.contains("DefibrillatorPos")) return InteractionResult.FAIL;

            BlockPos defibrillatorPos = NbtUtils.readBlockPos(nbt.getCompound("DefibrillatorPos"));

            // サーバー側で除細動器が充電完了しているかを検証
            if (pPlayer.level().getBlockState(defibrillatorPos).getValue(DefibrillatorBlock.CHARGED)) {
                // 検証成功！使用者のクライアントにQTE開始を命令する
                ModPackets.sendToPlayer(new ClientboundStartQTEPacket(pTarget.getId()), (ServerPlayer) pPlayer);
            } else {
                pPlayer.sendSystemMessage(Component.literal("§c除細動器のチャージが完了していません！"));
            }
        }
        return InteractionResult.CONSUME;
    }
}