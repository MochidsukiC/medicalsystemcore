package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID) // MODIDはあなたのMod IDに合わせてください
public class ModEvents {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Player player) {
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // 既に心停止状態の場合は、そのまま死亡させる
                if (medicalData.getHeartStatus() != HeartStatus.NORMAL) {
                    // 死亡時にステータスをリセット
                    medicalData.setHeartStatus(HeartStatus.NORMAL);
                    medicalData.setDamageImmune(false);
                    return;
                }

                // --- 死亡をキャンセルし、心停止に移行させる ---
                event.setCanceled(true); // 死亡をキャンセル
                player.setHealth(0.1f); // HPを極微量で維持
                medicalData.setHeartStatus(HeartStatus.CARDIAC_ARREST);
                medicalData.setDamageImmune(true); // ダメージ無効状態にする

                // クライアントに状態変化を通知
                if (player instanceof ServerPlayer serverPlayer) {
                    ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                            serverPlayer.getUUID(),
                            medicalData.getBloodLevel(),
                            HeartStatus.CARDIAC_ARREST,
                            medicalData.getBleedingSpeed(), // 新しいbleedingSpeedを使用
                            medicalData.getResuscitationChance()
                    ), serverPlayer);
                }
            });
        }
    }

    /**
     * エンティティがダメージを受ける直前に呼ばれるイベント
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Player player) {
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // ダメージ無効フラグがtrueなら、いかなるダメージもキャンセルする
                if (medicalData.isDamageImmune()) {
                    event.setCanceled(true);
                }
            });
        }
    }

    /**
     * プレイヤーなどのエンティティにCapabilityをアタッチ（紐付け）するイベント
     */
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            // 他のModと競合しないように、ユニークな名前でCapabilityを登録します
            if (!event.getObject().getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).isPresent()) {
                event.addCapability(new ResourceLocation(Medicalsystemcore.MODID, "medical_data"), new PlayerMedicalDataProvider());
            }
        }
    }


    /**
     * プレイヤーが死亡してリスポーンした際に、データを引き継ぐためのイベント
     */
    /*
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // プレイヤーが死亡によるリスポーンの場合のみ処理
        if (event.isWasDeath()) {
            // 古い(死ぬ前)プレイヤーからCapabilityを取得
            event.getOriginal().getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(oldStore -> {
                // 新しい(リスポーン後)プレイヤーからCapabilityを取得
                event.getEntity().getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(newStore -> {
                    // データを移すためのNBTタグを新規作成
                    net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                    // oldStoreのデータをnbtに保存
                    oldStore.saveNBTData(nbt);
                    // nbtに保存したデータをnewStoreに読み込ませる
                    newStore.loadNBTData(nbt);
                });
            });
        }
    }

     */

    /**
     * エンティティがダメージを受けた時に呼び出されるイベント
     */
    @SubscribeEvent
    public static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getSource().is(ModDamageTypes.BLEEDING_KEY)) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) {
            return;
        }
        float damageAmount = event.getAmount();

        // 新しい出血速度の計算ロジック
        player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            float speedIncrease = 0;
            if (damageAmount > 0.1f && damageAmount <= 2f) {
                if (Math.random() < 0.10) { // 10%の確率
                    speedIncrease = 1.0f;
                }
            } else if (damageAmount > 2f && damageAmount <= 5f) {
                speedIncrease = 3.0f;
            } else if (damageAmount > 5f && damageAmount <= 10f) {
                speedIncrease = 6.0f;
            } else if (damageAmount > 10f) {
                speedIncrease = 9.0f;
            }

            if (speedIncrease > 0) {
                float newSpeed = medicalData.getBleedingSpeed() + speedIncrease;
                medicalData.setBleedingSpeed(newSpeed);
                player.sendSystemMessage(Component.literal("出血速度が " + String.format("%.2f", newSpeed) + " になった。"));
            }
        });
    }

    /**
     * プレイヤーのTickごとに呼び出されるイベント（サーバーサイド）
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;


        serverPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            medicalData.getTransfusingFromStandPos().ifPresent(standPos -> {
                BlockEntity be = serverPlayer.level().getBlockEntity(standPos);

                // --- 接続維持の条件 ---
                // スタンドが存在し、プレイヤーが範囲内にいるか
                if (be instanceof IVStandBlockEntity standEntity && serverPlayer.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5) < 100.0) {

                    // --- エフェクト適用の処理 ---
                    // 3つのスロットを全てループでチェック
                    for (int i = 0; i < standEntity.itemHandler.getSlots(); i++) {
                        ItemStack packStack = standEntity.itemHandler.getStackInSlot(i);
                        if (packStack.isEmpty()) {
                            continue;
                        }

                        CompoundTag nbt = packStack.getOrCreateTag();
                        int ticksLeft = nbt.getInt("FluidVolumeTicks");

                        if (ticksLeft > 0) {
                            nbt.putInt("FluidVolumeTicks", ticksLeft - 1);

                            Item packItem = packStack.getItem();
                            // (ここに、パックの種類に応じたエフェクトを付与するif-else if文が入る)
                            if (packItem == Medicalsystemcore.BLOOD_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.TRANSFUSION.get(), 40, 0, true, false));
                            } else if (packItem == Medicalsystemcore.ADRENALINE_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.ADRENALINE_EFFECT.get(), 40, 0, true, false));
                            }
                            // ▼▼▼ 以下のelse ifブロックを追加 ▼▼▼
                            else if (packItem == Medicalsystemcore.FIBRINOGEN_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.FIBRINOGEN_EFFECT.get(), 40, 0, true, false));
                            } else if (packItem == Medicalsystemcore.TRANEXAMIC_ACID_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.TRANEXAMIC_ACID_EFFECT.get(), 40, 0, true, false));
                            }
                        } else {
                             packStack.setCount(packStack.getCount() - 1);
                            nbt.putInt("FluidVolumeTicks", 60*20);
                        }
                    }
                }
                // --- 接続が切れる場合の処理 ---
                else {
                    medicalData.setTransfusingFromStandPos(Optional.empty());
                    serverPlayer.sendSystemMessage(Component.literal("§e点滴が外れた。"));
                }
            });



            //----------------------出血処理------------------------
            // --- 1. 変数の準備 ---
            int ticks = medicalData.getTickCounter();
            medicalData.setTickCounter(ticks + 1);

            HeartStatus currentStatus = medicalData.getHeartStatus();
            float bloodLevel = medicalData.getBloodLevel();

            // --- 2. 状態を更新するロジック ---
            // (このセクションでは、条件に基づいてmedicalDataの値を変更するだけ)
            if (currentStatus != HeartStatus.NORMAL) {
                // 心停止中の時間経過処理
                int arrestTimer = medicalData.getCardiacArrestTimer();
                medicalData.setCardiacArrestTimer(arrestTimer + 1);

                if (arrestTimer > 20 * 60 * 20 || bloodLevel <= 0) {
                    serverPlayer.kill();
                    return;
                }
                if (currentStatus == HeartStatus.CARDIAC_ARREST && ticks % (20*60) == 0) {
                    medicalData.setResuscitationChance(medicalData.getResuscitationChance() - 5.0f);
                }
            }

            // 出血処理 (これは状態を直接変更しないので、ここに残す)
            // ... (出血ダメージと血液量減少のロジック) ...

            float currentSpeed = medicalData.getBleedingSpeed();
            if (currentSpeed > 0) {
                float recoveryRatePerSecond = 0.0f;
                int bandageLevel = 0;
                int plateletEffectLevel = 0;

                // 包帯エフェクトのレベルを確認
                if (serverPlayer.hasEffect(Medicalsystemcore.BANDAGE_EFFECT.get())) {
                    bandageLevel = serverPlayer.getEffect(Medicalsystemcore.BANDAGE_EFFECT.get()).getAmplifier() + 3;
                }

                // ▼▼▼ 新しいエフェクトのチェックを追加 ▼▼▼
                // フィブリノゲンエフェクトのレベルを確認
                if (serverPlayer.hasEffect(Medicalsystemcore.FIBRINOGEN_EFFECT.get())) {
                    plateletEffectLevel += 5; // 効果は重複するので加算
                }
                // トラネキサム酸エフェクトのレベルを確認
                if (serverPlayer.hasEffect(Medicalsystemcore.TRANEXAMIC_ACID_EFFECT.get())) {
                    plateletEffectLevel += 5; // 効果は重複するので加算
                }

                // 仕様の計算式を適用
                // 出血回復 = 0.1 * (包帯レベル + 5 * (フィブリノゲン有無 + トラネキサム酸有無))
                recoveryRatePerSecond = 0.1f * (bandageLevel + 5 * plateletEffectLevel);

                if (recoveryRatePerSecond > 0) {
                    float recoveryPerTick = recoveryRatePerSecond /60.0f/ 20.0f;
                    medicalData.setBleedingSpeed(currentSpeed - recoveryPerTick);
                    currentSpeed = medicalData.getBleedingSpeed();
                }
            }


            if (currentSpeed > 0) {
                float bloodLossPerTick;
                // 健康状態か心不全かで計算式を変更
                if (medicalData.getHeartStatus() == HeartStatus.NORMAL) {
                    bloodLossPerTick = currentSpeed / 60.0f / 20.0f; // 速度 / 60秒 / 20ticks
                } else {
                    if (medicalData.getBloodLevel() < 30.0f) {
                        bloodLossPerTick = currentSpeed / 200.0f / 60.0f / 20.0f;
                    } else {
                        bloodLossPerTick = currentSpeed / 20.0f / 60.0f / 20.0f;
                    }
                }
                medicalData.setBloodLevel(medicalData.getBloodLevel() - bloodLossPerTick);
            } else {
                // 出血速度が0なら、血液量を回復
                // 0.5/s = 1tickあたり 0.5/20
                medicalData.setBloodLevel(medicalData.getBloodLevel() + 0.5f / 20.0f);
            }


            if (medicalData.getHeartStatus() == HeartStatus.NORMAL) {
                // --- 低血液量による継続ダメージ ---
                if (ticks % 100 == 0) { // 5秒 (100 ticks) ごとに実行
                    float blood = medicalData.getBloodLevel();
                    float damageAmount = 0;

                    if (blood < 85 && blood >= 70) {
                        damageAmount = 4.0f;
                    } else if (blood < 70 && blood >= 65) {
                        damageAmount = 8.0f;
                    } else if (blood < 65 && blood >= 60) {
                        damageAmount = 12.0f;
                    } else if (blood < 60) {
                        damageAmount = 99999f;
                    }

                    if (damageAmount > 0) {
                        // ▼▼▼ ここを変更 ▼▼▼
                        // lowBloodVolumeの代わりに、既存のbleedingダメージタイプを使用
                        serverPlayer.hurt(ModDamageTypes.bleeding(serverPlayer.level()), damageAmount);
                    }
                }
            }


            // --- 3. 状態変化の検知と、それに伴う処理 ---
            // (このセクションでは、変更されたmedicalDataの値を見て、1回限りのアクションを実行する)
            HeartStatus newStatus = medicalData.getHeartStatus();
                // 状態が変化した瞬間にのみ実行
                if (newStatus != HeartStatus.NORMAL) {
                    // 倒れる処理
                    serverPlayer.setPose(Pose.SWIMMING); // <-- ここを修正
                } else {
                    // 起き上がる処理
                }
                serverPlayer.refreshDimensions(); // 当たり判定を更新

                // 全クライアントに状態を同期
            ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                    serverPlayer.getUUID(),
                    medicalData.getBloodLevel(),
                    newStatus,
                    medicalData.getBleedingSpeed(),
                    medicalData.getResuscitationChance()
            ), serverPlayer);

            // --- 4. 最後に、今の状態を「前の状態」として保存 ---
            //medicalData.setPreviousHeartStatus(newStatus);

            HeartStatus status = medicalData.getHeartStatus();
            float cycleTime = medicalData.getCycleTime();

            // 1ティックあたりの時間を加算 (1秒 = 20tick)
            cycleTime += 0.05f;

            int heartRate = calculateHeartRate(serverPlayer, status);
            float cycleDuration = heartRate > 0 ? 60.0f / heartRate : Float.MAX_VALUE;

            if (cycleTime >= cycleDuration) {
                cycleTime -= cycleDuration; // 周期リセット
            }

            float scalarPotential;
            float[] pathVector;

            switch (status) {
                case NORMAL -> {
                    // ガウス関数加算モデルでスカラーポテンシャルを計算
                    scalarPotential = calculateGaussianSumPotential(cycleTime, cycleDuration);
                    // 経路関数でベクトル方向を計算
                    pathVector = getHeartVectorPath(cycleTime, cycleDuration);
                    // 血液量に応じて振幅を調整
                    scalarPotential *= (medicalData.getBloodLevel() / 100.0f);
                }
                case VF -> {
                    // フィルタリングされたノイズモデル
                    scalarPotential = 1.0f;
                    pathVector = getVFWaveform(serverPlayer.level().getGameTime());
                }
                default -> { // CARDIAC_ARREST
                    scalarPotential = 0.0f;
                    pathVector = new float[]{0, 0};
                }
            }

            // Capabilityに計算結果を保存
            medicalData.setCycleTime(cycleTime);
            medicalData.setHeartVectorX(scalarPotential * pathVector[0]);
            medicalData.setHeartVectorY(scalarPotential * pathVector[1]);

            if(serverPlayer.getUUID().equals(UUID.fromString("0b9d2ada-2ed1-4fee-89c9-fa7d3fdd9ba2"))){
            System.out.println("CycleTime: " + cycleTime + " cycleDuration: " + cycleDuration + " ScalarPotential:" +scalarPotential +" pathVector0:"+ pathVector[0] + " pathVector1:"+ pathVector[1]);
}
        });
    }

    /**
     * プレイヤーが行動不能かチェックするヘルパーメソッド
     */
    private static boolean isPlayerIncapacitated(net.minecraft.world.entity.player.Player player) {
        // capabilityを取得し、心臓が正常でない場合は行動不能と判断
        return player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                .map(data -> data.getHeartStatus() != HeartStatus.NORMAL)
                .orElse(false);
    }

    /**
     * ガウス関数 (本報告書 2.2節)
     * a:振幅, mu:中心位置, sigma:幅
     */
    private static float gaussian(float t, float a, float mu, float sigma) {
        return (float) (a * Math.exp(-Math.pow(t - mu, 2) / (2 * Math.pow(sigma, 2))));
    }

    /**
     * 正常洞調律のスカラーポテンシャルを計算 (パラメータ調整版)
     */
    private static float calculateGaussianSumPotential(float cycleTime, float cycleDuration) {
        // P波 (少し幅を広げ、高さを調整)
        float p = gaussian(cycleTime, 0.2f, 0.12f * cycleDuration, 0.04f * cycleDuration);
        // QRS波 (R波を少しシャープに)
        float q = gaussian(cycleTime, -0.15f, 0.28f * cycleDuration, 0.01f * cycleDuration);
        float r = gaussian(cycleTime, 1.2f, 0.30f * cycleDuration, 0.01f * cycleDuration);
        float s = gaussian(cycleTime, -0.3f, 0.32f * cycleDuration, 0.01f * cycleDuration);
        // T波 (大幅に幅を広げ、出現タイミングを調整)
        float t = gaussian(cycleTime, 0.35f, 0.50f * cycleDuration, 0.08f * cycleDuration);

        return p + q + r + s + t;
    }

    private static float[] getHeartVectorPath(float cycleTime, float cycleDuration) {
        float progress = cycleTime / cycleDuration;
        // P波とT波は60度、QRS波は50-70度あたりを動くベクトルを簡易的に表現
        double angleRad = Math.toRadians(60);

        // ★★★ 修正点 ★★★
        // QRS波のタイミング判定を、スカラーポテンシャル計算(calculateGaussianSumPotential)の
        // タイミング (0.28 ~ 0.32) と一致させます。
        // これにより、波形の振幅とベクトルの方向が正しく同期します。
        if (progress > 0.27 && progress < 0.33) { // 0.28-0.32を少し広めにカバー
            // 0.27から0.33の範囲で、角度が50度から70度へ線形に変化するように計算
            double qrsProgress = (progress - 0.27) / (0.33 - 0.27);
            angleRad = Math.toRadians(50 + qrsProgress * 20);
        }
        return new float[]{(float) Math.cos(angleRad), (float) Math.sin(angleRad)};
    }

    /**
     * VFの波形を計算 (本報告書 2.3.1節)
     */
    private static float[] getVFWaveform(long gameTime){
        float time = (float)gameTime / 20.0f;
        // 複数のサイン波と乱数を合成
        float x = (float)(Math.sin(time * 8) * 0.4 + Math.sin(time * 15) * 0.6 + (Math.random() - 0.5) * 0.3);
        float y = (float)(Math.sin(time * 7) * 0.5 + Math.sin(time * 18) * 0.5 + (Math.random() - 0.5) * 0.3);
        return new float[]{x, y};
    }


    /**
     * プレイヤーのインタラクト（クリックなど）を監視するイベント
     */
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (isPlayerIncapacitated(event.getEntity())) {
            // 行動不能なプレイヤーのアクションをキャンセル
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        // アイテムを投げたのがプレイヤーで、かつそのプレイヤーが行動不能な場合
        if (event.getPlayer() != null && isPlayerIncapacitated(event.getPlayer())) {
            // イベントをキャンセルしてアイテムドロップを防ぐ
            event.setCanceled(true);
        }
    }

    public static int calculateHeartRate(Player player, HeartStatus status) {
        return switch (status) {
            case NORMAL -> {
                int base = 60 + player.level().random.nextInt(10);
                if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    base += (player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() + 1) * 10;
                }
                if (player.hasEffect(MobEffects.JUMP)) {
                    base += (player.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 10;
                }
                yield base;
            }
            case VF -> 300 + player.level().random.nextInt(100);
            case CARDIAC_ARREST -> 0;
        };
    }

}
