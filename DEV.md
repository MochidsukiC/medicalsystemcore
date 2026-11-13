# DEV.md

## 1. プロジェクト仕様書

医療システム MOD。失血・出血・心拍/心停止・除細動・点滴・担架・救助要請(/119 と救急ポータル)・医師カード権限・QTE・医療バッグを扱う。

- mod id: `medicalsystemcore` / パッケージ: `jp.houlab.mochidsuki.medicalsystemcore`

### リポジトリ構成
| ディレクトリ | 内容 |
|---|---|
| `forge/` | Forge 1.20.1 / Forge 47.4.6 / ForgeGradle 6 / Gradle 8.8 / Java 17 |
| `fabric/` | Fabric 26.2 / Loader 0.20.1 / Loom 1.19-SNAPSHOT / Fabric API 0.161.0+26.2 / Gradle 9.8.2 / Java 25 / splitEnvironmentSourceSets |
| `common/` | Minecraft 非依存の純 Java 17。計算ルールと設定。両ビルドが srcDir で直接取り込む。依存は Gson と SLF4J のみ。 |

Forge と Fabric は Gradle の要求が衝突するため独立したビルド。両方を維持する。

### ビルド
- Forge: `cd forge && ./gradlew compileJava --no-daemon -Dorg.gradle.java.home=C:/Users/dora2/.jdks/temurin-17.0.8.1`
  - ユーザーの `~/.gradle/gradle.properties` が他プロジェクト用に JDK 25 を指定しており、プロジェクトの gradle.properties より優先されるため、コマンドラインで JDK 17 を指定する。
- Fabric: `cd fabric && ./gradlew build --no-daemon`
- Fabric 起動: `cd fabric && ./gradlew runClient --no-daemon`

### 詳細資料
- [docs/bug-audit.md](docs/bug-audit.md) 確認済みバグ B01〜B63(重複あり)
- [docs/rules-inventory.md](docs/rules-inventory.md) ゲームルール計算の所在と設定 46 項目
- [docs/fabric-26.2-api-mapping.md](docs/fabric-26.2-api-mapping.md) Forge から Fabric 26.2 への対応表
- [docs/fabric-port-plan.md](docs/fabric-port-plan.md) Fabric 移植計画と確定事項

## 2. 現在の仕様

- 設定: Forge/Fabric とも `config/medicalsystemcore.json`(common の自前 JSON 実装、Gson)。ForgeConfigSpec は廃止済み。クライアントが参照する項目はログイン時にサーバーから同期。旧 `medicalsystemcore-server.toml` の値は手動移行。
- QTE 補正: 大成功 +20 / 成功 ±0 / 失敗は判定なし。補正値は設定項目にする。
- QTE 不正対策: サーバーが QTE 開始を記録し、結果は 1 回だけ受理。受理時に医師カード・距離・充電状態・患者の心停止を再検証する。成功判定はクライアント判定のまま(サーバー再計算はしない)。
- 蘇生成功後は HP 0.1・低血液量のまま(輸血・止血が必要。意図した仕様)。
- 救助要請はワールドのセーブデータに保存し、医師が対応済みの通報を削除できる。
- /119 に回数制限は付けない。
- /doctorcard revoke は失効した免許番号をワールドに保存し、権限確認で照合して無効化する。
- 心停止中でも奈落ダメージは通し、即死させる。
- 死亡時は医療データを引き継がない。End からの帰還では引き継ぐ。
- /kill は NORMAL なら心停止、心停止中なら死亡。

### Fabric 版の Forge との意図的な挙動差
- 心停止中の無敵時はノックバックも無い(ALLOW_DAMAGE)。
- 意識不明中はエンティティ攻撃も止める(Forge より範囲が広い)。
- トーテム所持時はトーテムが心停止より優先。
- シングルプレイの一時停止中は QTE 等のタイマーも止まる。
- 点滴台の保存形式が変わり(Items)、Forge のワールドの点滴台の中身は引き継げない。
- 使用済みチューブが新品とスタックする。
- 壊れた SavedData(救助要請・失効リスト・発行台帳)は空で読み込み、上書き保存しない(エラーログを出す)。
- クライアント状態は切断時に加えて次の接続開始時(JOIN)にも消去。
- 担架の乗客の足元は姿勢によらず担架の Y −0.25(Forge と同じ)。
- CardReaderItem・ClientStretcherHandler・DebugOverlayHandler は移植していない。

## 3. 問題

docs/bug-audit.md の監査バグ(B01〜B63)は、B20(意図した仕様)と B30(TODO 6)を除き修正済み。Fabric 26.2 版は動作確認済み。Forge 側の修正(492a0e8〜68e0b19、f36ae70)と QTE UI 刷新(245fb14、除細動器モニター風 HUD)は実装済みだが、ユーザーの動作確認待ち。

### 既知の不具合(Forge 由来、両ローダー共通)
- StretcherBlock に ticker が無く、BlockEntity の tick が動かない。
- インベントリ満杯で意識不明になると、手持ちアイテムを失うことがある。

## 4. TODO(順序付き)

1. Forge 修正セクションの動作確認(ユーザー)
2. 設定値をその場で変更できるコマンドと管理パネル(common の設定保管庫を使う)
3. ハードコードされた日本語文字列の i18n(両ローダー)
4. エフェクトアイコンの PNG(ユーザーが自作する)
5. rescue_tablet_base モデルのテクスチャ参照が #missing(Forge 由来の Blockbench モデル)
6. 未対応事項: 点滴の接続をディメンションをまたいで扱うか / QTE 中にチャンクがアンロードされた場合の放電 / PackColor 登録 / 意識不明中の移動禁止がクライアント側のみ(B30)
