package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
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
     * 自分への使用が完了した時に呼ばれるメソッド（Config値使用版）
     */
    @Override
    public ItemStack finishUsingItem(ItemStack pStack, Level pLevel, LivingEntity pLivingEntity) {
        if (pLivingEntity instanceof Player player) {
            // Config値を使用して自分にエフェクトを付与
            applyBandageEffect(player, Config.BANDAGE_SELF_LEVEL_INCREASE);
        }
        return super.finishUsingItem(pStack, pLevel, pLivingEntity);
    }

    /**
     * 包帯エフェクトをスタックさせるロジック（Config値使用版）
     * @param target 対象プレイヤー
     * @param levelToAdd 付与するエフェクトレベル
     */
    private void applyBandageEffect(Player target, int levelToAdd) {
        MobEffectInstance existingEffect = target.getEffect(Medicalsystemcore.BANDAGE_EFFECT.get());
        int currentLevel = 0;
        if (existingEffect != null) {
            currentLevel = existingEffect.getAmplifier() + 1;
        }

        // Config値を使用した最大レベル制限
        int newLevel = Math.min(Config.BANDAGE_MAX_LEVEL, currentLevel + levelToAdd);

        // Config値を使用した持続時間
        int duration = Config.BANDAGE_EFFECT_DURATION * 20; // 秒からtickに変換

        target.addEffect(new MobEffectInstance(
                Medicalsystemcore.BANDAGE_EFFECT.get(),
                duration,
                newLevel - 1, // amplifierは0から始まるため-1
                true,
                true
        ));
    }

    /**
     * このアイテムを使い終えるのにかかる時間（Config値使用版）
     */
    @Override
    public int getUseDuration(ItemStack pStack) {
        return Config.BANDAGE_SELF_USE_DURATION * 20; // 秒からtickに変換
    }

    /**
     * 使用中のアニメーションを指定する
     */
    @Override
    public UseAnim getUseAnimation(ItemStack pStack) {
        return UseAnim.BRUSH;
    }

    /**
     * 何もないところで右クリックを押し始めた時に呼ばれる
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        pPlayer.startUsingItem(pHand);
        return InteractionResultHolder.consume(itemstack);
    }

}