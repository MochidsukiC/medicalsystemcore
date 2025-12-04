# Medical System Core - プロジェクト解析レポート

**解析日:** 2025-12-04

## 概要

このレポートはMinecraft Forge Mod「Medical System Core」（1.20.1対応）のコードベース解析結果です。
セキュリティ、パフォーマンス、コード品質の観点から潜在的な問題を特定しました。

---

## 🔴 重大な問題 (Critical)

### 1. セキュリティ: 権限チェック未実装

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/RescueDataManager.java`
**行:** 47-48, 64-65

```java
// TODO: ここに更新を実行できる権限があるかどうかのチェックを入れる
```

**問題点:**
- 任意のプレイヤーが救急通報データ（チェックボックス・メモ）を改ざん可能
- 悪意のあるクライアントが他人の通報を変更できる

**推奨修正:**
```java
public static void updateRescueDataCheckbox(ServerPlayer player, int rescueId, boolean isDispatch, boolean isTreatment) {
    // 医師カード認証チェック
    if (!MedicalAuthorizationUtil.isAuthorized(player)) {
        return; // または警告メッセージを送信
    }
    // ... 既存のロジック
}
```

---

### 2. 設定ファイルの二重登録

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/Medicalsystemcore.java`
**行:** 215-216

```java
ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC, "medicalsystemcore-server.toml");
ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
```

**問題点:**
- 同じ`Config.SPEC`がSERVERとCOMMON両方に登録されている
- 設定の競合や予期しない動作を引き起こす可能性

**推奨修正:**
サーバー設定として使用する場合は COMMON の登録を削除：
```java
ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC, "medicalsystemcore-server.toml");
// 以下は削除
// ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
```

---

### 3. NBTデータ読み込み時の境界外アクセス

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/capability/PlayerMedicalData.java`
**行:** 229, 236

```java
this.heartStatus = HeartStatus.values()[nbt.getInt("heartStatus")];
this.previousHeartStatus = HeartStatus.values()[nbt.getInt("previousHeartStatus")];
```

**問題点:**
- NBTから読み込んだ値がEnum範囲外の場合、`ArrayIndexOutOfBoundsException`が発生
- 悪意のあるNBTデータや破損データでサーバークラッシュの可能性

**推奨修正:**
```java
@Override
public void loadNBTData(CompoundTag nbt) {
    this.bloodLevel = nbt.getFloat("bloodLevel");

    // 安全なEnum読み込み
    int heartStatusOrdinal = nbt.getInt("heartStatus");
    HeartStatus[] values = HeartStatus.values();
    this.heartStatus = (heartStatusOrdinal >= 0 && heartStatusOrdinal < values.length)
            ? values[heartStatusOrdinal]
            : HeartStatus.NORMAL;

    int previousStatusOrdinal = nbt.getInt("previousHeartStatus");
    this.previousHeartStatus = (previousStatusOrdinal >= 0 && previousStatusOrdinal < values.length)
            ? values[previousStatusOrdinal]
            : HeartStatus.NORMAL;

    // ... 残りの読み込み
}
```

---

## 🟠 中程度の問題 (Medium)

### 4. Entity Rendererの二重登録

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/Medicalsystemcore.java`
**行:** 244, 293-295

```java
// 1回目 (onClientSetup内, 244行目)
EntityRenderers.register(STRETCHER_ENTITY.get(), StretcherRenderer::new);

// 2回目 (registerRenderers内, 293-295行目)
@SubscribeEvent
public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(STRETCHER_ENTITY.get(), StretcherRenderer::new);
}
```

**推奨修正:**
`onClientSetup`内の登録を削除（`EntityRenderersEvent`が推奨される方法）

---

### 5. RescueDataの永続化なしとメモリリーク

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/RescueDataManager.java`

**問題点:**
1. サーバー再起動時に全ての救急通報データが消失
2. 古いデータを削除する仕組みがないため、長期間運用でメモリを圧迫
3. `nextId`カウンターが定義されているが未使用

**推奨修正:**
- `SavedData`を使用してワールドデータに保存
- 一定時間経過後または処理完了後のデータをアーカイブ/削除する仕組みを追加

---

### 6. ネットワークバージョン検証の緩さ

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ModPackets.java`
**行:** 23-24

```java
.clientAcceptedVersions(s -> true)
.serverAcceptedVersions(s -> true)
```

**推奨修正:**
```java
private static final String PROTOCOL_VERSION = "1.0";

.clientAcceptedVersions(PROTOCOL_VERSION::equals)
.serverAcceptedVersions(PROTOCOL_VERSION::equals)
```

---

### 7. メモ入力の長さ制限なし

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundUpdateRescueDataPacket.java`
**行:** 66

```java
this.memo = buf.readUtf();
```

**問題点:**
- デフォルトでは32767文字まで許可される
- 巨大なメモデータを送信してサーバーのメモリを枯渇させる攻撃が可能

**推奨修正:**
```java
this.memo = buf.readUtf(256); // 最大256文字に制限
```

---

## 🟡 軽度の問題 (Low)

### 8. 包帯回復率のロジックバグ

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java`
**行:** 362-363

```java
recoveryRatePerSecond = Config.BLEEDING_RECOVERY_BASE_RATE *
        (bandageLevel * Config.BLEEDING_RECOVERY_BANDAGE_MULTIPLIER);
```

**問題点:**
- `bandageLevel`が0の場合、回復率が0になる
- 包帯なしでも自然回復すべき場合はロジック修正が必要

**推奨修正（意図次第）:**
```java
recoveryRatePerSecond = Config.BLEEDING_RECOVERY_BASE_RATE +
        (bandageLevel * Config.BLEEDING_RECOVERY_BANDAGE_MULTIPLIER);
```

---

### 9. ハードコードされた日本語文字列

複数箇所で日本語がハードコードされています：

| ファイル | 行 | 文字列 |
|----------|-----|--------|
| ModEvents.java | 114 | `"出血速度が " + ...` |
| ModEvents.java | 178 | `"§c意識不明のためアイテムを落とすことができません。"` |
| ModEvents.java | 301, 306 | `"§e点滴パックが空になりました。"`, `"§e点滴が外れました。"` |
| IVStandBlockEntity.java | 121 | `"点滴スタンド"` |

**推奨修正:**
言語ファイル（`Component.translatable()`）を使用

---

### 10. IVStandBlockEntityのnullチェック不足

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/blockentity/IVStandBlockEntity.java`
**行:** 34

```java
level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
```

**推奨修正:**
```java
if (level != null && !level.isClientSide) {
    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
}
```

---

### 11. Capability invalidateCaps未実装

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/capability/PlayerMedicalDataProvider.java`

`LazyOptional`が初期化時に作成されているが、`invalidateCaps()`が未実装のため、ワールド変更時に古い参照が残る可能性があります。

---

## 📋 その他のコード品質問題

| 問題 | ファイル | 詳細 |
|------|----------|------|
| 未使用インポート | BandageItem.java | `MedicalAuthorizationUtil`がインポートされているが未使用 |
| 未使用変数 | RescueDataManager.java | `nextId`カウンターが未使用 |
| Config値のスレッドセーフティ | Config.java | static フィールドがvolatileでもsynchronizedでもない |

---

## 推奨される修正の優先順位

| 優先度 | 問題 | 推奨アクション |
|--------|------|----------------|
| 🔴 高 | 権限チェック未実装 | 医師カード認証の実装 |
| 🔴 高 | 設定二重登録 | SERVERかCOMMONのどちらかに統一 |
| 🔴 高 | NBT境界外アクセス | 範囲チェックを追加 |
| 🟠 中 | 二重Renderer登録 | 片方を削除 |
| 🟠 中 | データ永続化 | WorldSavedDataで保存 |
| 🟠 中 | メモ長さ制限 | `buf.readUtf(256)`等で制限 |
| 🟡 低 | ハードコード文字列 | 言語ファイル使用 |
| 🟡 低 | nullチェック | 適切なnullチェックを追加 |

---

## 総評

Medical System Coreは、Minecraftにおける本格的な医療システムを実装した意欲的なModです。
アーキテクチャは概ねForgeのベストプラクティスに従っており、Capability、ネットワークパケット、
BlockEntityの使用方法は適切です。

主な改善点は以下の3点です：
1. **セキュリティ強化**: 権限チェックの実装
2. **堅牢性向上**: NBTデータの検証とnullチェック
3. **運用性改善**: データの永続化とメモリ管理

---

# Part 2: 仕様・機能バグ分析

## 🔴 心電図シミュレーションの重大バグ

### ECG-1: 心拍数の毎tick再計算による波形異常

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java`
**行:** 432-454

```java
private static int calculateHeartRateUnified(ServerPlayer player, HeartStatus status) {
    return switch (status) {
        case NORMAL -> {
            int base = 60 + player.level().random.nextInt(10);  // ← 問題箇所
            // ...
        }
    };
}
```

**問題点:**
心拍数が**毎tick（1秒に20回）**ランダムに60〜69の間で再計算されます。

**影響:**
```
tick 1: heartRate = 63, cycleDuration = 0.952s
tick 2: heartRate = 68, cycleDuration = 0.882s  ← 7%変化
tick 3: heartRate = 61, cycleDuration = 0.984s  ← 11%変化
```

この変動がQRS波の振幅異常を引き起こします。

---

### ECG-2: cycleTime と cycleDuration の不整合

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java`
**行:** 456-464

```java
private static void updatePlayerHeartVector(...) {
    float cycleTime = medicalData.getCycleTime();
    cycleTime += 0.05f;  // 固定値（1tick = 0.05秒と仮定）

    float cycleDuration = heartRate > 0 ? 60.0f / heartRate : Float.MAX_VALUE;
    // ↑ heartRateが毎tick変わるので、cycleDurationも変動

    if (cycleTime >= cycleDuration) {
        cycleTime -= cycleDuration;
    }
}
```

**問題の本質:**

| 変数 | 挙動 | 問題 |
|------|------|------|
| `cycleTime` | 0.05sずつ**線形に**累積 | 安定 |
| `cycleDuration` | 心拍数に応じて**毎tick変動** | 不安定 |
| `progress` (cycleTime/cycleDuration) | **両方の影響で激しく変動** | QRS位置が不安定 |

---

### ECG-3: ガウス関数のパラメータ変動によるQRS振幅低下

**ファイル:** `src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java`
**行:** 494-502

```java
private static float calculateGaussianSumPotential(float cycleTime, float cycleDuration) {
    float q = gaussian(cycleTime, -0.15f, 0.28f * cycleDuration, 0.01f * cycleDuration);
    float r = gaussian(cycleTime, 1.2f, 0.30f * cycleDuration, 0.01f * cycleDuration);
    float s = gaussian(cycleTime, -0.3f, 0.32f * cycleDuration, 0.01f * cycleDuration);
    //                振幅        ピーク位置(mu)              幅(sigma)
}
```

**数学的分析:**

ガウス関数: `a * exp(-(t - mu)² / (2σ²))`

- **mu (ピーク位置)** = `0.30 * cycleDuration` → 毎tick変動
- **sigma (幅)** = `0.01 * cycleDuration` → 毎tick変動
- **cycleTime** = 0.05sずつ累積 → 安定

**具体例（R波の振幅計算）:**
```
cycleDuration = 0.923s の場合:
  mu = 0.277s, sigma = 0.009s

cycleDuration = 0.968s の場合:
  mu = 0.290s, sigma = 0.010s

cycleTimeが0.277sの時にcycleDurationが変わると:
  新しいmu = 0.290s
  差分 = |0.277 - 0.290| = 0.013s
  sigma = 0.010s

  R波振幅 = 1.2 * exp(-(0.013)² / (2 * 0.010²))
          = 1.2 * exp(-0.845)
          = 1.2 * 0.43
          = 0.516  ← 正常の43%まで低下！
```

これが「健常時にQRS波が著しく低くなる」現象の根本原因です。

---

### ECG-4: 心電図の視覚的表示の流れ

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          ECGシミュレーションの流れ                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ModEvents.onPlayerTick()  [毎tick実行]                                  │
│         │                                                               │
│         ▼                                                               │
│  calculateHeartRateUnified()                                            │
│    heartRate = 60 + random(10)  ← 🔴 毎tick変動                         │
│         │                                                               │
│         ▼                                                               │
│  updatePlayerHeartVector()                                              │
│    cycleTime += 0.05                                                    │
│    cycleDuration = 60 / heartRate  ← 🔴 毎tick変動                       │
│         │                                                               │
│         ▼                                                               │
│  calculateGaussianSumPotential(cycleTime, cycleDuration)                │
│    R波のmu = 0.30 * cycleDuration  ← 🔴 毎tick変動                       │
│    R波のsigma = 0.01 * cycleDuration  ← 🔴 毎tick変動                    │
│         │                                                               │
│    cycleTimeとmuの差が大きい時                                           │
│         │                                                               │
│         ▼                                                               │
│    QRS振幅が指数関数的に低下  ← 🔴 不整脈様の波形                         │
│         │                                                               │
│         ▼                                                               │
│  medicalData.setHeartVectorX/Y()                                        │
│         │                                                               │
│         ▼                                                               │
│  HeadsideMonitorBlockEntity.updateECGFromPlayerData()                   │
│    leadI, leadII, leadIII を計算                                        │
│         │                                                               │
│         ▼                                                               │
│  HeadsideMonitorBlockEntityRenderer.drawWaveform()                      │
│    波形を画面に描画                                                      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 推奨修正案

### 修正案A: 心拍数の安定化（最小限の変更）

```java
// ModEvents.java - calculateHeartRateUnified()

// 心拍数を保持するための追加フィールド（ModEventsまたは専用クラス）
private static final Map<UUID, Integer> stableHeartRates = new HashMap<>();
private static final Map<UUID, Long> lastHeartRateUpdate = new HashMap<>();

private static int calculateHeartRateUnified(ServerPlayer player, HeartStatus status) {
    UUID playerId = player.getUUID();
    long currentTick = player.level().getGameTime();

    return switch (status) {
        case NORMAL -> {
            // 20tick（1秒）ごとにのみ心拍数を更新
            if (!stableHeartRates.containsKey(playerId) ||
                currentTick - lastHeartRateUpdate.getOrDefault(playerId, 0L) >= 20) {

                int base = 65 + player.level().random.nextInt(5) - 2; // 63-67の範囲
                // エフェクト処理...
                stableHeartRates.put(playerId, Math.min(base, 200));
                lastHeartRateUpdate.put(playerId, currentTick);
            }
            yield stableHeartRates.get(playerId);
        }
        // ...
    };
}
```

### 修正案B: 進行度ベースの計算（推奨）

```java
// ModEvents.java - updatePlayerHeartVector()

private static void updatePlayerHeartVector(ServerPlayer player, IPlayerMedicalData medicalData,
                                            HeartStatus status, int heartRate) {
    // 進行度（0.0〜1.0）で管理
    float progress = medicalData.getCycleTime(); // progressとして再解釈
    float cycleDuration = heartRate > 0 ? 60.0f / heartRate : Float.MAX_VALUE;

    // 1tickあたりの進行度を計算（心拍数に基づく）
    float progressPerTick = 1.0f / (cycleDuration * 20.0f);
    progress += progressPerTick;

    if (progress >= 1.0f) {
        progress -= 1.0f;
    }

    float scalarPotential;
    float[] pathVector;

    switch (status) {
        case NORMAL -> {
            // progressベースで計算（cycleDurationに依存しない）
            scalarPotential = calculateGaussianSumPotentialNormalized(progress);
            pathVector = getHeartVectorPathNormalized(progress);
            scalarPotential *= (medicalData.getBloodLevel() / 100.0f);
        }
        // ...
    }

    medicalData.setCycleTime(progress); // progressを保存
    // ...
}

// 正規化されたガウス計算（progress = 0.0〜1.0）
private static float calculateGaussianSumPotentialNormalized(float progress) {
    // P波: 進行度12%にピーク
    float p = gaussian(progress, 0.2f, 0.12f, 0.04f);
    // Q波: 進行度28%にピーク
    float q = gaussian(progress, -0.15f, 0.28f, 0.01f);
    // R波: 進行度30%にピーク
    float r = gaussian(progress, 1.2f, 0.30f, 0.01f);
    // S波: 進行度32%にピーク
    float s = gaussian(progress, -0.3f, 0.32f, 0.01f);
    // T波: 進行度50%にピーク
    float t = gaussian(progress, 0.35f, 0.50f, 0.08f);

    return p + q + r + s + t;
}
```

---

## 🟠 その他の仕様バグ

### ECG-5: 心拍数表示の非同期

**ファイル:** `HeadsideMonitorBlockEntity.java:66-75`

```java
// 心拍数は1秒ごとに更新
if (level.getGameTime() % 20 == 0) {
    be.heartRate = data.getHeartRate();
}

// しかしECGデータは毎tick更新
be.updateECGFromPlayerData(monitoredPlayer, level);
```

モニターに表示される心拍数と波形の周期が一致しない可能性があります。

---

### ECG-6: VF波形のクライアント間不整合

**ファイル:** `ModEvents.java:517-518`

```java
float x = (float) (Math.sin(time * 8) * 0.4 + Math.sin(time * 15) * 0.6
          + (Math.random() - 0.5) * 0.3);  // ← Math.random()
```

`Math.random()`はクライアント/サーバー間で異なる値を返すため、マルチプレイヤー環境で波形が同期しません。

**推奨修正:**
```java
// シード付きランダムを使用
float noise = (player.level().random.nextFloat() - 0.5f) * 0.3f;
```

---

## バグ影響度サマリー

| ID | 問題 | 症状 | 重大度 |
|----|------|------|--------|
| ECG-1 | 心拍数の毎tick再計算 | QRS波の不規則な振幅変化 | 🔴 高 |
| ECG-2 | cycleTime/cycleDuration不整合 | 不整脈様の波形 | 🔴 高 |
| ECG-3 | ガウスパラメータの変動 | R波が43%まで低下 | 🔴 高 |
| ECG-4 | データフロー全体の問題 | 上記すべての複合 | 🔴 高 |
| ECG-5 | 心拍数表示の非同期 | UI不一致 | 🟠 中 |
| ECG-6 | VF波形のランダム性 | マルチプレイ不整合 | 🟡 低 |
