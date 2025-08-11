package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientHealingManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class BandageItem extends Item {
    private static final int MAX_EFFECT_LEVEL = 6;


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
     * 自分への使用が完了した時に呼ばれるメソッド
     */
    @Override
    public ItemStack finishUsingItem(ItemStack pStack, Level pLevel, LivingEntity pLivingEntity) {
        if (pLivingEntity instanceof Player player) {
            // ▼▼▼ この行を変更 ▼▼▼
            applyBandageEffect(player, 1); // 自分にはレベルIを付与
        }
        // アイテム消費は自動で行われるのでshrinkは不要
        return super.finishUsingItem(pStack, pLevel, pLivingEntity);
    }

    /**
     * 包帯エフェクトをスタックさせるロジック
     * @param target 対象プレイヤー
     * @param levelToAdd 付与するエフェクトレベル
     */
    private void applyBandageEffect(Player target, int levelToAdd) {
        MobEffectInstance existingEffect = target.getEffect(Medicalsystemcore.BANDAGE_EFFECT.get());
        int currentLevel = 0;
        if (existingEffect != null) {
            currentLevel = existingEffect.getAmplifier() + 1;
        }
        int newLevel = Math.min(MAX_EFFECT_LEVEL, currentLevel + levelToAdd);
        target.addEffect(new MobEffectInstance(Medicalsystemcore.BANDAGE_EFFECT.get(), 60 * 20, newLevel - 1, true, true));
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