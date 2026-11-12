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
    // 乗車者は独自に保持せず、バニラの騎乗状態（getFirstPassenger）を唯一の情報源にする。
    // 一度でも乗車者を確認したか（サーバーのみ）。乗車者が降りたことの検出に使う。
    private boolean passengerSeated = false;

    public StretcherEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CARRIER_UUID, Optional.empty());
    }

    // C:/Users/dora2/IdeaProjects/medicalsystemcore/src/main/java/jp/houlab/mochidsuki/medicalsystemcore/entity/StretcherEntity.java

    /**
     * エンティティの更新処理
     * クライアントサイド予測とサーバーサイド補正のハイブリッドモデルを実装
     */
    @Override
    public void tick() {
        super.tick();

        // --- サーバーサイドの処理 ---
        if (!this.level().isClientSide()) {
            // ロード後（再ログイン・再起動）はサーバー側の運搬者参照が無いので、保存された UUID から復元する
            if (this.carrier == null) {
                this.entityData.get(CARRIER_UUID).ifPresent(uuid ->
                        this.carrier = this.level().getPlayerByUUID(uuid)
                );
            }

            // 運搬者の有効性チェック
            if (this.carrier == null || !this.carrier.isAlive() || this.carrier.isRemoved()) {
                this.returnStretcherToCarrier();
                return;
            }
            // 距離チェック
            if (this.tickCount % 10 == 0 && this.distanceToSqr(this.carrier) > 100.0) {
                this.returnStretcherToCarrier();
                return;
            }

            // サーバーは常に「正解」の位置と向きを計算し、設定する
            // このデータはMinecraftの標準的な同期メカニズムでクライアントに送られる
            StretcherPositionCalculator.PositionResult result =
                    StretcherPositionCalculator.calculateStretcherTransform(
                            this.carrier, this.position(), this.getYRot(), false
                    );
            this.setPos(result.position.x, result.position.y, result.position.z);
            this.setYRot(result.yaw);

            // 乗車者の処理
            handlePassengerServerSide();
            return;
        }


        // --- クライアントサイドの処理 ---
        // 運搬者インスタンスを復元
        if (this.carrier == null) {
            this.entityData.get(CARRIER_UUID).ifPresent(uuid ->
                    this.carrier = this.level().getPlayerByUUID(uuid)
            );
        }

        if (this.carrier == null || !this.carrier.isAlive()) {
            return; // 運搬者がいなければ何もしない
        }

        // 運搬者がこのクライアントのプレイヤーである場合、完全な予測を行う
        if (this.carrier.isLocalPlayer()) {
            // プレイヤーの入力から「あるべき位置と向き」を即座に計算
            StretcherPositionCalculator.PositionResult targetResult =
                    StretcherPositionCalculator.calculateStretcherTransform(
                            this.carrier, this.position(), this.getYRot(), true
                    );

            // ★★★ 最重要: サーバーからの補正を考慮しつつ、滑らかに追従 ★★★
            // サーバーから送られてくる位置(this.position())と予測位置(targetResult.position)を補間する
            // これにより、操作のレスポンスを維持しつつ、サーバーとの大きなズレを防ぐ
            float interpolationFactor = 0.5f; // 補間の強さ（0.0～1.0）。大きいほど予測に近くなる。
            Vec3 interpolatedPos = this.position().lerp(targetResult.position, interpolationFactor);
            float interpolatedYaw = lerpYaw(this.getYRot(), targetResult.yaw, interpolationFactor);

            this.setPos(interpolatedPos);
            this.setYRot(interpolatedYaw);

        }
        // 他のプレイヤーが運搬者の場合、Minecraftの標準補間に任せる
        // (サーバーから送られてくる位置情報に自動で滑らかに追従する)


        // 乗車者の向きをリアルタイムで同期
        Player clientPassenger = this.getPassenger();
        if (clientPassenger != null && clientPassenger.isAlive()) {
            syncPassengerRotationRealtime(clientPassenger);
        }
    }

    /**
     * 角度を滑らかに補間する（-180～180度のラップアラウンドを考慮）
     * @param startAngle 開始角度
     * @param endAngle 終了角度
     * @param t 補間係数 (0.0 - 1.0)
     * @return 補間された角度
     */
    private float lerpYaw(float startAngle, float endAngle, float t) {
        float diff = endAngle - startAngle;
        while (diff < -180.0F) diff += 360.0F;
        while (diff >= 180.0F) diff -= 360.0F;
        return startAngle + diff * t;
    }

    /**
     * 乗車者の処理（サーバーサイド専用）
     */
    private void handlePassengerServerSide() {
        Player passenger = this.getPassenger();
        if (passenger == null) {
            // 乗せていた患者が降りた（Shift、搬送、切断など）場合は運搬者へ返却する。空の状態で回収された担架はそのまま運搬できる。
            if (this.passengerSeated) {
                this.returnStretcherToCarrier();
            }
            return;
        }
        this.passengerSeated = true;

        if (passenger.isRemoved() || !passenger.isAlive()) {
            passenger.stopRiding();
            this.returnStretcherToCarrier();
            return;
        }

        if (passenger.isShiftKeyDown()) {
            passenger.stopRiding();
            this.returnStretcherToCarrier();
            return;
        }

        // サーバーサイドで乗車者の体の向きをストレッチャーに同期
        if (passenger instanceof ServerPlayer serverPlayer) {
            float targetYaw = this.getYRot();
            serverPlayer.yBodyRot = lerpYaw(serverPlayer.yBodyRot, targetYaw, 0.3f);
            serverPlayer.yHeadRot = lerpYaw(serverPlayer.yHeadRot, targetYaw, 0.3f);
        }
    }

    // 以前のtick()メソッドで使われていた以下のヘルパーメソッドは不要になります。
    // private void updatePositionRealtime() { ... }
    // private void updatePositionWithCommonLogic() { ... }
    // private void handlePassengerClientSide() { ... }
    // private void handlePassenger() { ... }
    // private void syncPassengerRotationSmooth() { ... }

    /**
     * 乗車者がログアウトする際に、乗車者を降ろして担架を運搬者へ返却する（サーバー専用）。
     * 乗せたままログアウトされるとバニラが担架ごと保存・除去してしまい、運搬者に返らない。
     */
    public void releaseOnPassengerLogout() {
        this.ejectPassengers();
        this.returnStretcherToCarrier();
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
     * 乗車者の向きを同期（リアルタイム版 - クライアントサイド用）
     */
    private void syncPassengerRotationRealtime(Player passenger) {
        // ストレッチャーの向きに合わせてプレイヤーの体の向きを即座に設定
        float stretcherYaw = this.getYRot();
        passenger.yBodyRot = stretcherYaw;
        passenger.yBodyRotO = stretcherYaw;
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
        // 運搬者がまだ復元されていない状態でも UUID を失わないよう、同期データ側の UUID を保存する
        this.entityData.get(CARRIER_UUID).ifPresent(uuid -> tag.putUUID("Carrier", uuid));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // アクセサメソッドを追加（他のクラスからアクセスするため）
    public Player getCarrier() {
        return this.carrier;
    }

    @Nullable
    public Player getPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
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