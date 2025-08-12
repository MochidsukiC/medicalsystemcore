package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
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

import static jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager.isPlayerIncapacitated;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID)
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
                event.setCanceled(true);
                player.setHealth(0.1f);
                medicalData.setHeartStatus(HeartStatus.CARDIAC_ARREST);
                medicalData.setDamageImmune(true);

                // クライアントに状態変化を通知
                if (player instanceof ServerPlayer serverPlayer) {
                    ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                            serverPlayer.getUUID(),
                            HeartStatus.CARDIAC_ARREST,
                            medicalData
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
                if (medicalData.isDamageImmune() && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
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
            if (!event.getObject().getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).isPresent()) {
                event.addCapability(new ResourceLocation(Medicalsystemcore.MODID, "medical_data"), new PlayerMedicalDataProvider());
            }
        }
    }

    /**
     * エンティティがダメージを受けた時に呼び出されるイベント（Config値使用版）
     */
    @SubscribeEvent
    public static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getSource().is(ModDamageTypes.BLEEDING_KEY)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        float damageAmount = event.getAmount();

        player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            double speedIncrease = 0;

            // Config値を使用した出血判定
            if (damageAmount > 0.1f && damageAmount <= 2f) {
                if (Math.random() < Config.BLEEDING_CHANCE_LOW_DAMAGE) {
                    speedIncrease = Config.BLEEDING_SPEED_INCREASE_LOW;
                }
            } else if (damageAmount > 2f && damageAmount <= 5f) {
                speedIncrease = Config.BLEEDING_SPEED_INCREASE_MID;
            } else if (damageAmount > 5f && damageAmount <= 10f) {
                speedIncrease = Config.BLEEDING_SPEED_INCREASE_HIGH;
            } else if (damageAmount > 10f) {
                speedIncrease = Config.BLEEDING_SPEED_INCREASE_EXTREME;
            }

            if (speedIncrease > 0) {
                float newSpeed = medicalData.getBleedingSpeed() + (float) speedIncrease;
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
            // 点滴処理
            handleIVStandTreatment(serverPlayer, medicalData);

            // ティックカウンターの更新
            int ticks = medicalData.getTickCounter();
            medicalData.setTickCounter(ticks + 1);

            // 心停止関連処理
            handleCardiacArrest(serverPlayer, medicalData);

            // 出血回復処理
            handleBleedingRecovery(serverPlayer, medicalData);

            // 血液量変化処理
            handleBloodLoss(serverPlayer, medicalData);

            // 血液量ダメージ処理
            handleBloodDamage(serverPlayer, medicalData);

            // 意識状態判定
            handleConsciousnessState(serverPlayer, medicalData);

            // 心電図シミュレーション
            handleECGSimulation(serverPlayer, medicalData);

            // 状態同期
            ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                    serverPlayer.getUUID(),
                    medicalData.getHeartStatus(),
                    medicalData
            ), serverPlayer);
        });
    }

    /**
     * 点滴スタンドからの輸血・薬剤投与処理（Config値使用版）
     */
    private static void handleIVStandTreatment(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        medicalData.getTransfusingFromStandPos().ifPresent(standPos -> {
            BlockEntity be = serverPlayer.level().getBlockEntity(standPos);

            // Config値を使用した範囲チェック
            double maxRangeSquared = Config.IV_RANGE * Config.IV_RANGE;

            if (be instanceof IVStandBlockEntity standEntity &&
                    serverPlayer.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5) < maxRangeSquared) {

                boolean hasActiveTransfusion = false;

                for (int i = 0; i < standEntity.itemHandler.getSlots(); i++) {
                    ItemStack packStack = standEntity.itemHandler.getStackInSlot(i);
                    if (packStack.isEmpty()) continue;

                    CompoundTag nbt = packStack.getOrCreateTag();

                    // 初回使用時にConfig値から容量を設定
                    if (!nbt.contains("FluidVolumeTicks")) {
                        nbt.putInt("FluidVolumeTicks", Config.IV_PACK_DURATION * 20);
                    }

                    int ticksLeft = nbt.getInt("FluidVolumeTicks");

                    if (ticksLeft > 0) {
                        nbt.putInt("FluidVolumeTicks", ticksLeft - 1);
                        hasActiveTransfusion = true;

                        Item packItem = packStack.getItem();
                        applyPackEffect(serverPlayer, packItem);

                    } else {
                        if (packStack.getCount() > 1) {
                            packStack.shrink(1);
                            nbt.putInt("FluidVolumeTicks", Config.IV_PACK_DURATION * 20);
                            hasActiveTransfusion = true;
                            Item packItem = packStack.getItem();
                            applyPackEffect(serverPlayer, packItem);
                        } else {
                            standEntity.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                        }
                    }
                }

                if (!hasActiveTransfusion) {
                    medicalData.setTransfusingFromStandPos(Optional.empty());
                    serverPlayer.sendSystemMessage(Component.literal("§e点滴が完了しました。"));
                }

            } else {
                medicalData.setTransfusingFromStandPos(Optional.empty());
                serverPlayer.sendSystemMessage(Component.literal("§e点滴が外れました。"));
            }
        });
    }


    /**
     * パックの種類に応じてエフェクトを付与
     */
    private static void applyPackEffect(ServerPlayer player, Item packItem) {
        if (packItem == Medicalsystemcore.BLOOD_PACK.get()) {
            player.addEffect(new MobEffectInstance(Medicalsystemcore.TRANSFUSION.get(), 40, 0, true, false));
        } else if (packItem == Medicalsystemcore.ADRENALINE_PACK.get()) {
            player.addEffect(new MobEffectInstance(Medicalsystemcore.ADRENALINE_EFFECT.get(), 40, 0, true, false));
        } else if (packItem == Medicalsystemcore.FIBRINOGEN_PACK.get()) {
            // フィブリノゲン製剤：止血効果を付与
            player.addEffect(new MobEffectInstance(Medicalsystemcore.FIBRINOGEN_EFFECT.get(), 40, 0, true, false));
        } else if (packItem == Medicalsystemcore.TRANEXAMIC_ACID_PACK.get()) {
            // トラネキサム酸：止血効果を付与
            player.addEffect(new MobEffectInstance(Medicalsystemcore.TRANEXAMIC_ACID_EFFECT.get(), 40, 0, true, false));
        } else if (packItem == Medicalsystemcore.GLUCOSE_PACK.get()) {
            // ブドウ糖：Minecraftの既存の満腹度回復エフェクトを付与
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 100, 0, true, false));
        } else if (packItem == Medicalsystemcore.NORADRENALINE_PACK.get()) {
            // ノルアドレナリン：現在は未実装（将来的に実装予定）
            // player.addEffect(new MobEffectInstance(Medicalsystemcore.NORADRENALINE_EFFECT.get(), 40, 0, true, false));
        }
    }
    /**
     * 心停止時の処理（Config値使用版）
     */
    private static void handleCardiacArrest(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        HeartStatus currentStatus = medicalData.getHeartStatus();

        if (currentStatus != HeartStatus.NORMAL) {
            int arrestTimer = medicalData.getCardiacArrestTimer();
            medicalData.setCardiacArrestTimer(arrestTimer + 1);

            // Config値を使用した強制死亡判定
            int deathTimeTicks = Config.CARDIAC_ARREST_DEATH_TIME * 60 * 20;
            if (arrestTimer > deathTimeTicks || medicalData.getBloodLevel() <= 0) {
                serverPlayer.kill();
                return;
            }

            // Config値を使用した蘇生確率減少（毎分）
            if (currentStatus == HeartStatus.CARDIAC_ARREST && arrestTimer % (20 * 60) == 0) {
                double decayAmount = Config.RESUSCITATION_CHANCE_DECAY_RATE;
                medicalData.setResuscitationChance(medicalData.getResuscitationChance() - (float) decayAmount);

                if(medicalData.getResuscitationChance()<=0){
                    serverPlayer.kill();
                }
            }
        }
    }

    /**
     * 出血回復処理（Config値使用版、止血剤エフェクトは各エフェクトクラス内で処理）
     */
    private static void handleBleedingRecovery(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        float currentSpeed = medicalData.getBleedingSpeed();
        if (currentSpeed > 0) {
            double recoveryRatePerSecond = 0.0;
            int bandageLevel = 0;

            // 包帯エフェクトレベルを取得
            if (serverPlayer.hasEffect(Medicalsystemcore.BANDAGE_EFFECT.get())) {
                MobEffectInstance bandageEffect = serverPlayer.getEffect(Medicalsystemcore.BANDAGE_EFFECT.get());
                bandageLevel = bandageEffect.getAmplifier() + 1;
            }

            // Config値を使用した出血回復計算
            // 注意：フィブリノゲン・トラネキサム酸の効果は各エフェクトクラス内のapplyEffectTickで処理されるため、
            // ここでは既存の包帯と血小板エフェクトのみを処理
            recoveryRatePerSecond = Config.BLEEDING_RECOVERY_BASE_RATE *
                    (bandageLevel * Config.BLEEDING_RECOVERY_BANDAGE_MULTIPLIER);

            if (recoveryRatePerSecond > 0) {
                double recoveryPerTick = recoveryRatePerSecond / 20.0;
                float newSpeed = Math.max(0, currentSpeed - (float) recoveryPerTick);
                medicalData.setBleedingSpeed(newSpeed);
            }
        }
    }

    /**
     * 血液量変化処理（Config値使用版）
     */
    private static void handleBloodLoss(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        float currentSpeed = medicalData.getBleedingSpeed();

        if (currentSpeed > 0) {
            float bloodLossPerTick;

            if (medicalData.getHeartStatus() == HeartStatus.NORMAL) {
                // 健康状態: 出血速度/60/s
                bloodLossPerTick = currentSpeed / 60.0f / 20.0f;
            } else {
                // 意識障害時
                if (medicalData.getBloodLevel() < 30.0f) {
                    // 血液量30%以下: 出血速度/200/60/s
                    bloodLossPerTick = currentSpeed / 200.0f / 60.0f / 20.0f;
                } else {
                    // 意識障害: 出血速度/20/60/s
                    bloodLossPerTick = currentSpeed / 20.0f / 60.0f / 20.0f;
                }
            }

            float newBloodLevel = Math.max(0, medicalData.getBloodLevel() - bloodLossPerTick);
            medicalData.setBloodLevel(newBloodLevel);

        } else {
            // 出血していない場合は自然回復（Config値使用）
            float recoveryPerTick = (float) (Config.BLOOD_NATURAL_RECOVERY_RATE / 20.0);
            float newBloodLevel = Math.min(100.0f, medicalData.getBloodLevel() + recoveryPerTick);
            medicalData.setBloodLevel(newBloodLevel);
        }
    }

    /**
     * 血液量によるダメージ処理（Config値使用版）
     */
    private static void handleBloodDamage(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        if (medicalData.getHeartStatus() == HeartStatus.NORMAL) {
            int ticks = medicalData.getTickCounter();

            // Config値で設定されたダメージ間隔をチェック
            if (ticks % Config.BLOOD_DAMAGE_INTERVAL == 0) {
                float blood = medicalData.getBloodLevel();
                double damageAmount = 0;

                // Config値を使用した血液量判定
                if (blood < Config.BLOOD_DAMAGE_THRESHOLD_85 && blood >= Config.BLOOD_DAMAGE_THRESHOLD_70) {
                    damageAmount = Config.BLOOD_DAMAGE_85_70;
                } else if (blood < Config.BLOOD_DAMAGE_THRESHOLD_70 && blood >= Config.BLOOD_DAMAGE_THRESHOLD_65) {
                    damageAmount = Config.BLOOD_DAMAGE_70_65;
                } else if (blood < Config.BLOOD_DAMAGE_THRESHOLD_65 && blood >= Config.BLOOD_DAMAGE_THRESHOLD_60) {
                    damageAmount = Config.BLOOD_DAMAGE_65_60;
                } else if (blood < Config.BLOOD_DAMAGE_THRESHOLD_60) {
                    damageAmount = Config.BLOOD_DAMAGE_BELOW_60;
                }

                if (damageAmount > 0) {
                    serverPlayer.hurt(ModDamageTypes.bleeding(serverPlayer.level()), (float) damageAmount);
                }
            }
        }
    }

    /**
     * 意識状態の判定と制御
     */
    private static void handleConsciousnessState(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        HeartStatus newStatus = medicalData.getHeartStatus();

        // 意識障害時の姿勢制御
        if (newStatus != HeartStatus.NORMAL) {
            serverPlayer.setPose(Pose.SWIMMING);
        }

        boolean oldConscious = medicalData.isConscious();

        if(serverPlayer.getHealth()<5 || newStatus != HeartStatus.NORMAL){
            medicalData.setConscious(false);
        }else {
            medicalData.setConscious(true);
            if(!oldConscious){
                serverPlayer.setPose(Pose.STANDING);
            }
        }




        serverPlayer.refreshDimensions();
    }

    /**
     * 心電図シミュレーション処理
     */
    private static void handleECGSimulation(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        // 1. プレイヤー内で心拍数を確定
        HeartStatus status = medicalData.getHeartStatus();
        int heartRate = calculateHeartRateUnified(serverPlayer, status);
        medicalData.setHeartRate(heartRate);

        // 2. プレイヤー内で心拍数と心臓状態から心電位を生成
        updatePlayerHeartVector(serverPlayer, medicalData, status, heartRate);
    }

    /**
     * 統一された心拍数計算メソッド
     */
    private static int calculateHeartRateUnified(ServerPlayer player, HeartStatus status) {
        return switch (status) {
            case NORMAL -> {
                int base = 60 + player.level().random.nextInt(10);
                if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    base += (player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() + 1) * 10;
                }
                if (player.hasEffect(MobEffects.JUMP)) {
                    base += (player.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 10;
                }
                if (player.hasEffect(Medicalsystemcore.ADRENALINE_EFFECT.get())) {
                    base += 30;
                }
                yield Math.min(base, 200);
            }
            case VF -> {
                yield 300 + player.level().random.nextInt(100);
            }
            case CARDIAC_ARREST -> {
                yield 0;
            }
        };
    }

    /**
     * プレイヤーの心電位ベクトルを更新
     */
    private static void updatePlayerHeartVector(ServerPlayer player, IPlayerMedicalData medicalData, HeartStatus status, int heartRate) {
        float cycleTime = medicalData.getCycleTime();
        cycleTime += 0.05f;

        float cycleDuration = heartRate > 0 ? 60.0f / heartRate : Float.MAX_VALUE;

        if (cycleTime >= cycleDuration) {
            cycleTime -= cycleDuration;
        }

        float scalarPotential;
        float[] pathVector;

        switch (status) {
            case NORMAL -> {
                scalarPotential = calculateGaussianSumPotential(cycleTime, cycleDuration);
                pathVector = getHeartVectorPath(cycleTime, cycleDuration);
                scalarPotential *= (medicalData.getBloodLevel() / 100.0f);
            }
            case VF -> {
                scalarPotential = 1.0f;
                pathVector = getVFWaveform(player.level().getGameTime());
            }
            default -> {
                scalarPotential = 0.0f;
                pathVector = new float[]{0, 0};
            }
        }

        medicalData.setCycleTime(cycleTime);
        medicalData.setHeartVectorX(scalarPotential * pathVector[0]);
        medicalData.setHeartVectorY(scalarPotential * pathVector[1]);
    }

    /**
     * ガウス関数
     */
    private static float gaussian(float t, float a, float mu, float sigma) {
        return (float) (a * Math.exp(-Math.pow(t - mu, 2) / (2 * Math.pow(sigma, 2))));
    }

    /**
     * 正常洞調律のスカラーポテンシャルを計算
     */
    private static float calculateGaussianSumPotential(float cycleTime, float cycleDuration) {
        float p = gaussian(cycleTime, 0.2f, 0.12f * cycleDuration, 0.04f * cycleDuration);
        float q = gaussian(cycleTime, -0.15f, 0.28f * cycleDuration, 0.01f * cycleDuration);
        float r = gaussian(cycleTime, 1.2f, 0.30f * cycleDuration, 0.01f * cycleDuration);
        float s = gaussian(cycleTime, -0.3f, 0.32f * cycleDuration, 0.01f * cycleDuration);
        float t = gaussian(cycleTime, 0.35f, 0.50f * cycleDuration, 0.08f * cycleDuration);

        return p + q + r + s + t;
    }

    /**
     * 心臓ベクトルの経路関数
     */
    private static float[] getHeartVectorPath(float cycleTime, float cycleDuration) {
        float progress = cycleTime / cycleDuration;
        double angleRad = Math.toRadians(60);

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
     * プレイヤーのインタラクト（クリックなど）を監視するイベント
     */
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (isPlayerIncapacitated(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() != null && isPlayerIncapacitated(event.getPlayer())) {
            event.setCanceled(true);
        }
    }
}