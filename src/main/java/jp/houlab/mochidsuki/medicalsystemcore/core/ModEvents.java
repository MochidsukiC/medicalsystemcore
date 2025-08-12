package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
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
     * アルゴリズム：
     * 1. プレイヤー内で心拍数を確定(大まかな心臓状態と乱数)
     * 2. プレイヤー内で心拍数と心臓状態と乱数から心電位を医学的にシミュレートし生成
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;

        serverPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            // 既存の医療処理（点滴処理）
            medicalData.getTransfusingFromStandPos().ifPresent(standPos -> {
                BlockEntity be = serverPlayer.level().getBlockEntity(standPos);
                if (be instanceof IVStandBlockEntity standEntity && serverPlayer.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5) < 100.0) {
                    for (int i = 0; i < standEntity.itemHandler.getSlots(); i++) {
                        ItemStack packStack = standEntity.itemHandler.getStackInSlot(i);
                        if (packStack.isEmpty()) continue;

                        CompoundTag nbt = packStack.getOrCreateTag();
                        int ticksLeft = nbt.getInt("FluidVolumeTicks");

                        if (ticksLeft > 0) {
                            nbt.putInt("FluidVolumeTicks", ticksLeft - 1);
                            Item packItem = packStack.getItem();
                            if (packItem == Medicalsystemcore.BLOOD_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.TRANSFUSION.get(), 40, 0, true, false));
                            } else if (packItem == Medicalsystemcore.ADRENALINE_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.ADRENALINE_EFFECT.get(), 40, 0, true, false));
                            } else if (packItem == Medicalsystemcore.FIBRINOGEN_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.FIBRINOGEN_EFFECT.get(), 40, 0, true, false));
                            } else if (packItem == Medicalsystemcore.TRANEXAMIC_ACID_PACK.get()) {
                                serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.TRANEXAMIC_ACID_EFFECT.get(), 40, 0, true, false));
                            }
                        } else {
                            packStack.setCount(packStack.getCount() - 1);
                            nbt.putInt("FluidVolumeTicks", 60*20);
                        }
                    }
                } else {
                    medicalData.setTransfusingFromStandPos(Optional.empty());
                    serverPlayer.sendSystemMessage(Component.literal("§e点滴が外れた。"));
                }
            });

            // 既存の出血処理
            int ticks = medicalData.getTickCounter();
            medicalData.setTickCounter(ticks + 1);

            HeartStatus currentStatus = medicalData.getHeartStatus();
            float bloodLevel = medicalData.getBloodLevel();

            if (currentStatus != HeartStatus.NORMAL) {
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

            float currentSpeed = medicalData.getBleedingSpeed();
            if (currentSpeed > 0) {
                float recoveryRatePerSecond = 0.0f;
                int bandageLevel = 0;
                int plateletEffectLevel = 0;

                if (serverPlayer.hasEffect(Medicalsystemcore.BANDAGE_EFFECT.get())) {
                    bandageLevel = serverPlayer.getEffect(Medicalsystemcore.BANDAGE_EFFECT.get()).getAmplifier() + 3;
                }

                if (serverPlayer.hasEffect(Medicalsystemcore.FIBRINOGEN_EFFECT.get())) {
                    plateletEffectLevel += 5;
                }
                if (serverPlayer.hasEffect(Medicalsystemcore.TRANEXAMIC_ACID_EFFECT.get())) {
                    plateletEffectLevel += 5;
                }

                recoveryRatePerSecond = 0.1f * (bandageLevel + 5 * plateletEffectLevel);

                if (recoveryRatePerSecond > 0) {
                    float recoveryPerTick = recoveryRatePerSecond /60.0f/ 20.0f;
                    medicalData.setBleedingSpeed(currentSpeed - recoveryPerTick);
                    currentSpeed = medicalData.getBleedingSpeed();
                }
            }

            if (currentSpeed > 0) {
                float bloodLossPerTick;
                if (medicalData.getHeartStatus() == HeartStatus.NORMAL) {
                    bloodLossPerTick = currentSpeed / 60.0f / 20.0f;
                } else {
                    if (medicalData.getBloodLevel() < 30.0f) {
                        bloodLossPerTick = currentSpeed / 200.0f / 60.0f / 20.0f;
                    } else {
                        bloodLossPerTick = currentSpeed / 20.0f / 60.0f / 20.0f;
                    }
                }
                medicalData.setBloodLevel(medicalData.getBloodLevel() - bloodLossPerTick);
            } else {
                medicalData.setBloodLevel(medicalData.getBloodLevel() + 0.5f / 20.0f);
            }

            if (medicalData.getHeartStatus() == HeartStatus.NORMAL) {
                if (ticks % 100 == 0) {
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
                        serverPlayer.hurt(ModDamageTypes.bleeding(serverPlayer.level()), damageAmount);
                    }
                }
            }

            HeartStatus newStatus = medicalData.getHeartStatus();
            if (newStatus != HeartStatus.NORMAL) {
                serverPlayer.setPose(Pose.SWIMMING);
            }
            serverPlayer.refreshDimensions();

            // === 心電図シミュレーション（正しいアルゴリズム） ===

            // 1. プレイヤー内で心拍数を確定(大まかな心臓状態と乱数)
            HeartStatus status = medicalData.getHeartStatus();
            int heartRate = calculateHeartRateUnified(serverPlayer, status);
            medicalData.setHeartRate(heartRate); // ★ここで心拍数をCapabilityに保存


            // 2. プレイヤー内で心拍数と心臓状態と乱数から心電位を医学的にシミュレートし生成
            updatePlayerHeartVector(serverPlayer, medicalData, status, heartRate);

            // 状態同期
            ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                    serverPlayer.getUUID(),
                    medicalData.getBloodLevel(),
                    newStatus,
                    medicalData.getBleedingSpeed(),
                    medicalData.getResuscitationChance()
            ), serverPlayer);
        });
    }

    /**
     * 統一された心拍数計算メソッド
     * 全てのモニターで同じ値を使用するため、プレイヤー側で一度だけ計算
     */
    private static int calculateHeartRateUnified(ServerPlayer player, HeartStatus status) {
        return switch (status) {
            case NORMAL -> {
                // 基本心拍数 + エフェクトによる変動
                int base = 60 + player.level().random.nextInt(10);
                if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    base += (player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() + 1) * 10;
                }
                if (player.hasEffect(MobEffects.JUMP)) {
                    base += (player.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 10;
                }
                // アドレナリンエフェクトによる心拍数上昇
                if (player.hasEffect(Medicalsystemcore.ADRENALINE_EFFECT.get())) {
                    base += 30; // アドレナリンで心拍数+30
                }
                yield Math.min(base, 200); // 最大200bpmに制限
            }
            case VF -> {
                // VF時は非常に高い心拍数（測定困難な状態）
                yield 300 + player.level().random.nextInt(100); // 300-400bpm
            }
            case CARDIAC_ARREST -> {
                // 心停止時は心拍数0
                yield 0;
            }
        };
    }

    /**
     * プレイヤーの心電位ベクトルを更新
     * 心拍数はすでにCapabilityに保存されている値を使用
     */
    private static void updatePlayerHeartVector(ServerPlayer player, IPlayerMedicalData medicalData, HeartStatus status, int heartRate) {
        float cycleTime = medicalData.getCycleTime();

        // 1ティックあたりの時間を加算 (1秒 = 20tick)
        cycleTime += 0.05f;

        float cycleDuration = heartRate > 0 ? 60.0f / heartRate : Float.MAX_VALUE;

        // 周期リセット
        if (cycleTime >= cycleDuration) {
            cycleTime -= cycleDuration;
        }

        // 心臓ベクトルの計算
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
                pathVector = getVFWaveform(player.level().getGameTime());
            }
            default -> { // CARDIAC_ARREST
                scalarPotential = 0.0f;
                pathVector = new float[]{0, 0};
            }
        }

        // Capabilityに計算結果を保存（プレイヤー側で心電位を生成）
        medicalData.setCycleTime(cycleTime);
        medicalData.setHeartVectorX(scalarPotential * pathVector[0]);
        medicalData.setHeartVectorY(scalarPotential * pathVector[1]);
    }

    /**
     * ガウス関数 (本報告書 2.2節)
     */
    private static float gaussian(float t, float a, float mu, float sigma) {
        return (float) (a * Math.exp(-Math.pow(t - mu, 2) / (2 * Math.pow(sigma, 2))));
    }

    /**
     * 正常洞調律のスカラーポテンシャルを計算
     */
    private static float calculateGaussianSumPotential(float cycleTime, float cycleDuration) {
        // P波
        float p = gaussian(cycleTime, 0.2f, 0.12f * cycleDuration, 0.04f * cycleDuration);
        // QRS波
        float q = gaussian(cycleTime, -0.15f, 0.28f * cycleDuration, 0.01f * cycleDuration);
        float r = gaussian(cycleTime, 1.2f, 0.30f * cycleDuration, 0.01f * cycleDuration);
        float s = gaussian(cycleTime, -0.3f, 0.32f * cycleDuration, 0.01f * cycleDuration);
        // T波
        float t = gaussian(cycleTime, 0.35f, 0.50f * cycleDuration, 0.08f * cycleDuration);

        return p + q + r + s + t;
    }

    /**
     * 心臓ベクトルの経路関数
     */
    private static float[] getHeartVectorPath(float cycleTime, float cycleDuration) {
        float progress = cycleTime / cycleDuration;
        double angleRad = Math.toRadians(60);

        // QRS波の期間では角度を変化させる
        if (progress > 0.27 && progress < 0.33) {
            double qrsProgress = (progress - 0.27) / (0.33 - 0.27);
            angleRad = Math.toRadians(50 + qrsProgress * 20);
        }
        return new float[]{(float) Math.cos(angleRad), (float) Math.sin(angleRad)};
    }

    /**
     * VF波形生成
     */
    private static float[] getVFWaveform(long gameTime) {
        float time = (float)gameTime / 20.0f;
        float x = (float)(Math.sin(time * 8) * 0.4 + Math.sin(time * 15) * 0.6 + (Math.random() - 0.5) * 0.3);
        float y = (float)(Math.sin(time * 7) * 0.5 + Math.sin(time * 18) * 0.5 + (Math.random() - 0.5) * 0.3);
        return new float[]{x, y};
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


}
