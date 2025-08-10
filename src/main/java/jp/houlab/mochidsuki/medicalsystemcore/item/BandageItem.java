package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientHealingManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class BandageItem extends Item {
    public BandageItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        // サーバーサイドでは何もしない
        if (pPlayer.level().isClientSide()) {
            // クライアント側で治療の開始を試みる
            ClientHealingManager.startHealing(pTarget.getId());
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * アイテムの使用が完了した時に呼ばれるメソッド (食事モデル)
     */
    @Override
    public ItemStack finishUsingItem(ItemStack pStack, Level pLevel, LivingEntity pLivingEntity) {
        if (!pLevel.isClientSide() && pLivingEntity instanceof Player player) {
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(data -> {
                if (data.getBleedingLevel() > 0) {
                    data.setBleedingLevel(data.getBleedingLevel() - 1);
                    pLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.5f);
                    // アイテムを1つ消費する
                    pStack.shrink(1);
                }
            });
        }
        return pStack;
    }

    /**
     * このアイテムを使い終えるのにかかる時間 (tick単位)
     * 6秒 (120 ticks) に設定
     */
    @Override
    public int getUseDuration(ItemStack pStack) {
        return 6 * 20; // 72000から変更
    }

    /**
     * 使用中のアニメーションを指定する
     */
    @Override
    public UseAnim getUseAnimation(ItemStack pStack) {
        return UseAnim.BRUSH;
    }

    /**
     * 何もないところで右クリックを押し始めた時に呼ばれる (変更なし)
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        pPlayer.startUsingItem(pHand);
        return InteractionResultHolder.consume(itemstack);
    }
}