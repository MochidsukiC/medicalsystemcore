package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
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
                // プレイヤーがShiftキーを押している場合の降車処理
                if (player.isShiftKeyDown()) {
                    // 意識不明の場合は降りられない
                    boolean canExit = player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                            .map(data -> data.isConscious())
                            .orElse(true);

                    if (canExit) {
                        player.setPose(Pose.STANDING);
                        player.setForcedPose(null); // 強制姿勢を解除
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e担架から降りました。"));
                        be.setOccupyingPlayer(null);
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c意識不明のため担架から降りることができません。"));
                    }
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

                // 特別な姿勢は設定しない

            } else {
                // プレイヤーが見つからない場合はクリア
                be.occupyingPlayerUUID = null;
                be.cachedOccupyingPlayer = null;
                be.setChanged();
            }
        }
    }

    public void setOccupyingPlayer(@Nullable ServerPlayer player) {
        if (player != null) {
            this.occupyingPlayerUUID = player.getUUID();
            this.cachedOccupyingPlayer = player;
        } else {
            this.occupyingPlayerUUID = null;
            this.cachedOccupyingPlayer = null;
        }
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
            return this.cachedOccupyingPlayer;
        }

        // プレイヤーを再検索
        if (level instanceof ServerLevel serverLevel) {
            this.cachedOccupyingPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(this.occupyingPlayerUUID);
            return this.cachedOccupyingPlayer;
        }

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