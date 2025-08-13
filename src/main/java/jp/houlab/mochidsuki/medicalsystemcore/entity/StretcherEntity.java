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
     * 運搬者の向きから理想的な担架の角度を計算
     * 正しい関係：A = B - 90° → B = A + 90°
     * @param carrierYaw 運搬者の向き（A°）
     * @return 理想的な担架の角度（B°）
     */
    public static float calculateIdealStretcherYaw(float carrierYaw) {
        // B = A + 90°
        return AngleUtils.normalizeAngle(carrierYaw + 90.0f);
    }


    /**
     * 担架の角度からプレイヤーの体の向きを計算（修正版）
     * 正しい関係：B = C（担架とプレイヤーは同じ向き）
     */
    public static float calculatePlayerBodyYaw(float stretcherYaw) {
        // 修正：担架の角度とプレイヤーの体の向きは完全に同じ
        return AngleUtils.normalizeAngle(stretcherYaw);
    }


    /**
     * 運搬者の向きから担架の位置を計算
     * 担架は運搬者の横（90度回転した方向）に配置される
     * @param carrierPos 運搬者の位置
     * @param carrierYaw 運搬者の向き（A°）
     * @return 理想的な担架位置
     */
    public static Vec3 calculateIdealStretcherPosition(Vec3 carrierPos, float carrierYaw) {
        // 運搬者の右側（90度回転）に担架を配置
        // 運搬者が担架を横から押すように
        double yawRad = Math.toRadians(carrierYaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        return carrierPos.add(lookX * 1.0, 0.0, lookZ * 1.0);
    }

    /**
     * 位置更新メソッドの修正 - 角度の反転問題を解決
     */
    private void updatePositionRelativeToCarrier() {
        Player carrier = this.carriedByPlayer;

        // クライアント側では同期データからプレイヤーを取得
        if (carrier == null && this.level().isClientSide()) {
            carrier = getCarriedByPlayerFromData();
        }

        if (carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());  // A°

        // 統一された位置計算メソッドを使用
        Vec3 idealPos = calculateIdealStretcherPosition(carrierPos, carrierYaw);
        float idealStretcherYaw = calculateIdealStretcherYaw(carrierYaw);  // B° = A° + 90°

        // 担架の位置を設定（滑らか）
        this.setPos(idealPos.x, idealPos.y, idealPos.z);

        // 担架の角度を段階的に変更（修正：反転を防ぐ）
        float currentStretcherYaw = AngleUtils.normalizeAngle(this.getYRot());

        // 角度差分を正しく計算
        float angleDifference = AngleUtils.getAngleDifference(currentStretcherYaw, idealStretcherYaw);

        // 段階的な変更（最大15度/tick）
        float newStretcherYaw;
        if (Math.abs(angleDifference) <= 15.0f) {
            // 差分が小さい場合は直接設定
            newStretcherYaw = idealStretcherYaw;
        } else {
            // 段階的に変更（修正：正しい方向への変化）
            float changeAmount = Math.signum(angleDifference) * 15.0f;
            newStretcherYaw = AngleUtils.normalizeAngle(currentStretcherYaw + changeAmount);
        }

        this.setYRot(newStretcherYaw);

        // サーバーサイドでのプレイヤー制御
        if (!this.level().isClientSide() && this.carryingPlayer != null) {
            this.carryingPlayer.setPos(idealPos.x, idealPos.y + 1.0, idealPos.z);

            // プレイヤーの体の向きを担架の角度と完全に同じに設定（B = C）
            float playerBodyYaw = newStretcherYaw;  // 修正：calculatePlayerBodyYaw を直接使用せず、同じ値を使用
            updatePlayerBodyOrientationSimple(this.carryingPlayer, playerBodyYaw);

            // デバッグ情報（1秒毎）
            if (this.carryingPlayer.tickCount % 20 == 0) {
                this.carryingPlayer.sendSystemMessage(Component.literal(String.format(
                        "§6角度関係: A(運搬者)=%.1f°, B(担架)=%.1f°, C(プレイヤー)=%.1f°",
                        carrierYaw, newStretcherYaw, playerBodyYaw
                )));
            }
        }

        this.lastCarrierPos = carrierPos;
    }

    /**
     * プレイヤーの体の向きをシンプルに制御（修正版）
     */
    private void updatePlayerBodyOrientationSimple(ServerPlayer player, float targetBodyYaw) {
        float normalizedTargetYaw = AngleUtils.normalizeAngle(targetBodyYaw);

        // デバッグ情報
        player.sendSystemMessage(Component.literal(String.format("§7プレイヤー体角度設定: %.1f°",
                normalizedTargetYaw)));

        // 初回設定時
        if (!this.hasInitializedPlayerAngle) {
            sendStretcherPoseUpdate(player, true, normalizedTargetYaw);
            this.lastPlayerBodyYaw = normalizedTargetYaw;
            this.hasInitializedPlayerAngle = true;
            player.sendSystemMessage(Component.literal("§a正しい体角度制御開始"));
            return;
        }

        // 角度更新 - ここを修正
        float currentBodyYaw = AngleUtils.normalizeAngle(this.lastPlayerBodyYaw);
        float angleDifference = AngleUtils.getAngleDifference(currentBodyYaw, normalizedTargetYaw);

        if (Math.abs(angleDifference) > 2.0f) {
            sendStretcherPoseUpdate(player, true, normalizedTargetYaw);
            this.lastPlayerBodyYaw = normalizedTargetYaw;
            player.sendSystemMessage(Component.literal(String.format("§b体角度更新: %.1f°",
                    normalizedTargetYaw)));
        }
    }

    /**
     * クライアント側にストレッチャー姿勢の更新を送信（完全修正版）
     * NullPointerException を防ぐ安全な実装
     */
    private void sendStretcherPoseUpdate(ServerPlayer player, boolean onStretcher, float targetBodyYaw) {
        try {
            // プレイヤーの姿勢をSTANDINGに設定（レンダラーで制御）
            player.setPose(Pose.STANDING);

            float normalizedBodyYaw = AngleUtils.normalizeAngle(targetBodyYaw);

            // 両方とも同じ値を設定
            player.yBodyRot = normalizedBodyYaw;
            player.yBodyRotO = normalizedBodyYaw;

            // EntityDataの安全性チェック
            if (player.getEntityData() != null) {
                // packDirty()が null を返す可能性があるため、事前にチェック
                var dataList = player.getEntityData().packDirty();
                if (dataList != null && !dataList.isEmpty()) {
                    // クライアントに同期
                    player.connection.send(new ClientboundSetEntityDataPacket(
                            player.getId(), dataList
                    ));
                } else {
                    // データが空の場合は代替手段を使用
                    player.connection.send(new ClientboundTeleportEntityPacket(player));
                }
            }

            // デバッグ情報
            player.sendSystemMessage(Component.literal(String.format(
                    "§7sendStretcherPoseUpdate修正版: C°=%.1f° (yBodyRot=%.1f°, yBodyRotO=%.1f°)",
                    normalizedBodyYaw, player.yBodyRot, player.yBodyRotO
            )));
        } catch (Exception e) {
            // エラーが発生した場合の安全な処理
            player.sendSystemMessage(Component.literal("§c姿勢更新でエラーが発生しました: " + e.getMessage()));
            Medicalsystemcore.LOGGER.error("ストレッチャー姿勢更新エラー", e);
        }
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

    /**
     * 担架エンティティを作成して適切な位置に配置
     * @param level ワールド
     * @param carrierPlayer 運搬者
     * @param ridingPlayer 乗車するプレイヤー
     * @return 作成された担架エンティティ
     */
    public static StretcherEntity createAndPosition(Level level, Player carrierPlayer, Player ridingPlayer) {
        StretcherEntity stretcher = new StretcherEntity(Medicalsystemcore.STRETCHER_ENTITY.get(), level);

        // 召喚位置は乗車するプレイヤーの位置（保守性のため）
        Vec3 spawnPos = ridingPlayer.position();
        stretcher.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        // 初期角度設定
        float carrierYaw = carrierPlayer.getYRot();
        float stretcherYaw = calculateIdealStretcherYaw(carrierYaw);
        stretcher.setYRot(stretcherYaw);

        // 運搬者を設定
        stretcher.setCarriedByPlayer(carrierPlayer);

        return stretcher;
    }

    /**
     * 担架位置の更新（公開メソッド）
     * ClientStretcherHandlerから呼び出される
     * @param stretcher 担架エンティティ
     * @param carrier 運搬者
     */
    public static void updateStretcherPosition(StretcherEntity stretcher, Player carrier) {
        if (stretcher == null || carrier == null) return;

        Vec3 carrierPos = carrier.position();
        float carrierYaw = AngleUtils.normalizeAngle(carrier.getYRot());

        // 理想的な位置と角度を計算
        Vec3 idealPos = calculateIdealStretcherPosition(carrierPos, carrierYaw);
        float idealYaw = calculateIdealStretcherYaw(carrierYaw);

        // 現在位置との差が大きい場合のみ補間
        Vec3 currentPos = stretcher.position();
        double distance = currentPos.distanceTo(idealPos);

        if (distance > 0.1) { // 10cm以上離れている場合
            // 滑らかな補間（20%ずつ近づける）
            Vec3 lerpedPos = currentPos.lerp(idealPos, 0.2);
            stretcher.setPos(lerpedPos.x, lerpedPos.y, lerpedPos.z);
        }

        // 角度の滑らかな補間
        float currentYaw = AngleUtils.normalizeAngle(stretcher.getYRot());
        if (!AngleUtils.isAngleChangeSmall(currentYaw, idealYaw, 1.0f)) {
            float newYaw = AngleUtils.gradualAngleChange(currentYaw, idealYaw, 5.0f);
            stretcher.setYRot(newYaw);
        }
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
     * プレイヤーの体の向きのみを設定（視点の向きは変更しない）
     * 完全修正版：B=C関係を確実に実現
     */
    private void setPlayerBodyOrientation(ServerPlayer player, float bodyYaw) {
        float normalizedBodyYaw = AngleUtils.normalizeAngle(bodyYaw);

        // デバッグ：現在の値を記録
        float oldBodyYaw = player.yBodyRot;
        float oldBodyYawO = player.yBodyRotO;

        // *** 完全修正：両方とも同じ値を設定 ***
        player.yBodyRot = normalizedBodyYaw;
        player.yBodyRotO = normalizedBodyYaw;  // 修正：確実に同じ値

        // *** 修正された方法2：Entity#setYRotも統一 ***
        float originalYRot = player.getYRot();
        float originalXRot = player.getXRot();

        player.setYRot(normalizedBodyYaw);      // 修正：統一された値
        player.yBodyRot = normalizedBodyYaw;    // 再設定
        player.yBodyRotO = normalizedBodyYaw;   // 修正：統一された値

        // 視点角度を元に戻す（プレイヤーの自由な視点操作を保持）
        player.setYRot(originalYRot);
        player.setXRot(originalXRot);

        // *** 修正された方法3：強制的なクライアント同期 ***
        // EntityDataの更新
        player.getEntityData().set(net.minecraft.world.entity.Entity.DATA_POSE, net.minecraft.world.entity.Pose.STANDING);

        // 複数の同期パケットを送信
        player.connection.send(new ClientboundSetEntityDataPacket(
                player.getId(), player.getEntityData().packDirty()
        ));

        // 位置と角度の完全同期
        player.connection.send(new ClientboundTeleportEntityPacket(player));

        // *** 修正された方法4：遅延での再設定 ***
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                // 1tick後に再度設定（統一された値）
                player.yBodyRot = normalizedBodyYaw;
                player.yBodyRotO = normalizedBodyYaw;
            });
        }

        // デバッグ情報
        player.sendSystemMessage(Component.literal(String.format(
                "§d体角度設定完了: yBodyRot %.1f°→%.1f°, yBodyRotO %.1f°→%.1f°",
                oldBodyYaw, normalizedBodyYaw, oldBodyYawO, normalizedBodyYaw
        )));
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