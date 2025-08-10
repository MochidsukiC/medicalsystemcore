package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Config;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.capability.PlayerMedicalDataProvider;
import jp.houlab.mochidsuki.medicalsystemcore.network.ClientboundMedicalDataSyncPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID) // MODIDはあなたのMod IDに合わせてください
public class ModEvents {

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

        player.getCapability(PlayerMedicalDataProvider.PLAYER_MEDICAL_DATA).ifPresent(medicalData -> {
            int currentBleedingLevel = medicalData.getBleedingLevel();
            if (currentBleedingLevel >= 4) {
                return;
            }
            int levelIncrease = 0;
            double chance = Math.random();

            // ハードコードされた数値をConfigから読み込むように変更
            if (damageAmount >= 1 && damageAmount <= 5) {
                if (chance < Config.BLEED_CHANCE_LOW) {
                    levelIncrease = 1;
                }
            } else if (damageAmount > 5 && damageAmount <= 10) {
                if (chance < Config.BLEED_CHANCE_MID) {
                    levelIncrease = 1;
                }
            } else if (damageAmount > 10) {
                if (chance < Config.BLEED_CHANCE_HIGH_LVL2) { // 先にレベル2の確率を判定
                    levelIncrease = 2;
                } else if (chance < (Config.BLEED_CHANCE_HIGH_LVL1 + Config.BLEED_CHANCE_HIGH_LVL2)) {
                    levelIncrease = 1;
                }
            }

            if (levelIncrease > 0) {
                int newBleedingLevel = Math.min(4, currentBleedingLevel + levelIncrease);
                medicalData.setBleedingLevel(newBleedingLevel);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("出血レベルが " + newBleedingLevel + " になった。"));
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
                boolean connectionValid = false;
                BlockEntity be = serverPlayer.level().getBlockEntity(standPos);

                if (be instanceof IVStandBlockEntity standEntity) {
                    ItemStack packStack = standEntity.itemHandler.getStackInSlot(0);
                    // スタンドにパックがあり、プレイヤーが範囲内にいるかチェック
                    if (!packStack.isEmpty() && serverPlayer.position().distanceToSqr(standPos.getX(), standPos.getY(), standPos.getZ()) < 100.0) {
                        connectionValid = true;

                        // パックのNBTから残量を取得
                        CompoundTag nbt = packStack.getOrCreateTag();
                        int ticksLeft = nbt.getInt("FluidVolumeTicks");

                        if (ticksLeft > 0) {
                            // 残量を1減らす
                            nbt.putInt("FluidVolumeTicks", ticksLeft - 1);
                            // 輸血エフェクトをかけ続ける
                            serverPlayer.addEffect(new MobEffectInstance(Medicalsystemcore.TRANSFUSION.get(), 40, 0, true, false));
                        } else {
                            // 残量が0になったらパックを消滅させる
                            standEntity.itemHandler.setStackInSlot(0, ItemStack.EMPTY);
                            connectionValid = false; // 接続を無効にする
                            serverPlayer.sendSystemMessage(Component.literal("§eパックが空になりました。"));
                        }
                    }
                }

                if (!connectionValid) {
                    // 接続が無効になったら状態をリセット
                    medicalData.setTransfusingFromStandPos(Optional.empty());
                    if (be != null) { // beがnullでない（＝スタンドが壊されたわけではない）場合のみメッセージを出す
                        serverPlayer.sendSystemMessage(Component.literal("§e点滴が外れた。"));
                    }
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
            } else {
                // 正常時のリセット処理と、心停止への移行判定
                medicalData.setCardiacArrestTimer(0);
                medicalData.setResuscitationChance(100.0f);
                if (bloodLevel < 60.0f) {
                    medicalData.setHeartStatus(HeartStatus.CARDIAC_ARREST);
                }
            }

            // 出血処理 (これは状態を直接変更しないので、ここに残す)
            // ... (出血ダメージと血液量減少のロジック) ...

            int bleedingLevel = medicalData.getBleedingLevel();

            if ( bleedingLevel > 0) {
                int hpInterval = 0;
                int bloodInterval = 0;

                // ハードコードされた数値をConfigから読み込むように変更
                switch (bleedingLevel) {
                    case 1 -> {
                        hpInterval = (int)(Config.BLEED_LVL1_HP_INTERVAL * 20);
                        bloodInterval = (int)(Config.BLEED_LVL1_BLOOD_INTERVAL * 20);
                    }
                    case 2 -> {
                        hpInterval = (int)(Config.BLEED_LVL2_HP_INTERVAL * 20);
                        bloodInterval = (int)(Config.BLEED_LVL2_BLOOD_INTERVAL * 20);
                    }
                    case 3 -> {
                        hpInterval = (int)(Config.BLEED_LVL3_HP_INTERVAL * 20);
                        bloodInterval = (int)(Config.BLEED_LVL3_BLOOD_INTERVAL * 20);
                    }
                    case 4 -> {
                        hpInterval = (int)(Config.BLEED_LVL4_HP_INTERVAL * 20);
                        bloodInterval = (int)(Config.BLEED_LVL4_BLOOD_INTERVAL * 20);
                    }
                }

                // --- ▼▼▼ 出血速度の低下処理を追加 ▼▼▼ ---
                if (medicalData.getHeartStatus() != HeartStatus.NORMAL) {
                    if (bloodLevel < 30.0f) {
                        // 血液量30%未満の心停止中は、出血速度が1/200になる
                        hpInterval *= 200;
                        bloodInterval *= 200;
                    } else {
                        // 通常の心停止中は、出血速度が1/20になる
                        hpInterval *= 20;
                        bloodInterval *= 20;
                    }
                }

                if (hpInterval > 0 && ticks % hpInterval == 0) {
                    if (serverPlayer.getHealth() > 1.0f) {
                        serverPlayer.hurt(ModDamageTypes.bleeding(serverPlayer.level()), 1.0f); //新しいコード
                    }
                    serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c出血... §fHP: " + String.format("%.1f", serverPlayer.getHealth()) + ", 血液量: " + String.format("%.1f", medicalData.getBloodLevel()) + "%"));
                }
                if (bloodInterval > 0 && ticks % bloodInterval == 0) {
                    medicalData.setBloodLevel(medicalData.getBloodLevel() - 1.0f);
                }

            } else {
                // ハードコードされた数値をConfigから読み込むように変更
                int recoveryInterval = (int)(Config.BLOOD_RECOVERY_INTERVAL * 20);
                if (ticks % recoveryInterval == 0) {
                    if (medicalData.getBloodLevel() < 100.0f) {
                        medicalData.setBloodLevel(medicalData.getBloodLevel() + 1.0f);
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
            ModPackets.sendToAllTracking(new ClientboundMedicalDataSyncPacket(
                    serverPlayer.getUUID(),
                    medicalData.getBloodLevel(),
                    newStatus,
                    medicalData.getBleedingLevel(),
                    medicalData.getResuscitationChance()
            ), serverPlayer);

            // --- 4. 最後に、今の状態を「前の状態」として保存 ---
            //medicalData.setPreviousHeartStatus(newStatus);
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
