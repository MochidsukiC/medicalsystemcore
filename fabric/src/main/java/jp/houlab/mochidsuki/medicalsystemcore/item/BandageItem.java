package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientHealingHook;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BandageRules;
import jp.houlab.mochidsuki.medicalsystemcore.core.PendingHealManager;
import jp.houlab.mochidsuki.medicalsystemcore.effect.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class BandageItem extends Item {

    public BandageItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        if (pPlayer.level().isClientSide()) {
            // クライアント側で治療の開始を試みる(client の処理は main の受け口経由で呼ぶ)
            ClientHealingHook.startHealing(pTarget.getId());
        } else if (pHand == InteractionHand.MAIN_HAND && pPlayer instanceof ServerPlayer healer && pTarget instanceof ServerPlayer target) {
            // サーバーが治療の開始を記録する。完了パケットは経過時間をこの記録と照合して受け付ける
            PendingHealManager.begin(healer, target);
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
            applyBandageEffect(player, MedicalConfig.get(MedicalConfigKeys.BANDAGE_SELF_LEVEL_INCREASE));
        }
        // バニラの食料と同じく、クリエイティブでは消費しない
        if (!(pLivingEntity instanceof Player player && player.getAbilities().instabuild)) {
            pStack.shrink(1);
        }
        return super.finishUsingItem(pStack, pLevel, pLivingEntity);
    }

    /**
     * 包帯エフェクトをスタックさせるロジック（Config値使用版）
     * @param target 対象プレイヤー
     * @param levelToAdd 付与するエフェクトレベル
     */
    private void applyBandageEffect(Player target, int levelToAdd) {
        MobEffectInstance existingEffect = target.getEffect(ModEffects.BANDAGE_EFFECT);
        int currentLevel = 0;
        if (existingEffect != null) {
            currentLevel = BandageRules.levelFromAmplifier(existingEffect.getAmplifier());
        }

        int newLevel = BandageRules.nextLevel(currentLevel, levelToAdd, MedicalConfig.get(MedicalConfigKeys.BANDAGE_MAX_LEVEL));
        int duration = BandageRules.durationTicks(MedicalConfig.get(MedicalConfigKeys.BANDAGE_EFFECT_DURATION));

        target.addEffect(new MobEffectInstance(
                ModEffects.BANDAGE_EFFECT,
                duration,
                BandageRules.amplifierForLevel(newLevel),
                true,
                true
        ));
    }

    /**
     * このアイテムを使い終えるのにかかる時間（Config値使用版）
     */
    @Override
    public int getUseDuration(ItemStack pStack, LivingEntity pUser) {
        return BandageRules.durationTicks(MedicalConfig.get(MedicalConfigKeys.BANDAGE_SELF_USE_DURATION));
    }

    /**
     * 使用中のアニメーションを指定する
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack pStack) {
        return ItemUseAnimation.BRUSH;
    }

    /**
     * 何もないところで右クリックを押し始めた時に呼ばれる
     */
    @Override
    public InteractionResult use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        pPlayer.startUsingItem(pHand);
        return InteractionResult.CONSUME;
    }

}
