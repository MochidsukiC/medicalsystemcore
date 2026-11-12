Forge→Fabric 26.2 移植の段分けと依存です。対象は `forge/src/main/java/jp/houlab/mochidsuki/medicalsystemcore/` の全 100 クラスと `forge/src/main/resources` の全 72 ファイル。API の対応は `docs/fabric-26.2-api-mapping.md` を参照。

依存の抽出方法: 各クラスのソース（コメント・文字列・import 行を除く）に、他クラスの単純名が現れるかを調べた。加えて `Medicalsystemcore.FIELD`（登録ホルダ）の参照は、そのフィールドが生成するクラスへの依存として数えた（例: `Medicalsystemcore.STRETCHER` は StretcherItem）。unused import は依存に含めていない（`HeadsideMonitorBlockEntity` の `ModEvents` import は未使用で、削除すればよい）。

## 1. 段の定義と配置ルール

| 段 | 内容 | ソースセット |
|---|---|---|
| 1 土台 | attachment と PlayerMedicalData、設定の読込/同期、全 payload の record と登録、effect、damage type、tag、SavedData、RescueData、メモリ上の小さな core クラス | main |
| 2 ブロック・BE・アイテム・エンティティ・メニュー・リソース | 登録ホルダ（ModBlocks/ModItems など）、全アイテムの items JSON、loot_table/、tags/item/ | main（＋ resources） |
| 3 サーバーロジック | ModEvents、payload のサーバー側ハンドラ、コマンド、サーバー側 Mixin | main |
| 4 クライアント a | 受信ハンドラ、クライアント状態管理、入力系 Mixin、HUD | client |
| 5 クライアント b | レンダラー、Screen、special model、Screen を開く/更新する受信ハンドラ | client |

配置ルール:
1. 各クラスは「依存先がすべて満たされる最も早い段」に置く。
2. ただし、利用者が指定した段（ModEvents=3 など）に置くクラスは、(a) 依存先が上の段にあれば引き上げ、(b) 下の段のクラスから使われていれば引き下げる。引き上げ・引き下げが起きたクラスは 4 節に理由と選択肢を書いた。
3. 下の段から上の段への参照（逆向き依存）は 4 節で切り方を提案する。main から client への参照は、`splitEnvironmentSourceSets()` のためコンパイルできないので必ず切る。
4. 段をまたぐクラスは `Medicalsystemcore` だけ（2 節の「Medicalsystemcore（段またぎ）」）。
5. 床（その種類の最初の段）: `capability/`・`effect/`・`network/` の record と `ModPackets`・`core/` の SavedData と記録クラス・`util/`=1、`block/`・`blockentity/`・`item/`・`entity/`・`menu/`=2、`event/`・`command/`・`core/ModEvents`・`core/RescueDataManager`・サーバー側 payload ハンドラ=3、`client/` の状態・入力・HUD・クライアント側の受信ハンドラ=4、`client/renderer`・`client/screen`・`client/model`・Screen に触るハンドラ=5。この床より上に置いたクラスは、`RescueDataManager`（依存だけなら段 2 でも置けるが床が 3）と Screen に触るハンドラのほか、`PoseController`（床 1 のところ、依存で段 2。4 節 R3）、`MedicalAuthorizationUtil` と `PendingQteManager`（依存で段 2。R5、R6）。

クラス数（Forge の 100 ファイルを 1 回ずつ数える。payload は record の段で数える）:

| 段 | 1 | 2 | 3 | 4 | 5 | 段またぎ | 計 |
|---|---|---|---|---|---|---|---|
| クラス数 | 36 | 25 | 6 | 21 | 11 | 1 | 100 |

上の数は 4 節の★の選択肢を採った場合。ほかの選択肢では次のようにずれる: R2-B（ClientRevokedLicenses を client に残す）は段 1 が 35、段 4 が 22。R3-B は PoseController・StretcherBlock・StretcherBlockEntity・StretcherItem・StretcherEntity の 5 クラスが段 2 から段 3 に移り、段 2 が 20、段 3 が 11。R5-B は新規の `DoctorCardData` が段 1 に増える（既存 100 クラスの数は変わらない）。R6-B は PendingQteManager が段 1 に戻り（段 1 が 37、段 2 が 24）、除細動器を触る部分の新規クラスが段 2 に増える。

payload のハンドラは別クラスに分かれるので、ハンドラ 14 個の段は 3 節に別に書く（サーバー側 6 個は段 3、クライアント側 8 個は段 4 が 4 個・段 5 が 4 個）。

## 2. 全クラスの割り振り

列: 「依存」は自分より前の段または同じ段のクラスのうち主なもの。「Fabric での姿」は移植後の形（詳細は API 対応表）。

### 段 1（36 クラス）

| クラス | 依存 | Fabric での姿・備考 |
|---|---|---|
| capability/IPlayerMedicalData | なし | attachment の値の型としてそのまま |
| capability/PlayerMedicalData | IPlayerMedicalData | attachment の値クラス。Codec を足す（API 対応表 2 節） |
| capability/PlayerMedicalDataProvider | IPlayerMedicalData, PlayerMedicalData | **廃止**。`ModAttachments`（新規、同じ段）に置き換える |
| core/ModDamageTypes | なし | `ResourceKey<DamageType>` のキー定義 |
| core/ModTags | なし | タグキー定義 |
| effect/AdrenalineEffect, FibrinogenEffect, TranexamicAcidEffect | PlayerMedicalDataProvider | MobEffect。attachment 経由に書き換え。登録ホルダ `ModEffects`（新規） |
| effect/BandageEffect, BleedingEffect | なし | MobEffect |
| effect/TransfusionEffect | ClientboundCoreStatsPacket, ModPackets, PlayerMedicalDataProvider | MobEffect。CoreStats の record と送信ヘルパーに依存（どちらも段 1） |
| core/RescueData | なし | 通報 1 件。NBT の save/load は Codec に（API 対応表 12 節）。ネットワーク用の読み書きは payload 側の StreamCodec に |
| core/RescueSavedData | RescueData | SavedData（SavedDataType + Codec） |
| core/RevokedLicenseSavedData | ClientboundRevokedLicensesSyncPacket, ModPackets | SavedData。ログイン時送信のイベント登録は段 3 の ModEvents 側かこの段の `register()` に |
| core/IssuedLicenseSavedData | なし | SavedData |
| core/ConfigSyncHandler | ClientboundConfigSyncPacket, ModPackets | 設定 JSON の読込（SERVER_STARTING）と同期（JOIN、設定変更時）。common の MedicalConfig/MedicalConfigFile を使う |
| core/GiveUpGuard | PlayerMedicalDataProvider | メモリのみの Set。tick/ログアウト/停止の 3 イベントを Fabric のイベント登録に |
| core/PendingHealManager | なし | メモリのみの Map。ログアウト/停止の 2 イベント |
| core/DefibrillationHandler | IPlayerMedicalData | 医療データの純粋な更新処理。そのまま |
| network/ModPackets | 14 payload の record | `PayloadTypeRegistry` への登録と送信ヘルパー（`ServerPlayNetworking.send`、`PlayerLookup`）に作り直す |
| network/ClientboundConfigSyncPacket | なし | record（3 節） |
| network/ClientboundCoreStatsPacket | IPlayerMedicalData | record。医療データから作るファクトリは残してよい（段 1 内） |
| network/ClientboundStartQTEPacket, ClientboundOpenRescuePortalPacket, ClientboundRemoveRescueDataPacket, ClientboundRevokedLicensesSyncPacket | なし | record |
| network/ClientboundSyncAllRescueDataPacket, ClientboundUpdateRescueDataPacket | RescueData | record |
| network/ServerboundDeleteRescueDataPacket, ServerboundFinishHealPacket, ServerboundGiveUpPacket, ServerboundQTETResultPacket, ServerboundRequestRescueListPacket, ServerboundUpdateRescueDataPacket | なし | record |
| util/StretcherPositionCalculator | なし | 純粋な計算。そのまま |
| client/ClientRevokedLicenses | なし | **client から main へ移す**（`net.minecraft.client` を参照しない純 Java。DoctorCardItem が専用サーバーでも読む）。4 節 R2 |

新規（Forge に無い）クラス（段 1）: `ModAttachments`、`ModDataComponents`（DefibrillatorPos、MonitorPos、StartPos、FluidVolumeTicks、FluidLevel、医師カードの項目）、`ModEffects`、Fabric のメインクラスの骨格。

### 段 2（25 クラス）

| クラス | 依存 | Fabric での姿・備考 |
|---|---|---|
| block/DefibrillatorBlock | DefibrillatorBlockEntity, ElectrodeItem, MedicalAuthorizationUtil | Block。`setId` 必須（API 対応表 4 節） |
| block/HeadsideMonitorBlock | HeadsideCableItem, HeadsideMonitorBlockEntity, MedicalAuthorizationUtil | 同上 |
| block/IVStandBlock | IVStandBlockEntity, TubeItem, MedicalAuthorizationUtil, ModTags | 同上 |
| block/StretcherBlock | StretcherBlockEntity, StretcherEntity, StretcherItem, MedicalAuthorizationUtil | 同上 |
| blockentity/DefibrillatorBlockEntity | DefibrillatorBlock | 保存は ValueInput/ValueOutput |
| blockentity/HeadsideMonitorBlockEntity | PlayerMedicalDataProvider | 同上。未使用の ModEvents import を消す |
| blockentity/IVStandBlockEntity | IVStandMenu, ModTags | ItemStackHandler を SimpleContainer に（API 対応表 5 節） |
| blockentity/StretcherBlockEntity | PoseController, StretcherBlock | 同上 |
| item/BandageItem | BandageEffect, PendingHealManager | **main→client 参照あり**（ClientHealingManager）。4 節 R1 |
| item/CardReaderItem | なし | Forge でも未登録（クラスのみ）。移植するかは要確認 |
| item/DoctorCardItem | IssuedLicenseSavedData, RevokedLicenseSavedData, ClientRevokedLicenses | データコンポーネントに |
| item/ElectrodeItem | DefibrillatorBlock, DefibrillatorBlockEntity, PendingQteManager, ClientboundStartQTEPacket, ModPackets | 同上 |
| item/FluidPackItem | なし | FluidVolumeTicks/FluidLevel をデータコンポーネントに |
| item/HeadsideCableItem | HeadsideMonitorBlockEntity | データコンポーネントに |
| item/MedicalBagItem | MedicalBagMenu, MedicalAuthorizationUtil | `ExtendedMenuProvider` |
| item/RescueTabletItem | ClientboundOpenRescuePortalPacket, ModPackets, MedicalAuthorizationUtil | BEWLR の参照は消える（4 節 R4） |
| item/StretcherItem | StretcherBlock, StretcherBlockEntity, StretcherEntity | |
| item/TubeItem | IVStandBlock, IVStandBlockEntity, PlayerMedicalDataProvider | |
| entity/StretcherEntity | StretcherItem, StretcherPositionCalculator | `getAddEntityPacket` の override は削除 |
| menu/IVStandMenu | IVStandBlock, IVStandBlockEntity | `ExtendedMenuType`（D=BlockPos） |
| menu/MedicalBagMenu | MedicalBagItem | `ExtendedMenuType`（D=InteractionHand） |
| menu/RescuePortalMenu | なし | 通常の MenuType |
| util/MedicalAuthorizationUtil | DoctorCardItem, IssuedLicenseSavedData | **段 1 の想定から繰り下げ**。4 節 R5 |
| core/PendingQteManager | DefibrillatorBlock, DefibrillatorBlockEntity | **段 1 の想定から繰り下げ**。4 節 R6 |
| core/PoseController | StretcherEntity, PlayerMedicalDataProvider | **段 3 の想定から繰り上げ**（StretcherBlockEntity が使う）。4 節 R3。強制姿勢の Mixin（`Player#setForcedPose` 相当）も同じ段 |

段 2 の新規: 登録ホルダ（`ModBlocks`、`ModBlockEntities`、`ModItems`、`ModEntities`、`ModMenus`、`ModCreativeTabs`）、強制姿勢の Mixin、全 17 アイテムの items JSON、`loot_table/`、`tags/item/`（6 節）。

### 段 3（6 クラス ＋ サーバー側ハンドラ 6 個）

| クラス | 依存 | Fabric での姿・備考 |
|---|---|---|
| core/ModEvents | 効果 6 種、CoreStats record、ModPackets、DefibrillatorBlock/BE、ElectrodeItem、FluidPackItem、IVStandBlockEntity、PoseController、StretcherEntity | イベントは API 対応表 1 節。Mixin 2 本（防具後ダメージ、ItemToss）を同じ段に |
| core/RescueDataManager | MedicalAuthorizationUtil, RescueSavedData, ModPackets, Remove/Update の payload record | 依存だけなら段 2 でも置けるが、使うのは段 3 だけなので 3 に置く |
| command/DoctorCardCommand | DoctorCardItem, MedicalAuthorizationUtil, 2 つの SavedData | `CommandRegistrationCallback` |
| command/EmergencyCallCommand | DoctorCardItem, MedicalAuthorizationUtil, RescueDataManager | 同上 |
| event/CommandEventHandler | 2 つのコマンド | `CommandRegistrationCallback` に統合 |
| event/StretcherPlacementHandler | StretcherBlock, StretcherBlockEntity, StretcherEntity | `UseBlockCallback` |

### 段 4（21 クラス）

| クラス | 依存 | Fabric での姿・備考 |
|---|---|---|
| client/ClientMedicalData | なし | そのまま |
| client/ClientMedicalDataManager | ClientMedicalData | そのまま |
| client/ClientPoseController | ClientMedicalDataManager, StretcherEntity | そのまま（段 2 の StretcherEntity を参照） |
| client/StretcherPoseManager | StretcherEntity | そのまま |
| client/ClientQTEManager | ModPackets, ServerboundQTETResultPacket | `ClientPlayNetworking.send` |
| client/ClientHealingManager | BandageItem, ModPackets, ServerboundFinishHealPacket | 同上。4 節 R1 の受け口を実装 |
| client/ClientGiveUpManager | ClientMedicalDataManager, ModPackets, ServerboundGiveUpPacket | 同上 |
| client/ClientMouseHandler | ClientMedicalDataManager | 視点固定。イベントなし |
| client/ClientInputHandler | 上の 5 つの Manager | tick・入力・クリックのイベント（入力 2 つは Mixin）。API 対応表 1 節 |
| client/ClientHudHandler | ClientGiveUpManager, ClientHealingManager, ClientMedicalDataManager, ClientQTEManager, DefibMonitorHud | `HudElementRegistry.replaceElement(CROSSHAIR)`。API 対応表 1 節と 8 節 |
| client/hud/DefibMonitorHud | ClientMedicalDataManager, ClientQTEManager | そのまま移せる（API 対応表 8 節） |
| client/ClientPacketHandler | ClientQTEManager | ClientPlayNetworking の受信から呼ぶ |
| client/ClientTrackingManager | RescueData | そのまま |
| client/ClientConfigHandler | なし | DISCONNECT イベント |
| client/ClientMedicalStateCleaner | 各 Manager, StretcherPoseManager | DISCONNECT イベント |
| client/ClientRescueStateCleaner | ClientRevokedLicenses, ClientTrackingManager | DISCONNECT イベント |
| client/ClientWorldHandler | ClientPoseController | レベル切替/切断のイベント |
| client/ClientStretcherHandler | StretcherEntity | **削除**（中身が無い） |
| client/DebugOverlayHandler | なし | **削除**（空） |
| client/PackColor | なし | **廃止**（items JSON の tints に置き換え。JSON は段 2） |
| client/SimpleFluidLevelProperty | FluidPackItem | **廃止**（items JSON の select/range に置き換え。JSON は段 2） |

段 4 の新規: `MedicalsystemcoreClient`（ClientModInitializer の骨格）、入力系 Mixin 2 本（`MouseHandler#onButton`、`LocalPlayer#aiStep` の入力後 or `KeyboardInput#tick`）、`Player#tick` TAIL の Mixin（任意）。

### 段 5（11 クラス）

| クラス | 依存 | Fabric での姿・備考 |
|---|---|---|
| client/model/StretcherModel | なし | `EntityModel<S>`、`ModelLayerRegistry` |
| client/renderer/StretcherRenderer | StretcherEntity, StretcherModel | `EntityRenderer<T,S>` の extract/submit |
| client/renderer/blockentity/DefibrillatorBlockEntityRenderer | DefibrillatorBlock, DefibrillatorBlockEntity | `BlockEntityRenderer<T,S>` |
| client/renderer/blockentity/HeadsideMonitorBlockEntityRenderer | HeadsideMonitorBlock, HeadsideMonitorBlockEntity | 同上 |
| client/renderer/blockentity/IVStandBlockEntityRenderer | IVStandBlock, IVStandBlockEntity | 同上。ItemStackHandler は SimpleContainer に |
| client/renderer/RescueTabletBEWLR | ClientTrackingManager, RescueData | `SpecialModelRenderer` に作り直す（API 対応表 8 節） |
| client/screen/IVStandScreen | IVStandMenu | `MenuScreens.register` |
| client/screen/MedicalBagScreen | MedicalBagMenu | 同上 |
| client/screen/RescuePortalScreen | ClientTrackingManager, RescueDataListWidget, ModPackets, 3 つの Serverbound record | Screen。`gui.setScreen` |
| client/screen/widget/RescueDataListWidget | RescueData | ウィジェット |
| network/ClientRescuePortalHandler | RescuePortalScreen | Screen を開くだけ。client へ移す |

### Medicalsystemcore（段またぎ）

Forge の `Medicalsystemcore.java` は、登録ホルダ・初期化・クライアント初期化を 1 クラスに持つ。Fabric では次のように段ごとに育てる。

| 段 | 追加する内容 |
|---|---|
| 1 | `ModInitializer`（設定の読込フック、payload 型の登録、attachment・data component・effect の登録、`ConfigSyncHandler.registerListener`）。`MODID` 定数 |
| 2 | ブロック → BlockEntityType → アイテム → エンティティ型 → メニュー型 → クリエイティブタブの順に登録（ブロックのアイテムはブロックの後）。包帯のタブ追加（`CreativeModeTabEvents`） |
| 3 | イベント・コマンドの登録呼び出し |
| 4 | `ClientModInitializer`: payload 受信の登録、切断イベント、HUD の登録、入力イベント |
| 5 | `ClientModInitializer`: レンダラー、Screen、レイヤー定義、special model の登録 |

現行の登録（`Medicalsystemcore.java:88-197`）の全件。ID は Forge と同じ文字列（lang・items JSON・loot table・tag が ID に依存している）。

| 種別 | 件数 | ID（Fabric で登録する段） |
|---|---|---|
| Block | 4 | `iv_stand`、`defibrillator_block`、`head_side_monitor`、`stretcher`（段 2） |
| BlockEntityType | 4 | `iv_stand_block_entity`、`defibrillator_block_entity`、`head_side_monitor_block_entity`、`stretcher_block_entity`（段 2） |
| Item | 17 | BlockItem 3 件: `defibrillator_block`、`iv_stand`、`head_side_monitor`。`bandage`（stacksTo 64）、`head_side_cable`（1）、`electrode`（16）、`stretcher`（1。ブロックのアイテムではなく StretcherItem）、`doctor_card`、`rescue_tablet`（1）、`medical_bag`（1）、パック 6 件 `blood_pack`・`adrenaline_pack`・`tranexamic_acid_pack`・`fibrinogen_pack`・`noradrenaline_pack`・`glucose_pack`（各 16、FluidPackItem）、`tube`（64）（段 2） |
| MenuType | 3 | `iv_stand_menu`、`rescue_portal_menu`、`medical_bag_menu`（段 2） |
| MobEffect | 6 | `transfusion`、`bandage_effect`、`adrenaline_effect`、`fibrinogen_effect`、`tranexamic_acid_effect`、`bleeding`（段 1） |
| EntityType | 1 | `stretcher_entity`（段 2） |
| CreativeModeTab | 1 | `medical_system_core_tab`（段 2。タブの中身は 15 アイテム。`medical_bag` は `isPresent()` のときだけ追加） |
| payload | 14 | 3 節（段 1） |
| attachment / data component | 新規 | `ModAttachments`（medical_data）、`ModDataComponents`（段 1） |

Forge の `ClientModEvents` 内クラス（`FMLClientSetupEvent` など 4 つ）は client の初期化に移り、main の `Medicalsystemcore` から client クラスへの import（Forge では 10 本）はすべて消える。登録ホルダの参照はメソッド本体の中で行われている（`DefibrillatorBlock` → `ELECTRODE` など）ので、`ModBlocks` と `ModItems` が互いを static 初期化で参照する循環は起きない。

## 3. payload 14 件の段（record と登録は段 1、ハンドラは別クラス）

| payload | 方向 | record | ハンドラ | ハンドラの主な依存 |
|---|---|---|---|---|
| ClientboundConfigSyncPacket | S→C | 1 | 4 | MedicalConfig（common） |
| ClientboundCoreStatsPacket | S→C | 1 | 4 | ClientMedicalData, ClientMedicalDataManager, ClientPoseController |
| ClientboundStartQTEPacket | S→C | 1 | 4 | ClientPacketHandler |
| ClientboundRevokedLicensesSyncPacket | S→C | 1 | 4 | ClientRevokedLicenses（main） |
| ClientboundOpenRescuePortalPacket | S→C | 1 | 5 | ClientRescuePortalHandler → RescuePortalScreen |
| ClientboundSyncAllRescueDataPacket | S→C | 1 | 5 | RescuePortalScreen |
| ClientboundUpdateRescueDataPacket | S→C | 1 | 5 | RescuePortalScreen |
| ClientboundRemoveRescueDataPacket | S→C | 1 | 5 | ClientTrackingManager, RescuePortalScreen |
| ServerboundFinishHealPacket | C→S | 1 | 3 | BandageItem, PendingHealManager, PlayerMedicalData |
| ServerboundQTETResultPacket | C→S | 1 | 3 | PendingQteManager, DefibrillatorBlockEntity, ElectrodeItem, DefibrillationHandler, MedicalAuthorizationUtil |
| ServerboundGiveUpPacket | C→S | 1 | 3 | GiveUpGuard, RescueDataManager, DoctorCardItem, MedicalAuthorizationUtil |
| ServerboundRequestRescueListPacket | C→S | 1 | 3 | RescueDataManager, MedicalAuthorizationUtil |
| ServerboundUpdateRescueDataPacket | C→S | 1 | 3 | RescueDataManager, MedicalAuthorizationUtil |
| ServerboundDeleteRescueDataPacket | C→S | 1 | 3 | RescueDataManager, MedicalAuthorizationUtil |

Screen に触る受信ハンドラ 4 個（段 5）は、段 4 の時点では登録だけ空の受け口で置き、段 5 で中身を実装してもよい（4 節 R7）。

## 4. 逆向き依存と、依存で動いたクラス

親が選べるよう、各項目に選択肢を書いた。★は提案。

### R1. BandageItem（段 2）→ ClientHealingManager（段 4）

`BandageItem#interactLivingEntity` のクライアント分岐が `ClientHealingManager.startHealing(entityId)` を直接呼ぶ（BandageItem.java:34）。main から client を参照できない。
- ★ A: 段 2 に「クライアント受け口」（`IntConsumer` を 1 つ持つ小さなホルダ、main）を作り、BandageItem はそれを呼ぶ。段 4 で `ClientHealingManager::startHealing` を代入する。差分が最小。
- B: クライアント側の `UseEntityCallback`（client に置く）で包帯を手に持っているときだけ startHealing を呼び、BandageItem からクライアント分岐を消す。Forge の挙動（Item のフックから呼ぶ）からの設計変更になる。

### R2. DoctorCardItem（段 2）→ ClientRevokedLicenses（client）

ツールチップの失効表示で参照（DoctorCardItem.java:178,196）。`ClientRevokedLicenses` は `net.minecraft.client` を参照しない純 Java なので、
- ★ A: main のソースセットへ移し段 1 に置く（パッケージ名は任意）。受信ハンドラ（段 4）がそこへ書き込む。
- B: 受け口方式（R1 と同じ）。

### R3. PoseController（段 3 の想定）← StretcherBlockEntity（段 2）

`StretcherBlockEntity` が `PoseController` を呼ぶ。PoseController 自体の依存は StretcherEntity（段 2）と attachment（段 1）だけで、強制姿勢の Mixin があれば段 2 に置ける。
- ★ A: PoseController と強制姿勢の Mixin を段 2 に引き下げる（ストレッチャー 5 クラスは段 2 の中の循環として揃う）。
- B: PoseController を段 3 のままにして、StretcherBlock・StretcherBlockEntity・StretcherItem に加えて **StretcherEntity も**段 3 に引き上げる（StretcherEntity は `Medicalsystemcore.STRETCHER`（StretcherItem）を参照し、StretcherItem・StretcherBlock・StretcherBlockEntity と同じ循環に入っているため）。ストレッチャー関連の登録（ブロック、BE 型、アイテム、エンティティ型）がすべて段 3 になる。ModEvents や StretcherPlacementHandler と同じ段なのでテストはしやすい。

### R4. RescueTabletItem（段 2）→ RescueTabletBEWLR（段 5）

`initializeClient` の `IClientItemExtensions` で参照するだけ。26.2 では BEWLR 自体が廃止されて items JSON の special model に変わるので、この参照は消える。RescueTabletItem は段 2 のまま。

### R5. MedicalAuthorizationUtil（段 1 の想定）→ DoctorCardItem（段 2）

`hasValidDoctorCard`、`isValidCardFor` などが DoctorCardItem の静的メソッドを呼ぶ（MedicalAuthorizationUtil.java:23,43,56-66,95,114-124）。アイテムのインスタンス判定（`isDoctorCard`）には登録済みのアイテムが要る。
- ★ A: 段 2 に置く（DoctorCardItem の後、ブロック・アイテム・コマンドの前）。
- B: 段 1 に残すために、カードの読み取りを段 1 の `DoctorCardData`（データコンポーネントの読み書きだけ）へ切り出し、アイテム判定を段 2 に残す。MedicalAuthorizationUtil の中身が割れて差分が大きくなる。

### R6. PendingQteManager（段 1 の想定）→ DefibrillatorBlock / DefibrillatorBlockEntity（段 2）

`isChargedDefibrillator`、`releaseCharge`、`expire` がブロックと BE を直接触る（PendingQteManager.java:72-97）。ElectrodeItem（段 2）と QTETResult ハンドラ（段 3）が使う。
- ★ A: 段 2 に置く（段 2 にはすでに DefibrillatorBlock ⇄ ElectrodeItem ⇄ PendingQteManager の循環がある）。
- B: 段 1 には記録（`Pending`、`begin`、`peek`、`remove`、期限判定）だけを残し、除細動器を触る 3 メソッドを段 2 へ分ける。

### R7. Screen を触る受信ハンドラ（段 5）

OpenRescuePortal、SyncAllRescueData、UpdateRescueData、RemoveRescueData のハンドラは RescuePortalScreen に触る。
- ★ A: そのまま段 5 に置く。段 4 では受信登録をしない。
- B: 段 4 で受信し ClientTrackingManager などに書き込み、Screen への通知は受け口（R1 と同じ形）を介する。

### その他の main→client の参照（すべて「ハンドラを client へ移す」ことで消える）

`ClientboundCoreStatsPacket`（→ ClientMedicalData ほか 3 クラス）、`ClientboundStartQTEPacket`（→ ClientPacketHandler）、`ClientboundRevokedLicensesSyncPacket`（→ ClientRevokedLicenses）、`ClientboundRemoveRescueDataPacket`／`Update`／`SyncAll`（→ RescuePortalScreen）、`network/ClientRescuePortalHandler`（→ RescuePortalScreen）、`Medicalsystemcore`（10 本の client import）。いずれもハンドラ・初期化を client のクラスに分ければよい。

### 段内の循環（同じ段の中で相互参照する組）

- 段 2: {IVStandBlock, IVStandBlockEntity, IVStandMenu, TubeItem}、{MedicalBagItem, MedicalBagMenu}、{DefibrillatorBlock, DefibrillatorBlockEntity, ElectrodeItem, PendingQteManager}、{PoseController, StretcherBlock, StretcherBlockEntity, StretcherEntity, StretcherItem}。どれも実行時（メソッド本体）の参照で、Java では同じソースセット内なら問題にならない。登録の順序だけ 2 節の「Medicalsystemcore（段またぎ）」の表に従う。
- 段 1・3・4・5 には循環はない。

## 5. 新規 Mixin と、ほかに必要なもの（段ごと）

| 段 | Mixin | 理由 |
|---|---|---|
| 2 | `Player` の強制姿勢（`setForcedPose`/`getForcedPose` 相当、`updatePlayerPose` の HEAD） | PoseController（Forge が追加したメソッドを使っている） |
| 3 | `Player#actuallyHurt` の `causeFoodExhaustion` INVOKE の前で `dmg` を取る | 防具・吸収後のダメージ量（API 対応表 1 節） |
| 3 | `ServerPlayer#drop(ItemStack, boolean, boolean)` の HEAD（cancellable） | ItemTossEvent の代わり |
| 4 | `MouseHandler#onButton` の HEAD（cancellable） | 意識不明時のクリック無効と QTE の右クリック解除 |
| 4 | `LocalPlayer#aiStep` の `ClientInput#tick` の後（または `KeyboardInput#tick` の TAIL） | 意識不明時の入力ゼロ化 |
| 4（任意） | `Player#tick` の TAIL（server/client 共通） | PlayerTickEvent の順序を完全に合わせるとき |

## 6. Forge 側リソースの全ファイル（72 件）と Fabric 側の配置

Fabric 側の配置先はすべて `fabric/src/main/resources/` の下（Loom の split 環境でも、同じ MOD jar に入る）。

| Forge のパス | 件数 | Fabric 側の扱い |
|---|---|---|
| `META-INF/mods.toml` | 1 | 不要（`fabric.mod.json` は作成済み） |
| `pack.mcmeta`（pack_format 15） | 1 | Fabric の MOD リソースには必須でない（実機未検証）。置くなら `min_format`/`max_format`（API 対応表 10 節） |
| `assets/medicalsystemcore/blockstates/{defibrillator_block,head_side_monitor,iv_stand,stretcher}.json` | 4 | そのまま |
| `assets/medicalsystemcore/lang/{en_us,ja_jp}.json` | 2 | そのまま。HUD・QTE のキーがあることを確認 |
| `assets/medicalsystemcore/models/block/{defibrillator,defibrillator_handle,head_side_monitor,iv_stand,iv_stand_lower,stretcher_foot,stretcher_head}.json` | 7 | そのまま |
| `assets/medicalsystemcore/models/item/*.json`（18 件: adrenaline_pack, bandage, blood_pack, defibrillator_block, doctor_card, electrode, fibrinogen_pack, glucose_pack, head_side_cable, head_side_monitor, iv_stand, medical_bag, noradrenaline_pack, rescue_tablet, rescue_tablet_base, stretcher, tranexamic_acid_pack, tube） | 18 | モデル本体は残し、`overrides` は削除（6 つのパック。26.2 の方式では使われない）。`rescue_tablet.json`（`builtin/entity`）は不要になり、`rescue_tablet_base.json` を special model の土台に使う |
| `assets/medicalsystemcore/models/item/pack/pack_level_{0..9}.json` | 10 | そのまま。items JSON から参照する |
| `assets/medicalsystemcore/textures/block/stretcher.png` | 1 | そのまま |
| `assets/medicalsystemcore/textures/entity/{stretcher,stretcher_entity}.png` | 2 | そのまま |
| `assets/medicalsystemcore/textures/gui/{iv_stand_gui,rescue_icon,rescue_portal_gui}.png` | 3 | そのまま（`blit(RenderPipelines.GUI_TEXTURED, Identifier, ...)`） |
| `assets/medicalsystemcore/textures/item/{bandage,doctor_card,head_side_cable,tube}.png` と `pack/`（pack_base, pack_overlay, pack_overlay0〜8 の 11 件） | 15 | そのまま |
| `assets/medicalsystemcore/textures/mob_effect/{bandage_effect,bleeding}.png` | 2 | そのまま（26.2 のアトラスは `mob_effect/` ディレクトリを `mob_effect/` 接頭辞で読む: AtlasProvider.java:141）。ほかの効果 4 つは Forge でもアイコン無し |
| `data/medicalsystemcore/damage_type/bleeding.json` | 1 | そのまま（API 対応表 11 節） |
| `data/medicalsystemcore/loot_tables/blocks/{defibrillator_block,head_side_monitor,iv_stand}.json` | 3 | `loot_table/blocks/` に改名（ストレッチャーブロックの loot table は Forge にも無い）。中身の書式は 26.2 で未確認 |
| `data/medicalsystemcore/tags/items/{blood_packs,drug_packs}.json` | 2 | `tags/item/` に改名 |

### 新規作成する items JSON（`assets/medicalsystemcore/items/<id>.json`、全 17 アイテム）

| アイテム ID | 内容 |
|---|---|
| `bandage`、`doctor_card`、`electrode`、`head_side_cable`、`medical_bag`、`stretcher`、`tube` | `minecraft:model`（`models/item/<id>`） |
| `defibrillator_block`、`iv_stand`、`head_side_monitor` | `minecraft:model`（ブロックモデルを親にした `models/item/<id>`） |
| `blood_pack`、`adrenaline_pack`、`tranexamic_acid_pack`、`fibrinogen_pack`、`noradrenaline_pack`、`glucose_pack` | **overrides を移す対象（6 件）**。FluidLevel（整数 0〜9、Forge の既定は満タン 9）で `minecraft:select`（property `minecraft:component`）か range_dispatch により `pack/pack_level_0〜9` を切替。**ItemColors を移す対象（6 件）**: `tints` で layer1（tintindex 1）に色（blood 0xFF0000、adrenaline 0xFFEA00、tranexamic_acid 0x00F7FF、fibrinogen 0x00FFAA、noradrenaline 0xAA00FF、glucose 0x00FF00）、layer0 は定数 -1 |
| `rescue_tablet` | **BEWLR を移す対象（1 件）**。`minecraft:special`（SpecialModelRenderer）を `minecraft:composite` で `rescue_tablet_base` と組み、手と GUI の切替や左手の反転は `minecraft:select`（property `minecraft:display_context`）で書く（API 対応表 8 節） |

## 7. 確認できなかったもの

- `ClientPlayConnectionEvents.DISCONNECT` が呼ばれるスレッド。Fabric 側は `ConnectionMixin` の `channelInactive` の HEAD（netty スレッドの可能性）と `handleDisconnection` の INVOKE（メインスレッド）の 2 経路から呼ぶ。どちらが先に当たるかはバイトコードからは決まらず、実行しないと確定しない。段 4 のクリア処理は `Minecraft#execute` で包むかどうかを実機で確認する。
- 段 2 の loot table（26.2 でも `minecraft:explosion_decay` などの書式が同じか）。
- `models/block` の `format_version` 欄、`textures` の `block/red_wool` などバニラ参照が 26.2 でも通るか。
- `CardReaderItem` を移植するか（Forge でも登録されていない）。

## 8. 決定事項(親が確定。各段の実装はこれに従う)

- R1〜R7 はすべて ★A を採る。
  - R1: BandageItem → main の受け口(`IntConsumer` ホルダ)。段 4 で `ClientHealingManager::startHealing` を代入する。
  - R2: ClientRevokedLicenses は main へ移して段 1 に置く。
  - R3: PoseController と強制姿勢の Mixin は段 2。
  - R5: MedicalAuthorizationUtil は段 2。
  - R6: PendingQteManager は段 2。
  - R7: Screen に触る受信ハンドラは段 5。
- 姿勢復元と attachment 転送の順序: 自前の AFTER_RESPAWN リスナーは独自フェーズに登録し、`addPhaseOrdering(Event.DEFAULT_PHASE, 自前フェーズ)` で Fabric の転送の後に走らせる。
- SavedData: `dataFixType` は null。読込失敗時は 26.2 の挙動(ログを出して空データ)をそのまま受け入れる。Forge との挙動差として DEV.md に記録する。
- RescueSavedData の `synchronized` は残す(ロジックは変えない)。
- `ServerPlayer#server` は `player.level().getServer()` に置き換える。
- 移植しないもの: `CardReaderItem`、`ClientStretcherHandler`、`DebugOverlayHandler`。`HeadsideMonitorBlockEntity` の未使用 import は持ち込まない。
- 防具適用後のダメージ量は Mixin(`Player#actuallyHurt` の `causeFoodExhaustion` INVOKE 前)で取り、Forge と同じ値にする。
- 共通ルール:
  - ロジックは Forge 現行のまま移す。
  - Forge 側の不具合や挙動差を見つけても直さずに報告する。
  - 編集範囲は `fabric/` のみ。`forge/` と `common/` は触らない。
