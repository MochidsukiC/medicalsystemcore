package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.StretcherBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.util.MedicalAuthorizationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class StretcherItem extends Item {
    public StretcherItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        if (pPlayer.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!MedicalAuthorizationUtil.checkMedicalAuthorization(pPlayer, "ストレッチャーの使用")) {
            return InteractionResult.FAIL;
        }

        if (!(pTarget instanceof ServerPlayer targetPlayer)) {
            return InteractionResult.PASS;
        }

        // 基本的なチェック
        if (targetPlayer.isPassenger()) {
            pPlayer.sendSystemMessage(Component.literal("§cプレイヤーは既に何かに乗っています。"));
            return InteractionResult.FAIL;
        }

        // プレイヤーが既にストレッチャーエンティティを持っているかチェック
        StretcherEntity existingStretcher = pPlayer.level().getEntitiesOfClass(StretcherEntity.class,
                        pPlayer.getBoundingBox().inflate(10.0))
                .stream()
                .filter(stretcher -> stretcher.getCarrier() != null &&
                        stretcher.getCarrier().getUUID().equals(pPlayer.getUUID()))
                .findFirst()
                .orElse(null);

        if (existingStretcher != null) {
            pPlayer.sendSystemMessage(Component.literal("§c既にストレッチャーを所持しています。"));
            return InteractionResult.FAIL;
        }

        // 新仕様: ストレッチャーエンティティを作成し、アイテムを消費
        StretcherEntity stretcherEntity = StretcherEntity.create(
                pPlayer.level(), pPlayer, targetPlayer
        );

        // アイテムを消費（新仕様）
        pStack.shrink(1);

        pPlayer.sendSystemMessage(Component.literal(String.format(
                "§a%sを担架に乗せました。",
                targetPlayer.getName().getString()
        )));
        targetPlayer.sendSystemMessage(Component.literal("§e担架に乗せられました。"));

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        BlockPos pos = pContext.getClickedPos();
        Player player = pContext.getPlayer();
        ItemStack stack = pContext.getItemInHand();

        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        // 新仕様: ストレッチャーを持っている + 素手 + しゃがみ + 地面右クリックでブロック設置
        // まず、プレイヤーがストレッチャーエンティティを持っているかチェック
        StretcherEntity carriedStretcher = level.getEntitiesOfClass(StretcherEntity.class,
                        player.getBoundingBox().inflate(10.0))
                .stream()
                .filter(stretcher -> stretcher.getCarrier() != null &&
                        stretcher.getCarrier().getUUID().equals(player.getUUID()))
                .findFirst()
                .orElse(null);

        // 新仕様の条件チェック: ストレッチャー持ち + 素手 + しゃがみ
        if (carriedStretcher != null && player.getMainHandItem().isEmpty() && player.isShiftKeyDown()) {
            // クリックしたブロックの上に担架を設置（足パーツと、向いている方向の頭パーツの 2 ブロック分）
            BlockPos placePos = pos.above();
            Direction playerDirection = player.getDirection();
            BlockPos headPos = placePos.relative(playerDirection);

            // 設置可能かチェック（足と頭の両方）
            if (!level.getBlockState(placePos).canBeReplaced() || !level.getBlockState(headPos).canBeReplaced()) {
                player.sendSystemMessage(Component.translatable("message.medicalsystemcore.stretcher.cannot_place").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }

            // 担架ブロックを 2 ブロック分設置
            BlockState stretcherState = Medicalsystemcore.STRETCHER_BLOCK.get().defaultBlockState()
                    .setValue(StretcherBlock.FACING, playerDirection);
            StretcherBlock.placeDoubleBlock(level, placePos, stretcherState);

            // 足パーツのブロックエンティティを初期化し、患者が乗っていれば担架に移す
            if (level.getBlockEntity(placePos) instanceof StretcherBlockEntity stretcherBE) {
                stretcherBE.setBeingCollected(false);

                ServerPlayer ridingPlayer = carriedStretcher.getPassenger() instanceof ServerPlayer sp ? sp : null;
                if (ridingPlayer != null) {
                    ridingPlayer.stopRiding();
                    ridingPlayer.teleportTo(placePos.getX() + 0.5, placePos.getY() + 0.3, placePos.getZ() + 0.5);

                    // プレイヤーを担架に乗せる
                    stretcherBE.setOccupyingPlayer(ridingPlayer);

                    ridingPlayer.sendSystemMessage(Component.literal("§e担架が設置されました。"));
                }
            }

            // エンティティを削除（アイテムは返還しない）
            carriedStretcher.discard();
            player.sendSystemMessage(Component.literal("§a担架を設置しました。"));
            return InteractionResult.SUCCESS;
        }

        // 新仕様では通常のアイテムからのブロック設置は無効
        return InteractionResult.PASS;
    }
}