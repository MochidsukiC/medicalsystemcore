package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.IPlayerMedicalData;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BandageRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BleedingRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.BloodRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.CardiacRules;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.IvRules;
import jp.houlab.mochidsuki.medicalsystemcore.item.FluidPackItem;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundCoreStatsPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Player player) {
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // 心停止中の死亡と、奈落による死亡はそのまま死なせる。
                // 奈落を心停止にすり替えると、奈落の下で救助不能のまま落ち続けるため。
                // /kill(generic_kill)は仕様により、通常時は心停止になり、心停止中なら死亡する(上の HeartStatus 判定で成立)。
                boolean cannotBeSaved = event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD);
                if (medicalData.getHeartStatus() != HeartStatus.NORMAL || cannotBeSaved) {
                    medicalData.setHeartStatus(HeartStatus.NORMAL);
                    medicalData.setDamageImmune(false);
                    medicalData.setConscious(true);
                    // 姿勢制御をクリアして泳ぎ姿勢を解除し、全クライアントに同期。
                    // 意識不明(HP が低い NORMAL)のまま奈落で死ぬ場合もあるので、心停止でなくても必ず解除する。
                    if (player instanceof ServerPlayer serverPlayer) {
                        PoseController.clearPoseControl(serverPlayer);
                        ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                                serverPlayer.getUUID(),
                                HeartStatus.NORMAL,
                                medicalData
                        ), serverPlayer);
                    }
                    return;
                }

                event.setCanceled(true);
                player.setHealth(0.1f);
                medicalData.setHeartStatus(HeartStatus.CARDIAC_ARREST);
                medicalData.setDamageImmune(true);

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

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Player player) {
            player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
                // 心停止中の免疫でも、奈落・/kill のような無敵貫通ダメージは通す(onLivingDeath で死亡を成立させる)
                if (medicalData.isDamageImmune() && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                    event.setCanceled(true);
                }
            });
        }
    }

    /**
     * 医療データのコピー(エンドからの帰還)。
     * 死亡時(isWasDeath=true)は仕様どおりコピーしない(新しいプレイヤーは初期値から始まる)。
     * 帰還時(isWasDeath=false)は旧プレイヤーの医療データを新しいプレイヤーへ引き継ぐ。
     *
     * <p>Forge 1.20.1 では旧プレイヤーは removed になり capability が無効化されているため、
     * 読む間だけ reviveCaps() で復活させ、終わったら invalidateCaps() で戻す。
     * コピーは saveNBTData/loadNBTData の対を通す(保存形式と同じ内容が引き継がれ、点滴の接続先も含む)。
     * 姿勢とクライアント同期は、直後の PlayerRespawnEvent と次の tick で医療データから再導出される。</p>
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            original.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(oldData ->
                    event.getEntity().getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(newData -> {
                        CompoundTag copy = new CompoundTag();
                        oldData.saveNBTData(copy);
                        newData.loadNBTData(copy);
                    }));
        } finally {
            original.invalidateCaps();
        }
    }

    // 以下のイベントでは、サーバー側の強制姿勢を医療データから組み立て直す。
    // 強制姿勢(Entity#forcedPose)と PoseController の状態は保存されないため、
    // 医療データ(isConscious)だけが残る再ログイン・サーバー再起動・リスポーンで失われる。

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            restorePoseFromMedicalData(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 死亡後のリスポーンでは医療データが初期値なので姿勢制御は解除され、
        // エンドからの帰還ではコピーされた医療データに合わせて再設定される。
        // PoseController の状態は UUID で保持されるので、前のインスタンスの状態が残らないよう必ず作り直す。
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            restorePoseFromMedicalData(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // クライアントはレベルの切り替えで姿勢制御の状態を消す(ClientWorldHandler)ので、すぐ送り直す
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                    .ifPresent(medicalData -> syncCoreStats(serverPlayer, medicalData));
        }
    }

    /** 他のプレイヤーが追跡を始めたとき、その人に対象の現在の状態を 1 回送る(以後は変化時と定期再送で届く)。 */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker) {
            target.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData ->
                    ModPackets.sendToPlayer(new ClientboundCoreStatsPacket(
                            target.getUUID(), medicalData.getHeartStatus(), medicalData), tracker));
        }
    }

    private static void restorePoseFromMedicalData(ServerPlayer serverPlayer) {
        PoseController.clearPoseControl(serverPlayer);
        serverPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            if (!medicalData.isConscious()) {
                PoseController.setUnconsciousPose(serverPlayer, true);
            }
        });
    }

    /**
     * CoreStats の同期(B32)。毎 tick 全員へ送るとプレイヤー数の二乗で増えるため、次のときだけ送る。
     * <ul>
     *   <li>heartStatus か意識が変わったとき(即時)。クライアントはこの 2 つで姿勢制御を切り替える。</li>
     *   <li>前回から {@link #CORE_STATS_RESYNC_INTERVAL_TICKS} tick 経ったとき(再送)。
     *       クライアントはレベル切り替えで姿勢制御の状態を消すため、取りこぼしを自己修復する安全網。</li>
     *   <li>追跡開始時・ディメンション移動時(個別のイベントで 1 回)。</li>
     * </ul>
     * クライアントが CoreStats から読むのは isConscious/heartStatus(姿勢制御)だけで、補間は要らない。
     * HUD の進捗バーはクライアントのタイマー、ヘッドサイドモニター・心電図は BlockEntity の同期で動き、このパケットを使わない。
     * 血液量などの数値は現状どの表示も参照しない。
     */
    private static final int CORE_STATS_RESYNC_INTERVAL_TICKS = 20;

    private static void syncCoreStatsIfNeeded(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        if (medicalData.needsCoreStatsSync(serverPlayer.level().getGameTime(), CORE_STATS_RESYNC_INTERVAL_TICKS)) {
            syncCoreStats(serverPlayer, medicalData);
        }
    }

    private static void syncCoreStats(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        ModPackets.sendToAllTracking(new ClientboundCoreStatsPacket(
                serverPlayer.getUUID(),
                medicalData.getHeartStatus(),
                medicalData
        ), serverPlayer);
        medicalData.markCoreStatsSynced(serverPlayer.level().getGameTime());
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).isPresent()) {
                event.addCapability(new ResourceLocation(Medicalsystemcore.MODID, "medical_data"), new PlayerMedicalDataProvider());
            }
        }
    }

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
            double speedIncrease = BleedingRules.speedIncrease(
                    damageAmount, Math.random(), BleedingRules.IncreaseTable.fromConfig());

            if (speedIncrease > 0) {
                float newSpeed = medicalData.getBleedingSpeed() + (float) speedIncrease;
                medicalData.setBleedingSpeed(newSpeed);
                player.sendSystemMessage(Component.literal("出血速度が " + String.format("%.2f", newSpeed) + " になった。"));
            }
        });
    }

    /**
     * 意識不明時のアイテムドロップ制限（強化版）
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemStack tossedItem = event.getEntity().getItem();

        // 電極が投げ捨てられた場合の特別処理（既存のロジック）
        if (tossedItem.is(Medicalsystemcore.ELECTRODE.get())) {
            BlockPos defibrillatorPos = jp.houlab.mochidsuki.medicalsystemcore.item.ElectrodeItem.getLinkedPos(tossedItem);
            if (defibrillatorPos != null) {
                if (event.getPlayer().level() instanceof ServerLevel serverLevel) {
                    BlockEntity be = serverLevel.getBlockEntity(defibrillatorPos);
                    // 現在発行中の電極だけが除細動器へ戻る(再発行で無効になった電極・他機の電極は普通のアイテムとして落ちる)
                    if (be instanceof jp.houlab.mochidsuki.medicalsystemcore.blockentity.DefibrillatorBlockEntity defibrillator
                            && jp.houlab.mochidsuki.medicalsystemcore.item.ElectrodeItem.isLinkedTo(tossedItem, defibrillator)) {
                        defibrillator.returnPads();
                        if (defibrillator.getLevel() != null) {
                            defibrillator.getLevel().setBlock(
                                    defibrillatorPos,
                                    defibrillator.getBlockState().setValue(
                                            jp.houlab.mochidsuki.medicalsystemcore.block.DefibrillatorBlock.HAS_PADS,
                                            true
                                    ),
                                    3
                            );
                        }
                        defibrillator.setChanged();
                        event.getEntity().discard();
                        return; // 電極の場合は以下の意識不明チェックをスキップ
                    }
                }
            }
        }

        // 意識不明時のアイテム投げ捨てを防ぐ（強化版）
        if (event.getPlayer() != null && isPlayerUnconscious(event.getPlayer())) {
            event.setCanceled(true);

            // アイテムを強制的にプレイヤーのインベントリに戻す
            Player player = event.getPlayer();
            ItemStack droppedStack = event.getEntity().getItem();

            // インベントリに空きがあれば戻す、なければ強制的に手に持たせる
            if (!player.getInventory().add(droppedStack)) {
                // インベントリが満杯の場合は、現在持っているアイテムと交換
                ItemStack currentItem = player.getMainHandItem();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, droppedStack);
                if (!currentItem.isEmpty()) {
                    // 交換されたアイテムを地面にドロップ（正常時のみ）
                    if (!isPlayerUnconscious(player)) {
                        player.drop(currentItem, false);
                    }
                }
            }

            // デバッグメッセージ
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("§c意識不明のためアイテムを落とすことができません。"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;

        // 死亡後、リスポーンするまでの間も PlayerTickEvent は発火する。
        // この間に意識判定や姿勢維持を走らせると、死亡時に解除した UNCONSCIOUS 姿勢が再登録され、
        // リスポーン後の新しいプレイヤー(同じ UUID)に残ってしまう(B07)。
        if (!serverPlayer.isAlive()) return;

        serverPlayer.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            handleIVTransfusion(serverPlayer, medicalData);

            int ticks = medicalData.getTickCounter();
            medicalData.setTickCounter(ticks + 1);

            handleCardiacArrest(serverPlayer, medicalData);
            // handleCardiacArrest の kill() と handleBloodDamage の hurt() はこの tick の中で死亡させ得る。
            // 死亡したら以降(意識判定・姿勢維持・同期)は走らせない。
            if (!serverPlayer.isAlive()) return;
            handleBleedingRecovery(serverPlayer, medicalData);
            handleBloodLoss(serverPlayer, medicalData);
            handleBleedingEffect(serverPlayer, medicalData);
            handleBloodDamage(serverPlayer, medicalData);
            if (!serverPlayer.isAlive()) return;
            handleConsciousnessState(serverPlayer, medicalData);
            handleUnconsciousFloating(serverPlayer, medicalData);
            handleECGSimulation(serverPlayer, medicalData);

            // *** 一元姿勢管理システムによる姿勢制御の維持 ***
            PoseController.maintainPoseControl(serverPlayer);

            syncCoreStatsIfNeeded(serverPlayer, medicalData);
        });
    }

    // *** 修正：意識障害時の姿勢制御を一元管理システムに委譲 ***
    private static void handleConsciousnessState(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        HeartStatus newStatus = medicalData.getHeartStatus();
        boolean oldConscious = medicalData.isConscious();
        boolean newConscious = CardiacRules.isConscious(serverPlayer.getHealth(), newStatus);

        // 意識状態が変化した場合
        if (oldConscious != newConscious) {
            medicalData.setConscious(newConscious);

            // *** 一元姿勢管理システムを使用 ***
            PoseController.setUnconsciousPose(serverPlayer, !newConscious);
        }
    }

    // *** プレイヤーログアウト時の姿勢制御クリア ***
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PoseController.clearPoseControl(serverPlayer);
            // 担架に乗った状態でログアウトすると、PlayerList.remove が担架ごと保存・除去し、運搬者に返却されない。
            // PlayerLoggedOutEvent はそれより前に発火するので、ここで降ろして担架を運搬者へ返す
            if (serverPlayer.getVehicle() instanceof jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity stretcher) {
                stretcher.releaseOnPassengerLogout();
            }
        }
    }

    // [その他のメソッドは変更なし - handleIVStandTreatment, applyPackEffect, handleCardiacArrest等]
    private static void handleIVTransfusion(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        medicalData.getTransfusingFromStandPos().ifPresent(standPos -> {
            BlockEntity be = serverPlayer.level().getBlockEntity(standPos);

            if (be instanceof IVStandBlockEntity standEntity &&
                    IvRules.isInRange(serverPlayer.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5),
                            MedicalConfig.get(MedicalConfigKeys.IV_RANGE))) {

                boolean hasAnyPack = false;
                boolean hasEmptyPackThisTick = false;
                boolean itemsChanged = false; // アイテムが変更されたかのフラグ


                for (int i = 0; i < standEntity.itemHandler.getSlots(); i++) {
                    ItemStack packStack = standEntity.itemHandler.getStackInSlot(i);
                    if (packStack.isEmpty() || !(packStack.getItem() instanceof FluidPackItem)) continue;

                    // FluidPackItemのヘルパーメソッドを使用
                    IvRules.PackStep step = IvRules.stepPack(
                            FluidPackItem.getRemainingTicks(packStack), packStack.getCount(), FluidPackItem.getMaxTicks());

                    switch (step.action()) {
                        case DRAIN -> {
                            // 残量を1tick減らす
                            FluidPackItem.setRemainingTicks(packStack, step.newRemainingTicks());
                            hasAnyPack = true;
                            applyPackEffect(serverPlayer, packStack.getItem());
                        }
                        case REFILL_FROM_STACK -> {
                            // スタックに複数ある場合、1つ減らして新しいものをフル容量で作成
                            packStack.shrink(1);
                            FluidPackItem.setRemainingTicks(packStack, step.newRemainingTicks());
                            hasAnyPack = true;
                            applyPackEffect(serverPlayer, packStack.getItem());
                        }
                        case REMOVE_EMPTY -> {
                            // 最後の1つが空になったので削除
                            standEntity.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                            hasEmptyPackThisTick = true;
                        }
                    }
                    itemsChanged = true; // アイテムが変更された
                    // BlockEntityを更新して表示を同期
                    standEntity.setChanged();
                }

                // アイテムが変更された場合、クライアント同期を強制実行
                if (itemsChanged) {
                    standEntity.forceClientSync();
                }

                if (hasEmptyPackThisTick && !hasAnyPack) {
                    serverPlayer.sendSystemMessage(Component.literal("§e点滴パックが空になりました。"));
                }

            } else {
                medicalData.setTransfusingFromStandPos(Optional.empty());
                serverPlayer.sendSystemMessage(Component.literal("§e点滴が外れました。"));
            }
        });
    }

    private static void applyPackEffect(ServerPlayer player, Item packItem) {
        if (packItem == Medicalsystemcore.BLOOD_PACK.get()) {
            player.addEffect(new MobEffectInstance(Medicalsystemcore.TRANSFUSION.get(), IvRules.EFFECT_DURATION_TICKS, 0, true, false));
        } else if (packItem == Medicalsystemcore.ADRENALINE_PACK.get()) {
            player.addEffect(new MobEffectInstance(Medicalsystemcore.ADRENALINE_EFFECT.get(), IvRules.EFFECT_DURATION_TICKS, 0, true, false));
        } else if (packItem == Medicalsystemcore.FIBRINOGEN_PACK.get()) {
            player.addEffect(new MobEffectInstance(Medicalsystemcore.FIBRINOGEN_EFFECT.get(), IvRules.EFFECT_DURATION_TICKS, 0, true, false));
        } else if (packItem == Medicalsystemcore.TRANEXAMIC_ACID_PACK.get()) {
            player.addEffect(new MobEffectInstance(Medicalsystemcore.TRANEXAMIC_ACID_EFFECT.get(), IvRules.EFFECT_DURATION_TICKS, 0, true, false));
        } else if (packItem == Medicalsystemcore.GLUCOSE_PACK.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, IvRules.GLUCOSE_EFFECT_DURATION_TICKS, 0, true, false));
        } else if (packItem == Medicalsystemcore.NORADRENALINE_PACK.get()) {
            // ノルアドレナリン：現在は未実装
        }
    }

    private static void handleCardiacArrest(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        HeartStatus currentStatus = medicalData.getHeartStatus();

        if (currentStatus != HeartStatus.NORMAL) {
            CardiacRules.Progress progress = CardiacRules.advance(
                    currentStatus,
                    medicalData.getCardiacArrestTimer(),
                    medicalData.getBloodLevel(),
                    medicalData.getResuscitationChance(),
                    MedicalConfig.get(MedicalConfigKeys.CARDIAC_ARREST_DEATH_TIME),
                    MedicalConfig.get(MedicalConfigKeys.RESUSCITATION_CHANCE_DECAY_RATE));
            medicalData.setCardiacArrestTimer(progress.timer());
            medicalData.setResuscitationChance(progress.chance());
            if (progress.dead()) {
                serverPlayer.kill();
            }
        }
    }

    private static void handleBleedingRecovery(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        float currentSpeed = medicalData.getBleedingSpeed();
        if (currentSpeed > 0) {
            int bandageLevel = 0;

            MobEffectInstance bandageEffect = serverPlayer.getEffect(Medicalsystemcore.BANDAGE_EFFECT.get());
            if (bandageEffect != null) {
                bandageLevel = BandageRules.levelFromAmplifier(bandageEffect.getAmplifier());
            }

            medicalData.setBleedingSpeed(BleedingRules.recover(
                    currentSpeed,
                    bandageLevel,
                    MedicalConfig.get(MedicalConfigKeys.BLEEDING_RECOVERY_BASE_RATE),
                    MedicalConfig.get(MedicalConfigKeys.BLEEDING_RECOVERY_BANDAGE_MULTIPLIER)));
        }
    }

    private static void handleBloodLoss(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        medicalData.setBloodLevel(BloodRules.nextBlood(
                medicalData.getHeartStatus(),
                medicalData.getBloodLevel(),
                medicalData.getBleedingSpeed(),
                MedicalConfig.get(MedicalConfigKeys.BLOOD_NATURAL_RECOVERY_RATE)));
    }

    private static void handleBleedingEffect(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        float bleedingSpeed = medicalData.getBleedingSpeed();
        if (bleedingSpeed > 0) {
            int amplifier = BleedingRules.effectAmplifier(bleedingSpeed);
            // MobEffectInstance#update は amplifier が下がる更新を hiddenEffect に回すため、
            // 既存より低い amplifier を addEffect しても表示が古いまま残る。違うときは付け直す。
            MobEffectInstance current = serverPlayer.getEffect(Medicalsystemcore.BLEEDING_EFFECT.get());
            if (current != null && current.getAmplifier() != amplifier) {
                serverPlayer.removeEffect(Medicalsystemcore.BLEEDING_EFFECT.get());
            }
            serverPlayer.addEffect(new MobEffectInstance(
                    Medicalsystemcore.BLEEDING_EFFECT.get(),
                    BleedingRules.EFFECT_DURATION_TICKS, amplifier, true, true, true));
        } else {
            serverPlayer.removeEffect(Medicalsystemcore.BLEEDING_EFFECT.get());
        }
    }

    private static void handleBloodDamage(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        double damageAmount = BloodRules.damage(
                medicalData.getHeartStatus(),
                medicalData.getTickCounter(),
                medicalData.getBloodLevel(),
                BloodRules.DamageTable.fromConfig());

        if (damageAmount > 0) {
            serverPlayer.hurt(ModDamageTypes.bleeding(serverPlayer.level()), (float) damageAmount);
        }
    }

    private static void handleECGSimulation(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        HeartStatus status = medicalData.getHeartStatus();
        int heartRate = calculateHeartRateUnified(serverPlayer, status);
        medicalData.setHeartRate(heartRate);

        updatePlayerHeartVector(serverPlayer, medicalData, status, heartRate);
    }

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

    private static float gaussian(float t, float a, float mu, float sigma) {
        return (float) (a * Math.exp(-Math.pow(t - mu, 2) / (2 * Math.pow(sigma, 2))));
    }

    private static float calculateGaussianSumPotential(float cycleTime, float cycleDuration) {
        float p = gaussian(cycleTime, 0.2f, 0.12f * cycleDuration, 0.04f * cycleDuration);
        float q = gaussian(cycleTime, -0.15f, 0.28f * cycleDuration, 0.01f * cycleDuration);
        float r = gaussian(cycleTime, 1.2f, 0.30f * cycleDuration, 0.01f * cycleDuration);
        float s = gaussian(cycleTime, -0.3f, 0.32f * cycleDuration, 0.01f * cycleDuration);
        float t = gaussian(cycleTime, 0.35f, 0.50f * cycleDuration, 0.08f * cycleDuration);

        return p + q + r + s + t;
    }

    private static float[] getHeartVectorPath(float cycleTime, float cycleDuration) {
        float progress = cycleTime / cycleDuration;
        double angleRad = Math.toRadians(60);

        if (progress > 0.27 && progress < 0.33) {
            double qrsProgress = (progress - 0.27) / (0.33 - 0.27);
            angleRad = Math.toRadians(50 + qrsProgress * 20);
        }
        return new float[]{(float) Math.cos(angleRad), (float) Math.sin(angleRad)};
    }

    private static float[] getVFWaveform(long gameTime) {
        float time = (float) gameTime / 20.0f;
        float x = (float) (Math.sin(time * 8) * 0.4 + Math.sin(time * 15) * 0.6 + (Math.random() - 0.5) * 0.3);
        float y = (float) (Math.sin(time * 7) * 0.5 + Math.sin(time * 18) * 0.5 + (Math.random() - 0.5) * 0.3);
        return new float[]{x, y};
    }

    private static void handleUnconsciousFloating(ServerPlayer serverPlayer, IPlayerMedicalData medicalData) {
        if (!MedicalConfig.get(MedicalConfigKeys.UNCONSCIOUS_FLOAT_IN_LIQUID)) return;
        if (medicalData.isConscious()) return;
        if (!serverPlayer.isInWater() && !serverPlayer.isInLava()) return;

        Vec3 motion = serverPlayer.getDeltaMovement();
        serverPlayer.setDeltaMovement(motion.x, MedicalConfig.get(MedicalConfigKeys.UNCONSCIOUS_FLOAT_SPEED), motion.z);
        serverPlayer.hurtMarked = true;
    }

    // 意識障害判定のヘルパーメソッド
    private static boolean isPlayerUnconscious(Player player) {
        return player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA)
                .map(data -> !data.isConscious())
                .orElse(false);
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (isPlayerUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}