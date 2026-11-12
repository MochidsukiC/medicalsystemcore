# ゲームルール計算の所在一覧 (HEAD=3fcbad7)

パスは `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/` からの相対。`ModEvents` は `core/ModEvents.java`。
検証範囲: Config 使用箇所は全 89 ファイルを grep し、主要ロジックは該当行を直接読んだ。`PoseController` / `ClientPoseController` / `StretcherPoseManager` の内部計算や RescuePortal 系の UI は未精読。

## 0. 先に知っておくべき発見 (common 化・設定スキーマ設計に影響)

1. **Config に定義されているが未使用のキーが 3 つある。** 実装側は定数を直書きしている。
   - `defibrillator_vf_success_multiplier` (既定 2.0)。実装は `* 2` 直書き (`network/ServerboundQTETResultPacket.java:90`, `item/DefibrillatorItem.java:72`)。
   - `defibrillator_transition_chance` (既定 0.4)。実装は `0.4` 直書き (`ServerboundQTETResultPacket.java:108,111`, `DefibrillatorItem.java:63,76`)。
   - `bleeding_recovery_platelet_multiplier`。参照ゼロ。
2. **`defibrillator_cooldown` は `DefibrillatorItem` では使われていない。**
   - `item/DefibrillatorItem.java:49` が `30 * 20` を直書きしている。
   - Config を使っているのは `blockentity/DefibrillatorBlockEntity.java:106` だけ。
3. **除細動の成功判定が 2 経路に重複している** (下記 B-4)。
   - 経路 A は `item/DefibrillatorItem.java:38-99` で、QTE を使わずアイテムを直接使う。
   - 経路 B は `network/ServerboundQTETResultPacket.java:73-128` で、QTE 結果を使う。
   - 両者は QTE ボーナスと `damageImmune` 解除の有無が異なる。共有化の最優先対象。
4. **蘇生判定は QTE 結果ごとに補正値が違う。**
   - `GREAT_SUCCESS` は +20、`SUCCESS` は **-20**、`FAIL` は判定せず即終了 (`ServerboundQTETResultPacket.java:79-84`)。
   - `SUCCESS` が `GREAT_SUCCESS` より 40 低い。意図なら仕様、そうでなければバグの可能性がある。
5. **QTE の区間定数が `ClientQTEManager` にハードコードされており、判定がクライアント側で完結している。**
   - 定数は `client/ClientQTEManager.java:24-27`、判定は `:44-53`。
   - サーバーは結果 enum を信頼するだけ (`ServerboundQTETResultPacket.java:40-48`)。クライアント改ざんで常に GREAT_SUCCESS を送れる。
   - 共有化するなら判定関数を common に置き、サーバーが `barPosition` を受け取って再判定する設計が妥当。
6. **心停止タイマーの二重定義。** `ModEvents.java:344-346` は `arrestTimer` を読んでから +1 して書く。
   - `:348` の比較は +1 前の値なので、実質 1 tick 遅れる。
   - `:353` の `arrestTimer % (20*60) == 0` は、タイマーが 0 のとき (心停止直後の最初の tick) にも成立する。開始直後に 1 回減衰が入る。
7. **Config の登録が二重。** `Medicalsystemcore.java:226-227` で同一 SPEC を SERVER と COMMON の両方に登録している。
   - `Config.java:358-366` は搬送座標のみ SERVER 限定で読む回避策を入れている。
   - 実行時変更を作る際は「単一の真実の源」にする必要がある。
8. **クライアントも Config 静的フィールドを読む。**
   - `ClientGiveUpManager` (`:21,54,76`)、`ClientHudHandler` (`:45,48,54`)、`ClientHealingManager` (`:25`)。
   - 動的変更時はクライアント同期パケットが必須。

## A. 計算ロジック一覧

「散在数」は、同種の計算・定数が現れるファイル数と箇所数。

### A-1. 失血量 → ダメージ量 (閾値計算)
- **場所**: `ModEvents.java:424-447` (`handleBloodDamage`)
- **入力**: `HeartStatus`、`tickCounter`、`bloodLevel` (0-100)
- **出力**: ダメージ量 double (0 ならダメージなし)。呼び出し側が `hurt(bleeding)` を実行する。
- **Config**: `BLOOD_DAMAGE_INTERVAL`、`BLOOD_DAMAGE_THRESHOLD_85/70/65/60`、`BLOOD_DAMAGE_85_70/70_65/65_60/BELOW_60`
- **MC 依存**: `ServerPlayer` と `level` は `:443` の `hurt` 呼び出しだけ。計算は純粋で、`(HeartStatus, tickCounter, blood) -> double` で切り出せる。
- **散在**: 1 箇所 (ModEvents のみ)。
- **注意**: 閾値は 85 > 70 > 65 > 60 の順序前提で、Config の範囲検証では順序を保証していない。

### A-2. 出血速度の増加 (被ダメージ → 出血)
- **場所**: `ModEvents.java:98-128` (`onLivingDamage`)
- **入力**: `damageAmount` float、乱数
- **出力**: `speedIncrease` double (新速度 = 現速度 + 増分)
- **区間**:
  - 0.1 < d ≤ 2: 確率 `BLEEDING_CHANCE_LOW_DAMAGE` で `SPEED_INCREASE_LOW`
  - 2 < d ≤ 5: `MID`
  - 5 < d ≤ 10: `HIGH`
  - 10 < d: `EXTREME`
- **MC 依存**: `Player`、`event.getAmount()`。`Math.random()` が埋め込まれているため、common 化では乱数源 (`DoubleSupplier` または `RandomSource` 相当) の注入が必要。
- **散在**: 1 箇所。
- **注意**: `d ≤ 0.1` は出血なし。区間境界の包含は上記のとおり。

### A-3. 出血速度の回復 (包帯)
- **場所**: `ModEvents.java:364-384` (`handleBleedingRecovery`)
- **式**: `rate/秒 = BASE_RATE * (bandageLevel * BANDAGE_MULTIPLIER)`、`速度 -= rate/20`、下限 0
- **入力**: 現出血速度、包帯レベル (`effect amplifier + 1`、なければ 0)
- **MC 依存**: `hasEffect/getEffect` のみ。`bandageLevel` を引数化すれば純関数になる。
- **Config**: `BLEEDING_RECOVERY_BASE_RATE`、`BLEEDING_RECOVERY_BANDAGE_MULTIPLIER`
- **散在**: 1 箇所。`BLEEDING_RECOVERY_PLATELET_MULTIPLIER` は未使用。

### A-4. 失血量の増減 (出血による減少 / 自然回復)
- **場所**: `ModEvents.java:386-410` (`handleBloodLoss`)
- **式**:
  - 出血中、`NORMAL`: `speed / 60 / 20` を毎 tick 減算
  - 出血中、`NORMAL` 以外で blood < 30: `speed / 200 / 60 / 20`
  - 出血中、`NORMAL` 以外で blood ≥ 30: `speed / 20 / 60 / 20`
  - 出血なし: `+ BLOOD_NATURAL_RECOVERY_RATE / 20` (上限 100)
- **MC 依存**: なし (引数で `player` を受けているが未使用)。そのまま純関数化できる。
- **Config**: `BLOOD_NATURAL_RECOVERY_RATE`
- **散在**: `:386-410` の 1 箇所。ただし 30 / 200 / 20 / 60 の係数が直書きで、Config 化されていない。

### A-5. 出血エフェクトレベル
- **場所**: `ModEvents.java:412-422`
- **式**: `level = ceil(bleedingSpeed)`、amplifier = level-1、持続 40 tick
- **MC 依存**: `addEffect` の部分のみ。レベル計算は 1 行の純関数。

### A-6. 輸血による血液回復
- **場所**: `effect/TransfusionEffect.java:29-37`
- **式**: `blood = min(100, blood + TRANSFUSION_RECOVERY_RATE / 20)` (毎 tick)
- **Config**: `TRANSFUSION_RECOVERY_RATE`
- **MC 依存**: `Player` と Capability の取得。`ServerPlayer` への同期パケット送信は計算と分離可能。
- **散在**: 1 箇所。

### A-7. 薬剤: アドレナリン / フィブリノゲン / トラネキサム酸
- **アドレナリン**: `effect/AdrenalineEffect.java:21-24`。`蘇生確率 = min(100, chance + ADRENALINE_RESUSCITATION_RECOVERY_RATE/20)`。
- **フィブリノゲン**: `effect/FibrinogenEffect.java:53-61`。`出血速度 = max(0, speed - FIBRINOGEN_RATE/20 * (amplifier+1))`。
- **トラネキサム酸**: `effect/TranexamicAcidEffect.java:90-98`。式はフィブリノゲンと同一で、Config キーだけが違う。
- **MC 依存**: `Player` とエフェクト amplifier。いずれも `(現在値, amplifier) -> 新値` の純関数に落ちる。
- **散在**: 3 ファイルで同一パターン。1 つのパラメータ付き関数に統合できる。
- **ノルアドレナリン**: パックは存在するが効果は未実装 (`ModEvents.java:335-337`)。

### A-8. 点滴 (流量 / パック残量 / 有効範囲)
- **場所**: `ModEvents.java:257-338` (`handleIVTransfusion`、`applyPackEffect`)、`item/FluidPackItem.java:27,53,63,91,102,112`
- **ルール**:
  - パック容量は `IV_PACK_DURATION * 20` tick。毎 tick 1 減算する。
  - 範囲内判定は `distanceSq < IV_RANGE²`。
  - 空になったスタックは 1 個消費して満タンに戻す。
  - 効果付与は 40 tick (グルコースのみ 100 tick の SATURATION)。
- **入力**: 残 tick、スタック数、距離²
- **出力**: 新残 tick、消費 or 削除の判定、効果の種別
- **Config**: `IV_PACK_DURATION`、`IV_RANGE`
- **MC 依存**: `ItemStack` の NBT (`FluidVolumeTicks`)、`BlockEntity`、`Item` 比較。「残 tick・スタック数 → 次状態」の状態機械部分だけが純粋。
- **散在**: `IV_PACK_DURATION * 20` が 6 箇所 (`FluidPackItem` 5 箇所 + `ModEvents` 1 箇所)。

### A-9. 心停止の進行 (強制死亡・蘇生確率減衰)
- **場所**: `ModEvents.java:340-362` (`handleCardiacArrest`)
- **ルール**:
  - `HeartStatus != NORMAL` の間タイマーを +1 する。
  - `timer > CARDIAC_ARREST_DEATH_TIME*60*20` または `blood <= 0` で死亡。
  - `CARDIAC_ARREST` のみ 1200 tick (1 分) ごとに蘇生確率を `-RESUSCITATION_CHANCE_DECAY_RATE`。0 以下で死亡。
- **入力**: `HeartStatus`、timer、blood、resuscitationChance
- **出力**: 新 timer、新 chance、死亡フラグ
- **MC 依存**: `serverPlayer.kill()` のみ。結果を enum か record で返す純関数にできる。
- **Config**: `CARDIAC_ARREST_DEATH_TIME`、`RESUSCITATION_CHANCE_DECAY_RATE`
- **散在**: 1 箇所。冒頭の発見 6 を参照。

### A-10. 意識判定
- **場所**: `ModEvents.java:227-246`
- **ルール**: `health < 5 || heartStatus != NORMAL` なら意識なし。
- **MC 依存**: `getHealth()` (float) を引数化すれば純関数。
- **散在**: 少なくとも `ModEvents`。クライアント側は `ClientMedicalDataManager` が結果を保持している。

### A-11. 心拍数 / ECG 波形シミュレーション
- **場所**: `ModEvents.java:449-545`
- **内容**:
  - 心拍数は、NORMAL なら `60+rand(10)` に移動速度 (+10/レベル)、跳躍 (+10/レベル)、アドレナリン (+30) を加算して上限 200。VF は `300+rand(100)`、CARDIAC_ARREST は 0。
  - 波形は P/Q/R/S/T の 5 つのガウス和 (`:519-527`)、QRS 区間のベクトル角 (`:529-538`)、VF 波形 (`:540-545`)。
  - NORMAL の電位は `bloodLevel/100` を掛ける。
- **入力**: `HeartStatus`、各エフェクト amplifier (3 種)、cycleTime、duration、blood、`gameTime`、乱数
- **出力**: 心拍数、(vectorX, vectorY)、新 cycleTime
- **MC 依存**: `player.level().random`、`hasEffect`、`getGameTime`。エフェクト amplifier と乱数、gameTime の注入で純関数化できる。ガウス / ベクトル計算 (`:515-545`) は既に MC 非依存 (static)。
- **Config**: なし (係数はすべて直書き)。
- **散在**: 1 箇所。クライアントの描画側 `HeadsideMonitorBlockEntityRenderer` (360 行) が波形を描画しているが、波形生成そのものは持っていない (ここは未精読)。

### A-12. 心停止への遷移 (致死ダメージ時)
- **場所**: `ModEvents.java:37-75` (`onLivingDeath` 付近)
- **ルール**:
  - `heartStatus != NORMAL` なら死亡を確定し、状態を NORMAL に戻して解除する。
  - NORMAL なら死亡をキャンセルし、HP=0.1、`CARDIAC_ARREST`、`damageImmune=true` にする。
- **MC 依存**: イベント。この分岐は「(現在の HeartStatus) → (死亡 or 心停止遷移)」の純関数にできる。

### A-13. 除細動の成功判定 + 状態遷移 (2 経路)
- **場所**: 経路 A は `item/DefibrillatorItem.java:53-99`。経路 B は `network/ServerboundQTETResultPacket.java:65-117`。
- **共通ルール**:
  - `CARDIAC_ARREST`: 成功確率 = `chance/100`
  - `VF`: 成功確率 = `chance*2/100` (Config の `VF_SUCCESS_MULTIPLIER` 未使用)
  - 失敗時は 0.4 の確率で `CA↔VF` に遷移
  - 成功時は `NORMAL`、`chance=100`、`arrestTimer=0`
- **経路 B 固有**:
  - QTE ボーナス (GREAT +20 / SUCCESS -20 / FAIL は判定せず終了)。
  - 成功時に `damageImmune=false`。
  - 判定前に除細動器の `resetCharge` と `startCooldown` を実行する。
- **入力**: `HeartStatus`、`chance`、`QTEResult`、乱数 2 つ
- **出力**: 新 `HeartStatus`、新 `chance`、timer リセット要否、`damageImmune` 解除要否、結果種別 (成功 / 遷移 / 失敗)
- **MC 依存**: `ServerPlayer`、`Math.random`、チャットメッセージ、パケット送信。ロジック部は完全に純粋。
- **Config**: 本来は `DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER`、`DEFIBRILLATOR_TRANSITION_CHANCE` を使うべき箇所。
- **散在**: 2 ファイル。確率 `0.4` が 4 箇所、`*2` が 2 箇所。

### A-14. 除細動器の充電 / クールダウン
- **場所**: `blockentity/DefibrillatorBlockEntity.java:60-93` (充電 tick)、`:104-118` (クールダウンと進捗)
- **ルール**:
  - 充電時間 `= DEFIBRILLATOR_CHARGE_TIME * 20` tick。進捗 = `chargeProgress / chargeTime`。
  - 充電完了で `chargeProgress = -1`。
  - クールダウン終了 tick = `gameTime + COOLDOWN*20`。
- **MC 依存**: `Level`、`BlockState`、サウンドが密結合。進捗とクールダウンの数式だけが純粋。
- **Config**: `DEFIBRILLATOR_CHARGE_TIME`、`DEFIBRILLATOR_COOLDOWN`
- **散在**: `CHARGE_TIME * 20` が 2 箇所、`COOLDOWN` は BE のみ (Item は 30 直書き)。

### A-15. QTE 判定
- **場所**: `client/ClientQTEManager.java:17-27` (定数)、`:42-53` (判定)、`:62-74` (バー進行)
- **ルール**:
  - バー位置 `p ∈ [0,1]` で、`0.75 ≤ p ≤ 0.85` が GREAT_SUCCESS、`0.6 ≤ p ≤ 1.0` が SUCCESS (大成功を除く)、それ以外は FAIL。
  - `barSpeed = 0.02`/tick。`p > 1.0` で自動失敗。
- **入力**: `barPosition`
- **出力**: `QTEResult` (`core/QTEResult.java`)
- **MC 依存**: 判定部はなし。クライアント専用の static 状態で、メッセージ表示と `ModPackets` 送信が混在している。
- **Config**: なし。区間は定数。
- **散在**: 1 ファイル (定数 5 つ)。getter 経由で HUD も参照する。冒頭の発見 5 を参照。

### A-16. 包帯エフェクト (自分 / 他者)
- **場所**: `item/BandageItem.java:54-73` (自分)、`network/ServerboundFinishHealPacket.java:47-63` (他者)
- **式**:
  - `newLevel = min(BANDAGE_MAX_LEVEL, currentLevel + increase)`
  - amplifier = `newLevel - 1`、持続 = `BANDAGE_EFFECT_DURATION * 20`
  - 使用時間は自分が `SELF_USE_DURATION*20` (`BandageItem.java:81`)、他者が `OTHER_USE_DURATION*20` (`client/ClientHealingManager.java:25`)
- **入力**: 現在レベル、増分種別 (自分 / 他者)
- **MC 依存**: `MobEffectInstance` の取得と付与のみ。
- **Config**: `BANDAGE_MAX_LEVEL`、`BANDAGE_EFFECT_DURATION`、`BANDAGE_SELF_LEVEL_INCREASE`、`BANDAGE_OTHER_LEVEL_INCREASE`、`BANDAGE_SELF_USE_DURATION`、`BANDAGE_OTHER_USE_DURATION`
- **散在**: 2 ファイルにレベル更新式が重複している。

### A-17. 意識不明時の水中浮上
- **場所**: `ModEvents.java:547-555`
- **ルール**: `UNCONSCIOUS_FLOAT_IN_LIQUID` が true かつ意識なしで、水中または溶岩中なら Y 速度 = `UNCONSCIOUS_FLOAT_SPEED`。
- **MC 依存**: `Vec3`、`setDeltaMovement`。計算としては「条件 → 速度」だけで薄い。

### A-18. ギブアップ / 搬送
- **場所**: `network/ServerboundGiveUpPacket.java:57-113` (サーバー)、`client/ClientGiveUpManager.java:50-79` (クライアントのタイマー)
- **ルール**:
  - 要求 tick = `GIVE_UP_DURATION*20` (クライアント側、`:54`, `:76`)。
  - 意識ありのパケットは無視する。
  - `TRANSPORT_MODE` が true なら搬送、false なら自死 (CA 化 → `damageImmune` 解除 → kill)。
- **Config**: `GIVE_UP_ENABLED`、`GIVE_UP_DURATION`、`TRANSPORT_MODE`、`TRANSPORT_X/Y/Z`、`TRANSPORT_DIMENSION`
- **MC 依存**: 全面的に `ServerPlayer`、`ResourceKey<Level>`、テレポート。
- **注意**: サーバー側が `GIVE_UP_DURATION` を検証していない。サーバーは意識不明の確認のみで、クライアントが即送信できる (`ServerboundGiveUpPacket.java:59`)。

### A-19. 救助要請カテゴリ判定
- **場所**: `core/RescueCategory.java` (enum 5 種。名称→定数の変換は `fromValue`、`:59-63`)、`command/EmergencyCallCommand.java:102-135` (権限判定)
- **ルール**:
  - SELF / OTHER: 誰でも可。
  - SUPPORT: **条件が反転している。** `!hasValidDoctorCard` なら権限 OK、持っていれば「医師のみ可能」で拒否する (`:105-110`)。仕様の逆に見える。
  - POLICE / PREVENTIVE: `if (false)` で常に拒否 (`:114`, `:123`)。TODO のまま。
- **MC 依存**: 権限判定は `Player` のアイテム所持 (`DoctorCardItem.hasValidDoctorCard`)。入力を `boolean hasDoctorCard` に落とせば `(RescueCategory, hasDoctorCard, ...) -> allowed` が純関数になる。
- **Config**: なし。
- **散在**: `RescueCategory.fromValue` の文字列正規化が 2 コマンドハンドラ (`:65`, `:88`) に重複している。ディメンション表示名の switch も `EmergencyCallCommand.java:150-155` と `core/RescueData.java:105-114` に重複 (純粋な文字列変換で common 化可能)。
- **データモデル**: `core/RescueData.java` は `BlockPos` と `ResourceKey<Level>` を保持している。common 化するなら座標 (int×3) とディメンション文字列に置換する。`FriendlyByteBuf` 依存のシリアライズは loader 側へ分離する。

### A-20. ドクターカード権限
- **場所**: `util/MedicalAuthorizationUtil.java`、`item/DoctorCardItem.java:87-141`
- **ルール**: 本物 (`isGenuine`) かつ所有者一致 (`isOwnedBy`) のカードを、メインハンド → オフハンド → インベントリの順に探す。
- **MC 依存**: `ItemStack`、NBT、`Player`。全面的に MC 型で、common 化の対象はカード検証ルール (genuine フラグ・所有者 UUID 一致) のみ。

### A-21. 担架の位置 / 角度計算
- **`util/StretcherPositionCalculator.java`**:
  - 目標位置 `= carrierPos + (-sin(yaw)*1.2, +1, cos(yaw)*1.2)` (`:25-37`)。
  - 目標 yaw `= carrierYaw + 90` (`:44-47`)。
  - 位置補間は XZ を係数 0.3 で、Y は即時 (`:56-66`)。
  - yaw 補間は最短差分を係数 0.2 で (`:75-87`)。
  - 入力は `Player` (位置と yaw のみ使用)。**`Vec3` は `net.minecraft.world.phys.Vec3`、`Player` は MC 型。**
  - 剥がし方: `(double x, y, z, float yaw)` を受ける形にして `Vec3` 依存を自前 record に置換する。
  - 呼び出し元は `entity/StretcherEntity.java:74, 102` の 2 箇所 (サーバー用とクライアント予測用)。
- **`util/AngleUtils.java`**: 完全に MC 非依存で、そのまま common に移せる。
  - `normalizeAngle`、`getAngleDifference`、`lerpAngle`、`gradualAngleChange` などのメソッド群 (`:16-125`)。
  - 使用箇所は `client/StretcherPoseManager.java:105,140-146` のみ (`AngleUtils` を使っているのは 1 ファイル)。
- **重複**: yaw 補間ロジックが 3 箇所ある。
  - `StretcherPositionCalculator.interpolateYaw` (`:75-87`)
  - `StretcherEntity.lerpYaw` (`entity/StretcherEntity.java:135-140`、同種だが `diff >= 180` の境界が微妙に違う)
  - `AngleUtils.lerpAngle` (`:56-60`)
  - いずれも「最短差分で補間」の同一概念。common 化の際に 1 本化できる。

## B. 散在度の要約

| ルール | 実装箇所 | 重複度 |
|---|---|---|
| 除細動成功判定 | `DefibrillatorItem` + `ServerboundQTETResultPacket` | 2 経路で差異あり (最優先) |
| 包帯レベル更新 | `BandageItem` + `ServerboundFinishHealPacket` | 2 |
| 止血剤の減衰 | `FibrinogenEffect` + `TranexamicAcidEffect` | 2 (同一式) |
| yaw 補間 | 上記 3 箇所 | 3 |
| `IV_PACK_DURATION*20` | 6 箇所 | 6 |
| ディメンション表示名 | `EmergencyCallCommand` + `RescueData` | 2 |
| 失血ダメージ / 出血 / 心停止進行 / ECG | `ModEvents` のみ | 1 (ただし巨大クラス) |

## C. Config.java 全キー表 (`Config.java`)

- 型は `ForgeConfigSpec` の定義。
- 登録: `Medicalsystemcore.java:226` が SERVER (`medicalsystemcore-server.toml`)、`:227` が COMMON で、同一 SPEC を両方に登録している。
- 値の読み込みは `onLoad` (`Config.java:293-367`) で、`public static` フィールドにキャッシュされる。
- 「使用箇所」は Config フィールド参照の grep 結果 (`Config.java` 自身を除く)。
- 範囲は `defineInRange(min, max)`。

| セクション | キー名 | 型 | 既定値 | 範囲 | 使用箇所 |
|---|---|---|---|---|---|
| Blood System | blood_damage_threshold_85 | double | 85.0 | 0.0–100.0 | ModEvents:432 |
| Blood System | blood_damage_threshold_70 | double | 70.0 | 0.0–100.0 | ModEvents:432,434 |
| Blood System | blood_damage_threshold_65 | double | 65.0 | 0.0–100.0 | ModEvents:434,436 |
| Blood System | blood_damage_threshold_60 | double | 60.0 | 0.0–100.0 | ModEvents:436,438 |
| Blood System | blood_damage_85_70 | double | 4.0 | 0.0–1000.0 | ModEvents:433 |
| Blood System | blood_damage_70_65 | double | 8.0 | 0.0–1000.0 | ModEvents:435 |
| Blood System | blood_damage_65_60 | double | 12.0 | 0.0–1000.0 | ModEvents:437 |
| Blood System | blood_damage_below_60 | double | 99999.0 | 0.0–999999.0 | ModEvents:439 |
| Blood System | blood_damage_interval | int (tick) | 100 | 1–12000 | ModEvents:428 |
| Bleeding System | bleeding_chance_low_damage | double | 0.10 | 0.0–1.0 | ModEvents:111 |
| Bleeding System | bleeding_speed_increase_low | double | 1.0 | 0.0–100.0 | ModEvents:112 |
| Bleeding System | bleeding_speed_increase_mid | double | 3.0 | 0.0–100.0 | ModEvents:115 |
| Bleeding System | bleeding_speed_increase_high | double | 6.0 | 0.0–100.0 | ModEvents:117 |
| Bleeding System | bleeding_speed_increase_extreme | double | 9.0 | 0.0–100.0 | ModEvents:119 |
| Bleeding System | bleeding_recovery_base_rate | double | 0.1 | 0.0–10.0 | ModEvents:375 |
| Bleeding System | bleeding_recovery_bandage_multiplier | double | 0.5 | 0.0–100.0 | ModEvents:376 |
| Bleeding System | bleeding_recovery_platelet_multiplier | double | 5.0 | 0.0–100.0 | **未使用** |
| Hemostatic Agents | fibrinogen_bleeding_reduction_rate | double (/秒) | 2.0 | 0.0–100.0 | FibrinogenEffect:53 |
| Hemostatic Agents | tranexamic_acid_bleeding_reduction_rate | double (/秒) | 2.0 | 0.0–100.0 | TranexamicAcidEffect:90 |
| Blood Recovery | blood_natural_recovery_rate | double (%/秒) | 0.5 | 0.0–10.0 | ModEvents:406 |
| Blood Recovery | transfusion_recovery_rate | double (%/秒) | 1.0 | 0.0–10.0 | TransfusionEffect:29 |
| Cardiac System | resuscitation_chance_decay_rate | double (%/分) | 5.0 | 0.0–100.0 | ModEvents:354 |
| Cardiac System | cardiac_arrest_death_time | int (分) | 20 | 1–120 | ModEvents:347 |
| Cardiac System | adrenaline_resuscitation_recovery_rate | double (%/秒) | 0.5 | 0.0–10.0 | AdrenalineEffect:21 |
| Defibrillator | defibrillator_cooldown | int (秒) | 30 | 1–3600 | DefibrillatorBlockEntity:106 (Item は直書き 30) |
| Defibrillator | defibrillator_charge_time | int (秒) | 2 | 1–60 | DefibrillatorBlockEntity:70,116 |
| Defibrillator | defibrillator_vf_success_multiplier | double | 2.0 | 0.1–10.0 | **未使用** (直書き `*2`) |
| Defibrillator | defibrillator_transition_chance | double | 0.4 | 0.0–1.0 | **未使用** (直書き 0.4) |
| IV System | iv_pack_duration | int (秒) | 120 | 1–3600 | ModEvents:293, FluidPackItem:27,53,63,91,102,112 |
| IV System | iv_range | double (ブロック) | 10.0 | 1.0–100.0 | ModEvents:260 |
| Bandage System | bandage_self_use_duration | int (秒) | 5 | 1–60 | BandageItem:81 |
| Bandage System | bandage_other_use_duration | int (秒) | 3 | 1–60 | ClientHealingManager:25 |
| Bandage System | bandage_effect_duration | int (秒) | 300 | 1–3600 | BandageItem:65, ServerboundFinishHealPacket:55 |
| Bandage System | bandage_self_level_increase | int | 1 | 1–10 | BandageItem:43 |
| Bandage System | bandage_other_level_increase | int | 2 | 1–10 | ServerboundFinishHealPacket:54 |
| Bandage System | bandage_max_level | int | 6 | 1–20 | BandageItem:62, ServerboundFinishHealPacket:54 |
| Unconscious System | unconscious_float_in_liquid | boolean | true | – | ModEvents:548 |
| Unconscious System | unconscious_float_speed | double | 0.05 | 0.0–1.0 | ModEvents:553 |
| Unconscious System | give_up_enabled | boolean | true | – | ClientGiveUpManager:21, ClientHudHandler:48 |
| Unconscious System | give_up_duration | int (秒) | 5 | 1–60 | ClientGiveUpManager:54,76 |
| Unconscious System | transport_mode | boolean | true | – | ServerboundGiveUpPacket:61, ClientHudHandler:45,54 |
| Unconscious System | transport_x | int | 0 | -30000000–30000000 | ServerboundGiveUpPacket:64,81 |
| Unconscious System | transport_y | int | 64 | -64–320 | ServerboundGiveUpPacket:64,81 |
| Unconscious System | transport_z | int | 0 | -30000000–30000000 | ServerboundGiveUpPacket:64,81 |
| Unconscious System | transport_dimension | String | "minecraft:overworld" | – | ServerboundGiveUpPacket:64-73 |

合計 46 キー。うち**未使用 3 キー** (`bleeding_recovery_platelet_multiplier`、`defibrillator_vf_success_multiplier`、`defibrillator_transition_chance`)。

### 動的変更スキーマ化に向けた所見
- **キャッシュ更新**: static フィールドは `ModConfigEvent` でしか更新されない (`Config.java:293`)。コマンド / パネルから変えるなら、更新対象を単一の Settings オブジェクトにし、`onLoad` 経路と同じ書き込み口を通す必要がある。
- **クライアント側参照 5 キー**: `give_up_enabled`、`give_up_duration`、`transport_mode`、`bandage_other_use_duration`、(HUD 表示の `transport_mode`)。変更時はクライアントへの同期パケットが必要。
- **相互制約**: 閾値 `85 > 70 > 65 > 60` の順序は現状どこでも検証されていない。パネルでは項目間バリデーションが必要。
- **永続化の特例**: 搬送座標とディメンションは SERVER Config からのみ読む (`Config.java:358-366`)。COMMON の既定値で上書きされるのを防ぐ回避策なので、保存先を一本化するなら、この分岐を設計時に解消する。
- **ディメンション検証**: `transport_dimension` は文字列で、不正値はギブアップ時にのみ警告されて失敗する (`ServerboundGiveUpPacket.java:65-75`)。設定時の検証が望ましい。

## D. 今回は精読していない領域 (common 化候補の見落としの可能性)
- `core/PoseController.java` (267 行)、`client/ClientPoseController.java` (280 行)、`client/StretcherPoseManager.java` (183 行) の姿勢制御ロジック
- `entity/StretcherEntity.java` の乗車者回転同期 (`:165-167`, `:217`)
- `client/renderer/blockentity/HeadsideMonitorBlockEntityRenderer.java` の波形描画
- `RescuePortalScreen` 系の UI ロジック、`RescueDataManager`
- `block/*` と `blockentity/IVStandBlockEntity` / `StretcherBlockEntity` の状態遷移