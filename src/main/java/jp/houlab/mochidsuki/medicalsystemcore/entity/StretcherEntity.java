package jp.houlab.mochidsuki.medicalsystemcore.entity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class StretcherEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> CARRYING_PLAYER =
            SynchedEntityData.defineId(StretcherEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> CARRIED_BY_PLAYER =
            SynchedEntityData.defineId(StretcherEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private Player carriedByPlayer; // 担架を持っているプレイヤー
    private ServerPlayer carryingPlayer; // 担架に乗っているプレイヤー
    private Vec3 lastCarrierPos = Vec3.ZERO;

    public StretcherEntity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.noCulling = true; // レンダリング範囲を拡張
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CARRYING_PLAYER, Optional.empty());
        this.entityData.define(CARRIED_BY_PLAYER, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            handleClientTick();
        } else {
            handleServerTick();
        }

        // クライアント・サーバー両方で位置を更新
        if (this.carriedByPlayer != null || getCarriedByPlayerFromData() != null) {
            updatePositionRelativeToCarrier();
        }
    }

    private void handleClientTick() {
        // クライアント側でShiftキーによる降車判定
        if (this.carryingPlayer != null && this.carryingPlayer.isShiftKeyDown()) {
            // 意識不明の場合は降りられない
            if (!ClientMedicalDataManager.isPlayerUnconscious(this.carryingPlayer)) {
                // サーバーに降車要求を送信（実装時にネットワークパケットが必要）
                this.carryingPlayer.sendSystemMessage(Component.literal("§e担架から降りました。"));
            } else {
                this.carryingPlayer.sendSystemMessage(Component.literal("§c意識不明のため担架から降りることができません。"));
            }
        }

        // クライアント側でもプレイヤー参照を更新
        updatePlayerReferencesFromData();
    }

    private void handleServerTick() {
        // 担架を持っているプレイヤーが存在するかチェック
        if (this.carriedByPlayer != null) {
            if (!this.carriedByPlayer.isAlive() || this.carriedByPlayer.distanceToSqr(this) > 100) {
                // プレイヤーが死亡したか離れすぎた場合
                dropStretcher();
                return;
            }
        }

        // 乗っているプレイヤーがShiftキーを押しているかチェック
        if (this.carryingPlayer != null && this.carryingPlayer.isShiftKeyDown()) {
            // 意識不明の場合は降りられない
            if (this.carryingPlayer.getCapability(
                            jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                    .map(data -> data.isConscious())
                    .orElse(true)) {

                // 降車処理
                this.carryingPlayer.stopRiding();

                // 姿勢と強制姿勢を解除
                this.carryingPlayer.setPose(net.minecraft.world.entity.Pose.STANDING);
                this.carryingPlayer.setForcedPose(null);

                this.carryingPlayer.sendSystemMessage(Component.literal("§e担架から降りました。"));
                this.setCarryingPlayer(null);
            }
        }

        // 乗っているプレイヤーが存在しない場合の処理
        if (this.carryingPlayer != null && (!this.carryingPlayer.isAlive() || !this.carryingPlayer.isPassenger())) {
            this.setCarryingPlayer(null);
        }
    }

    private void updatePositionRelativeToCarrier() {
        Player carrier = this.carriedByPlayer;

        // クライアント側では同期データからプレイヤーを取得
        if (carrier == null && this.level().isClientSide()) {
            carrier = getCarriedByPlayerFromData();
        }

        if (carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float yaw = carrier.getYRot();

        // 水平回転のみを使用してlookVecを計算（縦回転は無視）
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        // プレイヤーの腰の前あたりに担架を配置
        Vec3 newPos = carrierPos.add(lookX * 1.0, 0.0, lookZ * 1.0);

        // 担架の位置を直接設定（補間なし）
        this.setPos(newPos.x, newPos.y, newPos.z);
        this.setYRot(yaw);

        // 乗っているプレイヤーの向きも担架の向きに合わせる（サーバーサイドのみ）
        if (!this.level().isClientSide() && this.carryingPlayer != null) {
            // プレイヤーの位置も担架と同じ位置に固定（Y軸修正）
            this.carryingPlayer.setPos(newPos.x, newPos.y + 1.0, newPos.z);

            // より確実な姿勢制御
            this.carryingPlayer.setYRot(yaw);
            this.carryingPlayer.setXRot(0);
            this.carryingPlayer.yBodyRot = yaw;
            this.carryingPlayer.yBodyRotO = yaw;
            this.carryingPlayer.setYHeadRot(yaw);
            this.carryingPlayer.xRotO = 0;

            // 強制的にSLEEPING姿勢に設定し、ネットワーク同期を強制
            if (this.carryingPlayer.getPose() != net.minecraft.world.entity.Pose.SLEEPING) {
                this.carryingPlayer.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                this.carryingPlayer.setForcedPose(net.minecraft.world.entity.Pose.SLEEPING);

                // 姿勢変更を明示的に全プレイヤーに同期
                this.carryingPlayer.refreshDimensions();
                if (this.carryingPlayer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    // 周囲のプレイヤーに更新を送信
                    serverLevel.getChunkSource().broadcast(this.carryingPlayer,
                            new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                                    this.carryingPlayer.getId(),
                                    this.carryingPlayer.getEntityData().packDirty()
                            )
                    );
                }
            }
        }

        this.lastCarrierPos = carrierPos;
    }

    private void dropStretcher() {
        // プレイヤーを降ろす
        if (this.carryingPlayer != null) {
            this.carryingPlayer.stopRiding();
            this.carryingPlayer.setPose(net.minecraft.world.entity.Pose.STANDING);
            this.carryingPlayer.setForcedPose(null); // 強制姿勢を解除
            this.carryingPlayer.sendSystemMessage(Component.literal("§e担架から降ろされました。"));
        }

        // 担架アイテムをドロップ
        if (!this.level().isClientSide()) {
            this.spawnAtLocation(new ItemStack(Medicalsystemcore.STRETCHER.get()));
        }

        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false; // 当たり判定を無効化
    }

    @Override
    public boolean canBeCollidedWith() {
        return false; // 衝突判定を無効化
    }

    @Override
    public boolean isPushable() {
        return false; // 押し出し判定を無効化
    }

    /**
     * クライアント側でEntityDataから運搬者プレイヤーを取得
     */
    private Player getCarriedByPlayerFromData() {
        Optional<UUID> carriedByUUID = this.entityData.get(CARRIED_BY_PLAYER);
        if (carriedByUUID.isPresent() && this.level().isClientSide()) {
            return this.level().getPlayerByUUID(carriedByUUID.get());
        }
        return null;
    }

    /**
     * クライアント側でEntityDataから乗車プレイヤーを取得
     */
    private Player getCarryingPlayerFromData() {
        Optional<UUID> carryingUUID = this.entityData.get(CARRYING_PLAYER);
        if (carryingUUID.isPresent() && this.level().isClientSide()) {
            return this.level().getPlayerByUUID(carryingUUID.get());
        }
        return null;
    }

    /**
     * クライアント側でEntityDataからプレイヤー参照を更新
     */
    private void updatePlayerReferencesFromData() {
        if (this.level().isClientSide()) {
            // 運搬者の更新
            if (this.carriedByPlayer == null) {
                this.carriedByPlayer = getCarriedByPlayerFromData();
            }

            // 乗車者の更新
            if (this.carryingPlayer == null) {
                Player carryingFromData = getCarryingPlayerFromData();
                if (carryingFromData instanceof net.minecraft.server.level.ServerPlayer) {
                    this.carryingPlayer = (net.minecraft.server.level.ServerPlayer) carryingFromData;
                }
            }
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return false; // 乗客を座らせない（横にしたいため）
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.1; // 担架の少し上に配置
    }

    @Override
    protected boolean canRide(Entity pEntity) {
        return pEntity instanceof Player; // プレイヤーのみ乗車可能
    }

    public void setCarriedByPlayer(@Nullable Player player) {
        this.carriedByPlayer = player;
        this.entityData.set(CARRIED_BY_PLAYER, Optional.ofNullable(player != null ? player.getUUID() : null));
    }

    public void setCarryingPlayer(@Nullable ServerPlayer player) {
        this.carryingPlayer = player;
        this.entityData.set(CARRYING_PLAYER, Optional.ofNullable(player != null ? player.getUUID() : null));
    }

    @Nullable
    public Player getCarriedByPlayer() {
        return this.carriedByPlayer;
    }

    @Nullable
    public ServerPlayer getCarryingPlayer() {
        return this.carryingPlayer;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        if (pCompound.hasUUID("CarriedBy")) {
            UUID carriedByUUID = pCompound.getUUID("CarriedBy");
            this.entityData.set(CARRIED_BY_PLAYER, Optional.of(carriedByUUID));
            // サーバーサイドでプレイヤーを復元
            if (!this.level().isClientSide()) {
                this.carriedByPlayer = this.level().getPlayerByUUID(carriedByUUID);
            }
        }
        if (pCompound.hasUUID("Carrying")) {
            UUID carryingUUID = pCompound.getUUID("Carrying");
            this.entityData.set(CARRYING_PLAYER, Optional.of(carryingUUID));
            // サーバーサイドでプレイヤーを復元
            if (!this.level().isClientSide() && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                this.carryingPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(carryingUUID);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        if (this.carriedByPlayer != null) {
            pCompound.putUUID("CarriedBy", this.carriedByPlayer.getUUID());
        }
        if (this.carryingPlayer != null) {
            pCompound.putUUID("Carrying", this.carryingPlayer.getUUID());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}