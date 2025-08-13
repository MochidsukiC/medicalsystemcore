package jp.houlab.mochidsuki.medicalsystemcore.entity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

    @Override
    public void tick() {
        super.tick();

        // 運搬者を取得
        if (this.carrier == null && this.level().isClientSide()) {
            Optional<UUID> carrierUUID = this.entityData.get(CARRIER_UUID);
            if (carrierUUID.isPresent()) {
                this.carrier = this.level().getPlayerByUUID(carrierUUID.get());
            }
        }

        // 運搬者がいない場合は削除
        if (this.carrier == null || !this.carrier.isAlive()) {
            dropStretcher();
            return;
        }

        // 位置と向きを更新
        updatePosition();

        // 乗車者の処理
        handlePassenger();
    }

    /**
     * 位置と向きの更新（シンプル版）
     */
    private void updatePosition() {
        // 運搬者の前方1ブロックに配置
        Vec3 carrierPos = this.carrier.position();
        float carrierYaw = this.carrier.getYRot();

        // 前方への位置計算
        double radians = Math.toRadians(carrierYaw);
        double offsetX = -Math.sin(radians);
        double offsetZ = Math.cos(radians);

        Vec3 targetPos = carrierPos.add(offsetX, 0, offsetZ);
        this.setPos(targetPos.x, targetPos.y, targetPos.z);

        // ストレッチャーの向きは運搬者と垂直（90度回転）
        this.setYRot(carrierYaw + 90.0f);
    }

    /**
     * 乗車者の向きを同期（シンプル版）
     */
    private void syncPassengerRotation() {
        if (!(this.passenger instanceof ServerPlayer serverPlayer)) return;

        // プレイヤーの体をストレッチャーと同じ向きに設定
        float stretcherYaw = this.getYRot();
        serverPlayer.yBodyRot = stretcherYaw;
        serverPlayer.yBodyRotO = stretcherYaw;

        // 寝そべりポーズに設定
        serverPlayer.setPose(Pose.SLEEPING);
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
     * 乗車者の処理（tick内で呼び出し）
     */
    private void handlePassenger() {
        if (this.passenger != null) {
            // Shiftキーで降車
            if (this.passenger.isShiftKeyDown()) {
                this.passenger.stopRiding();
                this.passenger = null;
                return;
            }

            // 乗車者の向きを同期
            syncPassengerRotation();
        }
    }

    /**
     * ストレッチャーを削除してアイテムをドロップ
     */
    private void dropStretcher() {
        if (this.passenger != null) {
            this.passenger.stopRiding();
        }

        if (!this.level().isClientSide()) {
            this.spawnAtLocation(new ItemStack(Medicalsystemcore.STRETCHER.get()));
        }

        this.discard();
    }

    @Override
    public boolean shouldRiderSit() {
        return false; // 横たわる
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
    // === 静的ファクトリーメソッド ===
    public static StretcherEntity create(Level level, Player carrier, Player passenger) {
        StretcherEntity stretcher = new StretcherEntity(Medicalsystemcore.STRETCHER_ENTITY.get(), level);
        stretcher.setPos(passenger.position());
        stretcher.setCarrier(carrier);
        stretcher.setPassenger(passenger);
        level.addFreshEntity(stretcher);
        return stretcher;
    }
}