package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.core.PoseController;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class StretcherBlockEntity extends BlockEntity {
    private UUID occupyingPlayerUUID = null;
    private ServerPlayer cachedOccupyingPlayer = null;

    public StretcherBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(Medicalsystemcore.STRETCHER_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StretcherBlockEntity be) {
        if (level.isClientSide()) return;

        if (be.occupyingPlayerUUID != null) {
            ServerPlayer player = be.getOccupyingPlayer();

            if (player != null) {
                // 修正: プレイヤーがストレッチャーの近くにいるかチェック
                double distanceToStretcher = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5);

                // プレイヤーが3ブロック以上離れている場合は徒歩で降りたと判断
                if (distanceToStretcher > 9.0) { // 3ブロック^2 = 9
                    // データをクリアして降車処理
                    PoseController.setStretcherPose(player, false);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7ストレッチャーから離れました。"));
                    be.clearOccupyingPlayer();
                    return;
                }

                // プレイヤーがShiftキーを押している場合の降車処理
                if (player.isShiftKeyDown()) {
                    // 意識不明の場合は降りられない
                    boolean canExit = player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                            .map(data -> data.isConscious())
                            .orElse(true);

                    if (canExit) {
                        // 一元姿勢管理システムを使用
                        PoseController.setStretcherPose(player, false);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eストレッチャーから降りました。"));
                        be.clearOccupyingPlayer();
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c意識不明のため担架から降りることができません。"));
                    }
                    return;
                }

                // プレイヤーの位置を担架の位置に固定し、向きも設定
                double targetX = pos.getX() + 0.5;
                double targetY = pos.getY() + 0.3;
                double targetZ = pos.getZ() + 0.5;

                if (player.distanceToSqr(targetX, targetY, targetZ) > 0.1) {
                    player.teleportTo(targetX, targetY, targetZ);
                }

                // 担架の向きに合わせてプレイヤーの体の向きを設定（顔の向きは変更しない）
                float blockYaw = state.getValue(jp.houlab.mochidsuki.medicalsystemcore.block.StretcherBlock.FACING).toYRot();
                player.yBodyRot = blockYaw;
                player.yBodyRotO = blockYaw;

                // 一元姿勢管理システムで姿勢制御を維持
                PoseController.maintainPoseControl(player);

            } else {
                // プレイヤーが見つからない場合はクリア
                be.clearOccupyingPlayer();
            }
        }
    }

    public void setOccupyingPlayer(@Nullable ServerPlayer player) {
        // 前のプレイヤーの姿勢制御を解除
        if (this.cachedOccupyingPlayer != null && this.cachedOccupyingPlayer != player) {
            PoseController.setStretcherPose(this.cachedOccupyingPlayer, false);
        }

        if (player != null) {
            this.occupyingPlayerUUID = player.getUUID();
            this.cachedOccupyingPlayer = player;

            // 一元姿勢管理システムで姿勢制御を開始
            PoseController.setStretcherPose(player, true);
        } else {
            clearOccupyingPlayer();
        }

        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 乗車プレイヤーデータを完全にクリア（新規追加）
     */
    private void clearOccupyingPlayer() {
        this.occupyingPlayerUUID = null;
        this.cachedOccupyingPlayer = null;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    public ServerPlayer getOccupyingPlayer() {
        if (this.occupyingPlayerUUID == null) {
            return null;
        }

        // キャッシュされたプレイヤーが有効かチェック
        if (this.cachedOccupyingPlayer != null &&
                this.cachedOccupyingPlayer.getUUID().equals(this.occupyingPlayerUUID) &&
                this.cachedOccupyingPlayer.isAlive()) {

            // 修正: プレイヤーが実際にストレッチャーの近くにいるかもチェック
            double distance = this.cachedOccupyingPlayer.distanceToSqr(
                    getBlockPos().getX() + 0.5,
                    getBlockPos().getY() + 0.3,
                    getBlockPos().getZ() + 0.5
            );

            // 距離が遠すぎる場合は無効とみなす
            if (distance > 9.0) { // 3ブロック^2
                clearOccupyingPlayer();
                return null;
            }

            return this.cachedOccupyingPlayer;
        }

        // プレイヤーを再検索
        if (level instanceof ServerLevel serverLevel) {
            this.cachedOccupyingPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(this.occupyingPlayerUUID);

            // 復元時に姿勢制御を再適用（距離チェック付き）
            if (this.cachedOccupyingPlayer != null) {
                double distance = this.cachedOccupyingPlayer.distanceToSqr(
                        getBlockPos().getX() + 0.5,
                        getBlockPos().getY() + 0.3,
                        getBlockPos().getZ() + 0.5
                );

                if (distance <= 9.0) { // 3ブロック以内の場合のみ有効
                    PoseController.setStretcherPose(this.cachedOccupyingPlayer, true);
                    return this.cachedOccupyingPlayer;
                } else {
                    // 距離が遠い場合はデータをクリア
                    clearOccupyingPlayer();
                    return null;
                }
            }
        }

        // プレイヤーが見つからない場合はデータをクリア
        clearOccupyingPlayer();
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        if (this.occupyingPlayerUUID != null) {
            pTag.putUUID("OccupyingPlayer", this.occupyingPlayerUUID);
        }
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        if (pTag.hasUUID("OccupyingPlayer")) {
            this.occupyingPlayerUUID = pTag.getUUID("OccupyingPlayer");
        } else {
            this.occupyingPlayerUUID = null;
        }
        this.cachedOccupyingPlayer = null; // キャッシュをクリア
    }

    @Override
    public void setRemoved() {
        // ブロックエンティティ削除時に姿勢制御を解除
        if (this.cachedOccupyingPlayer != null) {
            PoseController.setStretcherPose(this.cachedOccupyingPlayer, false);
        }
        super.setRemoved();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }
}