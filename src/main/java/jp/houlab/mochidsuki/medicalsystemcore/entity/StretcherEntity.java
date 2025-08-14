package jp.houlab.mochidsuki.medicalsystemcore.entity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.util.StretcherPositionCalculator;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * 単純化されたストレッチャーエンティティ
 * 基本機能：運搬者の前方に移動し、乗っているプレイヤーの向きを同期する
 */
public class StretcherEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> CARRIER_UUID =
            SynchedEntityData.defineId(StretcherEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private Player carrier; // 運搬者
    private Player passenger; // 乗車者

    public StretcherEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CARRIER_UUID, Optional.empty());
    }

    /**
     * エンティティの更新処理（クライアント・サーバー共通ラグフリー版）
     */
    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            // クライアントサイドでの運搬者の復元
            if (this.carrier == null) {
                Optional<UUID> carrierUUID = this.entityData.get(CARRIER_UUID);
                if (carrierUUID.isPresent()) {
                    this.carrier = this.level().getPlayerByUUID(carrierUUID.get());
                }
            }

            // クライアントサイドでのリアルタイム位置更新（ラグフリー）
            if (this.carrier != null && this.carrier.isAlive()) {
                updatePositionRealtime();
                handlePassengerClientSide();
            }
            return;
        }

        // サーバーサイドの処理
        // 修正: 基本的な有効性チェックを毎ティック実行
        if (this.carrier == null || !this.carrier.isAlive() || this.carrier.isRemoved()) {
            this.returnStretcherToCarrier(); // 新仕様: 運搬者無効時はアイテム返還
            return;
        }

        // 修正: 距離チェックを軽量化（より頻繁にチェック）
        if (this.tickCount % 5 == 0) { // 0.25秒ごと（5ティック）に距離チェック
            double distance = this.distanceToSqr(this.carrier);
            if (distance > 100.0) { // 10ブロック以上離れた場合
                this.returnStretcherToCarrier(); // 新仕様: 距離が離れた場合アイテム返還
                return;
            }
        }

        // 乗車者の有効性チェック（毎ティック）
        if (this.passenger != null) {
            if (!this.passenger.isAlive() || this.passenger.isRemoved()) {
                this.passenger = null;
                this.returnStretcherToCarrier(); // 新仕様: 乗車者無効時はアイテム返還
                return;
            }
        }

        // 位置と向きを更新（共通ロジック使用）
        updatePositionWithCommonLogic();

        // 乗車者の処理（毎ティック実行）
        handlePassenger();
    }

    /**
     * クライアントサイドでのリアルタイム位置更新（ラグフリー）
     */
    private void updatePositionRealtime() {
        // 共通計算ロジックを使用（即座に更新）
        StretcherPositionCalculator.PositionResult result =
                StretcherPositionCalculator.calculateStretcherTransform(
                        this.carrier, this.position(), this.getYRot(), true
                );

        // 位置と向きを即座に設定（クライアントサイドでのラグフリー動作）
        this.setPos(result.position.x, result.position.y, result.position.z);
        this.setYRot(result.yaw);
    }

    /**
     * サーバーサイドでの位置更新（共通ロジック使用）
     */
    private void updatePositionWithCommonLogic() {
        // 共通計算ロジックを使用（補間あり）
        StretcherPositionCalculator.PositionResult result =
                StretcherPositionCalculator.calculateStretcherTransform(
                        this.carrier, this.position(), this.getYRot(), false
                );

        // 位置と向きを設定
        this.setPos(result.position.x, result.position.y, result.position.z);
        this.setYRot(result.yaw);

        // クライアントに位置変更を通知
        this.level().broadcastEntityEvent(this, (byte) 1);
    }

    /**
     * クライアントサイドでの乗車者処理
     */
    private void handlePassengerClientSide() {
        if (this.passenger != null && this.passenger.isAlive()) {
            // クライアントサイドでの向き同期（ラグフリー）
            syncPassengerRotationRealtime();
        }
    }

    /**
     * 乗車者の処理（高頻度更新版）
     */
    private void handlePassenger() {
        if (this.passenger != null) {
            // プレイヤーが有効かチェック
            if (this.passenger.isRemoved() || !this.passenger.isAlive()) {
                this.passenger = null;
                this.returnStretcherToCarrier(); // 新仕様: プレイヤーが無効な場合アイテム返還
                return;
            }

            // Shiftキーで降車
            if (this.passenger.isShiftKeyDown()) {
                this.passenger.stopRiding();
                this.passenger = null;
                this.returnStretcherToCarrier(); // 新仕様: 降車時にアイテム返還
                return;
            }

            // 乗車者の向きを同期（サーバーサイド・補間あり）
            syncPassengerRotationSmooth();
        }
    }

    /**
     * 新仕様: ストレッチャーを運搬者に返還（アイテムとして）
     */
    private void returnStretcherToCarrier() {
        // 既に削除されている場合は何もしない
        if (this.isRemoved()) {
            return;
        }

        if (!this.level().isClientSide()) {
            ItemStack stretcherItem = new ItemStack(Medicalsystemcore.STRETCHER.get());

            // 新仕様: 運搬者がいる場合は必ずアイテムを返還
            if (this.carrier != null && this.carrier.isAlive()) {
                boolean added = this.carrier.getInventory().add(stretcherItem);
                if (added) {
                    this.carrier.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aストレッチャーがインベントリに戻りました。"));
                } else {
                    dropItemAtCarrierLocation(stretcherItem);
                    this.carrier.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eインベントリがフルのため、ストレッチャーを地面にドロップしました。"));
                }
            } else {
                // 運搬者がいない場合は現在位置にドロップ
                this.spawnAtLocation(stretcherItem);
            }
        }

        // エンティティを削除
        this.discard();
    }

    /**
     * 乗車者の向きを同期（サーバーサイド用・滑らか版）
     */
    private void syncPassengerRotationSmooth() {
        if (!(this.passenger instanceof ServerPlayer serverPlayer)) return;

        // ストレッチャーの向きに合わせてプレイヤーの体の向きを設定
        float stretcherYaw = this.getYRot();

        // 滑らかな向きの補間
        float currentBodyYaw = serverPlayer.yBodyRot;
        float yawDiff = stretcherYaw - currentBodyYaw;

        // 角度の正規化
        while (yawDiff > 180.0f) yawDiff -= 360.0f;
        while (yawDiff < -180.0f) yawDiff += 360.0f;

        // 滑らかに補間して設定
        float newBodyYaw = currentBodyYaw + yawDiff * 0.3f;
        serverPlayer.yBodyRot = newBodyYaw;
        serverPlayer.yBodyRotO = newBodyYaw;
    }

    /**
     * 乗車者の向きを同期（リアルタイム版 - クライアントサイド用）
     */
    private void syncPassengerRotationRealtime() {
        if (!(this.passenger instanceof Player)) return;

        // ストレッチャーの向きに合わせてプレイヤーの体の向きを即座に設定
        float stretcherYaw = this.getYRot();
        this.passenger.yBodyRot = stretcherYaw;
        this.passenger.yBodyRotO = stretcherYaw;
    }

    /**
     * エンティティが削除される際の処理（新仕様対応）
     */
    @Override
    public void remove(RemovalReason pReason) {
        // 乗車者がいる場合は降車させる
        if (this.passenger != null) {
            this.passenger.stopRiding();
        }
        super.remove(pReason);
    }
    /**
     * 運搬者を設定
     */
    public void setCarrier(Player carrier) {
        this.carrier = carrier;
        this.entityData.set(CARRIER_UUID, carrier != null ? Optional.of(carrier.getUUID()) : Optional.empty());
    }

    /**
     * 乗車者を設定
     */
    public void setPassenger(Player passenger) {
        this.passenger = passenger;
        if (passenger != null) {
            passenger.startRiding(this);
        }
    }


    /**
     * 運搬者の位置にアイテムをドロップ
     */
    private void dropItemAtCarrierLocation(ItemStack item) {
        Vec3 carrierPos = this.carrier.position();
        ItemEntity itemEntity = new ItemEntity(this.level(), carrierPos.x, carrierPos.y, carrierPos.z, item);

        // アイテムが運搬者だけが拾えるように設定（5秒間）
        itemEntity.setPickUpDelay(10); // 0.5秒後から拾得可能
        if (this.carrier instanceof ServerPlayer serverPlayer) {
            itemEntity.setThrower(serverPlayer.getUUID()); // 投げた人として設定
        }

        this.level().addFreshEntity(itemEntity);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.1;
    }

    // === NBT保存・読み込み ===
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Carrier")) {
            this.entityData.set(CARRIER_UUID, Optional.of(tag.getUUID("Carrier")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.carrier != null) {
            tag.putUUID("Carrier", this.carrier.getUUID());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // アクセサメソッドを追加（他のクラスからアクセスするため）
    public Player getCarrier() {
        return this.carrier;
    }

    public Player getPassenger() {
        return this.passenger;
    }

    // === 静的ファクトリーメソッド（更新版） ===
    public static StretcherEntity create(Level level, Player carrier, Player passenger) {
        StretcherEntity stretcher = new StretcherEntity(Medicalsystemcore.STRETCHER_ENTITY.get(), level);

        // 乗車者がいる場合はその位置に、いない場合は運搬者の位置に配置
        if (passenger != null) {
            stretcher.setPos(passenger.position());
        } else {
            stretcher.setPos(carrier.position());
        }

        stretcher.setCarrier(carrier);

        // 乗車者がいる場合のみ設定
        if (passenger != null) {
            stretcher.setPassenger(passenger);
        }

        level.addFreshEntity(stretcher);
        return stretcher;
    }

}