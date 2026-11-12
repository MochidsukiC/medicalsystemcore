# DEV.md

## 1. プロジェクト仕様書

医療システム MOD。失血・出血・心拍/心停止・除細動・点滴・担架・救助要請(/119 と救急ポータル)・医師カード権限・QTE・医療バッグを扱う。

- mod id: `medicalsystemcore` / パッケージ: `jp.houlab.mochidsuki.medicalsystemcore`

### リポジトリ構成
| ディレクトリ | 内容 |
|---|---|
| `forge/` | Forge 1.20.1 / Forge 47.4.6 / ForgeGradle 6 / Gradle 8.8 / Java 17 |
| `fabric/` | Fabric 26.2 / Loader 0.20.1 / Loom 1.19-SNAPSHOT / Fabric API 0.161.0+26.2 / Gradle 9.8.2 / Java 25 / splitEnvironmentSourceSets |
| `common/` | Minecraft 非依存の純 Java 17。計算ルールと設定。両ビルドが srcDir で直接取り込む。依存は Gson と SLF4J のみ。**未作成(これから作成)** |

Forge と Fabric は Gradle の要求が衝突するため独立したビルド。両方を維持する。

### ビルド
- Forge: `cd forge && ./gradlew compileJava --no-daemon -Dorg.gradle.java.home=C:/Users/dora2/.jdks/temurin-17.0.8.1`
  - ユーザーの `~/.gradle/gradle.properties` が他プロジェクト用に JDK 25 を指定しており、プロジェクトの gradle.properties より優先されるため、コマンドラインで JDK 17 を指定する。
- Fabric: `cd fabric && ./gradlew build --no-daemon`

### 詳細資料
- [docs/bug-audit.md](docs/bug-audit.md) 確認済みバグ B01〜B63(重複あり)
- [docs/rules-inventory.md](docs/rules-inventory.md) ゲームルール計算の所在と設定 46 項目
- [docs/fabric-26.2-api-mapping.md](docs/fabric-26.2-api-mapping.md) Forge から Fabric 26.2 への対応表

## 2. 現在の仕様

- 設定: Forge/Fabric とも `config/medicalsystemcore.json`(common の自前 JSON 実装、Gson)。ForgeConfigSpec は廃止予定。クライアントが参照する項目はログイン時にサーバーから同期。旧 `medicalsystemcore-server.toml` の値は手動移行。
- QTE 補正: 大成功 +20 / 成功 ±0 / 失敗は判定なし。補正値は設定項目にする。
- QTE 不正対策: サーバーが QTE 開始を記録し、結果は 1 回だけ受理。受理時に医師カード・距離・充電状態・患者の心停止を再検証する。成功判定はクライアント判定のまま(サーバー再計算はしない)。
- 蘇生成功後は HP 0.1・低血液量のまま(輸血・止血が必要。意図した仕様)。
- 救助要請はワールドのセーブデータに保存し、医師が対応済みの通報を削除できる。
- /119 に回数制限は付けない。
- /doctorcard revoke は失効した免許番号をワールドに保存し、権限確認で照合して無効化する。
- 心停止中でも奈落ダメージは通し、即死させる。
- 死亡時は医療データを引き継がない。End からの帰還では引き継ぐ。

## 3. 問題

詳細は docs/bug-audit.md。重複をまとめて記載(B 番号は代表)。上記「現在の仕様」で決定済みの対処方針に沿って修正する。

### CRITICAL / HIGH
- CRITICAL: 医療バッグ GUI 中のホットバー数字キー入れ替えで中身が複製される(B01, B04)
- HIGH: QTE 結果パケットをサーバーが検証せず、誰でも(自分も)無制限に蘇生できる(B02, B06, B09)
- HIGH: 他人への包帯でサーバー側の包帯が消費されず、所持・時間・意識の検証もない(B03, B08)
- HIGH: 点滴台の下半分を壊すとパックが二重ドロップする(B05, B10)
- HIGH: 死亡後の tick で UNCONSCIOUS 姿勢が再登録され、リスポーン後も水泳姿勢になる(B07)

### MEDIUM
- 通報の認可: 更新・閲覧に権限チェックなし、座標が全員に漏れる、メモ長上限なし(B11, B12, B15, B24)
- 応援通報(SUPPORT)の権限判定が逆(B14, B23)
- ギブアップ/搬送パケットで設定・長押し時間を未確認、連打でスパム可(B13, B22)
- 意識不明のまま再ログインで強制姿勢が復元されない(B16, B21)
- 担架: passenger フィールドが降車後も残る(B17)
- 除細動: オフハンド電極や QTE オーバーシュートでチャージが解除されない(B19)
- /doctorcard revoke がインベントリ内のカードしか消さない(B25)
- 点滴台: 爆発で 2 台になる(loot table に half=lower 条件なし)(B26)、満杯時に素手取り出しでパック消失(B29)
- Config.SPEC を SERVER と COMMON の両方で登録(B27, B39)
- /119 の無制限追加(B28、回数制限なしは仕様として決定済み。肥大化対策は永続化と削除で対応)
- クライアント心電図履歴 Map のリーク(B18)
- 蘇生後の HP 0.1(B20、意図した仕様のため修正対象外)

### LOW(分類)
- 保存と状態: 通報の static 保持・非永続・順序不整合(B34, B35, B38, B44)、切断時のクライアント状態未クリア(B36, B43)、End 帰還で医療状態リセット(B37)、医療データ NBT 非対称・点滴接続未保存(B46, B55)、奈落で救助不能(B50)
- 同期量: 全プレイヤーの状態を毎 tick 全追跡者へ送信(B32)
- サーバー検証: 意識不明中の移動禁止がクライアントのみ(B30)、ギブアップ照合が表示名/プロファイル名で不一致(B33)
- 除細動器・電極: クールダウン消失(B40, B47)、孤立電極で getValue(CHARGED) 例外(B31, B48, B58)、arePadsTaken が戻らない(B42)、他機の電極を収納可(B57)
- ケーブル: モニター右クリックで無限配布(B45, B60)
- 設定・計算: 未使用設定 DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER/TRANSITION_CHANCE(B49)、止血剤の説明と実装の不一致(B51)、心停止開始 tick のタイマー誤差(B52)、出血レベル表示の遅延と byte オーバーフロー(B53)
- アイテム: 包帯/チューブがクリエイティブでも消費(B54)、担架が足パーツのみ設置(B59)
- リソース: noradrenaline_pack のモデル/色なし(B56)、en_us 翻訳不足(B61)、パックのモデル参照・variant 不足(B62)、権限エラーの二重表示(B63)

### 移植時の注意
Fabric 移植で挙動差が出る点(AFTER_DAMAGE は防具適用前の値、ALLOW_DAMAGE で false だとノックバックも消える等)は docs/fabric-26.2-api-mapping.md を参照。

## 4. TODO(順序付き)

1. Forge: common/ 作成と設定の JSON 統一、計算ルールの切り出し(設定の二重登録・未使用設定・除細動判定の 2 経路・心停止タイマー・出血レベル表示の修正を含む)
2. Forge: サーバー側検証(QTE 開始記録、他人への包帯、通報の権限・メモ長、ギブアップ、応援通報の権限逆転)
3. Forge: 保存と状態(通報の永続化と削除、医師カード失効リスト、切断時のクライアント状態クリア、姿勢の不具合、End 帰還の引き継ぎ、医療データ NBT、同期量の削減、奈落)
4. Forge: アイテム・ブロックの複製/消失(医療バッグ、点滴台、担架、除細動器/電極、ケーブル、クリエイティブ消費)
5. Forge: リソース(翻訳・モデル不足、二重メッセージ)
6. QTE の UI 刷新(両ローダー。デザイン案を提示してから)
7. Fabric 26.2 への移植(docs/fabric-26.2-api-mapping.md に従う。Mixin 必要箇所あり)
8. 設定値をその場で変更できるコマンドと管理パネル(common の設定保管庫を使う)
