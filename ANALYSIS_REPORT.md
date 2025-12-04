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
