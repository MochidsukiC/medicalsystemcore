package jp.houlab.mochidsuki.medicalsystemcore.entity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.core.PoseController;
import jp.houlab.mochidsuki.medicalsystemcore.util.AngleUtils;
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

        // *** 更新頻度制限を削除 - 滑らかな動きのため毎フレーム更新 ***
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

                // 降車時の角度初期化をリセット
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

    private void updatePositionRelativeToCarrier() {
        Player carrier = this.carriedByPlayer;

        // クライアント側では同期データからプレイヤーを取得
        if (carrier == null && this.level().isClientSide()) {
            carrier = getCarriedByPlayerFromData();
        }

        if (carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());

        // 水平回転のみを使用してlookVecを計算
        double yawRad = Math.toRadians(carrierYaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        // プレイヤーの腰の前あたりに担架を配置
        Vec3 newPos = carrierPos.add(lookX * 1.0, 0.0, lookZ * 1.0);

        // 担架の位置を設定（滑らか）
        this.setPos(newPos.x, newPos.y, newPos.z);

        // 担架の角度を段階的に変更
        float currentStretcherYaw = AngleUtils.normalizeAngle(this.getYRot());
        float newStretcherYaw = AngleUtils.gradualAngleChange(currentStretcherYaw, carrierYaw, 15.0f);
        this.setYRot(newStretcherYaw);

        // サーバーサイドでのプレイヤー制御
        if (!this.level().isClientSide() && this.carryingPlayer != null) {
            // プレイヤーの位置を担架と同じ位置に固定
            this.carryingPlayer.setPos(newPos.x, newPos.y + 1.0, newPos.z);

            // *** 体の向きのみ制御（視点は自由） ***
            updatePlayerBodyOrientation(this.carryingPlayer, newStretcherYaw);

            // 姿勢制御を維持
            PoseController.maintainPoseControl(this.carryingPlayer);
        }

        this.lastCarrierPos = carrierPos;
    }

    /**
     * プレイヤーの体の向きのみを制御（視点は自由に保つ）
     */
    private void updatePlayerBodyOrientation(ServerPlayer player, float stretcherYaw) {
        float normalizedStretcherYaw = AngleUtils.normalizeAngle(stretcherYaw);

        // *** 寝転がっているプレイヤーの向きを正しく設定 ***
        // 頭の方向を担架の進行方向に合わせる
        // 担架が北向き(0度)の時、プレイヤーの頭が北向きになるように調整
        float playerBodyYaw = normalizedStretcherYaw + 90.0f; // 90度オフセットで正しい向きに
        playerBodyYaw = AngleUtils.normalizeAngle(playerBodyYaw);

        // 初回設定時は即座に角度を合わせる
        if (!this.hasInitializedPlayerAngle) {
            setPlayerBodyOrientation(player, playerBodyYaw);
            this.lastPlayerBodyYaw = playerBodyYaw;
            this.hasInitializedPlayerAngle = true;
            return;
        }

        // 段階的な体の向き変更
        float currentBodyYaw = AngleUtils.normalizeAngle(this.lastPlayerBodyYaw);

        // 角度変化が小さい場合のみ更新
        if (!AngleUtils.isAngleChangeSmall(currentBodyYaw, playerBodyYaw, 2.0f)) {
            float newBodyYaw = AngleUtils.gradualAngleChange(currentBodyYaw, playerBodyYaw, 10.0f);
            setPlayerBodyOrientation(player, newBodyYaw);
            this.lastPlayerBodyYaw = newBodyYaw;
        }
    }

    /**
     * プレイヤーの体の向きのみを設定（視点の向きは変更しない）
     */
    private void setPlayerBodyOrientation(ServerPlayer player, float bodyYaw) {
        float normalizedBodyYaw = AngleUtils.normalizeAngle(bodyYaw);

        // *** 体の向きのみ設定、視点（頭の向き）は変更しない ***
        player.yBodyRot = normalizedBodyYaw;
        player.yBodyRotO = normalizedBodyYaw;

        // 視点関連の角度は変更しない - プレイヤーが自由に視点移動可能
        // player.setYRot() は呼び出さない
        // player.setXRot() は呼び出さない
        // player.setYHeadRot() は呼び出さない
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
                if (carryingFromData instanceof net.minecraft.server.level.ServerPlayer) {
                    this.carryingPlayer = (net.minecraft.server.level.ServerPlayer) carryingFromData;
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
        // 前のプレイヤーの姿勢制御を解除
        if (this.carryingPlayer != null && this.carryingPlayer != player) {
            PoseController.setStretcherPose(this.carryingPlayer, false);
        }

        this.carryingPlayer = player;
        this.entityData.set(CARRYING_PLAYER, Optional.ofNullable(player != null ? player.getUUID() : null));

        // プレイヤー変更時に角度初期化をリセット
        this.hasInitializedPlayerAngle = false;

        // 新しいプレイヤーの姿勢制御を開始
        if (player != null) {
            PoseController.setStretcherPose(player, true);
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

    /**
     * クライアントサイドからアクセス可能な運搬者取得メソッド
     */
    @Nullable
    public Player getCarriedByPlayerFromDataPublic() {
        return getCarriedByPlayerFromData();
    }

    /**
     * クライアントサイドからアクセス可能な乗車者取得メソッド
     */
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
            if (!this.level().isClientSide() && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
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