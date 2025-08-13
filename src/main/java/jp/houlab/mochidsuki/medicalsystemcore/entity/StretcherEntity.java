package jp.houlab.mochidsuki.medicalsystemcore.entity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.PoseController;
import jp.houlab.mochidsuki.medicalsystemcore.util.AngleUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * ストレッチャーエンティティ
 * プレイヤーを乗せて運搬するためのエンティティ
 */
public class StretcherEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> CARRYING_PLAYER =
            SynchedEntityData.defineId(StretcherEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> CARRIED_BY_PLAYER =
            SynchedEntityData.defineId(StretcherEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private Player carriedByPlayer; // 担架を持っているプレイヤー
    private ServerPlayer carryingPlayer; // 担架に乗っているプレイヤー
    private Vec3 lastCarrierPos = Vec3.ZERO;

    // 角度制御用フィールド
    private float lastPlayerBodyYaw = 0.0f;
    private boolean hasInitializedPlayerAngle = false;

    public StretcherEntity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.noCulling = true;
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

        // 運搬者に追従する位置更新
        if (this.carriedByPlayer != null || getCarriedByPlayerFromData() != null) {
            updatePositionRelativeToCarrier();
        }
    }

    private void handleClientTick() {
        // クライアント側でShiftキーによる降車判定
        if (this.carryingPlayer != null && this.carryingPlayer.isShiftKeyDown()) {
            if (!ClientMedicalDataManager.isPlayerUnconscious(this.carryingPlayer)) {
                this.carryingPlayer.sendSystemMessage(Component.literal("§e担架から降りました。"));
            } else {
                this.carryingPlayer.sendSystemMessage(Component.literal("§c意識不明のため担架から降りることができません。"));
            }
        }

        updatePlayerReferencesFromData();
    }

    private void handleServerTick() {
        // 担架を持っているプレイヤーが存在するかチェック
        if (this.carriedByPlayer != null) {
            if (!this.carriedByPlayer.isAlive() || this.carriedByPlayer.distanceToSqr(this) > 100) {
                dropStretcher();
                return;
            }
        }

        // 乗っているプレイヤーがShiftキーを押しているかチェック
        if (this.carryingPlayer != null && this.carryingPlayer.isShiftKeyDown()) {
            if (this.carryingPlayer.getCapability(
                            jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                    .map(data -> data.isConscious())
                    .orElse(true)) {

                // 降車処理
                this.carryingPlayer.stopRiding();
                PoseController.setStretcherPose(this.carryingPlayer, false);

                this.hasInitializedPlayerAngle = false;
                this.carryingPlayer.sendSystemMessage(Component.literal("§e担架から降りました。"));
                this.setCarryingPlayer(null);
            }
        }

        // 乗っているプレイヤーが存在しない場合の処理
        if (this.carryingPlayer != null && (!this.carryingPlayer.isAlive() || !this.carryingPlayer.isPassenger())) {
            this.setCarryingPlayer(null);
        }
    }

    /**
     * 運搬者の向きから理想的な担架の角度を計算
     * 正しい関係：A = B - 90° → B = A + 90°
     */
    public static float calculateIdealStretcherYaw(float carrierYaw) {
        return AngleUtils.normalizeAngle(carrierYaw + 90.0f);
    }

    /**
     * 担架の角度からプレイヤーの体の向きを計算
     * 正しい関係：B = C
     */
    public static float calculatePlayerBodyYaw(float stretcherYaw) {
        return AngleUtils.normalizeAngle(stretcherYaw);
    }

    /**
     * 運搬者の向きから担架の位置を計算
     */
    public static Vec3 calculateIdealStretcherPosition(Vec3 carrierPos, float carrierYaw) {
        double yawRad = Math.toRadians(carrierYaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        return carrierPos.add(lookX * 1.0, 0.0, lookZ * 1.0);
    }

    private void updatePositionRelativeToCarrier() {
        Player carrier = this.carriedByPlayer;

        // クライアント側では同期データからプレイヤーを取得
        if (carrier == null && this.level().isClientSide()) {
            carrier = getCarriedByPlayerFromData();
        }

        if (carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());

        // 理想的な位置と角度を計算
        Vec3 idealPos = calculateIdealStretcherPosition(carrierPos, carrierYaw);
        float idealStretcherYaw = calculateIdealStretcherYaw(carrierYaw);

        // 担架の位置を設定
        this.setPos(idealPos.x, idealPos.y, idealPos.z);

        // 担架の角度を段階的に変更
        float currentStretcherYaw = AngleUtils.normalizeAngle(this.getYRot());
        float angleDifference = AngleUtils.getAngleDifference(currentStretcherYaw, idealStretcherYaw);

        float newStretcherYaw;
        if (Math.abs(angleDifference) <= 15.0f) {
            newStretcherYaw = idealStretcherYaw;
        } else {
            float changeAmount = Math.signum(angleDifference) * 15.0f;
            newStretcherYaw = AngleUtils.normalizeAngle(currentStretcherYaw + changeAmount);
        }

        this.setYRot(newStretcherYaw);

        // サーバーサイドでのプレイヤー制御
        if (!this.level().isClientSide() && this.carryingPlayer != null) {
            this.carryingPlayer.setPos(idealPos.x, idealPos.y + 1.0, idealPos.z);

            // プレイヤーの体の向きを担架の角度と同じに設定
            float playerBodyYaw = newStretcherYaw;
            updatePlayerBodyOrientationSimple(this.carryingPlayer, playerBodyYaw);
        }

        this.lastCarrierPos = carrierPos;
    }

    /**
     * プレイヤーの体の向きをシンプルに制御
     */
    private void updatePlayerBodyOrientationSimple(ServerPlayer player, float targetBodyYaw) {
        float normalizedTargetYaw = AngleUtils.normalizeAngle(targetBodyYaw);

        // 初回設定時
        if (!this.hasInitializedPlayerAngle) {
            sendStretcherPoseUpdate(player, true, normalizedTargetYaw);
            this.lastPlayerBodyYaw = normalizedTargetYaw;
            this.hasInitializedPlayerAngle = true;
            return;
        }

        // 角度更新
        float currentBodyYaw = AngleUtils.normalizeAngle(this.lastPlayerBodyYaw);
        float angleDifference = AngleUtils.getAngleDifference(currentBodyYaw, normalizedTargetYaw);

        if (Math.abs(angleDifference) > 2.0f) {
            sendStretcherPoseUpdate(player, true, normalizedTargetYaw);
            this.lastPlayerBodyYaw = normalizedTargetYaw;
        }
    }

    /**
     * クライアント側にストレッチャー姿勢の更新を送信（安全版）
     */
    private void sendStretcherPoseUpdate(ServerPlayer player, boolean onStretcher, float targetBodyYaw) {
        try {
            player.setPose(Pose.STANDING);

            float normalizedBodyYaw = AngleUtils.normalizeAngle(targetBodyYaw);
            player.yBodyRot = normalizedBodyYaw;
            player.yBodyRotO = normalizedBodyYaw;

            // EntityDataの安全性チェック
            if (player.getEntityData() != null) {
                var dataList = player.getEntityData().packDirty();
                if (dataList != null && !dataList.isEmpty()) {
                    player.connection.send(new ClientboundSetEntityDataPacket(player.getId(), dataList));
                } else {
                    player.connection.send(new ClientboundTeleportEntityPacket(player));
                }
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§c姿勢更新でエラーが発生しました: " + e.getMessage()));
            Medicalsystemcore.LOGGER.error("ストレッチャー姿勢更新エラー", e);
        }
    }

    /**
     * 担架エンティティを作成して適切な位置に配置
     */
    public static StretcherEntity createAndPosition(Level level, Player carrierPlayer, Player ridingPlayer) {
        StretcherEntity stretcher = new StretcherEntity(Medicalsystemcore.STRETCHER_ENTITY.get(), level);

        Vec3 spawnPos = ridingPlayer.position();
        stretcher.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        float carrierYaw = carrierPlayer.getYRot();
        float stretcherYaw = calculateIdealStretcherYaw(carrierYaw);
        stretcher.setYRot(stretcherYaw);

        stretcher.setCarriedByPlayer(carrierPlayer);
        return stretcher;
    }

    /**
     * 担架位置の更新（公開メソッド）
     */
    public static void updateStretcherPosition(StretcherEntity stretcher, Player carrier) {
        if (stretcher == null || carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());

        Vec3 idealPos = calculateIdealStretcherPosition(carrierPos, carrierYaw);
        float idealYaw = calculateIdealStretcherYaw(carrierYaw);

        Vec3 currentPos = stretcher.position();
        double distance = currentPos.distanceTo(idealPos);

        if (distance > 0.1) {
            Vec3 lerpedPos = currentPos.lerp(idealPos, 0.2);
            stretcher.setPos(lerpedPos.x, lerpedPos.y, lerpedPos.z);
        }

        float currentYaw = AngleUtils.normalizeAngle(stretcher.getYRot());
        if (!AngleUtils.isAngleChangeSmall(currentYaw, idealYaw, 1.0f)) {
            float newYaw = AngleUtils.gradualAngleChange(currentYaw, idealYaw, 5.0f);
            stretcher.setYRot(newYaw);
        }
    }

    private void dropStretcher() {
        if (this.carryingPlayer != null) {
            this.carryingPlayer.stopRiding();
            PoseController.setStretcherPose(this.carryingPlayer, false);
            this.carryingPlayer.sendSystemMessage(Component.literal("§e担架から降ろされました。"));
        }

        this.hasInitializedPlayerAngle = false;

        if (!this.level().isClientSide()) {
            this.spawnAtLocation(new ItemStack(Medicalsystemcore.STRETCHER.get()));
        }

        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private Player getCarriedByPlayerFromData() {
        Optional<UUID> carriedByUUID = this.entityData.get(CARRIED_BY_PLAYER);
        if (carriedByUUID.isPresent() && this.level().isClientSide()) {
            return this.level().getPlayerByUUID(carriedByUUID.get());
        }
        return null;
    }

    private Player getCarryingPlayerFromData() {
        Optional<UUID> carryingUUID = this.entityData.get(CARRYING_PLAYER);
        if (carryingUUID.isPresent() && this.level().isClientSide()) {
            return this.level().getPlayerByUUID(carryingUUID.get());
        }
        return null;
    }

    private void updatePlayerReferencesFromData() {
        if (this.level().isClientSide()) {
            if (this.carriedByPlayer == null) {
                this.carriedByPlayer = getCarriedByPlayerFromData();
            }

            if (this.carryingPlayer == null) {
                Player carryingFromData = getCarryingPlayerFromData();
                if (carryingFromData instanceof ServerPlayer) {
                    this.carryingPlayer = (ServerPlayer) carryingFromData;
                }
            }
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.1;
    }

    @Override
    protected boolean canRide(Entity pEntity) {
        return pEntity instanceof Player;
    }

    public void setCarriedByPlayer(@Nullable Player player) {
        this.carriedByPlayer = player;
        this.entityData.set(CARRIED_BY_PLAYER, Optional.ofNullable(player != null ? player.getUUID() : null));
    }

    public void setCarryingPlayer(@Nullable ServerPlayer player) {
        this.carryingPlayer = player;
        this.entityData.set(CARRYING_PLAYER, Optional.ofNullable(player != null ? player.getUUID() : null));
        this.hasInitializedPlayerAngle = false;

        if (player != null) {
            player.sendSystemMessage(Component.literal("§aストレッチャーに乗りました"));
        }
    }

    @Nullable
    public Player getCarriedByPlayer() {
        return this.carriedByPlayer;
    }

    @Nullable
    public ServerPlayer getCarryingPlayer() {
        return this.carryingPlayer;
    }

    @Nullable
    public Player getCarriedByPlayerFromDataPublic() {
        return getCarriedByPlayerFromData();
    }

    @Nullable
    public Player getCarryingPlayerFromDataPublic() {
        return getCarryingPlayerFromData();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        if (pCompound.hasUUID("CarriedBy")) {
            UUID carriedByUUID = pCompound.getUUID("CarriedBy");
            this.entityData.set(CARRIED_BY_PLAYER, Optional.of(carriedByUUID));
            if (!this.level().isClientSide()) {
                this.carriedByPlayer = this.level().getPlayerByUUID(carriedByUUID);
            }
        }
        if (pCompound.hasUUID("Carrying")) {
            UUID carryingUUID = pCompound.getUUID("Carrying");
            this.entityData.set(CARRYING_PLAYER, Optional.of(carryingUUID));
            if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
                ServerPlayer restoredPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(carryingUUID);
                if (restoredPlayer != null) {
                    this.carryingPlayer = restoredPlayer;
                    PoseController.setStretcherPose(restoredPlayer, true);
                    this.hasInitializedPlayerAngle = false;
                }
            }
        }

        this.lastPlayerBodyYaw = pCompound.getFloat("LastPlayerBodyYaw");
        this.hasInitializedPlayerAngle = pCompound.getBoolean("HasInitializedPlayerAngle");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        if (this.carriedByPlayer != null) {
            pCompound.putUUID("CarriedBy", this.carriedByPlayer.getUUID());
        }
        if (this.carryingPlayer != null) {
            pCompound.putUUID("Carrying", this.carryingPlayer.getUUID());
        }

        pCompound.putFloat("LastPlayerBodyYaw", this.lastPlayerBodyYaw);
        pCompound.putBoolean("HasInitializedPlayerAngle", this.hasInitializedPlayerAngle);
    }

    @Override
    public void remove(RemovalReason pReason) {
        if (this.carryingPlayer != null) {
            PoseController.setStretcherPose(this.carryingPlayer, false);
        }
        super.remove(pReason);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}