package jp.houlab.mochidsuki.medicalsystemcore.item;

import jp.houlab.mochidsuki.medicalsystemcore.block.StretcherBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager;
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

        // 対象がプレイヤーの場合のみ処理
        if (!(pTarget instanceof ServerPlayer targetPlayer)) {
            return InteractionResult.PASS;
        }

        // 既に担架エンティティが存在する場合は何もしない
        if (pPlayer.level().getEntitiesOfClass(StretcherEntity.class,
                        pPlayer.getBoundingBox().inflate(10.0))
                .stream()
                .anyMatch(stretcher -> stretcher.getCarryingPlayer() != null &&
                        stretcher.getCarryingPlayer().getUUID().equals(targetPlayer.getUUID()))) {
            pPlayer.sendSystemMessage(Component.literal("§cこのプレイヤーは既に担架に乗っています。"));
            return InteractionResult.FAIL;
        }

        // 担架エンティティを作成
        StretcherEntity stretcherEntity = new StretcherEntity(Medicalsystemcore.STRETCHER_ENTITY.get(), pPlayer.level());

        // 担架の位置をプレイヤーの腰の前に設定（水平回転のみ使用）
        Vec3 playerPos = pPlayer.position();
        float yaw = pPlayer.getYRot();
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        Vec3 stretcherPos = playerPos.add(lookX * 1.0, 0.0, lookZ * 1.0);

        stretcherEntity.setPos(stretcherPos.x, stretcherPos.y, stretcherPos.z);
        stretcherEntity.setYRot(yaw);
        stretcherEntity.setCarriedByPlayer(pPlayer);
        stretcherEntity.setCarryingPlayer(targetPlayer);

        // 対象プレイヤーを担架に乗せる
        targetPlayer.startRiding(stretcherEntity);

        // 体の向きを確実に担架と同じにする
        targetPlayer.setYRot(yaw);
        targetPlayer.setXRot(0);
        targetPlayer.yBodyRot = yaw;
        targetPlayer.yBodyRotO = yaw;
        targetPlayer.setYHeadRot(yaw);
        targetPlayer.xRotO = 0;

        // 担架の上では必ずSLEEPING姿勢
        targetPlayer.setPose(net.minecraft.world.entity.Pose.SLEEPING);
        targetPlayer.setForcedPose(net.minecraft.world.entity.Pose.SLEEPING);

        // 姿勢変更を強制的に同期
        targetPlayer.refreshDimensions();
        if (targetPlayer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // 周囲のプレイヤーに更新を送信
            serverLevel.getChunkSource().broadcast(targetPlayer,
                    new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                            targetPlayer.getId(),
                            targetPlayer.getEntityData().packDirty()
                    )
            );

            // さらに位置と回転の同期も強制
            serverLevel.getChunkSource().broadcast(targetPlayer,
                    new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(targetPlayer)
            );
        }

        pPlayer.level().addFreshEntity(stretcherEntity);
        pStack.shrink(1);

        pPlayer.sendSystemMessage(Component.literal("§a" + targetPlayer.getName().getString() + "を担架に乗せました。"));
        targetPlayer.sendSystemMessage(Component.literal("§e担架に乗せられました。Shiftキーで降りることができます。"));

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

            // ブロックエンティティにプレイヤー情報を設定
            if (level.getBlockEntity(placePos) instanceof StretcherBlockEntity stretcherBE) {
                stretcherBE.setOccupyingPlayer(ridingPlayer);

                // プレイヤーを担架ブロックの位置に移動
                ridingPlayer.stopRiding();
                ridingPlayer.teleportTo(placePos.getX() + 0.5, placePos.getY() + 0.3, placePos.getZ() + 0.5);
                ridingPlayer.setPose(net.minecraft.world.entity.Pose.SLEEPING);

                ridingPlayer.sendSystemMessage(Component.literal("§e担架が設置されました。"));
            }

            // エンティティを削除
            carriedStretcher.discard();
        }

        stack.shrink(1);
        player.sendSystemMessage(Component.literal("§a担架を設置しました。"));

        return InteractionResult.SUCCESS;
    }
}