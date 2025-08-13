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

public class StretcherEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> CARRYING_PLAYER =
            SynchedEntityData.defineId(StretcherEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> CARRIED_BY_PLAYER =
            SynchedEntityData.defineId(StretcherEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private Player carriedByPlayer; // 担架を持っているプレイヤー
    private ServerPlayer carryingPlayer; // 担架に乗っているプレイヤー
    private Vec3 lastCarrierPos = Vec3.ZERO;
    private int stableAngleFrames = 0; // 安定している角度フレーム数
    private float lastStableBodyYaw = 0.0f; // 最後に安定していた体の角度

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


    /**
     * プレイヤーの体の向きをシンプルに制御
     */
    private void updatePlayerBodyOrientationSimple(ServerPlayer player, float stretcherYaw) {
        float normalizedStretcherYaw = AngleUtils.normalizeAngle(stretcherYaw);

        // デバッグ情報
        player.sendSystemMessage(Component.literal(String.format("§7Simple - 担架角度: %.1f°",
                normalizedStretcherYaw)));

        // 初回設定時
        if (!this.hasInitializedPlayerAngle) {
            // クライアント側に姿勢情報を送信
            sendStretcherPoseUpdate(player, true, normalizedStretcherYaw);
            this.lastPlayerBodyYaw = normalizedStretcherYaw;
            this.hasInitializedPlayerAngle = true;
            player.sendSystemMessage(Component.literal("§aシンプル姿勢制御開始"));
            return;
        }

        // 角度更新
        float currentBodyYaw = AngleUtils.normalizeAngle(this.lastPlayerBodyYaw);
        float angleDifference = AngleUtils.getAngleDifference(currentBodyYaw, normalizedStretcherYaw);

        if (Math.abs(angleDifference) > 2.0f) {
            sendStretcherPoseUpdate(player, true, normalizedStretcherYaw);
            this.lastPlayerBodyYaw = normalizedStretcherYaw;
            player.sendSystemMessage(Component.literal(String.format("§b角度更新: %.1f°",
                    normalizedStretcherYaw)));
        }
    }

    /**
     * クライアント側にストレッチャー姿勢の更新を送信
     */
    private void sendStretcherPoseUpdate(ServerPlayer player, boolean onStretcher, float yaw) {
        // カスタムパケットまたはEntityDataを使用してクライアントに送信
        // ここでは簡単にEntityDataを使用

        // プレイヤーの姿勢をSTANDINGに設定（レンダラーで制御）
        player.setPose(Pose.STANDING);

        // 体の角度情報はlastPlayerBodyYawで管理
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;

        // クライアントに同期
        player.connection.send(new ClientboundSetEntityDataPacket(
                player.getId(), player.getEntityData().packDirty()
        ));
    }

    // updatePositionRelativeToCarrier() の修正版
    private void updatePositionRelativeToCarrier() {
        Player carrier = this.carriedByPlayer;

        if (carrier == null && this.level().isClientSide()) {
            carrier = getCarriedByPlayerFromData();
        }

        if (carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());

        double yawRad = Math.toRadians(carrierYaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        Vec3 newPos = carrierPos.add(lookX * 1.0, 0.0, lookZ * 1.0);

        this.setPos(newPos.x, newPos.y, newPos.z);

        float currentStretcherYaw = AngleUtils.normalizeAngle(this.getYRot());
        float newStretcherYaw = AngleUtils.gradualAngleChange(currentStretcherYaw, carrierYaw, 15.0f);
        this.setYRot(newStretcherYaw);

        // サーバーサイドでのプレイヤー制御
        if (!this.level().isClientSide() && this.carryingPlayer != null) {
            this.carryingPlayer.setPos(newPos.x, newPos.y + 1.0, newPos.z);

            // *** シンプルな姿勢制御 ***
            updatePlayerBodyOrientationSimple(this.carryingPlayer, newStretcherYaw);
        }

        this.lastCarrierPos = carrierPos;
    }


    // 追加のデバッグメソッド
    private void debugPlayerOrientation(ServerPlayer player) {
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float yBodyRot = player.yBodyRot;
        float yBodyRotO = player.yBodyRotO;
        float yHeadRot = player.getYHeadRot();

        player.sendSystemMessage(Component.literal(String.format(
                "§7デバッグ - YRot: %.1f°, XRot: %.1f°, BodyRot: %.1f°, BodyRotO: %.1f°, HeadRot: %.1f°",
                yRot, xRot, yBodyRot, yBodyRotO, yHeadRot
        )));
    }

    // テスト用のコマンドハンドラー（開発時のみ使用）
    public void testBodyRotation(ServerPlayer player, float targetAngle) {
        if (this.carryingPlayer == player) {
            setPlayerBodyOrientation(player, targetAngle);
            debugPlayerOrientation(player);
            player.sendSystemMessage(Component.literal(String.format("§aテスト: 体角度を%.1f°に設定", targetAngle)));
        }
    }

    /**
     * プレイヤーの体の向きのみを制御（視点は自由に保つ）
     * 完全修正版：体の向きが正しく担架の方向に従うように
     */
    private void updatePlayerBodyOrientation(ServerPlayer player, float stretcherYaw) {
        float normalizedStretcherYaw = AngleUtils.normalizeAngle(stretcherYaw);

        // *** 寝転がっているプレイヤーの向きを正しく設定 ***
        // 担架の向きに対して、プレイヤーの頭が担架の進行方向、足が反対方向になるように設定
        float playerBodyYaw = normalizedStretcherYaw; // 直接担架の角度を使用

        // デバッグ情報を一時的に表示（テスト用）
        player.sendSystemMessage(Component.literal(String.format("§7担架角度: %.1f°, 目標体角度: %.1f°",
                normalizedStretcherYaw, playerBodyYaw)));

        // 初回設定時は即座に角度を合わせる
        if (!this.hasInitializedPlayerAngle) {
            setPlayerBodyOrientation(player, playerBodyYaw);
            this.lastPlayerBodyYaw = playerBodyYaw;
            this.hasInitializedPlayerAngle = true;

            // 初回設定の確認メッセージ
            player.sendSystemMessage(Component.literal(String.format("§a初期体角度設定: %.1f°", playerBodyYaw)));
            return;
        }

        // 現在の体の角度を取得
        float currentBodyYaw = AngleUtils.normalizeAngle(this.lastPlayerBodyYaw);

        // 角度差分を計算（正確な最短経路を使用）
        float angleDifference = AngleUtils.getAngleDifference(currentBodyYaw, playerBodyYaw);

        // デバッグ情報：角度差分を表示
        if (Math.abs(angleDifference) > 1.0f) {
            player.sendSystemMessage(Component.literal(String.format("§e角度差分: %.1f° (現在: %.1f° → 目標: %.1f°)",
                    angleDifference, currentBodyYaw, playerBodyYaw)));
        }

        // より緩い条件で角度を更新（2度以上の変化で更新）
        if (Math.abs(angleDifference) > 2.0f) {
            // 段階的な角度変更（滑らかな回転）
            float maxChangePerTick = 15.0f; // より大きな変化量を許可

            if (Math.abs(angleDifference) <= maxChangePerTick) {
                // 目標角度に近い場合は直接設定
                setPlayerBodyOrientation(player, playerBodyYaw);
                this.lastPlayerBodyYaw = playerBodyYaw;

                player.sendSystemMessage(Component.literal(String.format("§b体角度更新: %.1f°", playerBodyYaw)));
            } else {
                // 段階的に変更
                float changeAmount = Math.signum(angleDifference) * maxChangePerTick;
                float newBodyYaw = AngleUtils.normalizeAngle(currentBodyYaw + changeAmount);
                setPlayerBodyOrientation(player, newBodyYaw);
                this.lastPlayerBodyYaw = newBodyYaw;

                player.sendSystemMessage(Component.literal(String.format("§c段階的角度変更: %.1f° (変化量: %.1f°)",
                        newBodyYaw, changeAmount)));
            }
        }
    }

    /**
     * プレイヤーの体の向きのみを設定（視点の向きは変更しない）
     * 強化版：確実な同期と複数の設定方法を試行
     */
    private void setPlayerBodyOrientation(ServerPlayer player, float bodyYaw) {
        float normalizedBodyYaw = AngleUtils.normalizeAngle(bodyYaw);

        // デバッグ：現在の値を記録
        float oldBodyYaw = player.yBodyRot;

        // *** 方法1：標準的な体の向き設定 ***
        player.yBodyRot = normalizedBodyYaw;
        player.yBodyRotO = normalizedBodyYaw;

        // *** 方法2：Entity#setYRotも一時的に使用して強制設定 ***
        // 注意：これは視点には影響しないはずですが、体の向きを確実に設定します
        float originalYRot = player.getYRot();
        float originalXRot = player.getXRot();

        player.setYRot(normalizedBodyYaw);  // 一時的に設定
        player.yBodyRot = normalizedBodyYaw;  // 体の角度を再設定
        player.yBodyRotO = normalizedBodyYaw;

        // 視点角度を元に戻す（プレイヤーの自由な視点操作を保持）
        player.setYRot(originalYRot);
        player.setXRot(originalXRot);

        // *** 方法3：強制的なクライアント同期 ***
        // EntityDataの更新
        player.getEntityData().set(net.minecraft.world.entity.Entity.DATA_POSE, net.minecraft.world.entity.Pose.SLEEPING);

        // 複数の同期パケットを送信
        player.connection.send(new ClientboundSetEntityDataPacket(
                player.getId(), player.getEntityData().packDirty()
        ));

        // 位置と角度の完全同期
        player.connection.send(new ClientboundTeleportEntityPacket(player));

        // *** 方法4：少し遅延させて再度設定（確実性向上） ***
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                // 1tick後に再度設定
                player.yBodyRot = normalizedBodyYaw;
                player.yBodyRotO = normalizedBodyYaw;
            });
        }

        // デバッグ情報
        player.sendSystemMessage(Component.literal(String.format("§d体角度設定完了: %.1f° → %.1f°",
                oldBodyYaw, normalizedBodyYaw)));
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
        // 前のプレイヤーのアニメーションを停止
        if (this.carryingPlayer != null && this.carryingPlayer != player) {
        }

        this.carryingPlayer = player;
        this.entityData.set(CARRYING_PLAYER, Optional.ofNullable(player != null ? player.getUUID() : null));

        this.hasInitializedPlayerAngle = false;

        // 新しいプレイヤーのアニメーションを開始
        if (player != null) {
            player.sendSystemMessage(Component.literal("§aストレッチャーアニメーション適用"));
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

    // remove メソッドの修正版
    @Override
    public void remove(RemovalReason pReason) {
        if (this.carryingPlayer != null) {
        }
        super.remove(pReason);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }


}