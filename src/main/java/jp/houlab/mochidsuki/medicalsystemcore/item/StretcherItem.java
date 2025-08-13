package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.StretcherBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.PoseController;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;

public class StretcherItem extends Item {
    public StretcherItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pTarget, InteractionHand pHand) {
        if (pPlayer.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(pTarget instanceof ServerPlayer targetPlayer)) {
            return InteractionResult.PASS;
        }

        // 既存チェック省略...

        // *** 修正された位置管理を使用 ***
        StretcherEntity stretcherEntity = StretcherEntity.createAndPosition(
                pPlayer.level(), pPlayer, targetPlayer
        );

        // 対象プレイヤーを担架に乗せる
        targetPlayer.startRiding(stretcherEntity);

        // 正しい体の向きを設定（C° = B°）
        float stretcherYaw = stretcherEntity.getYRot();  // B°
        float playerBodyYaw = StretcherEntity.calculatePlayerBodyYaw(stretcherYaw);  // C° = B°

        targetPlayer.setYRot(playerBodyYaw);
        targetPlayer.setXRot(0);
        targetPlayer.yBodyRot = playerBodyYaw;
        targetPlayer.yBodyRotO = playerBodyYaw;
        targetPlayer.setYHeadRot(playerBodyYaw);
        targetPlayer.xRotO = 0;

        stretcherEntity.setCarryingPlayer(targetPlayer);

        pPlayer.level().addFreshEntity(stretcherEntity);
        pStack.shrink(1);

        // デバッグ情報付きメッセージ
        float carrierYaw = pPlayer.getYRot();  // A°
        pPlayer.sendSystemMessage(Component.literal(String.format(
                "§a%sを担架に乗せました。角度: A=%.1f°, B=%.1f°, C=%.1f°",
                targetPlayer.getName().getString(), carrierYaw, stretcherYaw, playerBodyYaw
        )));
        targetPlayer.sendSystemMessage(Component.literal("§e担架に乗せられました。正しい角度で配置されています。"));

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

        // クリックしたブロックの上に担架を設置
        BlockPos placePos = pos.above();

        // 設置可能かチェック
        if (!level.getBlockState(placePos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        // 担架エンティティがあるかチェック（プレイヤーが担架を持っている場合）
        StretcherEntity carriedStretcher = level.getEntitiesOfClass(StretcherEntity.class,
                        player.getBoundingBox().inflate(10.0))
                .stream()
                .filter(stretcher -> stretcher.getCarriedByPlayer() != null &&
                        stretcher.getCarriedByPlayer().getUUID().equals(player.getUUID()))
                .findFirst()
                .orElse(null);

        // 担架ブロックを設置
        BlockState stretcherState = Medicalsystemcore.STRETCHER_BLOCK.get().defaultBlockState()
                .setValue(StretcherBlock.FACING, player.getDirection());
        level.setBlock(placePos, stretcherState, 3);

        // プレイヤーが乗っていた場合の処理
        if (carriedStretcher != null && carriedStretcher.getCarryingPlayer() != null) {
            ServerPlayer ridingPlayer = carriedStretcher.getCarryingPlayer();

            // ブロックエンティティにプレイヤー情報を設定（一元姿勢管理システムが自動的に処理）
            if (level.getBlockEntity(placePos) instanceof StretcherBlockEntity stretcherBE) {
                ridingPlayer.stopRiding();
                ridingPlayer.teleportTo(placePos.getX() + 0.5, placePos.getY() + 0.3, placePos.getZ() + 0.5);

                // *** 一元姿勢管理システムを使用（setOccupyingPlayerで自動的に設定される） ***
                stretcherBE.setOccupyingPlayer(ridingPlayer);

                ridingPlayer.sendSystemMessage(Component.literal("§e担架が設置されました。"));
            }

            // エンティティを削除（姿勢制御の解除は自動的に処理される）
            carriedStretcher.discard();
        }

        stack.shrink(1);
        player.sendSystemMessage(Component.literal("§a担架を設置しました。"));

        return InteractionResult.SUCCESS;
    }
}