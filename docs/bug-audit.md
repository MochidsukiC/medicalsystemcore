# Bug audit (HEAD 3fcbad7)

## B01 [CRITICAL] 医療バッグ GUI 中に数字キー入れ替えでバッグを手から外すと中身が複製される
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/menu/MedicalBagMenu.java:80 (content-cmd)

### 状況
前提: Forge 1.20.1 の AbstractContainerMenu.doClick(SWAP) と ServerPlayer.tick の stillValid 判定。(1) メインハンド（選択中ホットバー）の医療バッグを開き、中身を全部プレイヤーインベントリへ移す。(2) 空のインベントリスロットにカーソルを合わせ、選択中ホットバーの数字キーを押す（ClickType.SWAP, button=selected）。clicked() は slotId がバッグのホットバースロットと一致する場合しか弾かないため、バッグは別スロットへ移り、手は空になる。(3) 次の tick で stillValid(:62-64) が false になり closeContainer→removed()(:67-75) が呼ばれるが、手の物が MedicalBagItem でないため保存されない。バッグの NBT は開いた時点の中身のまま残り、取り出した分がまるごと複製される。移し先に別のバッグがあると、そちらの中身はこのセッションの内容で上書きされて失われる。オフハンドで開いた場合（hand==OFF_HAND）は保護がまったくなく、F キー（SWAP button=40）で同じことが起きる。改造クライアントから ServerboundSetCarriedItem を送って選択スロットを変えても同じ結果になる。

### 検証
報告どおり再現経路が成立する。
- 前提: Forge 1.20.1 のバニラ処理。ServerGamePacketListenerImpl.handleContainerClick はクリック前に stillValid だけを確認し、その後 menu.clicked() を呼ぶ。AbstractContainerMenu.doClick の SWAP は `inventory.getItem(button)` を手持ちスタックとして扱い、対象の Slot と入れ替える（手持ちが入っていて対象が空なら、Slot.mayPlace が true のとき `inventory.setItem(button, EMPTY)` を行い対象スロットへ置く）。ServerPlayer.tick は `!containerMenu.stillValid(this)` のとき closeContainer() を呼び、その中で menu.removed() が実行される。
- 呼び出し元: MedicalBagItem.java:37,40 では、開いた手 pHand だけを渡してメニューを作っている。MedicalBagMenu.java:36-39 は開いた時点の手の NBT を ItemStackHandler に読み込むだけで、開いた ItemStack の参照は保持していない。
- MedicalBagMenu.java:80-85 の clicked() の保護は「hand==MAIN_HAND かつ slotId==27+27+selected」の場合だけ。SWAP の button（=選択中のホットバー番号）でバッグを動かす経路は塞がれていない。プレイヤーインベントリのスロットは素の Slot（:51,:57）なので mayPlace/mayPickup は true になる。したがって、空のインベントリスロットにカーソルを合わせて選択中の数字キーを押すと、バッグはそのスロットへ移り、手は空になる。
- その後の stillValid（:62-64）は false になり removed() へ進む。しかし removed() は手の物が MedicalBagItem のときしか保存しない（:70-73）。保存箇所はここだけで（grep で確認済み。onContentsChanged の上書きもない）、バッグの NBT は開いた時点のままになる。一方、取り出してプレイヤーインベントリへ移した分は残るので、その分が複製される。stacksTo(1)（Medicalsystemcore.java:133）なので、移動が部分的に止まることもない。
- 移し先に別のバッグ B がある場合は、SWAP の両方埋まっているケース（mayPickup && mayPlace がどちらも true）になり、A と B が入れ替わる。手に B が来るので stillValid は true のまま続き、閉じるときに B の NBT がこのセッションの内容で上書きされる。B の元の中身は失われ、A は元の中身を保ったままになる。
- hand==OFF_HAND の場合、:80 の条件で保護は丸ごとスキップされる。SWAP の button=40（バニラのオフハンド入れ替えキー F）でオフハンドのバッグを空スロットへ移せるので、同じ複製が起きる。
- 改造していないバニラクライアントのキー操作だけで、際限なく繰り返せる複製になる。バッグに入るのは MOD のアイテムだけ（:137-141）だが、それでもサーバー経済を壊す複製であり、CRITICAL を維持する。

### 修正方針
症状を抑えるのではなく、「開いたバッグそのもの」を追跡するように直す。
(1) コンストラクタで開いた ItemStack の参照（サーバー側）を保持する。stillValid は `player.getItemInHand(hand) == openedStack`（参照の同一性で比較）で判定し、removed() でも保存先をこの参照に限定する。手が別のバッグに替わっていたら無効として閉じ、別バッグへは書き込まない。
(2) ItemStackHandler.onContentsChanged をオーバーライドし、変更のたびに openedStack の "Inventory" タグへ書き戻す。こうすれば、閉じる前にバッグが動いても NBT と中身が食い違わない。
(3) clicked() で ClickType.SWAP を次の場合に拒否する。hand==MAIN_HAND では button==selected、hand==OFF_HAND では button==40。既存の slotId 保護はオフハンドでも適用されるよう条件を見直す。可能なら、バッグが入っているホットバースロットを mayPickup/mayPlace=false の専用 Slot サブクラスにする。
(4) 改造クライアントから ServerboundSetCarriedItem で選択スロットを切り替える経路は、(1) の同一性チェックによって次の tick で閉じられ、(2) で保存も済んでいるので複製にはならない。

## B02 [HIGH] 除細動QTE結果パケットをサーバーが一切検証しないため、改造クライアントが任意プレイヤーを無制限に蘇生できる（自分自身も可）
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundQTETResultPacket.java:47 (net-sides)

### 状況
改造クライアントが ServerboundQTETResultPacket(targetEntityId=任意のプレイヤー, GREAT_SUCCESS) を連打する。handle() が見るのは sender の有無(:45)、target が同じレベルの ServerPlayer であること(:47-48)、手持ちの電極 NBT(:53。無ければ単に除細動器リセットをスキップするだけ)の3点だけ。医師カード、距離、意識の有無、電極の所持、除細動器の充電状態、サーバーが実際に QTE を開始したか(ElectrodeItem:43 の送信記録)、クールダウンは確認されない。蘇生判定(:87-91)は1パケットごとに乱数を振り直すので、数パケットで HeartStatus.NORMAL に戻り、damageImmune も解除される(:95-98)。心停止中の本人が自分の entityId を指定すれば自己蘇生もでき、医療システムの前提がまるごと崩れる。

### 検証
反証を試みたが、報告どおりに起きることを確認した。
- ModPackets.java:50-53 で ServerboundQTETResultPacket は PLAY_TO_SERVER として登録され、consumerMainThread で handle が呼ばれる。パケットの前段で検証する層はない（mixin も無く、他に送信者を絞る仕組みも見当たらない）。
- ServerboundQTETResultPacket.java:44-48 で確認しているのは、sender が null でないことと、targetEntityId が healer と同じレベルの ServerPlayer であることだけ。:51-62 の電極 NBT は、無ければ除細動器のリセットを飛ばすだけで、処理は止まらない。医師カード（MedicalAuthorizationUtil）、距離、healer の意識・心臓状態、除細動器の CHARGED 状態、保留中の QTE、クールダウンのどれも確認していない。
- サーバーが QTE を開始したことは、ElectrodeItem.java:41-43 で ClientboundStartQTEPacket を送るだけで、どこにも記録されない。QTEResult もクライアントが ClientQTEManager.java:41-56 で判定して送っているので、サーバー側には照合できる状態がない。
- :87-91 はパケットを受け取るたびに Math.random() で判定し直す。GREAT_SUCCESS なら chance+20 になる（:79-81）。心停止中は ModEvents.java:353-358 で chance が 0 以下になると kill されるため、生存中は chance>0 で、成功率は CARDIAC_ARREST で 20% 超、VF ではその倍になる。連打すれば数パケットで :95-98 に到達し、NORMAL への復帰、chance=100、タイマー 0、damageImmune 解除がすべて適用される。
- targetEntityId を送信者自身の ID にすれば、getEntity は送信者の ServerPlayer を返す（instanceof ServerPlayer を満たす）。心停止は Minecraft の死亡ではなく、エンティティは生存状態のまま接続も生きているので、自己蘇生もできる。
前提: 攻撃者は、Forge の SimpleChannel（同じ MOD バージョン）でこのパケットを組み立てて送れる改造クライアントを使う。バニラ Forge は、プレイヤーの状態を理由に PLAY_TO_SERVER のカスタムパケットを拒否しない。
重大度を付け直した理由: 正規クライアントでは起きず、改造クライアントが必要。被害は、ゲーム内の医療ルール（蘇生の権限・資源・時間制限）を完全に回避されることにとどまる。サーバーのクラッシュ、データ破壊、任意コード実行、他プレイヤーのアカウントへの影響はない。そのため CRITICAL ではなく HIGH とした（医療 RP サーバーで中核の仕組みが無効化されるので、MEDIUM より重い）。
付随して確認した問題: 有効でないパケットでも、healer がメインハンドに持つ電極の NBT が指す除細動器を、距離に関係なく resetCharge/startCooldown してしまう（:53-61）。また正規クライアントでも、バーが行き過ぎた場合（ClientQTEManager.java:68-73）は結果パケットが送られないため、除細動器の充電が消費されずに残る。

### 修正方針
報告の修正方針はおおむね妥当。具体的には次のとおり。
(1) ElectrodeItem.interactLivingEntity で QTE を開始するときに、サーバー側で保留 QTE を記録する（healer の UUID をキーに、target の UUID、defibPos、開始 gameTime を持つ。Map またはプレイヤーの capability に置く）。
(2) ServerboundQTETResultPacket.handle では、保留 QTE が存在し、targetEntityId と一致し、タイムアウト内であるときだけ受理し、受理した時点で記録を消して 1 回限りにする。
(3) 受理するときに次を再検証する: MedicalAuthorizationUtil による医師カード、healer の意識と心臓状態が NORMAL であること、healer と target の距離、defibPos の BlockState が CHARGED であること、メインハンドの電極 NBT の DefibrillatorPos が保留記録と一致すること。target が CARDIAC_ARREST/VF でなければ何もしない。
(4) 理想的には、開始 tick からの経過 tick でバー位置をサーバー側で再計算し、クライアントが申告した QTEResult は使わない（少なくとも、経過 tick と矛盾する申告は FAIL として扱う）。
(5) 除細動器のリセットは、検証を通った後でだけ行う。行き過ぎ（overshoot）の場合もクライアントから FAIL を送るか、サーバー側のタイムアウトで保留を破棄し、充電を消費させる。
実装時の注意: 保留状態をどこに置くか（新しい抽象を作るか、既存の capability に足すか）は設計判断にあたるため、実装前に承認を得ること。

## B03 [HIGH] 他人への包帯治療でサーバー側の包帯が消費されない（無限包帯）上、所持・治療時間・意識の検証もない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundFinishHealPacket.java:42 (net-sides)

### 状況
正規クライアントでも、他人への包帯治療が完了すると ClientHealingManager.java:59 でクライアント側のスタックだけが shrink(1) される。サーバーの handle(:35-71) は包帯を減らさないので、次のインベントリ同期で数が元に戻り、包帯は実質減らない。さらに改造クライアントなら、包帯を持たず、治療時間(BANDAGE_OTHER_USE_DURATION)も待たずに、5ブロック以内(:45)の任意のプレイヤーへ FinishHeal を連打できる。自分の entityId を送れば距離0の自己治療にもなる。意識不明の送信者も弾かれない(onPlayerInteract によるキャンセルはバニラの interact 経路だけに効き、MOD チャネルには効かない)。

### 検証
報告は正しい。ServerboundFinishHealPacket.handle（ServerboundFinishHealPacket.java:35-71）がサーバー側で確認しているのは、送信者が null でないこと（:40）、target が Player であること（:43）、距離の二乗が 25 未満であること（:45）の 3 点だけ。その後エフェクト付与（:57-63）と効果音（:65）を無条件で行い、包帯の消費・メインハンドの所持確認・target != healer・送信者の意識・経過時間のどれも確認していない。

包帯の消費は ClientHealingManager.java:59 の `mc.player.getMainHandItem().shrink(1)` だけで、これはクライアント側の ItemStack にしか効かない。サーバー側の ItemStack を減らす箇所はほかにない。BandageItem.interactLivingEntity（BandageItem.java:27-34）は、サーバー側では何もせず SUCCESS を返すだけ。BandageItem で shrink しているのは自己使用の finishUsingItem（:45）だけ。

前提（Forge 1.20.1 / バニラの挙動）: サーバー側の InventoryMenu.broadcastChanges は、サーバーのスタックと前回送信した remoteSlots の差分しか送らない。このためクライアントだけで shrink しても再送は起きず、表示上は減ったままになる。ただしサーバー側の個数（authoritative）は一度も減らない。ドロップ・使用・死亡時のドロップなどはサーバー側の個数で処理される。またスロットクリックなどで stateId がずれると sendAllDataToRemote で全体を再同期し、表示上の個数も元に戻る。したがって正規クライアントでも、他人への包帯治療では包帯が実質減らない（無限包帯）。

改造クライアントの場合は、ClientHealingManager.tick（:49-57）にある所持・視線・右クリック押下・BANDAGE_OTHER_USE_DURATION の判定を省いて FinishHeal を直接送れる。送れば半径 5 ブロック以内（:45）の任意のプレイヤーに BANDAGE_OTHER_LEVEL_INCREASE を付与できる。自分の entityId を送ると、`level().getEntity` は自分を返し距離は 0 なので自己治療になる。この場合、自己治療用の使用時間（BandageItem.getUseDuration :80-82）も BANDAGE_SELF_LEVEL_INCREASE も迂回される。

意識不明の扱い: ModEvents.onPlayerInteract（ModEvents.java:564-569）は PlayerInteractEvent をキャンセルするだけである。この Forge イベントはバニラの interact 経路（ServerGamePacketListenerImpl → ForgeHooks.onInteractEntity など）でしか発火しない。SimpleChannel のパケットハンドラには効かないので、意識不明の送信者も弾けない。

重大度: 正規クライアントでも無限包帯が成立し、改造クライアントなら遠隔・即時・無消費で治療できるため、HIGH のままとする。

### 修正方針
ServerboundFinishHealPacket.handle で、エフェクトを付与する前に次をサーバー側で確認する。
1. `healer.getMainHandItem().getItem() instanceof BandageItem` であること
2. `target != healer` であること
3. healer の PlayerMedicalData で isConscious() が true であること
4. 所要時間を満たしていること

所要時間の確認は次のように実装する。サーバー側の BandageItem.interactLivingEntity（`!level.isClientSide()` の分岐。意識不明なら ModEvents.onPlayerInteract で既にキャンセルされる）で、healer UUID → (targetId, 開始 gameTime) を記録する。FinishHeal を受けたら、targetId が一致し、経過時間が BANDAGE_OTHER_USE_DURATION*20 tick 以上であることを確認し、確認後に記録を消す。

すべて通ったら、サーバー側で `healer.getMainHandItem().shrink(1)` を行う（creative の扱いは自己使用側と揃える）。ClientHealingManager.java:59 のクライアント側 shrink は削除するか、予測表示として残してサーバーの同期に任せる。距離チェック（:45）は残す。

## B04 [HIGH] 医療バッグの中身はメニューを閉じた時だけ保存され、ホットバー数字キーのSWAPでバッグを手から外すとバニラクライアントでも中身を複製できる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/menu/MedicalBagMenu.java:67 (state-persist)

### 状況
前提: バニラ1.20.1の AbstractContainerMenu.doClick(SWAP) は、slotId のスロットとホットバー番号 button のスロットを、slot.mayPlace だけを見て入れ替える。手順: (1) 中身ありのバッグを開く(:38 で NBT を読む) (2) shift クリックで中身をプレイヤーインベントリへ移す (3) カーソルをプレイヤーインベントリの空きスロットに置き、バッグがあるホットバーの数字キーを押す。clicked() はバッグ自身のスロット番号しか弾かないので(:79-85)、バッグがインベントリへ移る (4) 手のアイテムがバッグでなくなり stillValid(:63) が false になってメニューが閉じる (5) removed()(:67-75) は手のアイテムが MedicalBagItem でないため保存しない。結果、バッグの NBT は開いた時の中身のまま残り、取り出した分がプレイヤーの手元にもある(複製)。逆に操作中にバッグへ入れた物は消える。サーバー/クライアントのクラッシュでも同じで、オートセーブでプレイヤーインベントリは保存されるがバッグ NBT は閉じるまで更新されないので、巻き戻ると複製になる。

### 検証
報告どおり起きる。コードの裏付け: MedicalBagMenu.java:36-39 は開いた時に getItemInHand(hand) の NBT を新しい ItemStackHandler へ読み込むだけで、以後 bagInventory の変更はどこにも書き戻されない（MedicalBagSlot:131-143 は SlotItemHandler をそのまま使い、onContentsChanged の上書きも無い）。書き戻しは removed():67-75 だけで、しかもその時点で getItemInHand(hand) が MedicalBagItem の場合に限られる。clicked():78-86 が弾くのは slotId == BAG_SLOT_COUNT+27+selected（バッグ自身のホットバースロット）だけで、ClickType を見ていない。OFF_HAND の場合は何も弾かない。
前提（バニラ/Forge 1.20.1）: AbstractContainerMenu.doClick の SWAP 分岐は、slots.get(slotId) と inventory.getItem(button) を slot.mayPickup/mayPlace だけを見て入れ替える。button はホットバー 0-8、オフハンドは 40。ServerGamePacketListenerImpl.handleContainerClick は処理の前にだけ stillValid を確認する。ServerPlayer.tick は stillValid が false なら closeContainer() を呼び、その中で menu.removed() が呼ばれる。
手順: (1) 中身ありのバッグ（stacksTo(1)、Medicalsystemcore.java:133）を開く (2) shift クリックで中身をプレイヤーインベントリへ移す（quickMoveStack:100-104） (3) 空いているプレイヤースロット（index 27-53 でバッグのスロットではないもの）にカーソルを置き、選択中のホットバー番号キーを押す。clicked() は通るので、バッグがそのスロットへ移る (4) 次の tick で stillValid(:63) が false になり、closeContainer から removed() が呼ばれる (5) :71 の判定で保存されない。結果、バッグの NBT は開いた時の中身のまま残り、取り出した分もインベントリにある。つまり複製になる。オフハンドのバッグはさらに簡単で、どのスロットでも F キー（button 40）の SWAP で同じことが起きる。
別のやり方: インベントリに 2 個目のバッグ B があれば、SWAP で手のバッグ A と B を入れ替えられる。この場合 stillValid は true のままで、閉じた時に A で編集した中身が B に上書きされる。B の元の中身は消え、A は元の中身を保つので、これも複製と消失になる。クラッシュ時の巻き戻りによる複製も、閉じるまで NBT を書かないので起きうる。
複製できるのは mayPlace(:137-142) により本 MOD 名前空間のアイテムだけだが、マルチサーバーでバニラクライアントの通常操作で再現できるので HIGH のままとする。

### 修正方針
根本原因は、編集中の bagInventory と書き戻し先の ItemStack が結び付いていないこと。(1) コンストラクタで、開いた時のバッグの ItemStack 参照（bagStack）を final フィールドに保持する。ItemStackHandler の onContentsChanged を override して、変更のたびに bagStack.getOrCreateTag().put("Inventory", serializeNBT()) で書き込む（サーバー側のみ）。バニラの SWAP は同じ ItemStack オブジェクトを別スロットへ移すので、バッグが移動しても参照先へ書き込まれ、複製は成立しなくなる。removed() で getItemInHand(hand) へ書き戻す処理はやめる（別のバッグ B への誤上書きを防ぐため）。(2) stillValid は「手のアイテム == 保持した bagStack（同一参照）」で判定するように変える。instanceof 判定のままだと別バッグとの入れ替えを見逃す。(3) clicked() で、バッグが入っているインベントリ index（MAIN_HAND なら selected、OFF_HAND なら 40）を button に指定する ClickType.SWAP を拒否する。OFF_HAND の場合も保護する。(1) と (2) があれば (3) は保険の位置付けだが、バッグが手から外れた後の挙動を明確にするため入れておくのがよい。

## B05 [HIGH] 点滴台の下半分を壊すと、中のパックが2回ドロップする(アイテム複製)
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/IVStandBlock.java:59 (state-persist)

### 状況
前提: Forge 1.20.1 の IForgeBlock.onDestroyedByPlayer は、playerWillDestroy を呼んでから自分の setBlock を行う(sources jar の IForgeBlock:192-195 で確認済み)。サーバーの LevelChunk.setBlockState はフラグ35でも onRemove を呼ぶ。手順: 血液パック等を入れた点滴台の下半分を素手で壊す。(1) playerWillDestroy(:161) が上半分を AIR にする → 上半分の onRemove で bePos=下半分(:59)。BE はまだ存在するので、全スロットのスタックを ItemEntity として生成する(:64)。ハンドラは空にしていない (2) 続いて下半分が消える → 下半分の onRemove が同じ BE から同じスタックをもう一度ドロップする。結果、パックが2倍になる(同じ ItemStack インスタンスを共有していても、チャンク保存・再読込やホッパー回収の時点で別個のスタックになる)。上半分を壊した場合は1回しかドロップしない。

### 検証
報告どおり起きることを確認した。反証できる箇所は見つからなかった。
(1) Forge 1.20.1-47.4.0 の sources jar で IForgeBlock.java:192-195 を確認した。onDestroyedByPlayer は playerWillDestroy(m_5707_) を呼んでから、自分の位置に対して setBlock(..., 3) を行う。
(2) 下半分を壊すと、IVStandBlock.java:161 の playerWillDestroy が上半分をフラグ 35 で AIR にする。前提として、1.20.1 の LevelChunk.setBlockState はサーバー側ではフラグに関係なく旧 state の onRemove を呼ぶ（vanilla の挙動。今回はソースを開いていない）。上半分の onRemove では :59 の bePos が下半分になる。この時点で下半分の BE はまだ残っているので、:60-66 で全スロットのスタックを ItemEntity として出す。ハンドラのスロットは空にしていない。上半分の super.onRemove(:69) は上半分の位置の BE を消そうとするが、そこに BE は無いので何も起きない。
(3) 続いて IForgeBlock:195 で下半分が置き換わる。下半分の onRemove が同じ BE の、まだ空になっていないハンドラからもう一度ドロップする。
(4) 複製が実際に残ることも確かめた。:64 は stack をコピーせずに渡すので、2 つの ItemEntity が同じ ItemStack インスタンスを共有する。パックは stacksTo(16)（Medicalsystemcore.java:137-147）で、2 つは同じ座標に出る。前提として、1.20.1 の ItemEntity の合体処理は、受け側に copyWithCount(1+1) で新しいスタックを作り、元のスタックを shrink する。共有インスタンスが 0 になった側は消えるが、合体先は数 2 の別スタックとして残る。このため、チャンクの再読込やホッパーを経なくても、数 tick で 1 個が 2 個になる。ホッパー回収（取り込むのは copy で、元の ItemEntity は shrink されない）や保存・再読込でも、別々のスタックになる。
(5) 上半分を壊した場合は 1 回だけになる。先に下半分の onRemove がドロップし、super.onRemove が BE を消すので、その後の上半分の onRemove では getBlockEntity が null になる。これも報告と一致する。
IVStandBlockEntity 側（blockentity/IVStandBlockEntity.java）に、中身を出す処理やクリア処理は無かった。重大度は、サバイバルで素手 1 回の破壊から確実に再現するアイテム複製なので、HIGH が妥当。補足として、クリエイティブで壊した場合も onRemove 経由で中身がドロップする（:160 のコメントの意図とは別の挙動）。

### 修正方針
ドロップは BE を持つ下半分の onRemove（pState.getValue(HALF)==LOWER）だけで行う。上半分の onRemove ではドロップしない。上半分を壊したときも、playerWillDestroy が下半分を AIR にするので、下半分の onRemove で 1 回だけドロップする。ドロップするときは、各スロットの getStackInSlot(i).copy() を ItemEntity に渡すか Containers.dropItemStack を使い、そのあと itemHandler.setStackInSlot(i, ItemStack.EMPTY) でスロットを空にする。ドロップした実体を BE に残さないことが目的。スタック共有による合体複製を断つため、コピーは必須。

## B06 [HIGH] QTE結果パケットをサーバーが検証しないため、充電・電極・距離・権限なしで誰でも蘇生できる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundQTETResultPacket.java:58 (state-persist)

### 状況
改造クライアントが ServerboundQTETResultPacket(任意のプレイヤーの entityId, GREAT_SUCCESS) を連続送信する。handle はエンティティが同じレベルにいることしか確認しない(:47-48)。電極 NBT が無ければ除細動器の処理は丸ごと飛ばされ(:53-62)、あっても isCharged もクールダウンも見ずに resetCharge/startCooldown するだけ(:58-61)。その後 :73 以降の蘇生判定が無条件に走るので、心停止・VF のプレイヤーを遠隔から何度でも蘇生できる。サーバーが QTE を開始した記録(ClientboundStartQTEPacket の送信)とも照合しない。

### 検証
反証を試みたが、報告どおり成立する。
- ModPackets.java:50-53 では ServerboundQTETResultPacket を PLAY_TO_SERVER として登録し、consumerMainThread(handle) を呼ぶだけで、前段に検証はない。
- ServerboundQTETResultPacket.java:44-48 の確認は、送信者が非 null で、healer.level().getEntity(id) が ServerPlayer であることだけ。Level#getEntity は同じディメンションにロード済みのエンティティを ID で引くだけで距離を見ないため、遠隔の対象にも当たる。
- :51-62 で除細動器の処理に入る条件は、メインハンドのアイテムが DefibrillatorPos NBT を持つことだけ。NBT が無ければ丸ごと飛ばされる。あっても DefibrillatorBlockEntity.isCharged()(DefibrillatorBlockEntity.java:27)やブロック状態 CHARGED、クールダウン中かどうかを見ずに、resetCharge と startCooldown を呼ぶだけ。
- :66 で返るのは FAIL のときだけ。SUCCESS/GREAT_SUCCESS なら :73-128 の蘇生判定が無条件に走る。CARDIAC_ARREST/VF の対象は確率で NORMAL に戻り、:95-98 でタイマーとダメージ無効も解除される。
- 充電チェック(ElectrodeItem.java:41)と医師カード認証(ElectrodeItem.java:29)は、ClientboundStartQTEPacket を送る前にしか行われない。サーバーには開始済み QTE の記録が一切ない。ClientboundStartQTEPacket には targetEntityId しか入っていない。ClientQTEManager.java:13-60 の状態もクライアント側の static 変数だけで、サーバーと照合する仕組みがない。
- 成功判定はクライアントの barPosition で決まる(ClientQTEManager.java:44-53)ので、成否そのものもクライアントの言い値になる。
- 失敗した試行は CA と VF の間を行き来するだけで(:108-113)、何度送っても止まらない。NORMAL の対象は状態が変わらない。連続送信すれば、心停止・VF のプレイヤーをほぼ確実に蘇生できる。
前提: Forge 1.20.1 の SimpleChannel は、受信パケットについて送信元クライアントが正規かどうかを検証しない。改造クライアントは任意の登録済みパケットを送れる。
重大度: 影響はゲーム進行の不正(医師権限・除細動器・距離制限の迂回)で、データ破壊やクラッシュではない。ただしこの MOD の中核ルールである医師カード権限と蘇生フローを完全に迂回できるため、マルチサーバーでは HIGH が妥当。

### 修正方針
報告の方針で妥当。具体的には次のとおり。
(1) ElectrodeItem.interactLivingEntity で ClientboundStartQTEPacket を送る時に、サーバー側の Map<UUID healer, PendingQTE(targetUUID または entityId, defibrillatorPos, dimension, expireGameTime)> に登録する。QTE 中の二重開始は拒否する。
(2) ServerboundQTETResultPacket.handle で pending を remove して取得し、次をすべて満たすことを検証する。満たさなければ何もしない。
  - pending があり、期限内で、targetEntityId が一致する
  - healer が MedicalAuthorizationUtil.checkMedicalAuthorizationSilent を満たす
  - メインハンドが ElectrodeItem で、その DefibrillatorPos が記録と一致する
  - その位置の DefibrillatorBlockEntity が isCharged(または blockstate CHARGED)である
  - healer と target の距離が interact 距離以内である
(3) 検証を通った後で、resetCharge/startCooldown と蘇生判定を行う。期限切れの pending は tick かログアウト時に掃除する。
クライアントが成否を決める設計は残る(タイミング自体の改ざんは防げない)。厳密にするなら、サーバーで開始 tick を記録し、結果受信 tick との差から判定ゾーンを再計算する方式も検討する。

## B07 [HIGH] 死亡後の tick で UNCONSCIOUS 姿勢が再登録され、リスポーン後も強制的に水泳姿勢になる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:233 (gameplay)

### 状況
前提: Forge 1.20.1 では死亡したプレイヤーもリスポーンするまで ServerPlayer.doTick→Player.tick が続き、PlayerTickEvent も発火する。手順: 心停止中のプレイヤーが kill() で死亡する（心停止タイマー切れ、蘇生確率 0、ギブアップ、/kill）。onLivingDeath(46-57) で conscious=true にし clearPoseControl で playerStates の UUID エントリが消える。ところが同じ tick の続き（handleCardiacArrest:349 の kill 後も onPlayerTick は 207-216 を最後まで実行する）か次の tick で、handleConsciousnessState が health<=0<5 を見て newConscious=false と判定し、PoseController.setUnconsciousPose(true) を呼ぶ。これで同じ UUID の UNCONSCIOUS エントリが作り直される。リスポーンで生成される新しい ServerPlayer は capability が初期値（isConscious=true）なので、oldConscious==newConscious となり解除は呼ばれない。一方 maintainPoseControl(PoseController:256-266) は UUID で古い状態を引き当て、毎 tick setForcedPose(SWIMMING) と姿勢の同期パケット送信を続ける。結果、リスポーンしたプレイヤーがサーバー上と他プレイヤーの画面で這った姿勢（当たり判定 0.6）のまま固定され、HP<5 になって回復するか再ログインするまで直らない。

### 検証
反証を試みたが、報告どおりに再現する。
1) 死亡処理: handleCardiacArrest(ModEvents.java:348-350, 357-358) で serverPlayer.kill() を呼ぶ。Forge 1.20.1 では LivingEntity.kill() は hurt(genericKill, Float.MAX_VALUE) になり、onLivingHurt(81) は GENERIC_KILL をキャンセルしない。そのため同期的に die() から LivingDeathEvent が発火する。onLivingDeath(45-58) は heartStatus!=NORMAL のとき NORMAL に戻し、setConscious(true) と PoseController.clearPoseControl を行う。clearPoseControl は PoseController.java:241 で playerStates から UUID を remove する。
2) 再登録: handleCardiacArrest の return は、そのメソッドを抜けるだけである。onPlayerTick(206-216) は同じ tick 内で handleConsciousnessState(211) を続けて実行する。このとき health=0 で 233 の条件が成り立ち、newConscious=false、oldConscious=true(48 で設定済み) なので差分ありと判定される(240)。結果 setUnconsciousPose(true)(244) が呼ばれ、setPoseControl の computeIfAbsent(PoseController.java:132) が同じ UUID に UNCONSCIOUS 状態を作り直す。/kill のように tick 外で死ぬ場合も、Forge 1.20.1 では ServerGamePacketListenerImpl.tick が死亡中でも player.doTick() を呼び、Player.tick 内で PlayerTickEvent が発火する(前提)。そのため次の tick で同じことが起きる。
3) リスポーン: PlayerEvent.Clone と PlayerRespawnEvent の購読はコード全体を grep しても存在しない。新しい ServerPlayer の capability は初期値になる(PlayerMedicalData.java:186 isConscious=true)。健康なプレイヤーは HP=20・NORMAL なので newConscious=true==old となり、解除(244 の false 経路)は呼ばれない。
4) 固定化: maintainPoseControl(PoseController.java:256-266) は UUID で古い状態を引き当てる。その結果、新しいエンティティに setPose/setForcedPose(SWIMMING)(168-169) を適用し、ClientboundSetEntityDataPacket を broadcast する(201-206)。解除は onPlayerLoggedOut(ModEvents.java:249-254) か、HP<5 になってから回復した時に限られる。
5) 影響範囲: NORMAL 状態での死亡は onLivingDeath(61-64) で必ずキャンセルされ、心停止に移行する。心停止中は damageImmune で GENERIC_KILL 以外を受けない(81)。つまり MOD 下で最終的に死亡する経路はすべて「非 NORMAL 状態で kill」になり、この不具合はほぼ毎回の死亡で起きる。
補足(1.20.1 の前提): Player.forcedPose はエンティティデータとして同期されない。このため本人のクライアントでは立ち姿勢に見える可能性がある。ただしサーバー側の当たり判定(這い姿勢の高さ 0.6)と、他プレイヤーの画面(DATA_POSE の同期)では這い姿勢のままになる。実害は大きく、HIGH が妥当。

### 修正方針
根本原因は 2 つある。(a) 死亡中のエンティティにも意識判定と姿勢維持が走ること。(b) UUID に紐づいた PoseController の状態がリスポーン時に破棄されないこと。両方を直す。
1) onPlayerTick で serverPlayer.isDeadOrDying()(または !isAlive())なら handleConsciousnessState と PoseController.maintainPoseControl を実行しない。同じ tick 内の kill 後にも効くように、handleCardiacArrest の後に判定するか、handleConsciousnessState の冒頭で判定する。冒頭で一度だけ判定すると、kill した tick の残りの処理は防げない。
2) PlayerEvent.PlayerRespawnEvent(サーバー側)で PoseController.clearPoseControl(新しい ServerPlayer) を呼び、UUID の残留状態を確実に捨てる。ディメンション移動でも ServerPlayer が作り直されるかは確認が必要で、必要なら PlayerChangedDimensionEvent でも同様にする。
3) リスポーン後に医療データを引き継ぐかどうか(Clone ハンドラが無いので現状は全項目が初期化される)は、別の仕様判断として確認する。

## B08 [HIGH] 他人への包帯でサーバー側のアイテムが消費されない（無限包帯）
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundFinishHealPacket.java:46 (gameplay)

### 状況
包帯で他プレイヤーを治療し終えると、ClientHealingManager.tick:131 がクライアント側のスタックだけ shrink(1) し、ServerboundFinishHealPacket を送る。サーバー側ハンドラ(42-67)は包帯エフェクトを付けるだけで、healer のインベントリを一切減らさない。次にインベントリが同期されると（スロット操作など）、クライアントの個数も元に戻り、包帯が実質無限になる。さらにサーバーは、healer が包帯を持っているか、治療時間（BANDAGE_OTHER_USE_DURATION）が経ったかも確認しない。そのため細工したクライアントなら、包帯を持たずに即時・連続でエフェクトを付けられる。

### 検証
報告どおり実在する。ただし行番号に誤りがあり、クライアント側の shrink は ClientHealingManager.java:59 にある（報告の :131 ではない）。
(1) BandageItem.java:27-34 の interactLivingEntity は、クライアント側（isClientSide）で ClientHealingManager.startHealing を呼ぶだけ。サーバー側は何もせず SUCCESS を返し、shrink もしない。
(2) ClientHealingManager.java:53-61 では、クライアントが healingTicks を数え、治療が終わると ServerboundFinishHealPacket を送る（:58）。そのうえで mc.player.getMainHandItem().shrink(1) を実行しており（:59）、これはクライアント側のスタックだけを減らす。
(3) ServerboundFinishHealPacket.java:35-72 のサーバーハンドラは、距離が 5 ブロック未満（:45）であることしか確認しない。包帯エフェクトを付けて音を鳴らすだけで、healer のインベントリには触れない。healer が包帯を持っているか、治療開始が記録されているか、BANDAGE_OTHER_USE_DURATION が経過したかは一切確認しない。BANDAGE_OTHER_USE_DURATION を参照しているのはクライアント側の ClientHealingManager.java:25 だけ。
(4) リポジトリ全体を shrink で検索しても、他人への包帯使用でサーバー側のアイテムを減らす箇所は他にない。BandageItem.java:45 の shrink は自分に使う場合（finishUsingItem）専用で、他人への治療では通らない。
前提（Minecraft/Forge 1.20.1 の仕様）: インベントリの正本はサーバーが持つ。クライアントだけで shrink しても、サーバーの個数は変わらない。サーバー側でそのスロットが変化したとき、またはスロットクリック時に状態の不一致が見つかって全体が再同期されたときに、クライアントの表示もサーバーの個数に戻る。そのため通常プレイでも、他人への包帯は実質無限に使える。さらに改造クライアントなら、包帯を持たずに targetEntityId を指定したパケットを連打するだけで、5 ブロック以内の任意のプレイヤーに即時・連続でエフェクトを付けられる（最大レベルは Config.BANDAGE_MAX_LEVEL）。アイテムが減らない不具合と、サーバーでの検証がない不具合が重なっており、実害が大きいので HIGH が妥当。

### 修正方針
消費と検証はサーバー側で行う。(a) BandageItem.interactLivingEntity のサーバー側の分岐で、治療開始（healer の UUID、対象のエンティティ ID、開始 gameTime、使った手）をサーバー側に記録する。(b) ServerboundFinishHealPacket.handle では次を確認する: 記録した治療開始が存在し、対象のエンティティ ID が一致すること。gameTime の経過が BANDAGE_OTHER_USE_DURATION*20 以上であること（ネットワーク遅延に備えて少し余裕を持たせる）。healer のメインハンドが BandageItem であること。距離が条件内であること。すべて満たしたら、非クリエイティブのときだけ healer.getMainHandItem().shrink(1) してから、エフェクトを付けて記録を消す。(c) ClientHealingManager.java:59 のクライアント側の shrink は削除し、サーバーからの同期に任せる。中断時（stopHealing）にサーバーの記録を消す仕組みがない点は、開始時刻と対象の一致を確認すれば悪用できないため、記録をタイムアウトで破棄する程度でよい。

## B09 [HIGH] QTE 結果パケットをサーバーが検証せず、クライアントの申告だけで誰でも蘇生できる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundQTETResultPacket.java:47 (gameplay)

### 状況
handle(42-128) は、targetEntityId の ServerPlayer に対して申告された QTEResult（GREAT_SUCCESS なら +20%）でそのまま蘇生判定を行う。除細動器が充電済みか、healer が電極を持っているか、サーバーが ClientboundStartQTEPacket を送ったか、距離、ドクターカードの権限のいずれも確認していない（権限確認は ElectrodeItem:196 だけ）。改造クライアントがこのパケットを繰り返し送れば、マップ上のどの心停止者でも、除細動器もクールダウンもなしに何度でも蘇生判定を回せる。電極を持っていない場合は 53-62 の resetCharge/startCooldown も実行されない。

### 検証
反証を試みたが、指摘は成り立つ。ただし一部の行番号が間違っている。
1) ServerboundQTETResultPacket.java:44-48 では、送信者 healer と targetEntityId から healer.level().getEntity で取った ServerPlayer しか確かめていない。73-128 では、クライアントが申告した result を使って、そのまま蘇生判定（GREAT_SUCCESS で +20、VF なら確率 2 倍）と状態変更をしている。サーバー側に QTE セッションの記録はない。QTE 関連の全ファイルを grep したが、サーバー側の状態を持つクラスは見つからなかった。ClientboundStartQTEPacket（ModPackets.java:36）の送信を記録している所もない。
2) ドクターカードの権限確認は ElectrodeItem.java:29 だけにある（報告の「:196」は誤り。このファイルは 50 行しかない）。充電済みかどうかの確認も ElectrodeItem.java:41 だけで、結果パケット側にはどちらもない。結果パケットの handler は電極の NBT があるとき resetCharge/startCooldown を呼ぶが（53-61）、除細動器の CHARGED を確かめていない。NBT が無ければその処理はまるごと飛ばされ、そのまま判定に進む。距離、制限時間、healer が電極を持っているかも確かめていない。
3) ModPackets.java:50 の登録は PLAY_TO_SERVER で、consumerMainThread を使っているだけ。Forge 側に送信元を正当性検証する仕組みはない。前提として、Forge 1.20.1 の SimpleChannel は、登録済みの serverbound メッセージなら改造クライアントからいつでも受け付ける。
4) 正規クライアントは ClientQTEManager.stop（ClientQTEManager.java:56）でしか送らないが、それはクライアント側の制約にすぎない。改造クライアントなら、同じディメンションでエンティティとしてロードされている心停止者 / VF のプレイヤーに対して、GREAT_SUCCESS を毎 tick 送れる。そうすれば、医師カードも除細動器もクールダウンもなしに、何度でも蘇生判定を回してほぼ確実に蘇生できる。
重大度: 正規クライアントでは再現しないので、改造クライアントが前提になる。それでも、この MOD の中心である権限（ドクターカード）とリソース（除細動器）の制約を完全に回避できるので HIGH のままとする。
付随して見つけたもの: ClientQTEManager.java:69-74 でバーを行き過ぎて失敗した場合は、結果パケットを送らない。そのため除細動器の resetCharge/startCooldown が呼ばれず、充電が残る。

### 修正方針
報告の方針は妥当なので、それに次を加える。
(a) ElectrodeItem.interactLivingEntity で ClientboundStartQTEPacket を送るとき（ElectrodeItem.java:43）、サーバー側のセッションとして healer UUID → (targetId, defibPos, 開始 gameTime) を記録する。
(b) ServerboundQTETResultPacket.handle では最初にセッションを取り出して消費する（remove）。次を照合し、外れたら破棄する: セッションがあること、targetId が一致すること、制限時間内であること（バーの最長時間に余裕を足した値）、healer がまだ電極を主手に持ち、その NBT の DefibrillatorPos が記録と同じであること、その位置の BlockState が CHARGED であること、healer と target の距離。
(c) 権限確認（MedicalAuthorizationUtil.checkMedicalAuthorization）を結果処理側でもやり直す。
(d) クライアントが行き過ぎて失敗したときにも FAIL を送るようにして（ClientQTEManager.java:69-74）、充電のリセットとクールダウンが正しく走るようにする。期限切れのセッションはサーバー側で消すか、FAIL 扱いにする。
QTE の結果判定をクライアントに任せる設計は残るが、セッション照合によって回数・対象・リソースの制約はサーバーが担保できる。

## B10 [HIGH] 点滴台の下半分を壊すと中のパックが二重にドロップする（同じ ItemStack インスタンスを共有）
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/IVStandBlock.java:57 (content-cmd)

### 状況
前提: 1.20.1 の LevelChunk.setBlockState はフラグに関係なく onRemove を呼び、ItemEntity.tryToMerge は近くの同種アイテムを合体させる。パックが入った点滴台の下半分をサバイバルで壊すと、playerWillDestroy(:153-165) がまず上半分を AIR にする(:161)。上半分の onRemove(:57-) は bePos=下半分の BE を見つけて中身をドロップする(:64)。続いて下半分自体の onRemove でも、BE はまだ残っていてハンドラも空にしていないため、同じ ItemStack オブジェクトがもう一度ドロップされる。同じインスタンスを持つ 2 つの ItemEntity は、合体時に copyWithCount(1+1) で数が 2 になる。合体しなくてもチャンク保存時に別々にシリアライズされるので、再読み込み後は 2 個になる。結果、パックが複製される。

### 検証
報告は正しく、実際に起きる。下半分を壊したときにだけ、パックが 2 回ドロップされる。

【前提とした 1.20.1 / Forge の挙動】
- LevelChunk.setBlockState はサーバー側で、フラグに関係なく必ず旧ステートの onRemove を呼ぶ。フラグ 32（ドロップ抑制）が抑えるのは、隣接ブロックの形状更新で起きる破壊だけ。
- ServerPlayerGameMode.destroyBlock は次の順で進む。
  1. playerWillDestroy を呼ぶ。
  2. onDestroyedByPlayer から setBlock(pos, 空気, 3) を呼ぶ。
- BlockBehaviour.onRemove は、ブロックが別のブロックに置き換わるときに removeBlockEntity(pos) を呼ぶ。

【経路】
1. サバイバルで LOWER を壊す。playerWillDestroy（IVStandBlock.java:153-165）が otherPos = 上半分を求め（:157）、そこを AIR にする（:161、フラグ 35）。
2. UPPER の onRemove（:57）が呼ばれる。
   - :58 の判定は真（AIR は別ブロック）。
   - :59 で bePos = pPos.below() = LOWER の座標になる。LOWER はまだ IVStand のままなので、:60 で BE が見つかる。
   - :61-65 で各スロットの stack をそのまま new ItemEntity に渡す（:64）。1 回目のドロップ。
   - :69 の super.onRemove が removeBlockEntity を呼ぶのは上半分の座標なので、BE は何も消えない。
3. 続いて LOWER 自体が空気に置き換わり、LOWER の onRemove が呼ばれる。
   - bePos = pPos で、BE はまだ残っている。super.onRemove（:69）はループの後なので、BE が消えるのはドロップの後。
   - itemHandler は一度も空にされていない（setStackInSlot や extractItem の呼び出しが無い）。
   - そのため同じ ItemStack インスタンスが :64 でもう一度ドロップされる。2 回目のドロップ。

【UPPER を壊した場合（起きない）】
- playerWillDestroy が先に LOWER を AIR にする。LOWER の onRemove で 1 回ドロップし、:69 で BE が消える。
- その後に UPPER の onRemove が呼ばれても、below は空気なので :60 の判定が偽になる。ドロップは 1 回だけ。
- 二重ドロップは LOWER を壊したときだけ起きる。

【本当に増えるか】
- ItemEntity の setItem はスタックをコピーしないので、2 つのエンティティが同じインスタンスを共有する。
- 合体する場合: パックは stacksTo(16)（Medicalsystemcore.java:137-147）なので、tryToMerge の対象になる。ItemEntity.merge は dest.copyWithCount(1+1) で新しいスタックを作り、src.shrink(1) を呼ぶ。結果は 2 個になる。
- 拾う場合:
  - 1 体目を拾うと Inventory.add がコピーを作り、共有スタックの数を 0 にする。
  - その直後に playerTouch が itemstack.setCount(i) で数を 1 に戻す。
  - 2 体目は数 1 のまま残るので、2 個目も拾える。
- 共有インスタンスのせいで片方が自然に消える、ということは無い。
- 防いでいる箇所も見当たらない。
  - IVStandBlockEntity（blockentity/IVStandBlockEntity.java）に、破壊時の処理を独自に持つ override は無い。
  - getSlotLimit=1 は 1 スロットあたりの個数を制限するだけ。
- クリエイティブでも同じ経路を通るので、同じく二重にドロップする。

【重大度】
血液パックや薬剤パックがサバイバルで確実に増やせるので、HIGH が妥当。

### 修正方針
報告の修正方針で妥当。具体的には次のとおり。

1. onRemove で、ドロップするのは pState.getValue(HALF)==LOWER のときだけにする。UPPER のときは何もしない。LOWER が消えるときは必ず LOWER の onRemove が呼ばれるので、取りこぼしは無い。
2. 各スロットでは、stack = itemHandler.getStackInSlot(i) を stack.copy() にして ItemEntity に渡し、そのあと itemHandler.setStackInSlot(i, ItemStack.EMPTY) で空にする。
   - Containers.dropItemStack を使ってもよい。
   - 注意: setStackInSlot は onContentsChanged から level.sendBlockUpdated を呼ぶ。破壊途中でも、BE が残っている間は害は無い。
   - extractItem(i, getSlotLimit, false) で取り出してドロップする形でもよい。
3. 関連する注意点: handleItemInsertion（:117）は、取り出した currentStack インスタンスをそのままドロップしている。直後の :119 でスロットが置き換わるので、こちらは複製にはならない。

## B11 [MEDIUM] 通報データの更新(出動/処置チェック・メモ)に権限チェックがなく、医師カードなしの誰でも任意の通報を改竄できる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/RescueDataManager.java:51 (net-sides)

### 状況
救急ポータルを開くには RescueTabletItem.java:28 で医師カードが必要だが、それはクライアントに画面を開かせる時点のチェックにすぎない。改造クライアントは ServerboundUpdateRescueDataPacket を直接送れるため、カードなしのプレイヤーでも任意の rescueId の isDispatch/isTreatment を書き換えたり、メモを消去・上書きしたりでき(updateRescueDataCheckbox :50-61 / updateRescueDataMemo :67-77、該当 TODO は :51, :68)、変更は全クライアントへブロードキャストされる。ServerboundUpdateRescueDataPacket.java:94-98 も player の null 以外を確認していない。

### 検証
報告は正しい。反証を試みたが、サーバー側でこの改竄を止めている箇所は見つからなかった。
(1) ModPackets.java:57-61 で ServerboundUpdateRescueDataPacket が PLAY_TO_SERVER として登録されている。このため、画面を開いているかどうかに関係なく、接続中のどのクライアントからでも送れる（Forge SimpleChannel の仕様上、送信元の UI 状態はサーバー側で一切検証されないという前提）。
(2) ServerboundUpdateRescueDataPacket.java:87-99 のハンドラが確認しているのは sender の null（:91）だけで、そのまま RescueDataManager.updateRescueDataCheckbox / updateRescueDataMemo を呼ぶ。
(3) RescueDataManager.java:50-61 と :67-77 は TODO（:51, :68）のままで権限チェックがない。rescueId が存在すれば isDispatch/isTreatment/memo を書き換え、:58/:74 の sendToAllClients で全員にブロードキャストする。
(4) 医師カードのチェックは RescueTabletItem.java:28 の checkMedicalAuthorization だけで、これはポータル画面を開く経路にしか掛かっていない。
(5) rescueId は連番である。しかも ClientboundUpdateRescueDataPacket が全クライアントに送られ（RescueDataManager.java:43）、ServerboundRequestRescueListPacket.java:15-17 も権限チェックなしで全リストを返すので、カードのないクライアントでも有効な ID を簡単に知ることができる。memo は readUtf()（既定上限 32767 文字）で読まれるので、長い文字列で上書き・消去することもできる。
重大度は MEDIUM に下げる。攻撃には改造クライアントが必要で、影響はゲーム内の通報記録（出動・処置フラグとメモ）の改竄と荒らしにとどまる。サーバーのクラッシュや権限昇格にはつながらない。ただし医師カードによる権限モデルを素通りできるので、修正対象であることに変わりはない。
修正時の注意: ServerboundGiveUpPacket.java:163-176 / :182-195 は、救助を諦めた患者本人（通常は医師カードを持たない）を player として updateRescueDataMemo を呼んでいる。共有メソッドの先頭に権限チェックを入れるだけでは、この「死亡」「搬送」の自動追記が壊れる。

### 修正方針
権限チェックは、クライアントから届く入口であるパケットハンドラ層に置く。ServerboundUpdateRescueDataPacket.handle 内（:91 の直後）で MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(player) が false ならパケットを破棄する。RescueDataManager 側の更新メソッドは、player を受け取らない（または権限を問わない）内部用の API に整理する。こうすれば ServerboundGiveUpPacket（:173, :192）からのサーバー内部の追記経路は今までどおり動く。
報告の修正方針である「updateRescueData* の先頭でチェックし、GiveUp 用の経路は別メソッドに分ける」でも同じ結果になるが、GiveUp 経路を分けることは必須条件である。あわせて、memo の長さを readUtf(上限) で制限することを推奨する。ServerboundRequestRescueListPacket で通報一覧の閲覧に権限チェックがない点、全クライアントにブロードキャストしている点は、閲覧の秘匿が必要かどうかという仕様判断になるので、別件として扱う。

## B12 [MEDIUM] 通報一覧の要求に認可がなく、新規・更新の通報も全クライアントへ送られるため、通報者の座標が全員に漏れる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundRequestRescueListPacket.java:17 (net-sides)

### 状況
医師カードを持たない改造クライアントが ServerboundRequestRescueListPacket を送ると、全通報(通報者名・座標・ディメンション・内容・メモ)が返ってくる(:17)。それ以前の問題として、RescueDataManager.createNewRescueData(:43) と更新(:58, :74) が ModPackets.sendToAllClients で全接続クライアントに送られており、改造クライアントは受信した ClientboundUpdateRescueDataPacket を読むだけで /119 通報者の現在地をリアルタイムに把握できる(PvP サーバーなら位置特定に使える)。

### 検証
報告は大筋で正しい。ただし「リアルタイム」の部分は言い過ぎ。
(1) ServerboundRequestRescueListPacket.handle (network/ServerboundRequestRescueListPacket.java:13-18) は送信者の権限を確認せず、RescueDataManager.getRescueDataList() の全件を ClientboundSyncAllRescueDataPacket で送り返す。ModPackets.java:69-72 で PLAY_TO_SERVER として登録されているため、どのクライアントからでも送れる。正規の経路では、ポータル画面は RescueTabletItem.use (item/RescueTabletItem.java:28) で checkMedicalAuthorization(医師カード) を通ったときだけ開く (ClientboundOpenRescuePortalPacket → RescuePortalScreen:75 で一覧要求)。つまりサーバー側の制限は「画面を開けるか」だけで、パケット自体には何の制限もない。
(2) RescueDataManager.createNewRescueData (core/RescueDataManager.java:43)、updateRescueDataCheckbox (:58)、updateRescueDataMemo (:74) はいずれも ModPackets.sendToAllClients で送られる。これは ModPackets.java:115-116 の PacketDistributor.ALL.noArg() で、全接続プレイヤーに届く。RescueData.toBytes (core/RescueData.java:147-157) は通報者名・内容・BlockPos・メモ(とディメンション)を書き込んでいる。バニラ相当のクライアントは、RescuePortalScreen が開いていないとき受信データを表示も保持もしない (ClientboundUpdateRescueDataPacket.java:37,53)。それでもパケットはネットワーク上で届いているので、改造クライアントなら読める(前提: Forge SimpleChannel の PacketDistributor.ALL は、MOD チャンネルを持つ全 ServerPlayer に送る)。
(3) 設計の意図は「医療権限者だけが見る」ことである。EmergencyCallCommand.java:161-167 では、通知を hasValidDoctorCard かつ credentials 有りのプレイヤーに絞っている。したがって全員への配信は、この意図からの漏洩と言える。
訂正: 座標は EmergencyCallCommand.java:142 で通報した時点の player.blockPosition() を一度だけ取ったもので、後から更新されない。漏れるのは「通報した瞬間の通報者の位置」で、現在地を追跡し続けられるわけではない。ただ /119 を打つのは負傷・救助待ちで動けない状況が多いので、PvP サーバーでは位置特定に実際に使える。
改造クライアントが必要な情報漏洩で、改ざんや権限昇格ではないため、MEDIUM が妥当。補足として、:58/:74 の更新を引き起こす ServerboundUpdateRescueDataPacket 側にも認可がない(RescueDataManager.java:51,68 の TODO)。これは別件の書き込み認可の欠陥。

### 修正方針
修正は共有層 (RescueDataManager / ModPackets) でまとめて行う。
(a) ServerboundRequestRescueListPacket.handle では、context.getSender() が null でないことと DoctorCardItem.hasValidDoctorCard(sender) (または MedicalAuthorizationUtil.checkMedicalAuthorizationSilent) を確認する。満たさなければ何も返さない。
(b) 「医療権限者全員に送る」ヘルパーを ModPackets に 1 つ用意し、RescueDataManager の 3 か所 (:43, :58, :74) の sendToAllClients をこれに置き換える。中身は server.getPlayerList().getPlayers() を hasValidDoctorCard で絞り、PacketDistributor.PLAYER で個別に送る。サーバー参照は ServerLifecycleHooks.getCurrentServer() か呼び出し元から渡す。
カードが失効した後に画面を開いたままのクライアントには更新が届かなくなるが、仕様として妥当。
別件として、ServerboundUpdateRescueDataPacket / updateRescueDataCheckbox / updateRescueDataMemo にも同じ医師カード認可を入れるべき(TODO :51, :68)。

## B13 [MEDIUM] ギブアップ無効の設定と長押し時間をサーバーが確認せず、搬送モードではパケットを連打して全員への通知をスパムできる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundGiveUpPacket.java:59 (net-sides)

### 状況
Config.GIVE_UP_ENABLED と GIVE_UP_DURATION を確認しているのはクライアント(ClientGiveUpManager.java:21, :54)だけで、サーバーの handle は意識不明かどうか(:59)しか見ない。サーバー管理者が give-up を無効にしていても、改造クライアントは意識不明になった直後に即死(自死モード、:111 player.kill())や即搬送(:81 teleportTo)ができ、蘇生を待つというルールを回避できる。また TRANSPORT_MODE=true の場合、テレポート後も意識不明が続くので同じパケットを何度でも受理する。そのたびに notifyStatusChange が全プレイヤーへ4行のチャットとベル音を送り(:128-157)、通報メモにも「/ 搬送」が追記され続ける。

### 検証
報告どおり起きることをコードで確認した。
(1) サーバーは設定を確認していない。ServerboundGiveUpPacket.handle（ServerboundGiveUpPacket.java:51-116）の条件は `if (medicalData.isConscious()) return;`（:59）だけで、Config.GIVE_UP_ENABLED と GIVE_UP_DURATION はどこにも出てこない。grep で確かめると、この 2 つを参照しているのはクライアント側の ClientGiveUpManager.java:21/:54/:76 と ClientHudHandler.java:48 だけだった。パケットの登録は ModPackets.java:81-84 の PLAY_TO_SERVER / consumerMainThread で、他に検証する層はない。そのため改造クライアントは、give_up_enabled=false の環境でも、意識不明になった直後にパケットを送れる。自死モードなら :97-111 で CARDIAC_ARREST を設定したあと player.kill() が呼ばれ、ModEvents.java:45-48 の onLivingDeath で死亡が成立する。搬送モードなら :81 で teleportTo される。
(2) 搬送後も意識不明は続く。意識の判定は毎 tick 行われ、条件は health<5 または heartStatus!=NORMAL（ModEvents.java:233-241）。:61-93 の処理は体力も心拍状態も変えないので、次のパケットも :59 を通過する。前提として、Forge 1.20.1 の ServerPlayer.teleportTo はディメンションをまたいでも同じエンティティを使うので、capability は保持される。
(3) 受理されるたびに次のことが起きる。notifyStatusChange（:128-157）で、搬送のときは権限フィルタ（:130）を通らずに全プレイヤーへベル音と 4 行のシステムメッセージが送られる。appendTransportToRescueMemo（:191）がメモに「/ 搬送」を追記し続け、RescueDataManager.updateRescueDataMemo（RescueDataManager.java:71-74）は内容が変わるたびに ClientboundUpdateRescueDataPacket を全クライアントへブロードキャストする。前提として、Forge 1.20.1 / vanilla 1.20.1 にはカスタムペイロード向けの汎用レート制限がない。
補足（報告より悪い点）: 改造していない正規クライアントでも繰り返しは起きる。ClientGiveUpManager.java:55-58 は送信後に reset() するだけである。Shift を押し続け、かつ意識不明のままなら、次の tick から holdTicks が再び積み上がり、GIVE_UP_DURATION 秒（既定 5 秒、Config.java:211-212）ごとにパケットが再送される。改造しなくても、5 秒ごとの全体通知とメモ肥大が起きる。
重大度: 前提はチート（改造クライアント）またはプレイヤーの長押し継続である。ゲームのルール回避（蘇生待ちをスキップ）、全員へのチャット・音のスパム、メモの際限ない肥大とその全体ブロードキャストに留まり、サーバーのクラッシュや権限の奪取には至らない。よって MEDIUM が妥当と判断した。

### 修正方針
サーバー側の handle で不変条件を確認する。
1) 冒頭で `if (!Config.GIVE_UP_ENABLED) return;` を確認する。
2) 意識不明に変わった時点のゲーム時間を、サーバーの capability（PlayerMedicalData）に記録する。記録場所は ModEvents.handleConsciousnessState の oldConscious!=newConscious 分岐（ModEvents.java:240-245）。パケット受理時に `level.getGameTime() - unconsciousSince >= GIVE_UP_DURATION*20` を満たさなければ拒否する。長押しそのものはサーバーでは検証できないので、最低経過時間の保証にとどまる点は承知しておくこと。
3) 搬送は 1 回の意識不明につき 1 回だけ受理する。capability に transported フラグを持たせ、意識が回復したとき（同じ分岐で newConscious=true になった時点）と onLivingDeath でクリアする。受理済みならパケットを無視する。
4) クライアント側の ClientGiveUpManager も、送信後は意識が回復するまで再送しないようにする（sent フラグなど）。こうすれば正規クライアントによる 5 秒ごとの再送もなくなる。ただし防御の本体はサーバー側の 1)〜3) である。

## B14 [MEDIUM] 応援通報(SUPPORT)の権限判定が逆で、医師は拒否され、非医師だけが送れる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/command/EmergencyCallCommand.java:105 (net-sides)

### 状況
有効な医師カードを持つプレイヤーが /119 SUPPORT ... を実行すると、:105 の if (!hasValidDoctorCard) が偽になって else 側に入り、『応援通報は医師のみ可能です。』で拒否される。カードを持たない一般プレイヤーは if 側(権限OK)に入り、応援通報を送れてしまう。

### 検証
報告のとおり条件が逆になっている。EmergencyCallCommand.java:105 は `if (!DoctorCardItem.hasValidDoctorCard(player)) { /* 権限OK */ } else { sendFailure("応援通報は医師のみ可能です。"); return false; }` となっている。DoctorCardItem.java:126-147 の hasValidDoctorCard は、本物で本人所有の医師カードを手かインベントリに持っていれば true を返すので、医師ほど拒否され、非医師は通る。checkPermission の呼び出し元は executeTypeOnlyReport(:72) と executeReport(:95) の 2 つだけで、どちらも結果が false なら return 0、true なら processReport(:137) に進む。processReport や RescueDataManager.createNewRescueData の手前に別の権限チェックは無く、Grep でもこの判定を補う別経路は見つからなかった。非医師が出した応援通報は、そのまま全医師へ通知され(:161-219)、RescueData にも登録される(:143)。
報告の再現手順には 1 点不正確な点がある。RescueCategory.fromValue(RescueCategory.java:38-42) は enum 名ではなく表示名(getName())と照合するため、`/119 SUPPORT ...` は「無効な通報の種類です。」(:68/:91) で弾かれ、権限判定まで届かない。実際に届く入力は `/119 "応援通報" ...` で、これは補完候補(getNamesForCommand, :25-31)にも引用符付きで出てくる。:61/:83 で引用符を外してから照合されるので、この入力で checkPermission(:104) に到達し、逆転した判定がそのまま起きる。Brigadier の StringArgumentType.string() は引用符付きの文字列を受け付けるという前提に立っている。
重大度は MEDIUM のままとする。理由は 2 つで、本来の利用者である医師が応援通報機能をまったく使えないことと、権限の無い一般プレイヤーが応援通報を出せて全医師に通知を送れることである。ただし害は誤通報・スパム程度にとどまり、データ破損やクラッシュは起きない。

### 修正方針
EmergencyCallCommand.java:105 の否定を取り除き、`if (!DoctorCardItem.hasValidDoctorCard(player)) { source.sendFailure(Component.literal("§c応援通報は医師のみ可能です。")); return false; }` にする。空の「権限OK」ブロックを持つ if/else ではなく、権限が無いときに早期 return する形でよい。他の判定経路と揃えるなら MedicalAuthorizationUtil の判定(MedicalAuthorizationUtil.java:41 は hasValidDoctorCard を返す)を使ってもよい。報告にある再現コマンドは `/119 SUPPORT` ではなく `/119 "応援通報"` が正しい。

## B15 [MEDIUM] メモ長に上限がなく、巨大なメモで全通報の同期パケットが上限を超えると、全員の救急ポータル一覧が壊れたままになる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundUpdateRescueDataPacket.java:66 (net-sides)

### 状況
サーバーは readUtf()(既定上限 32767 文字)を受理し(:66)、長さの検証なしで保存・ブロードキャストする(RescueDataManager:67-77)。通報リストは static で上限もクリアもない(RescueDataManager.java:23)ため、改造クライアントが /119 で約33件の通報を作り(レート制限なし)、各メモを約32KBにすると、ClientboundSyncAllRescueDataPacket が 1MB を超える。前提: 1.20.1 の ClientboundCustomPayloadPacket はペイロードが 1048576 バイトを超えると構築時に IllegalArgumentException を投げる。この場合、一覧要求の処理(ServerboundRequestRescueListPacket:17)がサーバースレッド上で毎回例外になり、サーバーを再起動するまで誰のポータルにも一覧が表示されない。加えて、ギブアップ時の追記(ServerboundGiveUpPacket:172, :191)でメモが 32767 文字を超えると writeUtf が EncoderException を投げ、その後の notifyStatusChange と player.kill() が実行されずに中断する。正規クライアントでも、RescuePortalScreen.java:108 の MultiLineEditBox に setCharacterLimit がないため、長文を貼り付けると serverbound の 32767 バイト上限を超え、切断(またはクライアント側で EncoderException)になりうる。

### 検証
報告どおりのことが実際に起きると確認した。Forge 47.4.0 のソースとパッチも読んで裏付けた（プロジェクト側は forge_version=47.4.6）。

1) 入力側に制限がない
- ServerboundUpdateRescueDataPacket.java:66 は buf.readUtf()（既定上限 32767 文字）で受理し、:97 で RescueDataManager.updateRescueDataMemo に渡す。
- RescueDataManager.java:67-77 には長さ検証も権限チェックもない（:68 は TODO のまま）。setMemo の後に sendToAllClients でブロードキャストする。
- RESCUE_DATA_LIST（RescueDataManager.java:23）は add（:40）しかしない。grep で remove/clear が一箇所もないことを確認した。
- /119（EmergencyCallCommand.java:39-48）は requires もクールダウンもない。SELF などのカテゴリは誰でも実行できる（:130-132）。
- serverbound の上限は ServerboundCustomPayloadPacket の 32767 バイトで、Forge パッチもこの値を変えていない。したがって ASCII のメモなら 1 件あたり約 32.7KB まで載る。正規クライアントでも、RescuePortalScreen.java:108 の MultiLineEditBox に setCharacterLimit がない（既定は Integer.MAX_VALUE）。:244 の saveChanges でそのまま送信されるので、改造クライアントがなくても長文を貼れば同じ状態を作れる。

2) 一覧取得が恒久的に壊れる
- RescueData.toBytes（RescueData.java:147-158）がメモを含む全フィールドを書き出し、ClientboundSyncAllRescueDataPacket.toBytes（:28-31）が全件を連結する。約 33 件 × 約 32KB で 1048576 バイトを超える。
- Forge 1.20.1 では NetworkDirection.PLAY_TO_CLIENT が new ClientboundCustomPayloadPacket(n, d) を作る（NetworkDirection.java:28）。Forge のパッチ ClientboundCustomPayloadPacket.java.patch でも「writerIndex() > 1048576 なら IllegalArgumentException」のチェックは残っている。
- VanillaPacketSplitter はバニラパケットの分割用で、SimpleChannel のペイロードは対象外。
- そのため ServerboundRequestRescueListPacket.java:17 の送信がサーバースレッドで毎回例外になる。この例外は enqueueWork（BlockableEventLoop の submitAsync）の中で握り潰されるかログに出るだけで、サーバーは落ちない（前提: 1.20.1 の BlockableEventLoop の挙動）。
- ポータルを開くと必ず一覧を要求する（RescuePortalScreen.java:75）。通報を削除する手段がないので、サーバーを再起動するまで全員の一覧が空のままになる。

3) ギブアップ・搬送時の追記
- ServerboundGiveUpPacket.java:172 と :191 は「 / 死亡」「 / 搬送」を無制限に追記する。
- メモが 32767 文字を超えると、ClientboundUpdateRescueDataPacket の data.toBytes から RescueData.java:157 の writeUtf が EncoderException を投げる。SimpleChannel.send はその場でエンコードするので、例外は updateRescueDataMemo からそのまま上がる。その結果、:104/:105 の notifyStatusChange と player.kill()、搬送モードなら :84/:87 の notify と同期パケットが実行されない。
- しかも setMemo は送信前に終わっているので、上限を超えたメモがサーバーに残る。以後の全件同期も writeUtf で失敗し、件数が少なくても 2) と同じ一覧の恒久破壊が起きる。
- ただしこの経路に入るには、メモがすでに約 32758 文字ある状態で追記が 2 回必要で、到達条件は狭い。

報告の訂正（細部）
- 正規クライアントが上限を超えるメモを送ろうとした場合、起きるのは「切断」ではない。クライアント側で例外が出る。32767 文字を超えれば writeUtf の EncoderException、バイト数が超えれば ServerboundCustomPayloadPacket コンストラクタの IllegalArgumentException になる。
- この例外は saveChanges（:239-244）の中、つまり画面の入力ハンドラ内で起きる。1.20.1 では Screen.wrapScreenError によって、送った本人のクライアントがクラッシュすると考えられる（1.20.1 の前提）。影響は本人だけ。

重大度
- サーバーのクラッシュやデータ破損はない。影響は救急ポータルという機能が全員分止まることだが、再起動するまで直らない。
- 権限チェックがないので、一般プレイヤー 1 人が意図的に起こせる。
- 以上から MEDIUM が妥当。

### 修正方針
1) サーバー側の受信: ServerboundUpdateRescueDataPacket.java:66 を readUtf(MAX_MEMO) に変える（例: 512）。updateRescueDataMemo でも長さを検証し、上限を超えたものは拒否する。メモ更新の権限チェック（RescueDataManager.java:68 の TODO）も同時に入れる。

2) 追記時: RescueDataManager に、上限を守る追記用メソッド（例: appendMemo）を設ける。ServerboundGiveUpPacket.java:172/:191 は文字列を自分で連結せず、このメソッドを使う。上限に達したら古い部分を切り詰めて追記をやめる。こうすれば、送信例外で kill や notify が飛ばされる経路自体がなくなる。

3) クライアント: RescuePortalScreen.java:108 の memoBox に setCharacterLimit(MAX_MEMO) を設定し、サーバーと同じ定数を共有する。

4) 件数: RESCUE_DATA_LIST に件数上限を設けて古い通報を捨てるか、完了した通報を削除する手段を用意する。/119 には、プレイヤーごとのクールダウンを設ける。

5) 4) を入れても全件同期が 1MB に近づく可能性が残る。上限値は「件数 × 1 件の最大バイト数 < 1MB」になるように決める。足りなければ ClientboundSyncAllRescueDataPacket を分割送信にする。分割は同期パケット共通の層での設計変更になるので、実装前に提案して承認を得る。

## B16 [MEDIUM] 意識不明のまま再ログイン/サーバー再起動すると、サーバー側の強制姿勢が復元されず、他プレイヤーから立っているように見える
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:240 (state-persist)

### 状況
前提: バニラの Player.forcedPose は NBT に保存されない。手順: 意識不明(isConscious=false)のプレイヤーがログアウトする → onPlayerLoggedOut(:250-253)が PoseController.clearPoseControl で forcedPose を解除し、playerStates から消す(PoseController.java:233-241)。一方 isConscious=false は NBT に保存される(PlayerMedicalData.java:214, 238)。再ログイン後の handleConsciousnessState は oldConscious(false)==newConscious(false) なので setUnconsciousPose を呼ばない(:240)。PoseController に状態が無いので maintainPoseControl も何もしない。結果、サーバーは STANDING のまま当たり判定も立ち姿勢になり、他クライアントには立った患者が見える。本人のクライアントだけは ClientboundCoreStatsPacket 経由で ClientPoseController が SWIMMING を当てるため、表示が食い違う。

### 検証
コードを読んで確認した。サーバー側で姿勢の復元漏れが起きることは確かで、報告の主張は成り立つ。ただし「他クライアントから立って見える」の部分は Forge の挙動しだいで、確実とは言えない。

経路:
(1) 意識の状態は保存・復元される。PlayerMedicalData.java:214 で isConscious、:205 で heartStatus を NBT に書き、:229 と :238 で読み戻す。PlayerMedicalDataProvider は INBTSerializable を実装しているので（PlayerMedicalDataProvider.java:14,39-48）、ログアウトやサーバー再起動をまたいでも残る。体力もバニラが保存する。
(2) ログアウト時、ModEvents.java:250-253 の onPlayerLoggedOut が PoseController.clearPoseControl を呼ぶ。ここで releasePoseControl が forcedPose を null にし、playerStates.remove で状態も消える（PoseController.java:233-241）。前提として、Forge 1.20.1 の Player.forcedPose は NBT に保存されない一時的なフィールドであり、再ログインで ServerPlayer のインスタンスも作り直される。そのため clear が無くても forcedPose は null から始まる。
(3) 再ログイン後、heartStatus が NORMAL 以外か体力が 5 未満のままなら、ModEvents.java:233-237 で newConscious=false になる。oldConscious も false なので :240 の条件が成立せず、:244 の setUnconsciousPose は呼ばれない。
(4) PoseController.maintainPoseControl（:256-258）は、state が null か制御中でなければ何もせず抜ける。
(5) ログイン時・Clone 時にこの状態を戻すハンドラはコード中に存在しない。PlayerLoggedInEvent、PlayerEvent.Clone、ChangedDimension を grep して 0 件だった。

結果として、意識が戻るまで（isConscious が true に変わるまで）サーバーの forcedPose は null のまま、ポーズは STANDING のままになる。当たり判定・目の高さ・衝突は立ち姿勢のサイズで扱われ、意識不明でない普通の場合（forcedPose=SWIMMING で横たわる）と食い違う。これはコード上で確定している。

補足と確信が持てない点:
- 本人のクライアントは ClientboundCoreStatsPacket.java:92-99 で ClientPoseController が SWIMMING を当てる。ここは報告どおり。
- 他のクライアントの見え方は報告ほど単純ではない。他プレイヤー向けにも ClientboundCoreStatsPacket.java:103-108 で updateFromMedicalData が呼ばれ、ClientPoseController.setPoseControl から setPose(SWIMMING) が一度だけ実行される（ClientPoseController.java:145-150,170）。ただ、1.20.1 の Player.tick は他プレイヤーのエンティティに対しても毎 tick updatePlayerPose でポーズを計算し直す。forcedPose は通信で同期されないので、このローカル setPose はすぐ上書きされる可能性が高い。その場合、他クライアントでは再ログインの有無と関係なく「立って見える」問題が起きうる。つまり、見え方の食い違いが再ログイン特有かどうかは Forge/バニラの挙動しだいで断定できない。報告の中で確実に成り立つのはサーバー側の姿勢と当たり判定が戻らない点なので、重大度は MEDIUM のままとした。
- ディメンション移動（ネザーなど）について：1.20.1 の ServerPlayer.changeDimension は同じインスタンスを使い続けるので、forcedPose も playerStates（UUID がキー）も残る。修正方針の「ディメンション移動後に再適用」は不要。エンドからの帰還（Clone で wasDeath=false）では新しいインスタンスになるが、Clone ハンドラが無いため capability 自体が既定値（isConscious=true）に戻る。これは別の問題。

### 修正方針
いちばん簡単で直すべき場所に効く方法は、ModEvents.handleConsciousnessState（ModEvents.java:227-246）で、PoseController.setUnconsciousPose(serverPlayer, !newConscious) を「意識状態が変わった時」だけでなく毎 tick 呼ぶようにすること。setConscious は変化した時だけ呼べばよい。PoseController.setPoseControl（PoseController.java:130-157）は computeIfAbsent で状態を作り、wasControlled/targetPose に変化があった時だけ適用するので、毎 tick 呼んでも同じ結果になり負荷も小さい。これで永続化されている isConscious と、永続化されない PoseController の状態が毎 tick 揃う。

別案として PlayerLoggedInEvent で capability の isConscious を読み、setUnconsciousPose を適用し直してもよい。

報告の修正方針の訂正:
- 「isControlledBy(UNCONSCIOUS) でなければ補正」は避けるべき。担架（STRETCHER）が優先される場合は currentReason が UNCONSCIOUS にならないので、毎回補正が走ってしまう。
- ディメンション移動時の再適用は、インスタンスが同じなので不要。
- Clone 時の capability コピーが無い件は、別の不具合として扱う。

## B17 [MEDIUM] 担架エンティティの独自 passenger フィールドが降車後も残り、搬送された患者が担架設置時に引き戻されて固定される
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/entity/StretcherEntity.java:145 (state-persist)

### 状況
前提: ServerPlayer.teleportTo(別ディメンション) では同じエンティティが使われ、isRemoved=false・isAlive=true のまま。手順: 担架で運搬中の患者がギブアップ(搬送モード)を実行 → ServerboundGiveUpPacket:78 で stopRiding してから別ディメンションへテレポートする。StretcherEntity.passenger は null に戻されず、handlePassengerServerSide(:145-161)は removed/alive/shift しか見ないので残り続ける。その後、運搬者がしゃがみ右クリックで担架を設置すると(StretcherPlacementHandler.java:77、StretcherItem.java:115)、getPassenger() が搬送済みの患者を返す。患者は自分のディメンション内の同じ座標へ teleportTo され、StretcherBlockEntity の占有者にされる。StretcherBlockEntity.tick は生の座標で距離判定するため、3ブロック以内とみなして毎 tick その座標へ引き戻し続け、意識不明の患者は抜け出せない。

### 検証
コードを読んで確認した結果、報告の手順どおりに起きる。反証できる点は見つからなかった。

1. フィールドが残る
- StretcherEntity.java:36 の独自フィールド passenger は、setPassenger (:244-249) で設定される。
- null に戻るのは handlePassengerServerSide の :151（removed/!alive のとき）と :158（passenger.isShiftKeyDown のとき）だけ。
- removePassenger / positionRider のオーバーライドはなく、バニラ側の騎乗解除とは同期しない。

2. 搬送で降車させても消えない
- ServerboundGiveUpPacket.java:78 で player.stopRiding() を呼んだあと、:81 で teleportTo(targetLevel,...) している。
- 前提（Forge 1.20.1）: ServerPlayer.teleportTo(別 ServerLevel) は同じ ServerPlayer オブジェクトのまま removePlayerImmediately(CHANGED_DIMENSION) から unsetRemoved を通る。そのため isRemoved=false のままになる。
- 意識不明の患者は ModEvents.java:61-62 で死亡がキャンセルされ、setHealth(0.1f) になる。よって isAlive=true。
- この 2 つにより :150 の条件に当たらず、フィールドは搬送済みの患者を指したまま残る。
- 担架は運搬者だけを見て存続する（:62-70）。

3. 設置時に患者を引き戻す
- 運搬者が素手でしゃがみ右クリックすると StretcherPlacementHandler.java:77-86 に入る。
  - getPassenger() が非 null なので、ridingPlayer.stopRiding()（何もしない）のあと teleportTo(x,y,z) を呼ぶ。
  - 3 引数の Entity.teleportTo は、プレイヤーが今いるレベル（搬送先ディメンション）内で移動させる。患者は搬送先ディメンションの、担架ブロックと同じ座標へ飛ばされる。
  - 続けて setOccupyingPlayer で担架ブロックの占有者にされる。
- StretcherItem.java:115-124 も同じ構造（オフハンドに担架を持つ場合のみ到達）。

4. 固定され続ける
- StretcherBlockEntity.tick (:34, :68-69) と getOccupyingPlayer (:130-147) は、player.distanceToSqr を生の座標で計算するだけで、レベルが一致するかを見ない。
- 距離はほぼ 0 になり、毎 tick teleportTo で引き戻されて姿勢も固定される。
- 意識不明だと Shift でも降りられない（:46-59）。

5. 解除される条件と実害
- 抜けられるのは次のいずれか。
  - 意識が回復する。
  - 元ディメンション側のチャンクがアンロードされる。この場合、再検索 (:151-152) が serverLevel 内だけを探すため null になり、占有がクリアされる。
  - 担架ブロックが回収される。
- 永続的な破損ではない。ただし通常の運用手順（担架で運搬中に患者が搬送を選び、医療者が担架を設置する）で起き、患者が搬送先の任意座標（壁の中など）へ飛ばされて固定される。このため MEDIUM が妥当。

### 修正方針
元となる原因は、バニラの騎乗状態と二重管理している StretcherEntity.passenger フィールド。

1. フィールドを廃止する。getPassenger() は this.getFirstPassenger() instanceof Player p ? p : null を都度返す。
   - 対象: tick のクライアント側 :123、handlePassengerServerSide、remove :228。
   - setPassenger は startRiding だけにする。
   - 代案として、removePassenger(Entity) をオーバーライドし、super 呼び出しのあとにフィールドを null にする方法でもよい。
2. 設置処理の 2 か所に条件を追加する。
   - 対象: StretcherPlacementHandler.java:77、StretcherItem.java:115。
   - 条件: ridingPlayer.getVehicle() == carriedStretcher かつ ridingPlayer.level() == level。満たすときだけ占有者にする。
3. 補強として、StretcherBlockEntity の getOccupyingPlayer / tick に player.level() == this.level の判定を加える。これでディメンションが違う占有を無効にできる。
   - これは症状の隠蔽ではなく、ディメンションを無視した距離判定そのものの欠陥の修正。
   - ギブアップ搬送で担架ブロックから患者が移動するケースへの防御にもなる。

## B18 [MEDIUM] 心電図波形の履歴 Map が BE インスタンスを強参照したまま解放されず、ClientLevel ごとリークする
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/client/renderer/blockentity/HeadsideMonitorBlockEntityRenderer.java:25 (state-persist)

### 状況
render のたびに computeIfAbsent(pBlockEntity)(:63)で static HashMap(:25)へ BE を登録するが、削除用の cleanup(:358)は呼び出し元が0件(Grep で確認)。チャンクの再読込で BE が作り直されるたびにエントリが増え、ワールドから抜けた後も BE→level の参照で古い ClientLevel(チャンク・エンティティ全体)が GC されない。サーバー移動やワールド切替を繰り返す長時間セッションほどメモリが増え続ける。

### 検証
報告どおりのリークがコード上で確認できた。
- HeadsideMonitorBlockEntityRenderer.java:25 で `private static final Map<HeadsideMonitorBlockEntity, WaveformHistory> waveformHistories = new HashMap<>()` を宣言し、:63 の render 内 `computeIfAbsent(pBlockEntity, ...)` で、描画した BE をすべて強参照のキーとして登録している。
- 削除経路は :358-360 の `cleanup()` だけ。src 全体を Grep しても呼び出し元は 0 件だった。HeadsideMonitorBlockEntity.java には setRemoved / onChunkUnloaded / equals / hashCode のオーバーライドがないため、キーは同一性比較になる。チャンクを読み直して BE が作り直されるたびに、別エントリとして増えていく。
- ワールド離脱時のハンドラ ClientWorldHandler.java:19-23(LevelEvent.Unload)は ClientPoseController.onClientDisconnect() を呼ぶだけで、この Map は消していない。他に LoggingOut や ChunkEvent でこの Map を掃除する箇所もない(Grep で確認)。
- 前提(Minecraft/Forge 1.20.1 の仕様): BlockEntity は protected フィールド `level` で自分の Level を保持し、setRemoved() でもこのフィールドは null にならない。また、ディメンション移動・サーバー移動・ワールド切替のたびに、クライアントは新しい ClientLevel を作る。このため、古い BE を強参照するエントリが残っていると、BE→level を通じて古い ClientLevel(チャンクキャッシュやエンティティ)が GC されない。static フィールドなので JVM が終了するまで残る。
- 重大度: 1 エントリ自体は float 300 個程度で小さい。ただし、モニターを描画したことのある ClientLevel が 1 つ分丸ごと残るため、ディメンション移動やサーバー移動を繰り返すとメモリが増え続ける。増える量は、モニターを描画したワールドの数で頭打ちになる。クラッシュに直結するものではないため、MEDIUM が妥当と判断した。

### 修正方針
static Map は廃止し、波形履歴(WaveformHistory 相当の配列、index、点滅状態)を HeadsideMonitorBlockEntity のクライアント専用フィールドとして持たせる。NBT には保存せず、使うのはクライアント側だけにする。こうすれば履歴の寿命が BE と一致し、BE と一緒に GC されるので掃除の処理は不要になる。どうしても renderer 側に残す場合は WeakHashMap にする。値(WaveformHistory)はキーを参照していないため、弱参照キーとして正しく機能する。そのうえで ClientWorldHandler の LevelEvent.Unload(クライアント側)でも clear() すると、なお安全になる。呼ばれていない cleanup() は削除する。

## B19 [MEDIUM] オフハンドの電極や QTE のオーバーシュートでは除細動器のチャージが解除されず、クールダウンも始まらない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundQTETResultPacket.java:51 (gameplay)

### 状況
(a) DefibrillatorBlock:122 は player.setItemInHand(hand, ...) なので電極をオフハンドで受け取れ、ElectrodeItem.interactLivingEntity もオフハンドで動く。しかし結果パケットは MAIN_HAND(51) の NBT しか見ないため、resetCharge/startCooldown が呼ばれない。それでも蘇生判定は行われるので、充電済みのまま連続でショックできる。(b) ClientQTEManager.tick:200-205 でバーが 1.0 を超えると、クライアントは isActive=false にするだけでサーバーへ何も送らない。除細動器は CHARGED のまま残り、クールダウン（DEFIBRILLATOR_COOLDOWN）なしですぐ再試行できる。

### 検証
報告の両ケースとも、コードを読んで確認できた。ただし (b) の行番号は誤り（ClientQTEManager は全 90 行で、該当箇所は tick() の 62-74 行）。

(a) オフハンド: ServerboundQTETResultPacket.java:51 は healer.getItemInHand(InteractionHand.MAIN_HAND) に固定されている。DefibrillatorBlock.java:52-63 の resetCharge/startCooldown は、このメインハンドのスタックに DefibrillatorPos NBT がある場合しか呼ばれない。一方で FAIL 以外の蘇生判定（:73-128）はこの分岐の外にあり、無条件に実行される。ElectrodeItem.interactLivingEntity（ElectrodeItem.java:27-49）は pHand を問わず、ブロックステートが CHARGED なら ClientboundStartQTEPacket を送る（:41-43）。

前提（Forge 1.20.1 バニラ挙動）: エンティティへの右クリックは MAIN_HAND→OFF_HAND の順に試行される。メインハンドが空なら ItemStack.EMPTY が PASS を返し、オフハンドの interactLivingEntity が呼ばれる。

報告にある「DefibrillatorBlock:122 でオフハンドに直接受け取る」経路は実際には起きにくい。use() はクライアントでも認可通過後に常に SUCCESS を返すため（DefibrillatorBlock.java:73-74,94）、MAIN_HAND の時点で処理が止まり、オフハンドの block use には到達しない。ただし電極をメインハンドで受け取り、F キーでオフハンドへ移せば成立する。また QTE 中（最大約 2.5 秒）にホットバーのスロットを変えた場合も、MAIN_HAND が電極でなくなり同じ状態になる。

結果として、除細動器は CHARGED のまま（DefibrillatorBlockEntity.java:79-80 の chargeProgress=-1 は、resetCharge か電源断でしか解除されない）。クールダウンもなく何度でもショックできる。

(b) オーバーシュート: ClientQTEManager.java:69-74 は barPosition>1.0 になると isActive=false にするだけで、パケットを送らない。結果パケットを送るのは stop()（:56）だけで、stop() は右クリックを離したとき（ClientInputHandler.java:84-85）にしか呼ばれない。サーバー側には QTE セッションもタイムアウトもない（grep の結果、resetCharge と startCooldown の呼び出し元は ServerboundQTETResultPacket だけ）。そのため右クリックを押し続けて約 50 tick 超えると CHARGED のまま残り、すぐ再試行できる。

付随する問題（同じ根本原因）: 結果パケットはサーバー側で何も検証していない。QTE が開始されたか、除細動器が CHARGED か、対象との距離、いずれも確認しない。このため改造クライアントが SUCCESS/GREAT_SUCCESS を送るだけで、充電なしで蘇生判定を何度でも起こせる。

実害は、蘇生判定が充電とクールダウンの制約を受けずに連打できるという、ゲームバランス上のエクスプロイトにとどまる。クラッシュやデータ破損はないため MEDIUM が妥当。

### 修正方針
根本原因は、QTE セッションの状態がクライアントにしかないこと。サーバー側でセッションを持つ形に直す。

1. ElectrodeItem.interactLivingEntity で QTE を開始するとき、サーバー側に保留中 QTE を記録する。healer の UUID をキーにし、defibPos・targetId・開始 gameTime を持たせる。
2. ServerboundQTETResultPacket.handle では手のアイテムを読まず、このセッションから defibPos を取り出す。セッションが無い、targetId が一致しない、期限切れ、除細動器が CHARGED でない、のいずれかなら処理せず破棄する。有効なら結果に関係なく resetCharge と startCooldown を実行し、そのあとで蘇生判定を行い、セッションを消す。
3. 時間切れの扱い。ClientQTEManager.tick のオーバーシュート時に FAIL の結果パケットを送る。加えてサーバー側でも、開始から一定 tick（バー到達時間 + 余裕）が過ぎたセッションは、サーバー tick 処理で resetCharge と startCooldown してから破棄する。クライアントが送らない場合への備え。
4. 既存の resetChargeAndStartCooldown（DefibrillatorBlockEntity.java:49）はどこからも呼ばれていない。上の処理と統合を検討する。

## B20 [MEDIUM] 蘇生成功後も HP 0.1 と低血液量のままで、すぐ再び心停止に戻る
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundQTETResultPacket.java:94 (gameplay)

### 状況
心停止に入ると ModEvents:62 で HP が 0.1 になる。蘇生成功時(95-98)は心拍・確率・タイマー・免疫を戻すだけで、HP も血液量も触らない（103 の TODO『1分間低血液量ダメージ無効』は未実装）。血液量 <60% のまま蘇生すると、最大 BLOOD_DAMAGE_INTERVAL(既定 100tick) 以内に handleBloodDamage(ModEvents:438-443) が 99999 ダメージを与え、onLivingDeath が NORMAL なので再びキャンセルして心停止に戻す。85-60% 帯でも 4-12 ダメージで HP0.1 が尽き、同じく心停止に戻る。蘇生時に resuscitationChance=100 と cardiacArrestTimer=0 がリセットされるので、『蘇生→即心停止』を繰り返すと 20 分の強制死亡タイマーも事実上リセットされ続ける。

### 検証
中核の主張は正しい。ただし条件が二つ付く。

【確認できた事実】
- 心停止に入る経路: ModEvents.java:61-64 で LivingDeathEvent をキャンセルし、setHealth(0.1f) → CARDIAC_ARREST、damageImmune=true にしている。
- 蘇生成功時の処理: ServerboundQTETResultPacket.java:94-98 で触るのは heartStatus=NORMAL、resuscitationChance=100、cardiacArrestTimer=0、damageImmune=false の 4 つだけ。HP と血液量には触れておらず、:103 の TODO（『1分間低血液量ダメージ無効』）も未実装。ほかに HP を回復する MOD 側コードは無い（src/main で setHealth/heal を検索すると ModEvents:62 だけ）。
- 低血液量ダメージ: handleBloodDamage（ModEvents.java:424-446）は heartStatus==NORMAL のとき、tickCounter % BLOOD_DAMAGE_INTERVAL==0（既定 100、Config.java:100-101）ごとに判定する。ダメージ量は、血液 <60 で 99999（Config.java:97-98）、85-60 帯で 4/8/12（Config.java:91-96）。猶予を判定する処理は無い。bleeding damage_type には防具貫通タグが無いが、99999 は防具があっても確実に致死。
- 再心停止: 蘇生後 NORMAL のまま死亡すると、onLivingDeath（ModEvents.java:45 は NORMAL 以外のときだけ真の死亡）は 61-64 に進み、キャンセルして再び心停止に戻る。蘇生時に cardiacArrestTimer=0、chance=100 へ戻しているので、handleCardiacArrest（ModEvents.java:344-360）の強制死亡タイマーと確率減衰も最初からやり直しになる。ここも報告どおり。

【血液 <60 のまま蘇生が起こりうるか】
handleBloodLoss（ModEvents.java:386-409）では、bleedingSpeed>0 なら心停止中も血液はゆっくり減り続け、自然回復は bleedingSpeed==0 のときしか起きない。出血速度は包帯効果が無いと減らない（handleBleedingRecovery:375-376 は bandageLevel=0 なら回復量 0）。したがって、出血の処置をしていない患者を除細動で蘇生すると、最大 100tick 以内に確実に再心停止する。

【報告に付ける条件】
1. 『HP 0.1 のまま』は常に成り立つわけではない。前提: Minecraft 1.20.1 では FoodData.tick が、満腹度 18 以上かつ naturalRegeneration が有効なら Player.heal を呼ぶ。MOD 側にこれを止める処理は無い（damageImmune は hurt をキャンセルするだけ）。そのため心停止中でも空腹でなければ HP は回復する。85-60 帯（4-12 ダメージ）での再心停止は満腹度次第で起きないことがある。一方、血液 <60 の 99999 ダメージは HP に関係なく必ず起きる。
2. 停止前に出血を止めていれば、心停止中も 0.5%/秒で自然回復するため（Config.java:141-142）、蘇生時には 85% 以上に戻っていることがある。

結論として、TODO で明示された仕様（蘇生後の低血液量ダメージ猶予）が未実装である。その結果、出血が続く患者は蘇生しても 5 秒以内に再心停止し、蘇生を繰り返すと 20 分の強制死亡タイマーがリセットされ続ける。これは実在する。致命的なクラッシュではなくゲームバランス・仕様上の欠陥なので、重大度は MEDIUM が妥当。

### 修正方針
蘇生成功の処理（ServerboundQTETResultPacket.java:94-105）で、低血液量ダメージの猶予を開始する。handleBloodDamage（ModEvents.java:424）側では、猶予が有効な間は判定を飛ばす。

猶予の持ち方は次のどちらか。
- 専用の MobEffect（1200tick）。
- PlayerMedicalData に永続化（NBT 保存）する猶予 tick カウンタ。

カウンタ方式にするなら、ログアウトしても残るよう serialize/deserialize に必ず加える。

蘇生直後の HP は、固定値（例: 最大 HP の一定割合）に setHealth するよう仕様で決める。猶予時間や HP 値を新しい Config 項目にする場合は、CLAUDE.md の制約に従ってユーザー承認を得る。まずは定数か既存仕様（TODO の 1 分）に合わせるのが妥当。

あわせて、『蘇生→再心停止』の繰り返しで強制死亡タイマーがリセットされる点も、仕様として許すかどうかを決める。許さないなら、通算の心停止時間を保持するなどの対応を設計で判断する。

## B21 [MEDIUM] 意識不明のまま再ログインすると、サーバー側の強制姿勢が戻らない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:240 (gameplay)

### 状況
isConscious は NBT に保存される(PlayerMedicalData:214,238)。一方 PoseController の状態はログアウト時(ModEvents:249-254)に消え、新しい ServerPlayer の forcedPose は null。再ログイン後、handleConsciousnessState は old=false / new=false で『変化なし』と判断し、setUnconsciousPose を呼ばない。maintainPoseControl もエントリがないので何もしない。結果、心停止・意識不明の患者がサーバー上と他プレイヤーの画面では立ち姿勢・立ち当たり判定に戻る（本人のクライアントは ClientPoseController で這い姿勢に見える場合がある）。

### 検証
反証を試みたが、報告どおり起きる。
(1) 永続化: PlayerMedicalDataProvider は ICapabilityProvider と INBTSerializable を実装している (PlayerMedicalDataProvider.java:14,39-48)。Forge の CapabilityDispatcher は INBTSerializable なプロバイダを ForgeCaps として保存・復元するので、isConscious=false は再ログイン後も残る (PlayerMedicalData.java:214,238)。心停止状態 (heartStatus) と体力 <5 も保存される。
(2) 姿勢状態はメモリ上にしかない: PoseController.playerStates は static な HashMap で UUID をキーにしている (PoseController.java:125)。ログアウト時に clearPoseControl が呼ばれ、エントリを消して forcedPose=null にする (ModEvents.java:249-254, PoseController.java:233-243)。サーバーを再起動した場合も同じく空になる。
(3) 前提 (Forge 1.20.1): Player の forcedPose は Forge が追加したフィールドで NBT に保存されない。DATA_POSE の初期値は STANDING。Player.updatePlayerPose は forcedPose が null なら姿勢を通常どおり計算するので、陸上では STANDING になる。
(4) 再適用する経路がない: setUnconsciousPose/setPoseControl を呼んでいるのは ModEvents.java:244 だけ。その呼び出しは oldConscious != newConscious のときに限られる (ModEvents.java:240)。再ログイン後は old=false (NBT から復元) で new=false (heartStatus != NORMAL または体力 <5、ModEvents.java:233) なので呼ばれない。maintainPoseControl は state==null なら何もせず return する (PoseController.java:257-258)。PlayerLoggedInEvent のハンドラはコードベース内に存在しない (Grep で確認)。
(5) 結果: サーバー上の姿勢と当たり判定は STANDING のままになり、他クライアントへも STANDING が同期される。本人のクライアントでは、ログイン直後の ClientboundCoreStatsPacket で ClientMedicalData.isConscious が初期値 true から false に変わる。そのため ClientPoseController.setUnconsciousPose(true) が呼ばれて這い姿勢になり (ClientboundCoreStatsPacket.java:81-95, ClientMedicalData.java:22)、サーバーと食い違う。
(6) リスポーンとディメンション移動は対象外: リスポーンは onLivingDeath で setConscious(true) と clearPoseControl が実行され (ModEvents.java:45-51)、Clone ハンドラもないので、新しいプレイヤーは意識清明の既定値で始まる。1.20.1 のディメンション移動では ServerPlayer が同じインスタンスのまま使われるので、forcedPose と playerStates (UUID キー) は維持される。したがって実害が出るのはログイン時 (サーバー再起動後のログインを含む) に限られる。
重大度: データ破壊やクラッシュはない。ただし、意識不明・心停止の患者がサーバー上と第三者の画面で立ち姿勢と立ち当たり判定に戻り、本人の画面とも食い違う。ゲームプレイ上の不整合なので MEDIUM が妥当。

### 修正方針
PlayerEvent.PlayerLoggedInEvent (サーバー側) を追加する。そこで capability の isConscious を読み、false なら PoseController.setUnconsciousPose(serverPlayer, true) を呼んで強制姿勢と playerStates を作り直す。同時に ClientboundCoreStatsPacket を sendToAllTracking で送り、クライアントとも同期する。リスポーンとディメンション移動は既存の経路で整合が取れているので、追加しなくてよい。より根本的な代替案もある: 意識状態の「変化」だけを見るのをやめ、maintainPoseControl (または handleConsciousnessState) が毎 tick「!isConscious なのに UNCONSCIOUS の制御が無効」になっている状態を検出して適用する方式にする。こうすると、ログインやサーバー再起動などメモリ状態が消えるすべての経路をまとめて塞げる。

## B22 [MEDIUM] ギブアップ／搬送パケットがサーバー側で GIVE_UP_ENABLED と長押し時間を確認していない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundGiveUpPacket.java:275 (gameplay)

### 状況
GIVE_UP_ENABLED と GIVE_UP_DURATION はクライアント(ClientGiveUpManager:169,202)でしか見ていない。サーバー側(273-277)は !isConscious だけを確認して処理する。サーバーで give_up_enabled=false にしても、細工したクライアントなら意識不明（HP<5 だけの NORMAL 状態も含む）になった瞬間にパケットを送り、搬送モードなら搬送先へ即テレポート、自死モードなら即死亡できる。

### 検証
報告の内容は正しい。ただし行番号がずれている（ServerboundGiveUpPacket.java は 198 行しかない。HEAD 79079c3 の src は 3fcbad7 と同一）。実際の該当箇所は次のとおり。
- サーバーハンドラ ServerboundGiveUpPacket.java:57-59 が確認しているのは `if (medicalData.isConscious()) return;` だけで、Config.GIVE_UP_ENABLED と GIVE_UP_DURATION はどこでも参照していない。その直後に、搬送モード（:61-93）では stopRiding・teleportTo・メモ追記・全員への通知、自死モード（:94-112）では CARDIAC_ARREST 設定・kill を実行する。
- GIVE_UP_ENABLED / GIVE_UP_DURATION を参照しているのはクライアント側の ClientGiveUpManager.java:21,54,76 と ClientHudHandler.java:48 だけ（Grep で確認）。
- 意識の判定は ModEvents.java:232 `serverPlayer.getHealth() < 5 || newStatus != HeartStatus.NORMAL` で行われる。そのため HP<5 で心拍が NORMAL の状態でも isConscious=false となり、ハンドラを通過する。
- ModPackets.java:81-84 は PLAY_TO_SERVER で登録しているだけで、追加の検証は無い。
前提（Forge 1.20.1）: SimpleChannel は細工したクライアントが送ったパケットもそのまま consumerMainThread に渡し、サーバー側の検証は MOD 自身が行う必要がある。
実害:
- サーバーで give_up_enabled=false にしても、細工したクライアントなら意識不明になった瞬間に搬送先へテレポートできる（戦闘からの離脱、救助ロールプレイの回避）。自死モードなら即死亡できる。
- 長押し時間の確認も、1 回の意識不明につき 1 回という制限も無い。このため搬送モードでは、意識不明中に毎 tick パケットを送ると次のことが繰り返される。
  - notifyStatusChange（:123-158）が全プレイヤーにベル音と 4 行のチャットを送り続ける（スパム）。
  - appendTransportToRescueMemo（:182-196）が RescueData のメモに「 / 搬送」を際限なく追記し続ける。
影響は改造クライアントが前提で、主に送信者自身とサーバーのルール回避にとどまる。一方で全体への通知スパムとメモの肥大化も起こせるため、MEDIUM が妥当と判断した。

### 修正方針
ServerboundGiveUpPacket.handle のサーバー側冒頭で、次の順に確認する。
1. `Config.GIVE_UP_ENABLED` が false なら return する。
2. 意識不明になった時刻をサーバーで記録する。ModEvents.handleConsciousnessState（ModEvents.java:239-244）で conscious が true→false に変わったときに、medicalData に gameTime を保存する。ハンドラでは `now - unconsciousSince >= Config.GIVE_UP_DURATION * 20` を確認する。
3. 報告の修正方針に加えて、1 回の意識不明につき 1 回しか受け付けないフラグも必要。意識が戻ったらリセットする。これが無いと、規定時間が過ぎた後に連打されたとき、搬送通知のスパムやメモへの重複追記を防げない。
なお、新しいフィールドを capability に追加するなら、NBT に保存するかどうかも決めておく必要がある。

## B23 [MEDIUM] /119 の応援通報(SUPPORT)の権限判定が逆になっている
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/command/EmergencyCallCommand.java:105 (content-cmd)

### 状況
医師カードを持たない一般プレイヤーが『/119 "応援通報"』を実行すると、!hasValidDoctorCard が true なので許可される。本来送れるはずの医師が実行すると、else 側で『応援通報は医師のみ可能です』と拒否される。

### 検証
報告どおりのバグで、実際に起きる。EmergencyCallCommand.java:105 の `if (!DoctorCardItem.hasValidDoctorCard(player)) { // 権限OK } else { sendFailure("応援通報は医師のみ可能です"); return false; }` は条件が逆になっている。hasValidDoctorCard（DoctorCardItem.java:126-）は、本物で本人所有の医師カードをメインハンド・オフハンド・インベントリのどこかに持っていれば true を返す。そのため医師カードを持たない人は許可され、持っている医師だけが拒否される。

実際に到達するかも確認した。`/119 "応援通報"` も `/119 "応援通報" 内容` も、引数を StringArgumentType.string() で受け取り、引用符を除去して toUpperCase した上で RescueCategory.fromValue に渡す（:61-65 / :83-88）。日本語には大文字小文字がないので toUpperCase しても文字列は変わらない。fromValue は equalsIgnoreCase で getName() と照合するため（RescueCategory.java:38-41）、SUPPORT に一致する。その後 checkPermission（:72, :95）を必ず通る。この判定を別の場所で防いでいる箇所はない（SUPPORT を参照しているのは :104 と enum 定義だけ）。前提として、Brigadier の quoted string は引用符を外した値を返すので replace は実質何もしない。それでも結果は変わらない。

重大度を MEDIUM に下げた理由：一般プレイヤーが得るのは「応援通報」という種別ラベルで通報を送れることだけである。SELF/OTHER は元々誰でも送れ、processReport の通知処理は種別に関係なく同じなので、新たな権限や資源を得るわけではない。一方で、本来の利用者である医師は応援通報を一切送れない。権限判定の反転で機能が丸ごと壊れているが、データ破壊やセキュリティ上の昇格は起きない。

### 修正方針
EmergencyCallCommand.java:105 の否定を外し、`if (DoctorCardItem.hasValidDoctorCard(player)) { /* OK */ } else { 拒否 }` にする。分岐を明確にしたいなら、`if (!DoctorCardItem.hasValidDoctorCard(player)) { source.sendFailure(...); return false; }` のように拒否側だけを書く形でもよい。processReport の通知先の判定（:166 は hasValid && credentials != null）と合わせるなら、MedicalAuthorizationUtil.getMedicalCredentials(player) != null も条件に加えるかを設計者に確認する。ただしこれは仕様の判断なので、ここでは否定を外す修正だけで足りる。

## B24 [MEDIUM] 救助データの閲覧・更新パケットにサーバー側の権限チェックがない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundUpdateRescueDataPacket.java:87 (content-cmd)

### 状況
医師カードの確認は RescueTabletItem.use でポータルを開く時だけ（クライアントに開く指示を出す前）にしかない。改造クライアントや別 MOD から ServerboundRequestRescueListPacket(ServerboundRequestRescueListPacket.java:13-17) を送れば、カードのない者でも全通報の座標・通報者・メモを取得できる。ServerboundUpdateRescueDataPacket を送れば、任意の通報の出動/処置チェックやメモを書き換えられ、それが全クライアントへブロードキャストされる（RescueDataManager.java:51,68 に TODO のまま残っている）。memo は buf.readUtf()（上限 32767 文字）で読まれ、そのまま全員へ送り返される。

### 検証
書き込み側は報告どおり起きる。読み取り側は「起きる」が、報告の修正方針では塞がらない。

【書き込み（確認済み）】
- ServerboundUpdateRescueDataPacket.handle（network/ServerboundUpdateRescueDataPacket.java:87-101）は sender が null かどうかしか見ていない（:91）。そのまま RescueDataManager.updateRescueDataCheckbox / updateRescueDataMemo を呼ぶ（:95, :97）。
- 呼び出し先の core/RescueDataManager.java:51 と :68 には「TODO: 権限チェック」と書かれているだけで、実際のチェックはない。値が変わっていれば sendToAllClients で全員に送り直す（:58, :74）。
- 医師カードのチェックは item/RescueTabletItem.java:28 の 1 か所だけ。これはクライアントに「ポータル画面を開け」と指示する前に行うもので、パケットの受け口には届かない。
- このパケットは ModPackets.java の PLAY_TO_SERVER として登録されているので、改造クライアントなら画面を開かずに直接送れる。カードもタブレットも持っていない人が、任意の rescueId の出動/処置フラグとメモを書き換えられる。
- memo は buf.readUtf() で読む（:66）。1.20.1 の上限は 32767 文字。ただしクライアントからサーバーへ送るカスタムペイロードの上限は約 32KB なので（この点は Forge/MC の仕様が前提）、実際の上限はおよそ 32KB。これがそのまま全員にブロードキャストされるので、スパムや帯域の水増しに使える。

【読み取り（報告より状況が悪い）】
- ServerboundRequestRescueListPacket.handle（:13-17、ファイルとしては表示上 114-121 行）は認可なしで getRescueDataList() を送り返す。ここまでは報告どおり。
- ただし、それ以前に次の 2 か所で、カードのない人も含めて全クライアントにデータが送られている。
  - RescueDataManager.createNewRescueData:43 が、新しい通報（通報者名・内容・座標・ディメンション・メモ。core/RescueData.java:147-157）を sendToAllClients で全員に送る。
  - 更新があるたびにも全員へ送る（:58, :74）。
- 正規のクライアントは RescuePortalScreen を開いていない時はこれを捨てる（ClientboundUpdateRescueDataPacket.java:37）。しかし改造クライアントなら受け取れる。
- つまり、リスト要求パケットだけに認可を足しても、情報の漏れは止まらない。

【重大度】
- 悪用には改造クライアントが必要。影響範囲はゲーム内の通報データの改ざん、座標などの漏えい、スパムにとどまる。サーバーのクラッシュや任意コード実行、他のプレイヤーへの直接の害はない。
- 以上から HIGH ではなく MEDIUM とした。

### 修正方針
1. 書き込み: RescueDataManager.updateRescueDataCheckbox と updateRescueDataMemo の冒頭で `if (!MedicalAuthorizationUtil.checkMedicalAuthorizationSilent(player)) return;` を必須にする。パケット側ではなく、TODO が置かれている Manager 側の共有の入口でチェックする。必要なら、手にタブレットを持っていることも条件に加える。
2. memo の長さ: ServerboundUpdateRescueDataPacket のデコードを `buf.readUtf(N)` にして、妥当な上限（例: 256〜1024。仕様に合わせて決める）を設ける。クライアント側の memoBox の最大長も同じ値にそろえる。
3. 読み取り: ServerboundRequestRescueListPacket.handle で `checkMedicalAuthorizationSilent(sender)` を必須にする。これに加えて、createNewRescueData:43 と更新時（:58, :74）の sendToAllClients をやめ、有効な医師カードを持つプレイヤーだけに送る（例: サーバーのプレイヤー一覧を絞り込んで sendToPlayer する）。これをしないと読み取りの漏れは塞がらない。

## B25 [MEDIUM] /doctorcard revoke がインベントリ内のカードしか消さず、失効として機能しない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/command/DoctorCardCommand.java:125 (content-cmd)

### 状況
カードが正規かどうかはアイテム NBT の isGenuine と ownerUUID だけで決まる（DoctorCardItem.java:87-121）。失効リストはない。対象者がカードを医療バッグ（MedicalBagSlot は自 MOD のアイテムを全部受け入れるので doctor_card も入る）・チェスト・エンダーチェスト・地面に退避した状態で revoke されると、removed=false で『所持していません』と表示されて終わる。後で取り出せば権限はそのまま戻る。

### 検証
報告は事実。反証を試みたが、別の場所で防いでいる箇所は見つからなかった。
1) 失効はカードの物理削除だけで行っている。DoctorCardCommand.java:125-133 は targetPlayer.getInventory() の 0..getContainerSize()-1 を走査し、isDoctorCard && isGenuine && isOwnedBy を満たすスタックを removeItem で消すだけ。医療バッグの ItemStackHandler、チェスト、エンダーチェスト、地面の ItemEntity は走査していない。1 枚も見つからなければ 141 行目の sendFailure「有効な医師カードを所持していません」で終わり、サーバー側には何も記録されない。
2) 正規かどうかは NBT だけで決まる。DoctorCardItem.java:87-93 (isGenuine は isGenuine タグの boolean)、105-121 (isOwnedBy は ownerUUID と player.getUUID() の一致)、126-147 (hasValidDoctorCard は手持ちとインベントリを NBT で照合するだけ)。src/main/java 全体を revok / SavedData / blacklist で grep したが、失効状態を持つ仕組みはなかった。権限判定の利用元 (MedicalAuthorizationUtil.java:21,41、EmergencyCallCommand.java:105、ServerboundGiveUpPacket.java:131) はすべて hasValidDoctorCard を通るので、カードを取り出せば権限がそのまま戻る。
3) バッグにも入る。MedicalBagMenu.java:137-142 の MedicalBagSlot.mayPlace は、名前空間が MODID で、かつ MEDICAL_BAG 以外なら受け入れる。doctor_card は Medicalsystemcore.DOCTOR_CARD (同じ MOD の登録アイテム) なので入る。
前提 (Forge 1.20.1): Inventory.getContainerSize() はメイン・防具・オフハンドの 41 スロットで、Item の Capability (バッグの中身) やエンダーチェストは含まない。
補足: revoke の引数は EntityArgument.player() (DoctorCardCommand.java:44) なので、オフラインのプレイヤーには実行できない。オフライン中の医師は失効させる手段がない。この点も同じ原因 (サーバー側に失効状態がない) から来ている。
重大度: 被害は「OP が行った権限剥奪をすり抜けられる」ことにとどまる。所有者 UUID の照合があるので他人には使えず、データ破損やクラッシュも起きない。よって MEDIUM が妥当。

### 修正方針
失効状態をサーバー側に持たせ、hasValidDoctorCard (DoctorCardItem.java:126) の 1 か所で照合する。権限判定はすべてここを通るので、ここを直せば全経路に効く。
キーはライセンス番号単独だと弱い。手動 grant では番号を自由に指定でき、自動発行の番号も「日付 + UUID 末尾 4 桁」なので重複しうる。そこで次の方式を推奨する。
- overworld の DimensionDataStorage に SavedData を置き、UUID ごとに失効時刻 (revokedAt) を記録する。
- カード NBT にある既存の issueDate を使い、ownerUUID の revokedAt 以前に発行されたカードは無効とみなす。
- revoke はこの記録を書き込み、インベントリ内のカード削除は見た目の後片付けとして残す。
- 再 grant で発行されるカードは issueDate が revokedAt より後になるので、そのまま有効になる。
- 失効対象をオンラインのプレイヤーに限定しないよう、引数を GameProfileArgument などに変えるとよい。
バッグの受け入れ対象から doctor_card を外す案は不要。サーバー側で照合すれば、チェストやエンダーチェストへの退避と同様に無害になる。外すかどうかは仕様上の好みで決めればよい。
なお、新しい SavedData の追加は永続化層の設計変更にあたる。CLAUDE.md の規定に従い、実装前にユーザーの承認を取ること。

## B26 [MEDIUM] 点滴台の loot table に half=lower 条件がなく updateShape もないため、爆発で 2 台になる
- src/main/resources/data/medicalsystemcore/loot_tables/blocks/iv_stand.json:1 (content-cmd)

### 状況
前提: 1.20.1 の爆発は対象ブロックごとに loot を落とす。点滴台が爆発に巻き込まれると、上下両方の半分が iv_stand アイテムの loot を持つので、survives_explosion 判定を通れば最大 2 台落ちる。さらに onRemove が上下それぞれで BE の中身をドロップするため、中身も二重になり得る。IVStandBlock は updateShape/canSurvive を override していないので、/setblock などで片方だけ消えると孤立した半分が残る。孤立した上半分を壊すと 1 台ドロップする。

### 検証
報告は成立する。根拠は以下のとおり。
(1) src/main/resources/data/medicalsystemcore/loot_tables/blocks/iv_stand.json:1-19 には survives_explosion 条件しかなく、block_state_property(half=lower) がない。上下どちらの半分も同じ loot table を参照するので、UPPER を loot 経由で壊しても iv_stand が出る。
(2) src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/IVStandBlock.java は updateShape も canSurvive も override していない（ファイル全体を確認済み）。上下の連動消去は playerWillDestroy（:153-166、プレイヤーが破壊したときだけ呼ばれる）にしかない。
(3) 前提とする Forge 1.20.1 の仕様: Explosion.finalizeExplosion は toBlow の各座標について、その時点で空気でなければ getDrops を回収してから onBlockExploded(setBlock AIR) を呼ぶ。片方を消しても updateShape がないためもう片方は残り、両方が toBlow に入っていれば両方から loot が落ちる。DESTROY_WITH_DECAY（TNT など）のときは各半分が survives_explosion（1/半径）で判定されるので、TNT なら約 1/16 の確率で 2 台になる。
(4) src/main/resources/data に recipes が存在しない。iv_stand はクラフトできない（コマンドかクリエイティブで入手する）アイテムなので、爆発で増えるのは実害がある。他所で爆発を処理しているコードもない（ExplosionEvent を grep して 0 件）。
(5) 孤立半分について: updateShape がないので /setblock で片方だけ消すと残る。さらに上半分には BE がない（newBlockEntity :186）ため、ピストンで上半分だけ押し出せる可能性もある。孤立した上半分を壊すと loot で 1 台落ちる。

担当外だが関連する重大事項として、中身の二重ドロップは爆発に限らず通常の破壊でも起きる。onRemove(:57-70) は HALF に関係なく bePos=下半分 の BE の中身を ItemEntity にして落とし、スロットをクリアしない。プレイヤーが下半分を壊すと次の順で処理される。
- playerWillDestroy(:161) が上半分を setBlock(AIR,35) する。サーバーでは LevelChunk.setBlockState が上半分の onRemove を呼ぶ。この時点で下の BE はまだ残っているので、中身が 1 回目のドロップとして出る。
- 続いて下半分が除去され、その onRemove で同じ中身が 2 回目のドロップとして出る。
ItemEntity(Level,x,y,z,stack) はスタックを copy しないので、2 つの ItemEntity が同じ ItemStack を共有する。片方を拾えばもう片方は空になって消える。しかし ItemEntity どうしの merge（blood/drug pack は stacksTo(16) で、:137-141 から merge 可能）や、チャンクを保存して再ロードしたときにはスタックが別物になり、実際に増殖する。上半分を先に壊した場合は lower の onRemove が先に走り、BE が除去されるため二重にならない。

### 修正方針
報告の方針はおおむね妥当で、次を追加・訂正する。
(a) loot table の pool に minecraft:block_state_property（block=medicalsystemcore:iv_stand, properties {half:"lower"}）の条件を追加する。survives_explosion は残す。
(b) IVStandBlock に DoorBlock と同じ形の updateShape を実装する。向きが UP/DOWN で、片割れが this でないか HALF が一致しないときは AIR を返す。LOWER のときは canSurvive も合わせて実装する。ただし注意点がある。爆発で上半分が先に処理されると、下半分は shape update 経由の destroyBlock(drop=true) で壊れ、explosion パラメータなしで 100% ドロップする。これはバニラのドアと同じ挙動なので許容範囲だが、仕様として把握しておく。
(c) 中身の二重ドロップは onRemove 自体で直す。HALF==LOWER のときだけ自位置の BE の中身を落とすように限定し、その際は Containers.dropContents 相当で copy を落とすかスロットを空にする。上半分の onRemove では中身を扱わない。(b) で片割れが連動して消えるようにすると、(c) をしないと爆発でも通常破壊でも二重ドロップの経路が増えるので、(b) と (c) は同時に入れる必要がある。

## B27 [MEDIUM] 同じ Config.SPEC を SERVER と COMMON の両方で登録している
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/Medicalsystemcore.java:226 (content-cmd)

### 状況
前提: Forge 1.20.1 の ForgeConfigSpec は最後に acceptConfig したファイルの値を参照する。同じ SPEC を medicalsystemcore-server.toml(SERVER) と medicalsystemcore-common.toml(COMMON) の両方に登録しているので、ワールドを読み込むと SERVER 側の値で上書きされ、common.toml を編集しても反映されない。サーバー設定が unload された時（シングルでワールドを抜けた時など）に spec の config が null になり、値の再読込やイベントで例外になる可能性がある（要確認）。次に実装予定の『設定値を動的に変更するコマンド/パネル』で、どちらのファイルに保存・同期するかが曖昧になる。

### 検証
コードで確認した事実: Medicalsystemcore.java:226-227 で同一の Config.SPEC を SERVER（medicalsystemcore-server.toml）と COMMON（既定名 medicalsystemcore-common.toml）に登録している。Config.java:293-297 の onLoad は ModConfigEvent 全般（Loading/Reloading/Unloading）を spec の一致だけで受け、Config.java:300-356 で全 *_SPEC.get() を static フィールドへ写している。Config.java:358-366 のコメントも「同一SPECが両方に登録されている」ことを開発者が認識している証拠になる。

Forge 1.20.1 の挙動を前提とした推論（scenario の前提）: ModConfig.setConfigData は spec.acceptConfig(data) を呼び、ForgeConfigSpec は最後に accept された childConfig 1 つだけを参照する。COMMON は mod ロード時に読み込まれ、SERVER はサーバー起動時（ServerAboutToStart、シングルのワールド読込を含む）に読み込まれる。リモートクライアントにはログイン時に同期される。したがって実際のゲームプレイ中は常に SERVER（world/serverconfig/medicalsystemcore-server.toml か defaultconfigs）の値が使われ、config/medicalsystemcore-common.toml を編集しても効かない。ConfigWatcher による common.toml のホットリロードは acceptConfig を呼び直さず、afterReload と Reloading イベントを出すだけである。そのため onLoad が再読込しても読むのはサーバー側データになる。つまり common.toml は同じキーを持ちながら効果のない、誤解を招くファイルになる。これは報告どおり実在する。

報告の誇張・不確かな部分: (1) 「unload 時に例外」について。1.20.1 の ConfigTracker.closeConfig は Unloading イベントを setConfigData(null) より前に発火すると理解しているので、その時点の get() では例外は出ない。setConfigData(null) の後に common.toml の Reloading が来ると ConfigValue.get() が走る。dev 環境ではこれが checkState による IllegalStateException になり得るが、production では既定値を返すだけでクラッシュしない。dev 限定であり、Forge ソースを手元で確認していないため確証はない。(2) クラッシュや不正値は通常プレイでは発生しない。実害は「COMMON 側の設定変更が黙って無視される」ことと、次に作る動的変更機能で保存先が曖昧になることに限られる。そのため MEDIUM（設定機能の誤動作で、データ破壊やクラッシュはない）とした。

### 修正方針
Medicalsystemcore.java:227 の COMMON 登録を削除し、ゲームプレイの数値は SERVER（:226）だけに一本化する。Config.java:358-366 の TRANSPORT_* だけを SERVER 時に限る分岐とそのコメントは不要になるので、他の値と同じ扱いに戻す。動的変更のコマンドやパネルは、サーバー側で ForgeConfigSpec.ConfigValue.set() を呼んでから SPEC.save()（または ModConfig.save()）で serverconfig に保存する。static キャッシュの更新とクライアントへの同期（必要な値だけ独自パケットで送るか、再同期する）も明示的に行う設計にする。SERVER が unload された後（タイトル画面など）に Config の static 値や get() を参照する経路がないことも併せて確認する。

## B28 [MEDIUM] /119 に回数制限がなく、通報データが際限なく増えて全員に配信される
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/command/EmergencyCallCommand.java:39 (content-cmd)

### 状況
/119 は権限なしで誰でも実行できる（requires がない）。実行のたびに RescueDataManager の static リスト(RescueDataManager.java:23)へ追加され(:40)、全クライアントへ送られる。医師全員にはタイトル・効果音・チャット 6 行が届く。マクロで連打すれば医師側の画面とチャットが埋まる。リストは削除もワールドごとの初期化もされないので、メモリが増え続け、ClientboundSyncAllRescueDataPacket も肥大化する。static なので、シングルで別ワールドに入っても前のワールドの通報が残る。

### 検証
コードを読んで報告どおりであることを確認した。
(1) 権限: EmergencyCallCommand.java:39-48 の Commands.literal("119") に .requires がない。登録は CommandEventHandler.java:15 で、ここでも制限はかけていない。引数なしの /119 (:40, :51-55) は checkPermission を通らずに SELF 種別で processReport を呼ぶので、一般プレイヤーでも実行できる。
(2) 追加と配信: processReport の :143 が RescueDataManager.createNewRescueData を呼ぶ。そこで RescueDataManager.java:40 が static な RESCUE_DATA_LIST(:23) に追加し、:43 が sendToAllClients で全クライアントへ送る。続いて EmergencyCallCommand.java:161-206 が、医師カードを持つ全員に効果音・サブタイトル・チャットを送る。チャットは区切り線から区切り線までの 6 行で、内容がある場合は 7 行になる。最寄りの医師には :222-232 でさらに 1 行と効果音が届く。
(3) 寿命管理なし: RESCUE_DATA_LIST を参照しているのは RescueDataManager.java の中だけ（:23,40,86,96）で、remove/clear はどこにもない。ServerStopping や ServerStarting でリセットする処理もない。ID カウンタも static で、RescueData.java:13 の nextId と RescueDataManager.java:25 が使われずに残っている。シングルプレイでは統合サーバーが同じ JVM 内で動くため、ワールドを入り直しても前のワールドの通報とカウンタが残る。この部分は Minecraft の挙動を前提にしている。
(4) 全件同期: ServerboundRequestRescueListPacket.java:17 が毎回リスト全体を ClientboundSyncAllRescueDataPacket で送る。:28-31 には件数の上限がない。
重大度を下げる要因: Minecraft 1.20.1 では、ServerGamePacketListenerImpl.handleChatCommand が detectRateSpam を呼ぶ（バニラの仕様が前提）。1 件ごとに 20 加算、1 tick ごとに 1 減算され、200 を超えるとキックされる。そのため OP でないプレイヤーがマクロで連打しても、続けられるのはおよそ 1 回/秒、一度に打てるのは 10 回程度までになる。「瞬時に際限なく」とはいかない。それでも 1 時間で約 3600 件はたまり、医師の画面とチャットは埋まる。
さらに Forge 1.20.1 の SimpleChannel は ClientboundCustomPayloadPacket を使い、ペイロードは 1MiB までという制限がある（これも前提）。数千件たまると全件同期パケットがこの上限を超え、Rescue Portal を開いたプレイヤーへの送信が例外になるおそれがある。連打しなくても、長く動かしているサーバーでは件数が単調に増え続ける。
メモリへの影響は 1 件あたり数百バイトと小さい。実害の中心は、誰でも医師への嫌がらせ通知を出せることと、同期パケットが肥大化することなので、MEDIUM が妥当と判断した。

### 修正方針
報告の方針は基本的に妥当。ただし次の点を補う。
(a) 通報データの寿命を管理する。ServerStoppingEvent（または ServerStartingEvent）で RESCUE_DATA_LIST をクリアし、ID カウンタもリセットする。ワールドを再起動しても通報を残したいなら、SavedData に移して overworld の DataStorage に置く。その場合は、使われていない RescueData.nextId(:13) と RescueDataManager.nextId(:25) を一本化する。
(b) 件数の上限を設け、古いものや完了済み（治療済みフラグなど）から削除する。削除したことをクライアントへ伝える仕組みも必要になる（今の ClientboundUpdateRescueDataPacket には削除の概念がない）。
(c) /119 にプレイヤー（UUID）ごとのクールダウンを入れる。秒数を設定値として新たに追加する場合は、ユーザーの承認が必要。今回ユーザーは設定値を動的に変えられる管理パネルを依頼しているので、その設定項目に含めるかを一緒に確認するとよい。
(d) 必要なら、全件同期パケットにも件数や容量の上限を設ける。

## B29 [MEDIUM] 点滴台からパックを素手で取り出す時、インベントリが満杯だとパックが消える
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/IVStandBlock.java:126 (content-cmd)

### 状況
インベントリが満杯の状態で点滴台を素手で右クリックすると、extractItem(i,1,false) で台からは取り除かれる。しかし Inventory.add が false を返しても戻り値を見ていないので、パックは消える。

### 検証
報告どおり実際に起きる。IVStandBlock.java:87-89 で、素手かつ非スニークで右クリックすると、サーバー側（:74 でクライアントは return 済み）で handleItemRemoval が呼ばれる。:126 の `player.getInventory().add(blockEntity.itemHandler.extractItem(i, 1, false))` は、simulate=false の extractItem で先にスロットから 1 個取り除き、確定させている。IVStandBlockEntity.java:30-50 の ItemStackHandler は onContentsChanged / isItemValid / getSlotLimit(=1) を上書きしているだけで、extractItem は上書きしていない。このため Forge 標準どおりスロットは空になり、setChanged と sendBlockUpdated も走る。前提は Minecraft 1.20.1 の Inventory.add(ItemStack) の仕様である。サバイバルでは、空きスロットも同じアイテムの重ね先も無いとスタックをそのまま残して false を返す。戻り値は無視されているので、取り出した ItemStack はどこにも保持されず消える。この経路に、ドロップや台へ戻す処理などの代替は見当たらない（:123-130 全体）。同じアイテムの重ね先が無く空きスロットも無い、つまり満杯なら、通常プレイで十分起こる。クリエイティブモードでは add が true を返して count=0 にするので、消えるのはバニラと同じ挙動で問題にならない。実害は医療消耗品（血液・薬剤パック）1 個の喪失で、権限を持つ操作者に限られる。サーバーのクラッシュやデータ破損は無いので MEDIUM が妥当。

### 修正方針
修正方針は報告のとおりで妥当。:126 を `ItemStack extracted = blockEntity.itemHandler.extractItem(i, 1, false); if (!player.getInventory().add(extracted)) player.drop(extracted, false);` に変える。Player.drop(ItemStack, boolean) は 1.20.1 に存在する。代替として、Forge の `ItemHandlerHelper.giveItemToPlayer(player, extracted)` を使ってもよい（入りきらない分は自動でドロップされる）。別の方法として、事前に `extractItem(i,1,true)` で中身を確かめ、入らない場合は取り出さないこともできる。ただしその場合は「満杯では取り出せない」とプレイヤーに伝えるメッセージが要る。

## B30 [LOW] 意識不明中の移動禁止がクライアント側でしか実施されておらず、改造クライアントなら意識不明のまま移動できる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/client/ClientInputHandler.java:63 (net-sides)

### 状況
意識不明中の移動入力のゼロ化(:63-76)、マウスクリックのキャンセル(:97-98)、速度の停止(:54)はすべてクライアント側の処理。サーバー側は PoseController が強制ポーズを設定するだけで(PoseController.java:168-169)、grep した範囲では MOVEMENT_SPEED 属性の変更や位置の巻き戻しによる移動抑止は見当たらない(ModEvents の onPlayerInteract はインタラクトのキャンセルのみ)。入力イベントを無視する改造クライアントは、心停止・意識不明の状態で(swimming ポーズのまま)移動して安全地帯へ逃げられる。前提: バニラ 1.20.1 のサーバーは forced pose 中でもクライアントの移動パケットを受理する。

### 検証
報告どおり、意識不明中の移動抑止はクライアント側にしかない。
- 入力のゼロ化 ClientInputHandler.java:63-76、速度の停止 :52-54、クリックのキャンセル :97-98 は、すべて Dist.CLIENT 限定の購読クラスにある(:18)。
- サーバー側の tick は ModEvents.onPlayerTick(ModEvents.java:194-224)。意識不明に関わる処理は次の 3 つだけで、いずれも水平移動を止めない。
  - handleConsciousnessState(:227-246)は PoseController.setUnconsciousPose を呼ぶだけ。
  - handleUnconsciousFloating(:547-555)は液体中に Y 速度を設定するだけ。
  - PoseController.maintainPoseControl(PoseController.java:256-267)はポーズを再適用するだけ。applyPoseControl も setPose/setForcedPose と同期パケットの送信だけ(:168-173)。
- src/main 全体で MOVEMENT_SPEED、AttributeModifier、Mixin、移動パケットへの介入を grep したが、移動抑止に当たる処理はなかった。MobEffects.MOVEMENT_SPEED が出るのは ModEvents.java:461 の心拍計算だけで、鈍化系エフェクトの付与もない。
- ModEvents.onPlayerInteract(:564-569)とアイテム投棄のキャンセル(:166-167)は操作を止めるだけで、移動は止めない。

前提(バニラ/Forge 1.20.1): ServerGamePacketListenerImpl.handleMovePlayer は「moved too quickly」と「moved wrongly」(衝突の食い違い)しか検証しない。forced pose が SWIMMING でも、移動速度属性に見合っているかまでは確かめない。このため、入力イベントを無視する改造クライアントは、意識不明のまま匍匐姿勢で水平移動でき、サーバーもそれを受理する。

重大度を下げた理由:
- 再現には改造クライアントが必要で、正規クライアントでは起きない。
- バニラ自身も移動をおおむねクライアントに任せており、スピードハック等と同じ種類の不正にあたる。
- データ破損やクラッシュは起きず、影響はゲームルール(救助待ち)を回避されることに限られる。
以上から MEDIUM ではなく LOW とした。

### 修正方針
元の方針のうち「MOVEMENT_SPEED を AttributeModifier で 0 にする」だけでは、改造クライアントは止まらない。属性値はクライアント側の移動計算にしか効かず、サーバーは移動パケットを速度属性に照らして検証しないためである。この方法は正規クライアントで動きを止める補助としてなら使えるが、その目的なら既存のクライアント側処理で足りている。

改造クライアント対策の本体は、サーバー側での位置の巻き戻しにする。
1. 意識不明になったとき(ModEvents.handleConsciousnessState の遷移時、:240-245)に、その時点の X/Z をアンカーとして保存する。
2. onPlayerTick で意識不明の間は、アンカーからの水平移動量を確かめる。閾値を超えていたら serverPlayer.connection.teleport(anchorX, 現在Y, anchorZ, yRot, xRot) で X/Z だけ戻す。Y は戻さないので、落下や handleUnconsciousFloating の浮上はそのまま働く。
3. 次の場合は巻き戻しの対象から外す。外したときは、アンカーを新しい位置に更新するか、検査を飛ばす。
   - 乗り物に乗っている間(担架 StretcherEntity での搬送)。
   - MOD 自身が意図的に動かした直後。該当箇所は StretcherBlock.java:130、StretcherBlockEntity.java:69、StretcherPlacementHandler.java:83、StretcherItem.java:121、ServerboundGiveUpPacket.java:81 の teleportTo。
   - ノックバックや水流による受動的な移動。小さな閾値で許容するか、アンカーを追従させる。
4. 意識が戻ったらアンカーを破棄する。

アンカーの保存先として capability などに新しいフィールドを追加するのは、データ構造の変更にあたる。実装前に設計方針の承認を得ること。

## B31 [LOW] 除細動器が撤去された後も電極に DefibrillatorPos が残り、getValue(CHARGED) が別のブロックに対して呼ばれて例外になる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/item/ElectrodeItem.java:41 (net-sides)

### 状況
電極を取り出す(DefibrillatorBlock.java:120 で NBT に座標を記録)と、その後に除細動器ブロックを壊しても DefibrillatorBlock に onRemove 等の後始末がなく、電極の NBT が残る。この電極で患者を右クリックすると、サーバーで getBlockState(pos) が空気などを返し、:41 の getValue(DefibrillatorBlock.CHARGED) が IllegalArgumentException(プロパティが存在しない)を投げる。前提: 1.20.1 ではインタラクトパケットの処理中の例外はエラーとしてログに出るか、キックにつながる(挙動は要確認)。いずれにせよその電極は使えない。ServerboundQTETResultPacket:55-58 は instanceof で確認しているので安全。

### 検証
報告どおり起きることを確認した。
- DefibrillatorBlock.java:118-122: 電極を取り出すと、NBT "DefibrillatorPos" に座標だけが書かれる。ディメンションは書かれない。
- DefibrillatorBlock.java 全体(1-209行): onRemove / playerWillDestroy の上書きがなく、ブロックを壊しても電極の NBT を無効にする処理はない。Grep でも DefibrillatorPos を参照するのは ElectrodeItem、ModEvents:140、ServerboundQTETResultPacket:53 だけで、撤去時の後始末はどこにもない。
- ElectrodeItem.java:34-41: サーバー側で対象が Player なら、NBT の座標の BlockState に対してブロック種別を確かめずに getValue(DefibrillatorBlock.CHARGED) を呼ぶ。1.20.1 の StateHolder.getValue は、その state に無いプロパティを渡すと IllegalArgumentException("Cannot get property ... as it does not exist in ...") を投げる。空気や他のブロックに置き換わっていれば必ずこの例外になる。
- 同じ値を使う ModEvents.java:144-145 と ServerboundQTETResultPacket.java:55-58 は getBlockEntity の instanceof で確かめているので安全。危ないのは ElectrodeItem:41 の 1 か所だけ。
- 報告に無い発生経路も 1 つある。座標にディメンションが含まれないので、ネザーなど別のディメンションへ持って行って使うと、そのディメンションの同じ座標のブロック(多くは除細動器ではない)に対して同じ例外になる。

重大度を MEDIUM から LOW に下げた理由(前提は Minecraft/Forge 1.20.1 の挙動):
- インタラクトパケットの処理はサーバースレッドで実行される(PacketUtils.ensureRunningOnSameThread → BlockableEventLoop.doRunTask)。例外はそこで捕まえられ、ログにエラーが出るだけになる。サーバーのクラッシュやキックにはならない見込み。
- 実害は 3 つ。その電極で除細動が始まらない。プレイヤーへの説明メッセージが出ない。使うたびにスタックトレースがログに出る。
- 復旧はできる。DefibrillatorBlock.java:106-108 の処理で、電極を持って稼働中の任意の除細動器の上部を右クリックすれば電極は回収されて消える。ModEvents:144-158 は除細動器 BE が無い場合は何もしないので、捨てた場合は普通のアイテムとして落ちる。
- データ破損や永続的な詰みは無い。

### 修正方針
1. ElectrodeItem.java:41 で、getValue の前に除細動器かどうかを確かめる。
   - 例: `BlockState st = level.getBlockState(pos); if (!(st.getBlock() instanceof DefibrillatorBlock)) { 「除細動器が見つかりません」を送って FAIL; }` のあとで st.getValue(CHARGED) を読む。
   - 例外を握りつぶす try-catch は使わない。
   - ModEvents や QTE パケットと揃えるなら、getBlockEntity(pos) instanceof DefibrillatorBlockEntity で確かめてもよい。
2. ディメンションの扱いを仕様として決める。NBT にディメンションキーも記録し、使用時に一致を確かめる方法がある。
3. 除細動器を撤去したときの扱い(onRemove で何をするか)も仕様として決める。インベントリ中の電極を全部追跡して直すのは難しいので、1 の使用時チェックで「接続先なし」と扱う方式が現実的。

## B32 [LOW] 全プレイヤーの医療状態を毎 tick、追跡中の全員へ送っており、パケット数がプレイヤー数の二乗で増える
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:218 (net-sides)

### 状況
onPlayerTick(END) が、各プレイヤーについて毎 tick ClientboundCoreStatsPacket を TRACKING_ENTITY_AND_SELF に送る(:218-222)。N 人が互いに追跡範囲内にいると、毎 tick N² パケット(例: 50人なら 2500 パケット/tick = 50,000 パケット/秒)になる。値が変わっていなくても送られる。今後の動的設定変更や管理パネルの同期を同じ方式で載せると、さらに悪化する。

### 検証
報告の事実関係はコードで確認できた。ただし機能が壊れるバグではなく性能上の非効率で、実害は人数が多い場合に限られるため LOW に下げる。
- ModEvents.java:194-198: onPlayerTick はサーバー側の Phase.END で、ServerPlayer ごとに毎 tick 実行される。
- ModEvents.java:218-222: 値が変わったかを見ず、無条件に ClientboundCoreStatsPacket を ModPackets.sendToAllTracking に渡している。
- ModPackets.java:107-108: sendToAllTracking は PacketDistributor.TRACKING_ENTITY_AND_SELF で送る。
- 間引きや差分判定はどこにもない。StartTracking の購読もない（Grep で該当なし）。今は毎 tick の全件送信が、初期同期も兼ねている。
- 前提（Forge 1.20.1）: TRACKING_ENTITY_AND_SELF は ChunkMap.broadcastAndSend で、そのエンティティを追跡している各プレイヤーと本人に1回ずつ送る。したがって N 人が互いに追跡範囲内にいると、1 tick あたり N² 通になる（50人なら 2500 通/tick）。この点は報告どおり。

重大度を下げる理由:
- 中身は小さい（ClientboundCoreStatsPacket.java:60-67）。UUID 16B、float×3、enum、boolean で約30B。Forge のカスタムペイロード包装を足しても1通あたり数十B。
- 試算: 50人なら受信側1人あたり約 49×20×60B ≈ 60KB/s、サーバー送信の合計は約 3MB/s。10人程度なら1人あたり約 10KB/s で、ほぼ無視できる。
- バニラも、追跡中のプレイヤーごとに移動・頭の向きのパケットを tick 単位で送っており、同じ O(N²) 構造。このパケットがあると通信量はおおよそその分だけ増えるが、桁が変わるほどではない。
- 機能面の不具合はない。受信側の処理（ClientboundCoreStatsPacket.java:78-110）はデータの上書きと ClientPoseController.updateFromMedicalData だけで軽い。
- 他プレイヤーのデータをクライアントで使っているのは ClientPoseController.java:253 経由の姿勢制御だけ。bloodLevel や bleedingSpeed を他人に送る必要は今のところない。ただし報告の重大度には影響しない。

「今後、動的設定の変更や管理パネルの同期も同じ方式で載せると悪化する」という部分は、将来の設計に関する指摘であり、現時点の不具合ではない。

### 修正方針
報告の方針はおおむね妥当。ただし次の点を補う。
(1) 定期送信の置き換え: ModEvents:218 の毎 tick 送信をやめ、前回送った値を覚えておいて変わったときだけ送る。bloodLevel は出血中ほぼ毎 tick 変わるので、差分判定だけでは減らない。量子化（例: 0.5 単位）か一定間隔（例: 10 tick）と組み合わせる。heartStatus と isConscious が変わったときは即時に送る。
(2) 宛先の分割: 本人には sendToPlayer で全項目を送る。追跡している他プレイヤーへの TRACKING_ENTITY には、姿勢制御に必要な isConscious と heartStatus だけを、変わったときに送る。
(3) 初期同期: 毎 tick の送信をやめると初期同期もなくなる。そのため次を必ず追加する。
- PlayerEvent.StartTracking で、追跡を始めたプレイヤーに対象の状態を1回送る。
- PlayerLoggedIn / PlayerRespawn / PlayerChangedDimension で、本人に1回送る。
これを入れないと、後から範囲に入ったプレイヤーが意識不明の人の姿勢を正しく表示できない。
(4) 既存の送信: 状態が変わったときに個別に送っている箇所（ModEvents:52,67、TransfusionEffect:37、ServerboundGiveUpPacket:90、ServerboundQTETResultPacket:120、DefibrillatorItem:94,97）は残し、上の仕組みと二重に送らないよう、前回送った値の記録をそこで更新する。

## B33 [LOW] 通報者名は表示名で保存されるのに、ギブアップ時の照合はプロファイル名で行われ、チーム接頭辞やニックネームがあると追記に失敗する
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/command/EmergencyCallCommand.java:138 (net-sides)

### 状況
/119 は reporter = player.getDisplayName().getString() を保存する(:138)。チームの prefix/suffix は表示名に含まれる。一方 ServerboundGiveUpPacket の appendDeathToRescueMemo/appendTransportToRescueMemo(:164, :183)は getGameProfile().getName() と equals で照合する。チームに所属しているプレイヤーやニックネーム系 MOD を使っている環境では一致せず、『死亡』『搬送』がメモに追記されない(エラーも出ない)。

### 検証
報告どおり。EmergencyCallCommand.java:138 で reporter = player.getDisplayName().getString() を取り、:143 → RescueDataManager.createNewRescueData(reporterName,...)(RescueDataManager.java:35-37) → RescueData コンストラクタ(RescueData.java:28-30)でそのまま name に保存される。RescueData を作る経路は他になく(他の new RescueData はクライアント側のデコードのみ: ClientboundUpdateRescueDataPacket.java:24, ClientboundSyncAllRescueDataPacket.java:25)、正規化もしていない。一方 ServerboundGiveUpPacket.java:164/183 は player.getGameProfile().getName() を取り、:170/189 で playerName.equals(data.getName()) と完全一致で比較している。前提(Minecraft/Forge 1.20.1): Player#getDisplayName() は PlayerTeam.formatNameForTeam(getTeam(), getName()) を返し、チームの prefix/suffix が付く。さらに Forge は Player の表示名を PlayerEvent.NameFormat で差し替えられるようにしている(ニックネーム系 MOD)。getString() はこれらの文字列を含むため、チーム所属時やニックネーム使用時は GameProfile 名と一致しない。一致しない場合はループが break せずに終わり、updateRescueDataMemo(RescueDataManager.java:67) が呼ばれないので、『死亡』『搬送』がメモに追記されないまま、ログもエラーも出ない(:168-176, :187-195)。チームも NameFormat も使っていない素の環境では両者は一致するので、問題が出るのはその条件がある環境だけ。実害はメモへの自動追記が抜けることだけで、死亡処理(player.kill)、搬送テレポート、notifyStatusChange による通知は通常どおり動く。よって LOW が妥当。

### 修正方針
RescueData に通報者の UUID フィールド(reporterUuid)を追加する。EmergencyCallCommand.processReport で player.getUUID() を渡し、createNewRescueData / コンストラクタ / FriendlyByteBuf の encode・decode(RescueData.java:129-149)にも UUID を追加する。ServerboundGiveUpPacket の appendDeathToRescueMemo/appendTransportToRescueMemo は player.getUUID().equals(data.getReporterUuid()) で照合する。表示用の name は今の表示名のまま残してよい。最小の修正にするなら、保存側を player.getGameProfile().getName() に揃える方法もある。ただし表示名を見せたいという意図が消え、名前変更にも弱いので、UUID で照合する方が望ましい。

## B34 [LOW] 通報リストと ID カウンタが static のまま一度もクリアされず、シングルプレイでは別ワールドへ持ち越され、ID 採番もスレッドセーフでない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/RescueDataManager.java:23 (net-sides)

### 状況
RESCUE_DATA_LIST(:23)はサーバー停止時にもクリアされない(ServerStopping 等のハンドラは grep で見つからない)。統合サーバーでワールド A の通報を作ってからタイトルに戻りワールド B を開くと、A の通報(座標・ディメンション)がポータルに残っている。ID は RescueData.java:29 の static int nextId++ で採番され(非アトミック)、RescueDataManager.java:25 の AtomicInteger は使われていない。

### 検証
報告の一部だけが正しい。ワールドをまたいで通報が残る点は実際に起きる。スレッドセーフでない点は実害なし。

1) ワールドをまたぐ持ち越し（実在する）
- 通報リストは static final で宣言されている（RescueDataManager.java:23）。
- リストへの操作は add（:40）・find（:84-89）・copy（:95-99）だけで、remove も clear もどこにもない。
- サーバー停止系のハンドラは Medicalsystemcore.java:240 の ServerStartingEvent しかない。ServerStopping/Stopped/LevelEvent.Unload でリストをリセットしている箇所はない（ClientWorldHandler.java:19 は姿勢制御のクリアだけ）。
- RescuePortalScreen.java:75 は画面を開くたびに ServerboundRequestRescueListPacket を送る。サーバーはこれを受けて、static リストの中身をそのまま ClientboundSyncAllRescueDataPacket で返す（ServerboundRequestRescueListPacket.java:17）。
- 前提: 統合サーバー（シングルプレイ）では、ワールド A を閉じて B を開いても同じ JVM 上で動くため、static 状態はクラスが再ロードされない限り残る（Forge 1.20.1 の通常の挙動）。
- その結果、B のポータルに A の通報（座標・ディメンション）が表示される。ID も A から連番で続く。
- 専用サーバーでは再起動すると通報が全部消える。これは「永続化しない」という設計の問題でもある。

2) スレッドセーフでない採番（実害なし）
- RescueData.java:13 の static int nextId と :29 の nextId++ は確かに非アトミック。RescueDataManager.java:25 の AtomicInteger はどこからも使われていない死にコード。
- ただし、この採番コンストラクタを呼ぶのは RescueDataManager.java:37 だけで、さらにそこを呼ぶのは EmergencyCallCommand.java:143 だけ。
- 前提: Forge 1.20.1 ではコマンドはサーバーのメインスレッドで実行される。
- 通報関連のパケットはすべて consumerMainThread か enqueueWork でメインスレッドに乗る（ModPackets.java:60/72/84、ServerboundUpdateRescueDataPacket.java:89、ServerboundGiveUpPacket.java:53）。
- クライアント側は FriendlyByteBuf 版のコンストラクタ（RescueData.java:129-141）を使い、nextId に触れない。
- このため採番が並行して走ることはなく、ID の競合は起きない。

重大度: 不正確な通報が表示されるのはシングルプレイでワールドを切り替えたときだけ。データ破損やクラッシュはないので LOW が妥当。

### 修正方針
- 通報データの寿命をサーバーの寿命に合わせる。
  - 第一候補: ServerStoppedEvent（FORGE バス）で RESCUE_DATA_LIST.clear() を呼び、ID カウンタもリセットする。
  - ワールドごとに通報を残す仕様にするなら、SavedData（overworld の DataStorage）に移し、ID カウンタも一緒に保存する。
- 採番は RescueDataManager の AtomicInteger に一本化する。具体的には、RescueData の static nextId を削除し、コンストラクタで ID を引数として受け取る。現状でも並行実行はないので、これは安全性のためではなく、二重管理（死にコード）を解消するための整理。
- クライアント側の通報キャッシュも、ワールド切断時にクリアされているかを合わせて確認する。

## B35 [LOW] 新しい通報はクライアント一覧の先頭に挿入されるのに、全件同期はサーバー側の古い順なので、一覧の並びが混在する
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ClientboundUpdateRescueDataPacket.java:39 (net-sides)

### 状況
サーバーは通報を末尾に追加し(RescueDataManager.java:40、コメントの『先頭に追加』は実装と異なる)、全件同期は古い順に届く。ポータルを開いている間に新しい通報が来ると、index 0 に挿入される(:39)。そのため『新しい通報が先頭、その後ろは古い順』という並びになり、ポータルを開き直すと新しい通報が末尾に移る。RescueDataListWidget は index の順で描画・クリック判定をするので、表示位置が変わるだけで選択の不整合は起きない。

### 検証
報告どおりの不整合を確認した。
(1) サーバーは末尾に追加している。RescueDataManager.java:40 は `RESCUE_DATA_LIST.add(newData)` で、コメントの「常にリストの先頭に追加」は実装と食い違っている。getRescueDataList (:95-98) はこの順のままコピーを返す。
(2) 全件同期はこの古い順のまま届く。ServerboundRequestRescueListPacket.java:17 が ClientboundSyncAllRescueDataPacket を送り、受信側の :25 は同じ順で読み込む。RescuePortalScreen.updateRescueDataList (:287-290) はそのリストをそのまま画面とウィジェットに渡す。
(3) 個別通知 isNew=true は ClientboundUpdateRescueDataPacket.java:39 で `add(0, data)` され、先頭に入る。受け取るリストは Collectors.toList() で作られた可変 ArrayList か、初期値の new ArrayList なので、add(0) は普通に成功する。
(4) RescueDataListWidget.render (:55-57) はループを逆向きに回しているが、各行の Y 座標は `i * ROW_HEIGHT` で決まる。描画処理の順番が逆になるだけで、表示位置は index 0 が一番上のまま。クリック判定 (:95-101) も同じ index から選ぶ。

結果として、全件同期の直後は上から古い順に並ぶ。ポータルを開いている間に届いた新しい通報だけが一番上に入り、その下に古い順の一覧が続く混在状態になる。開き直す、または画面サイズの変更で init (:75) が再び全件同期を要求すると、新しい通報は末尾に戻る。表示とクリックは同じ index を使うので、選択の不整合は起きない。実害は表示順の一貫性だけなので LOW が妥当。

修正方針への補足: サーバー側を先頭追加 (add(0, ...)) に変えて合わせるのは誤り。ServerboundGiveUpPacket.java:167-168 と :186-187 は「リストの末尾が最新」を前提に、末尾から探して最新の通報を見つけている。サーバーの順序を変えると、死亡・搬送のメモが古い通報に追記されてしまう。

### 修正方針
サーバーの並び順（末尾が最新）はそのまま保つ。ServerboundGiveUpPacket.java:167-168/:186-187 がこの順序に依存している。誤っている RescueDataManager.java:40 のコメントだけを「末尾に追加（末尾が最新）」に直す。並び順の統一はクライアント側で行う。古い順の表示で良ければ ClientboundUpdateRescueDataPacket.java:39 を `add(data)` に変える。新しい順で表示したいなら、RescuePortalScreen.updateRescueDataList で受け取ったリストを reportTime（または id）の降順に並べ替え、:39 の add(0, data) はそのまま残す。表示順を一か所で決めれば、全件同期と個別通知の結果がそろう。

## B36 [LOW] 切断時にクライアント側の医療データ・追跡・QTE・治療の状態がクリアされず、別のサーバーへ持ち越される
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/client/ClientMedicalDataManager.java:16 (net-sides)

### 状況
ClientWorldHandler.onWorldUnload(:22)がクリアするのは ClientPoseController.playerStates だけで、PLAYER_DATA_MAP(:16)、ClientTrackingManager.trackedData、ClientQTEManager や ClientHealingManager の static 状態は残る。ModPackets.java:23-24 はチャネルのバージョンを s -> true で受け入れるので、MOD 入りのクライアントはこの MOD がないサーバーにも接続できる。意識不明のまま切断して MOD なしのサーバーに入ると、isConscious=false が残り、移動入力とマウスクリックが永続的に無効化される(ClientInputHandler:70, :97)。前のサーバーの追跡データもタブレットに表示され続ける(RescueTabletBEWLR:64)。MOD 入りのサーバーなら毎 tick の同期ですぐ上書きされる。

### 検証
報告どおり起きると判断した。
- クライアントの状態は static フィールドにある: ClientMedicalDataManager.java:16 の PLAYER_DATA_MAP と ClientTrackingManager.java:10 の trackedData。このフィールドをクリアするのは、追跡解除時の RescuePortalScreen.java:221 だけだった。
- ClientWorldHandler.java:19-23 の LevelEvent.Unload で呼ぶのは ClientPoseController.onClientDisconnect() だけ。ClientPlayerNetworkEvent.LoggingOut / LoggedOut の購読はリポジトリ内に無い（Grep で確認）。ModEvents.java:250 の onPlayerLoggedOut はサーバー側の処理なので、クライアントの状態には関係しない。
- isConscious の値はサーバーからのパケット（ClientboundCoreStatsPacket.java:87）でしか更新されない。MOD 入りのサーバーでは ModEvents.java:218 が毎 tick 送るので上書きされる。シングルプレイも統合サーバーに MOD が入っているので同じく上書きされる。
- ModPackets.java:23-24 は clientAcceptedVersions(s -> true) になっている。前提: Forge 1.20.1 では、相手サーバーにこのチャネルが無いとき、NetworkRegistry.ABSENT / ACCEPTVANILLA を判定関数に渡す。true を返すと接続が許可されるので、MOD 無しの Forge サーバーにもバニラサーバーにも入れる。
- その接続先ではパケットが一切届かず、isConscious=false が残る。UUID は online/offline のどちらのモードでも同名なら同じなので、getPlayerData(player.getUUID()) が古いエントリを返す。その結果、次がすべて永続的に起きる:
  - ClientInputHandler.java:70-75 が移動入力を 0 にする
  - :97-98 が画面を開いていない時のマウスクリックをキャンセルする
  - :36-42 が視点を固定し、姿勢制御と移動停止を毎 tick 行う
- 抜けるにはクライアントの再起動しか無い。

報告の訂正（1点）: trackedData は MOD 入りのサーバーでも上書きされない。サーバーからは送られず、クライアント側の RescuePortalScreen.java:219-223 だけが設定している。そのため、前のサーバーやワールドの救助要請が、別の MOD 入りサーバーやシングルプレイのタブレットにも表示され続ける（RescueTabletBEWLR.java:64-68）。一方、MOD 無しサーバーにはタブレットのアイテム自体が存在しないので、「MOD 無しサーバーでタブレットに表示される」という説明は当たらない。

重大度: 起きた場合の影響は大きい（入力がほぼ全部止まる）。ただし発生条件が「意識不明のまま切断し、同じクライアントで MOD 無しのサーバーに入る」に限られ、この MOD は特定サーバー向けなので起きにくい。追跡データの持ち越しは表示が古くなるだけの軽い問題。以上から LOW のままとする。

### 修正方針
ログアウト時にクライアントの状態をまとめてリセットする処理を足すのが根本的な直し方。
- 新しいクライアント側ハンドラ（または ClientWorldHandler）で ClientPlayerNetworkEvent.LoggingOut を購読する。
- ClientMedicalDataManager に clearAll()（PLAYER_DATA_MAP.clear()）を追加し、ClientTrackingManager.clearTracking()、ClientQTEManager / ClientHealingManager / ClientGiveUpManager の状態リセット、ClientPoseController.onClientDisconnect() をまとめて呼ぶ。
- PLAYER_DATA_MAP のクリアを LevelEvent.Unload に入れてはいけない。Unload はディメンション移動でも発火するので、ログアウトに限定する。
- チャネルのバージョン判定を厳密にする（ABSENT/ACCEPTVANILLA を拒否する）と、MOD 無しのサーバーに接続できなくなるという仕様変更になる。根本修正の代わりにはならないので、別の判断としてユーザーの承認を取る。

## B37 [LOW] PlayerEvent.Clone ハンドラが無く、エンドからの帰還で医療状態(血液量・出血・心停止)がリセットされる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:89 (state-persist)

### 状況
前提: Forge 1.20.1 では、エンド出口ポータルから帰還すると PlayerList.respawn(keepEverything=true) で新しい ServerPlayer が作られ、capability は自動コピーされない(Clone イベントでコピーするのは MOD の責任)。手順: 出血中・血液量50%のプレイヤーがエンドラ撃破後に出口ポータルへ入る → 新しいプレイヤーは PlayerMedicalData の初期値(bloodLevel=100、bleedingSpeed=0、heartStatus=NORMAL、damageImmune=false)になる。体力は restoreFrom で引き継がれるので、医療状態だけがリセットされる。コード全体で Clone/PlayerRespawnEvent の購読は0件(Grep で確認)。死亡時は onLivingDeath(:45-58)で NORMAL に戻すので初期値でも矛盾しないが、wasDeath=false の場合は状態が失われる。

### 検証
報告どおりの欠陥はコード上で確認できた。ただし実害は小さいため重大度は LOW に下げる。

【確認した事実】
- ModEvents.java:88-95 の onAttachCapabilitiesPlayer は、新しい PlayerMedicalDataProvider を付けるだけ。
- src 全体を `Clone|PlayerRespawnEvent|reviveCaps|PlayerChangedDimension|getPersistentData` で検索したところ 0 件。`respawn|wonGame|changeDimension|EntityTravelToDimension|LoggedIn` も 0 件だった。UUID をキーにした Map など、別経路で状態を引き継ぐ仕組みも無い。
- PlayerMedicalDataProvider.java は、capability の値を NBT（Minecraft の保存形式）に書き出す・読み込む機能を持つだけ。実際に読み書きされるのは、ディスクへの保存とロードの時に限られる。
- PlayerMedicalData.java:12-26 の初期値は次のとおり: bloodLevel=100、heartStatus=NORMAL、bleedingSpeed=0、damageImmune=false、resuscitationChance=100、cardiacArrestTimer=0、blockPos(点滴位置)=empty。保存処理(saveNBTData:202)と読み込み処理(loadNBTData:226)は実装されているので、修正方針どおりコピーに使える。

【前提にした Minecraft/Forge 1.20.1 の挙動】(ソースは読んでいない。既知の仕様として扱う)
- エンドからオーバーワールドへ戻る時、ServerPlayer.changeDimension は wonGame=true を立ててクレジット画面を出す。
- その後クライアントが PERFORM_RESPAWN を送り、ServerGamePacketListenerImpl.handleClientCommand が PlayerList.respawn(player, true) を呼ぶ。
- ここで新しい ServerPlayer が作られ、restoreFrom(old, keepEverything=true) の中から PlayerEvent.Clone(wasDeath=false) が発火する。
- Forge は capability を自動ではコピーしない。また旧プレイヤーの capability は無効化されている。そのため、MOD が Clone でコピーしない限り、新しいプレイヤーの capability は初期値のままになる。
- 体力・インベントリは restoreFrom で引き継がれるので、医療状態だけがリセットされる。報告の筋書きは成立する。

【死亡時】
- 死亡が確定するのは onLivingDeath(:45-58) で heartStatus が NORMAL 以外のまま死亡が通った場合。この時、旧データは NORMAL に戻される。
- 新しいプレイヤーが初期値で始まっても矛盾は無い。

【重大度を LOW に下げた理由】
- 再現するのは、エンド出口ポータルからの帰還(wasDeath=false の Clone)だけ。普通のディメンション移動(ネザー往復など)では ServerPlayer が作り直されないので起きない。
- 失われる状態は、出血・血液不足・心停止・点滴接続。どれもプレイヤーに有利な方向にリセットされる(全快する抜け道)。データ破損やクラッシュにはつながらない。
- 心停止中や意識不明中にエンドの出口ポータルへ入る状況は、ほぼ起きない。

### 修正方針
ModEvents に PlayerEvent.Clone の購読を追加する。

- event.isWasDeath() が false の時だけコピーする。
  1. event.getOriginal().reviveCaps() で旧プレイヤーの capability を一時的に使える状態に戻す。
  2. 旧データを saveNBTData で CompoundTag に書き出す。
  3. 新プレイヤーの loadNBTData で読み込む。
  4. finally で event.getOriginal().invalidateCaps() を呼んで元に戻す。
- 死亡時(isWasDeath=true)はコピーしない。初期値から始めるという方針を、分岐とコメントで明示する。
- コピーした後、新プレイヤーの姿勢制御(PoseController)とクライアントへの同期は、次の tick の onPlayerTick で ClientboundCoreStatsPacket が送られるので回復する。ただし意識不明状態を引き継ぐ場合は、PoseController.setUnconsciousPose を呼び直す必要がないか確認すること。handleConsciousnessState(:240) は「状態が変化した時」にしか呼ばないため、isConscious=false をコピーすると姿勢が再設定されない可能性がある。

## B38 [LOW] 救助要請リストが static のみで保存されない。再起動で消え、シングルプレイでは別ワールドへ持ち越され、削除経路も無い
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/RescueDataManager.java:23 (state-persist)

### 状況
(1) 専用サーバーを再起動すると RESCUE_DATA_LIST(:23)が空になり、全通報が消える。ID も RescueData.nextId(RescueData.java:13, 29)が1に戻り、再起動前に追跡していた通報の ID と新しい通報の ID が衝突する。 (2) シングルプレイでワールドAで通報を作り、タイトルへ戻ってワールドBを開くと、同じ JVM の static なので A の通報(A の座標・ディメンション)がBのポータルに表示される。ServerStopping/Stopped の購読は0件(Grep で確認)。 (3) リストから削除する API が無いので、通報は増え続ける。ServerboundRequestRescueListPacket は全件を1パケットで返す。RescueDataManager.nextId(:25)は未使用のデッドコード。

### 検証
報告の骨格はコードで裏付けられたが、一部は誇張されている。

確認できた点:
- 通報一覧は `RescueDataManager.java:23` の `private static final List RESCUE_DATA_LIST` だけにある。書き込みは `:40` の add のみで、remove や clear はどこにも無い（Grep で確認）。参照しているのは `:87` と `:97` の読み取りだけで、削除経路が無いため件数は増え続ける。
- ID は `RescueData.java:13` の `static int nextId` を `:29` で採番している。`RescueDataManager.java:25` の `AtomicInteger nextId` はどこからも参照されないデッドコード。
- SavedData、ServerStopping/ServerStopped の購読は 0 件。サーバー系イベントは `Medicalsystemcore.java:240` の ServerStartingEvent だけで、ここでも一覧は初期化されない。
- 永続化の処理は無いので、専用サーバーを再起動すると JVM ごと static が消え、通報はすべて失われる。
- シングルプレイの持ち越しについて（前提: Forge 1.20.1 の統合サーバーはクライアントと同じ JVM で動き、ワールドを抜けても MOD のクラスはアンロードされず static が残る）: ワールド A の通報は B に入っても消えない。`ServerboundRequestRescueListPacket.java:17` が全件を返すため、B のポータルに A の通報が表示される。さらに `ServerboundGiveUpPacket.java:163-176` と `:182-195` はプレイヤー名だけで最新の通報を探してメモに「死亡」「搬送」を追記する。シングルプレイではプレイヤー名が同じなので、B での操作が A の通報データを書き換える。これは報告に無かった追加の実害。

反証・誇張と判断した点:
- 「再起動後の ID 衝突」は実害がほぼ無い。再起動で一覧自体も空になるので、サーバー内に旧 ID が残ることは無い。クライアント側の保持は `RescuePortalScreen.java:29` の画面インスタンス内のリストだけで、画面を開くたびにサーバーから取り直す。古い ID を参照し続ける経路は見当たらない。
- 1 パケットで全件を返す件（`ClientboundSyncAllRescueDataPacket`）は、通報 1 件が数百バイト程度のため、現実的な件数ではパケット上限（約 1MB）に届かない。現時点では理論上の懸念にとどまる。
- 再起動で消えることについては、救助要請をサーバーが動いている間だけの一時データとして設計した可能性を否定できない。

重大度の付け直し: 専用サーバー運用（この MOD の主な想定）で起きる実害はデータ消失と緩やかなメモリ増加だけで、クラッシュや不整合は起きない。データが混ざるのはシングルプレイでワールドを切り替えた場合に限られるため、MEDIUM から LOW に下げる。

### 修正方針
最低限の修正として、ServerStoppedEvent（シングルプレイのワールド終了でも発火する）で RESCUE_DATA_LIST を clear し、RescueData の nextId を 1 に戻す。これでワールド間の持ち越しと、GiveUp による別ワールドの通報への追記が止まる。採番は static int の RescueData.nextId を廃止し、RescueDataManager の AtomicInteger に一本化する。現状ではこちらが未使用で、使われている方はスレッドセーフでない。

再起動後も通報を残すのが仕様なら、一覧と次の ID を overworld の DimensionDataStorage 上の SavedData に移す。RescueData に NBT 化を足し、作成・更新のたびに setDirty する。これは新しい永続化の仕組みを入れる設計変更なので、ユーザーの承認を得てから進める。

完了・古い通報を削除する手段（保持期間または件数の上限、もしくは削除コマンドやパケット）は、仕様が決まってから追加する。

## B39 [LOW] 同じ ForgeConfigSpec を SERVER と COMMON の両方に登録しており、設定値の出どころが読み込み順で変わる(動的変更コマンドの実装前に要整理)
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/Medicalsystemcore.java:226 (state-persist)

### 状況
確実な事実: :226-227 で Config.SPEC を SERVER と COMMON に二重登録している。ForgeConfigSpec の裏付けデータ(childConfig)は1つだけで、acceptConfig で後勝ちになる(Forge 47.4.0 sources の ForgeConfigSpec:57, 98 で確認)。Config.onLoad(Config.java:293-357)はどちらのイベントでもほぼ全値を読み直し、:358-360 のコメント自身が COMMON による上書きを認めている。前提が必要な推測: シングルプレイでワールドを抜けて SERVER 設定がアンロードされる時に、ModConfigEvent.Unloading が childConfig=null の後に届くと、onLoad の .get() が「Cannot get config value before config is loaded」で例外を投げる可能性がある(fmlcore のソースが無く、順序は未確認)。また動的変更で ConfigValue.set() すると、その時点の childConfig(SERVER か COMMON か)に書かれるため、どちらのファイルに残るかが不定になる。

### 検証
Forge 47.4.0 で確認したところ、構造の指摘は正しいが、報告にある実害 3 点はどれも成立しないか、ほぼ無害だった。実際に残る問題は、COMMON 側の toml が誰にも読まれない「死んだ設定ファイル」になっていることと、コメントの誤りだけ。

【事実として正しい点】
- Medicalsystemcore.java:226-227 で同じ Config.SPEC を SERVER と COMMON の両方に登録している。
- ForgeConfigSpec が保持する設定データは childConfig 1 つだけ（ForgeConfigSpec.java:56）。acceptConfig→setConfigData（:98→:78-95）で後から読んだ方に上書きされる。
- Config.onLoad（Config.java:293-367）は ModConfigEvent を全種類受け取り、どちらの設定でもほぼ全値を読み直す。

【報告の主張 1「値の出どころが読み込み順で変わる」→ 実際は順序が固定で、成立しない】
- COMMON はMOD読み込み時に読まれる。SERVER は ServerLifecycleHooks.java:96（サーバー起動直前）で後から読まれ、childConfig は SERVER のファイルになる。
- 稼働中にファイルが変更されたとき（fmlcore 47.4.0 の ConfigFileTypeHandler$ConfigWatcher を javap で逆アセンブルして確認）、Forge はそのファイル自身の内容を load() し、afterReload() を呼び、Reloading イベントを出すだけ。childConfig は差し替えない。
- そのため稼働中に COMMON の toml を編集しても、onLoad は childConfig＝SERVER から読み直す。COMMON の値がゲームに反映される経路はない。Config.java:358-360 のコメント（COMMON のデフォルト値で上書きされる）は事実と異なる。

【報告の主張 2「アンロード時に例外が出る」→ 反証】
- ConfigTracker.closeConfig のバイトコード順序は、handler.unload → fireEvent(unloading) → save → setConfigData(null)。
- Unloading イベントは childConfig を null にする前に届くので、onLoad 内の .get() は ForgeConfigSpec.java:848 の checkState に引っかからない。

【報告の主張 3「動的変更で set() するとどちらのファイルに残るか不定」→ 反証】
- サーバー稼働中の childConfig は常に SERVER のファイルなので、ConfigValue.set()（:892-896）と spec.save()（:132-137）は medicalsystemcore-server.toml に確定的に書かれる。

【残る小さな問題】
- medicalsystemcore-common.toml は同じキーを持つ重複ファイルとして生成されるが、どこからも使われない。管理者がこちらを編集しても何も反映されない。
- 端の挙動: シングルプレイでワールドを抜けると childConfig は null になる（closeConfig 末尾の setConfigData(null)）。その状態で COMMON の toml を編集すると、Reloading(COMMON) を受けた onLoad の .get() が、開発環境では :848 で IllegalStateException を投げる。本番環境では :855 でデフォルト値を返し、static 変数がデフォルトに戻る。ただし次にワールドに入るとき SERVER が読み直されるので、実害はない。
- 前提: Forge 1.20.1-47.4.0 の forge sources と fmlcore jar（javap）に基づく判断。

### 修正方針
今すぐ直す必要のある不具合ではない。動的変更コマンドを作る前の整理として、次の 3 点を行う。
(1) Medicalsystemcore.java:227 の COMMON 登録を削除し、SPEC は SERVER 専用にする。全項目がサーバー側の判定値で、クライアントの HUD が使う TRANSPORT_MODE はサーバーから同期されるため、COMMON 登録は不要。
(2) Config.onLoad は ModConfigEvent.Loading と Reloading だけを購読し、Unloading は無視する。Config.java:358-366 の SERVER 判定と、事実と異なるコメントは削除する。
(3) 動的変更コマンドは、サーバースレッド上で SERVER SPEC の ConfigValue.set()→SPEC.save() を呼び、その後 static 変数を読み直す。クライアントの表示に要る値（TRANSPORT_MODE 等）は、独自パケットで同期する。
報告の修正方針にある「SERVER 用と COMMON 用で SPEC を分ける」は、COMMON 側に置くべき値が今は存在しないので不要。

## B40 [LOW] 除細動器のクールダウンがチャンク再読込・サーバー再起動で消える(load 時 level==null)
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/blockentity/DefibrillatorBlockEntity.java:142 (state-persist)

### 状況
前提: 1.20.1 の BlockEntity.loadStatic(pos, state, tag) は level を設定する前に load(tag) を呼ぶ(srg jar の m_155241_ を javap で確認。level を受け取る引数が無い)。saveAdditional は残り tick を相対値で保存しているが(:125-127)、load では `remainingTicks > 0 && level != null` が常に偽になり、cooldownEndTick=0 になる(:141-146)。電気ショック直後にチャンク外へ離れて戻る、または再起動すると、すぐ再充電できる。クライアント側は onDataPacket 経由で level ありで load するので正しく、サーバーとクライアントで表示が食い違う。

### 検証
反証を試みたが、報告どおりに起きることを確認した。
(1) DefibrillatorBlockEntity.java:141-146 で、load() は `remainingTicks > 0 && level != null` のときだけ cooldownEndTick を復元し、それ以外は 0 にする。
(2) 1.20.1 の vanilla 実装を javap で確認した(Fabric loom の mojmap 版 minecraft-common-1.20.1 jar を使用。Forge もこの経路は改変しない前提)。BlockEntity.loadStatic(BlockPos, BlockState, CompoundTag) は Level を引数に取らない。中では type.create(pos,state) の後にラムダ内で load(tag) を呼んでいる。LevelChunk.getBlockEntity → promotePendingBlockEntity → loadStatic と進み、level が設定されるのはその後の addAndRegisterBlockEntity/setBlockEntity(setLevel) の時点。したがってチャンク再読込とサーバー再起動のどちらでも、サーバー側の load 時点では level==null で、cooldownEndTick=0 になる。
(3) 保存側(:125-127)は level ありで残り tick を正しく書いているので、失われるのは復元時だけ。
(4) クールダウンの判定箇所は DefibrillatorBlock.java:153 の be.isOnCooldown() だけで、ほかに防ぐ処理はない。ServerboundQTETResultPacket.java:60 で startCooldown が呼ばれた直後でも、チャンクのアンロードとリロード(または再起動)を挟めば、すぐ再充電できる。
補足として、報告の「サーバーとクライアントで表示が食い違う」は概ね誤り。チャンクを再送するときクライアントはサーバーの getUpdateTag(:156-160)を受け取る。サーバー側の残り値が既に 0 なので、クライアントもクールダウンなしと表示し、両者は一致する(ずれるとしても、リロード前からクライアントが保持していた状態との間だけ)。
実害はクールダウンを回避されることに限られる。チャンク外への移動か再起動という能動的な操作が必要で、データ破損やクラッシュは起きない。よって LOW が妥当。

### 修正方針
load() では、NBT の CooldownRemainingTicks を一時フィールド(例: pendingCooldownTicks)に保持するだけにする。level 設定後に呼ばれる Forge の onLoad()(level != null が保証される)で cooldownEndTick = level.getGameTime() + pending に変換し、pending を 0 に戻す。
クライアントの onDataPacket 経路は level があるので、即時変換を残してもよい。ただし load を一本化するなら、「level != null なら即時変換、null なら pending に保持して onLoad で変換」という形にする。
代替案として、絶対時刻をやめて残り tick を tick() ごとに減算する方式もある。ただし tick() は POWERED=false のとき早期 return するため(:61-67)、減算処理はその return より前に置く必要がある。

## B41 [LOW] 担架エンティティの運搬者がロード後に復元されず、患者がログアウトすると担架ごと消えて運搬者に返らない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/entity/StretcherEntity.java:275 (state-persist)

### 状況
前提: バニラの PlayerList.remove は、ルート車両の乗員がプレイヤー1人だけなら、車両を UNLOADED_WITH_PLAYER で setRemoved し、プレイヤーデータの RootVehicle として保存する(srg jar の m_11286_ を javap で確認)。手順: 患者を担架エンティティで運搬中に患者がログアウトする → StretcherEntity は remove() を通らずに消え(:226 のオーバーライドも returnStretcherToCarrier も走らない)、運搬者はアイテムを受け取れない。患者が再ログインすると RootVehicle から担架が復元される。readAdditionalSaveData(:275-279)は CARRIER_UUID を同期データにしか入れず、サーバー側の carrier フィールドは null のままなので、最初の tick(:62-64)で患者の位置にアイテムがドロップされる。患者が戻らなければ担架は失われる。チャンク再読込・再起動でも同じく carrier が null になり、運搬状態は継続されない。

### 検証
報告のとおりに起きる。前提は 1.20.1 バニラ/Forge の挙動で、PlayerList.remove は先頭で Forge の PlayerLoggedOutEvent を発火し、続けて save(player) を呼ぶ。ServerPlayer.addAdditionalSaveData は、ルート車両の乗員がプレイヤー 1 人だけならその車両を RootVehicle として保存する。その後 getPassengersAndSelf() の各要素に setRemoved(UNLOADED_WITH_PLAYER) を直接呼ぶので、Entity.remove() のオーバーライドは通らない。
- 担架は乗員を 1 人しか持たない。StretcherItem.java:61 と StretcherBlock.java:165 の create(..., carrier, patient) から StretcherEntity.java:247 の passenger.startRiding(this) に至るため、患者がログアウトすると担架は患者と一緒に保存され、ワールドから消える。StretcherEntity.java:226-232 の remove() も、:181 の returnStretcherToCarrier も呼ばれない。removePassenger のオーバーライドもない。
- ログアウト時の救済処理もない。ModEvents.java:250-254 の onPlayerLoggedOut は PoseController.clearPoseControl を呼ぶだけで、担架を扱っていない。このため運搬者はアイテムを受け取れない。
- 再ログインすると担架が復元されるが、readAdditionalSaveData(:275-279)は CARRIER_UUID を同期データに入れるだけで、サーバー側の carrier フィールドは null のままになる。サーバー側で carrier を UUID から引き直す処理は無い（クライアント側の :89-93 にしか無い）。
- そのため最初の tick の :62-63 で returnStretcherToCarrier が呼ばれ、:199-201 の spawnAtLocation で担架の位置（患者の位置）にアイテムが落ちる。患者が戻らなければ、そのアイテムは運搬者の手に戻らない。
- サーバー再起動後の読み込みでも同じく carrier が null になり、運搬状態は続かずにその場にドロップする。
- チャンク再読込は、担架が運搬者（チャンクを読み込むプレイヤー）に追従するため、運搬中には起きにくい。運搬者がログアウトした場合は :62 の isRemoved で正しくドロップするので、問題は無い。
実害は担架アイテム 1 個の紛失（または患者の位置へのドロップ）と、運搬状態の中断にとどまり、クラッシュやデータ破損は無い。重大度は LOW が妥当。

### 修正方針
1) ModEvents.onPlayerLoggedOut(ModEvents.java:250) で player.getVehicle() が StretcherEntity なら、PlayerList.remove による保存と setRemoved より先に降ろし、担架を運搬者へ返却して discard する。PlayerLoggedOutEvent は PlayerList.remove の先頭で発火するので間に合う。そのために returnStretcherToCarrier を public にするか、返却用の API を用意する。患者が担架ごと保存されなくなるので、再ログイン時の誤ドロップも同時に防げる。
2) サーバー再起動など、保存データから担架を読み込むケースの対策として、サーバー tick の :62 の前で、carrier==null かつ CARRIER_UUID がある場合に level.getPlayerByUUID で復元を試みる。見つからない場合は今の挙動（ドロップ）でよい。restore 時に passenger フィールドも getFirstPassenger() から同期しておくと、:146 以降の乗員処理と整合する。

## B42 [LOW] 電極を失う、または除細動器を壊すと arePadsTaken が true のまま戻らず、孤立した電極の使用でサーバーに FATAL ログが出る
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/item/ElectrodeItem.java:41 (state-persist)

### 状況
(1) 電極を取り出すと arePadsTaken=true・HAS_PADS=false になる(DefibrillatorBlock.java:115-127)。電極を戻す経路は「手に持って上部を右クリック」(:106-113)か ItemTossEvent(ModEvents.java:138-162)だけ。死亡ドロップのデスポーン、溶岩、/clear などで電極を失うと、除細動器は永久に電極なし(:129-130 で「使用中」)になる。 (2) 電極を持ったまま除細動器を壊したり置き換えたりすると、ElectrodeItem:41 の getBlockState(pos).getValue(CHARGED) が CHARGED を持たない BlockState で IllegalArgumentException を投げる。前提: 1.20.1 ではパケット処理の例外は BlockableEventLoop.doRunTask で捕捉され、「Error executing task」として FATAL マーカー付きでログされる(javap で確認)。サーバーは落ちないが、使うたびにエラーログが出て操作は失敗する。

### 検証
(2) は実際に起きる。(1) は「永久に」の部分が言い過ぎで、通常の救済手段がある。

[(2) 確認済み] ElectrodeItem.java:34-41 では、サーバー側でアイテム NBT の DefibrillatorPos を読み、そのまま `pPlayer.level().getBlockState(defibrillatorPos).getValue(DefibrillatorBlock.CHARGED)` を呼んでいる。ブロックの種類も BE の型も確かめていない。DefibrillatorBlock には onRemove の上書きがなく（DefibrillatorBlock.java 全体）、除細動器を壊しても電極側に知らせる処理は無い。そのため電極を持ったまま除細動器を壊す・別ブロックに置き換えると、その座標は air などになる。そこで getValue(CHARGED) を呼ぶと、Minecraft の StateHolder.getValue は存在しないプロパティに対して IllegalArgumentException を投げる（Minecraft の仕様が前提）。呼び出し元は ServerGamePacketListenerImpl.handleInteract → Player.interactOn → ItemStack.interactLivingEntity というパケット処理経路である（Forge 1.20.1 の仕様が前提）。例外はメインスレッドのタスク実行部で捕捉されてログに出るだけで、サーバーは落ちない。報告者は javap で FATAL「Error executing task」を確認している。私は再逆コンパイルしていないが、どちらの経路でも「エラーログが出て操作が失敗する・クラッシュはしない」という結論は変わらない。なお同じ座標を読む ServerboundQTETResultPacket.java:55-58 は `be instanceof DefibrillatorBlockEntity` で守られており、問題は ElectrodeItem.java:41 だけである。

[(1) 一部反証] 電極を取り出すと arePadsTaken=true・HAS_PADS=false になる（DefibrillatorBlock.java:115-127）。これを戻す経路は電極を手に持って戻す操作（:106-113）と ItemTossEvent（ModEvents.java:138-162）だけ。arePadsTaken を false にする resetChargeAndStartCooldown（DefibrillatorBlockEntity.java:49-58）はどこからも呼ばれていない。電極は stacksTo(16)・NBT 付きで、レシピも存在しない（resources に electrode のレシピなし）。死亡時のドロップは Forge では LivingDropsEvent 経由で ItemTossEvent が発火しない（Forge の仕様が前提）。よって電極を失うと、素手で上部を右クリックしても「電極は既に使用中です」（:129-130）のままになる、という点までは正しい。
ただし除細動器は strength(1.5f) で道具なしで壊せ（Medicalsystemcore.java:93）、ルートテーブルで BE データ無しのまま自分自身をドロップする（loot_tables/blocks/defibrillator_block.json）。壊して置き直せば新しい BE は arePadsTaken=false（DefibrillatorBlockEntity.java:19）、既定の状態は HAS_PADS=true（DefibrillatorBlock.java:50）に戻るので、サバイバルでも回復できる。また :106 はどの電極でも NBT を確認せずに受け入れるため、クリエイティブタブ（Medicalsystemcore.java:186）や他の除細動器の電極を使っても戻せる。本当に詰むのは、アドベンチャーモードや保護領域でブロックを壊せない環境に限られる。

重大度は LOW のままとする。データ消失やクラッシュは無く、エラーログが出て操作が失敗するのと、使い勝手が悪くなるだけである。

### 修正方針
(2) は根本原因を直す。ElectrodeItem.interactLivingEntity で、`level.getBlockEntity(pos) instanceof DefibrillatorBlockEntity be`（または `state.is(Medicalsystemcore.DEFIBRILLATOR_BLOCK.get())`）を確かめてから CHARGED を読む。確認できないときは「除細動器が見つかりません」と知らせて FAIL を返す。CHARGED は be.isCharged() でもよい。これはエラーを握りつぶすのではなく、リンク先が無くなったという正当な状態を判定する処理である。ServerboundQTETResultPacket はすでに instanceof で判定しているので、同じ形にそろえる。
(1) は「壊して置き直す」という救済が既にあるため必須の修正ではない。直す場合も報告の方針をそのまま採るのではなく、次の最小限の対応がよい。DefibrillatorBlock に onRemove を上書きする必要はない（電極側は (2) の修正で安全になる）。素手で右クリックしたときの回収手段を足すなら、電極 NBT にリンク ID を持たせ、古い電極を無効にする設計変更になる。仕様判断が要るのでユーザーの承認を得る。あわせて、未使用の resetChargeAndStartCooldown（DefibrillatorBlockEntity.java:49）を消すか使うかを決める。

## B43 [LOW] クライアントの static 状態(医療データ Map・追跡中の通報)が切断・ワールド切替で消えない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/client/ClientMedicalDataManager.java:16 (state-persist)

### 状況
ClientWorldHandler.java:22 の LevelEvent.Unload で消しているのは ClientPoseController だけ。ClientMedicalDataManager.PLAYER_DATA_MAP(:16)は見たプレイヤーの UUID を全て保持し続ける。サーバーAで意識不明のまま切断してサーバーBに入ると、最初の ClientboundCoreStatsPacket が届くまでの間、isPlayerUnconscious が古い true を返し、入力が遮断される。ClientTrackingManager.trackedData(ClientTrackingManager.java:10)も消えないので、別のサーバーやワールドへ移っても前の通報の追跡オーバーレイ(RescueTabletBEWLR)が表示され続ける。再起動で ID が1に戻ると、ID 比較(RescuePortalScreen の toggleTracking)で別の通報を「追跡中」と誤判定する。

### 検証
コードを読んで確認した。後始末が抜けているのは本当だが、実害は報告より小さく、重大度は LOW のままとする。

【確認できた事実】
- client/ClientWorldHandler.java:22-26: LevelEvent.Unload で消しているのは ClientPoseController.onClientDisconnect() だけ。ClientPlayerNetworkEvent.LoggingOut/LoggingIn の購読はリポジトリ内のどこにも無い（grep で 0 件）。
- ClientMedicalDataManager.java:16 の PLAYER_DATA_MAP と ClientTrackingManager.java:10 の trackedData は static で、どこからも消されない。trackedData を null に戻すのは RescuePortalScreen.java:221 の手動トグルだけ。

【意識不明の判定が古いまま残る件: 起きるが、影響する時間はごく短い】
- サーバーは ModEvents.onPlayerTick（ModEvents.java:192-222）の毎 tick 末に、本人と周囲のプレイヤーへ ClientboundCoreStatsPacket を送る（sendToAllTracking = TRACKING_ENTITY_AND_SELF、ModPackets.java:107-108）。
- 受信側の ClientboundCoreStatsPacket.java:80 は同じ UUID のエントリを上書きする。
- そのため、この MOD が入ったサーバー B では、古い isConscious=false が効くのはログイン直後の数 tick だけで、入力遮断（ClientInputHandler.java:36,70,97、ClientMouseHandler:34）もその間に限られる。報告の「最初のパケットが届くまで」は正しいが、実際にはほぼ気付かない長さ。
- 例外の可能性が一つある。ModPackets.java:23-24 が clientAcceptedVersions/serverAcceptedVersions を s -> true にしているため、この MOD の無いサーバーにも接続できるかもしれない。その場合 CoreStats が一度も届かず、入力遮断がずっと続く。ただし Forge 1.20.1 で、MOD 追加のレジストリがある状態でバニラ/未導入サーバーに入れるかは未検証。前提付きの可能性として挙げるにとどめる。
- 他プレイヤーの UUID が溜まり続けるのは小さなメモリリークで、実害は無視できる。

【追跡中の通報が残る件: 確実に起きる】
- 別サーバーやワールドへ移っても trackedData は残る。RescueTabletBEWLR.java:64-67 は isTracking() だけを見てオーバーレイを描くので、前のサーバーの座標を指す表示が出続ける。ディメンションキーは minecraft:overworld などで一致しうる。
- 通報の ID は RescueData.java:13,29 の static int nextId++ で振られ、パケットではサーバーの ID をそのまま運ぶ（RescueData.java:129-130,148）。RescueDataManager.java:23 の通報リストもメモリ上にあるだけで保存されない。
- そのため専用サーバーを再起動すると ID は 1 から振り直しになる。RescuePortalScreen.java:220,231 は getId() だけを比べるので、別の通報を「追跡中」と表示し、トグルすると追跡が解除される。報告どおり起きる。
- シングルプレイでは、クライアントと内蔵サーバーが同じ JVM で static を共有する。ワールドを替えても ID は振り直されないので、ID の衝突は起きにくい。それでも古い追跡が残る問題は同じく起きる。

【ClientQTE/Healing/GiveUp】
これらの static（ClientQTEManager.java:17-21、ClientHealingManager.java:14-16、ClientGiveUpManager.java:11-12）も切断時に消されていない。ただし、これが原因の具体的な不具合までは確かめていない。

まとめ: 切断時の後始末が無いことは事実なので real=true。実害は、追跡オーバーレイの誤表示と ID 衝突による「追跡中」の誤判定が中心。意識不明による入力遮断は、MOD 導入サーバー同士ならほぼ一瞬で問題にならない。

### 修正方針
このファイル（Dist.CLIENT の FORGE バス）に ClientPlayerNetworkEvent.LoggingOut の購読を追加する。そこで以下をまとめて初期化する。
- ClientMedicalDataManager に clear() を追加して PLAYER_DATA_MAP を空にする
- ClientTrackingManager.clearTracking()
- ClientQTEManager.stop()（または static を初期値に戻す reset()）
- ClientHealingManager.stopHealing()
- ClientGiveUpManager の holdTicks/holding を初期値に戻す
- 姿勢状態（ClientPoseController.onClientDisconnect()）

姿勢状態の初期化は、ディメンション移動で起きる LevelEvent.Unload にも残す必要があるか、意図を確認してから移す。

あわせて、ID の衝突は通報 ID が永続でも一意でもないことが根本原因なので、追跡情報は切断時に必ず捨てる運用にする。古いエントリが溜まる問題は、LoggingOut で消せば解決するので、フォールバック値で抑え込む方式にはしない。

## B44 [LOW] 新着通報の挿入位置がサーバーの並び順と逆で、ポータルを開き直すと並びが変わる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ClientboundUpdateRescueDataPacket.java:39 (state-persist)

### 状況
サーバーは RESCUE_DATA_LIST.add(newData) で末尾に追加する(RescueDataManager.java:40。コメントは「先頭に追加」と書いているが実装は追加)。全件同期はこの古い順で届く。一方、クライアントは新着を add(0, data) で先頭に入れる(:39)。ポータルを開いている間に届いた通報は一番上に表示されるが、開き直すと一番下に移る。

### 検証
報告どおり再現する。(1) サーバー: RescueDataManager.java:40 は RESCUE_DATA_LIST.add(newData) で末尾追加（コメント「常にリストの先頭に追加」は実装と食い違う）。getRescueDataList() (:95-98) はこの順のコピーを返し、ServerboundRequestRescueListPacket.java:17 → ClientboundSyncAllRescueDataPacket.java:25,29-30 で順序を保ったままシリアライズ/復元され、:38 で screen.updateRescueDataList() に渡る。つまり全件同期は古い順。(2) クライアント: ClientboundUpdateRescueDataPacket.java:38-39 は isNew 時に screen.getRescueDataList().add(0, data) で先頭挿入。RescuePortalScreen.java:288-289 で screen と widget は同一リスト参照を共有しているので、widget にも即反映される。(3) 描画: RescueDataListWidget.render はループこそ逆順 (for i = size-1..0) だが、行の Y 座標は y - scroll + i*ROW_HEIGHT で index 0 が最上段になる。mouseClicked も clickedIndex = relativeY/ROW_HEIGHT で同じ対応になっている。したがって全件同期直後は古い順に上から並び、ポータルを開いたまま受信した新着は最上段に出る。開き直す（あるいは init が再実行されるリサイズ時。RescuePortalScreen.java:75 で init のたびに再要求している）と全件同期で最下段へ移る。前提として、Forge 1.20.1 の SimpleChannel はリストを toBytes/デコーダ経由で順序を保って運び、Collectors.toList() が返すのは実質 ArrayList なので add(0) は例外にならない。選択状態は selectedId（ID）で保持しているため、挿入で index がずれても選択の取り違えは起きない。データの欠落や誤更新はなく表示順だけの不整合なので、重大度は LOW のまま。

### 修正方針
並び順の規約を一つに決めて両経路を揃える。どの位置に追加するかは表示側で決めるべきことなので、サーバーは時系列（末尾追加）のまま保ち、RescueDataManager.java:40 の誤ったコメントを直す。クライアントは ClientboundUpdateRescueDataPacket.java:39 を add(data)（末尾追加）にして全件同期と同じ順にする。「新しい順で表示したい」という UI 要件があるなら、RescuePortalScreen.updateRescueDataList と新着追加の両方で同じ規約（例: 受信リストを reverse して保持し、新着は add(0, …)）を使うか、RescueDataListWidget の位置計算（rowTopY / clickedIndex）を逆順インデックスに統一する。どちらの場合も描画とクリック判定のインデックス対応を同時に変えること。

## B45 [LOW] ヘッドサイドモニターを右クリックするたびにケーブルが無制限に配られる(isCableTaken が一度も true にならない)
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/HeadsideMonitorBlock.java:56 (state-persist)

### 状況
use() はコメントで「まだ取り出されていない場合のみ」と書いているのに、isCableTaken を確認せずに毎回 HEAD_SIDE_CABLE を付与する(:54-61)。setCableTaken の呼び出しは HeadsideCableItem の false だけで、true にする経路が無い。HeadsideMonitorBlockEntity は CableTaken を保存/読込/同期しているが、常に false の死んだ状態になっている。

### 検証
報告どおりの状況が実際に起きることを確認しました。

- HeadsideMonitorBlock.java:54-61 の use() は、サーバー側で権限チェック（:51）を通ると、毎回新しい HEAD_SIDE_CABLE を作って MonitorPos を書き込み、プレイヤーのインベントリに add します。:55 のコメントには「まだ取り出されていない場合のみ」とありますが、monitor.isCableTaken() を確認していません。
- src/main/java 全体を grep した結果、setCableTaken の呼び出しは HeadsideCableItem.java:43 の setCableTaken(false) だけでした。true を設定する経路はありません。isCableTaken() の読み出し元もありません。
- HeadsideMonitorBlockEntity.java:22, 44-51, 175, 193 で値の保存と読込はしていますが、値は常に false のまま使われていません。

前提（Forge 1.20.1 の仕様）: Block#use は手ごとに呼ばれますが、メインハンドで SUCCESS を返すとオフハンドの処理は行われません。そのため 1 回のクリックで配られるのは 1 本です。しかしクリックするたびに 1 本増えます。

実害が限られる理由:
- 配布は医療権限を持つプレイヤーに限られます（:51）。
- ケーブルは stacksTo(1)（Medicalsystemcore.java:122-123）です。どのケーブルも同じモニターへ接続先を上書きするだけです（HeadsideCableItem.java:41）。
- 結果として、インベントリが同じアイテムで埋まることと、意図した 1 本制限が働かないことに留まります。データの破壊やクラッシュはないので LOW が妥当です。

付随する点: Inventory#add はインベントリが満杯だと false を返しますが、戻り値を無視しているため、その場合ケーブルは渡されずに捨てられます。

### 修正方針
use() で以下を行います。

1. monitor.isCableTaken() が true の場合は配布せず、メッセージを返します。
2. 配布するときは pPlayer.getInventory().add(cableStack) の戻り値を確認します。成功した場合だけ setCableTaken(true) を呼び、続けて sendBlockUpdated（または setChanged）を行います。失敗した場合は drop するか、何もせず taken を立てないようにします。

注意点として、報告の修正方針どおり単に true を立てるだけでは、ケーブルを失ったときにモニターが永久にロックされます。失う例は、アイテムのデスポーン、死亡、/clear、溶岩への投入、別のモニター用ケーブルとして保持したまま放置、などです。そのため、ロックを解除する経路が必須です。例を挙げます。
- taken 状態のモニターを（スニークで）右クリックするとリセットされ、再配布できるようにする。
- 配布時にケーブルの NBT へ一意 ID を入れ、モニター側は最新の ID だけを有効とする（古いケーブルは HeadsideCableItem で拒否する）。

「モニターを破壊したときに false へ戻す」処理は不要です。ブロックエンティティごと消えるためです。既存の HeadsideCableItem.java:43 による接続時の false 戻しはそのまま使えます。

ただし、1 本制限が本当に仕様上必要かは設計者に確認する価値があります。不要であれば、:55 の誤ったコメントと、使われていない CableTaken フィールドおよびその NBT を削除するのが根本的な対処です。

## B46 [LOW] PlayerMedicalData の NBT が非対称(点滴接続・tickCounter は保存されない)で、キーが無い時の既定値も初期値と食い違う
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/capability/PlayerMedicalData.java:219 (state-persist)

### 状況
確実な事実: transfusingFromStandPos は保存されないので(:219 のコメント)、再ログインすると点滴が「点滴が外れました」の通知なしに切れる(ModEvents.java:318-319 は接続中にしか走らない)。tickCounter も保存されない。loadNBTData(:225-243)はキーの有無を見ずに getFloat/getBoolean するため、キーが無いと bloodLevel=0、resuscitationChance=0、isConscious=false になる。フィールドの初期値(100/100/true)と食い違う。要確認: このフィールドが後から追加され、追加前の版で保存されたプレイヤーデータがある場合(git 履歴は読み取り制約のため未確認)、ロード直後に血液量0で handleCardiacArrest が kill する可能性がある。

### 検証
報告は 3 つの主張に分かれ、実害が確認できるのは 1 つだけ。

(1) 点滴接続が保存されない件は事実で、再現する。saveNBTData(PlayerMedicalData.java:202-220)は blockPos フィールド(:22)を書いておらず、:219 のコメントで意図的に省いている。loadNBTData(:226-243)も読まない。接続をセットするのは TubeItem.java:85 だけ。切断時の通知は、ModEvents.handleIVTransfusion(:257-321)の else 節(:317-319)と TubeItem.disconnectExistingConnections(:121-122)の 2 か所だけで、どちらも Optional が present の時にしか動かない。ログアウト処理 onPlayerLoggedOut(ModEvents.java:249-254)は PoseController.clearPoseControl を呼ぶだけで、点滴には触れない。結果として、再ログイン後は通知なしで点滴が切れている。IVStandBlockEntity 側は接続中のプレイヤーを保持していないので(blockentity 配下の grep で UUID 参照は Stretcher と HeadsideMonitor のみ)、宙に浮いた状態は残らない。実害は「再接続が必要なことに気付きにくい」程度。前提: Forge 1.20.1 では再ログイン時に ServerPlayer が作り直され、ケイパビリティは ForgeCaps の NBT からしか復元されない。

(2) tickCounter が保存されない件は事実だが、害はない。使われているのは ModEvents.java:203-204 の加算と :426-428 の `ticks % Config.BLOOD_DAMAGE_INTERVAL == 0` だけ。0 から数え直しても、出血ダメージの周期の位相がずれるだけで、仕様の破綻は起きない。

(3) キーが無い時の既定値が初期値と食い違う件は、コード上は事実(getFloat/getBoolean はキーが無いと 0/false を返す)。ただし現状のコードでは到達しない。前提: Forge 1.20.1 の CapabilityDispatcher.deserializeNBT は、ケイパビリティ名のキーが ForgeCaps にある時だけ PlayerMedicalDataProvider.deserializeNBT(:46-48)を呼ぶ。新規プレイヤーや、この MOD を入れる前のプレイヤーデータではロード自体が走らず、フィールド初期値(100/true)が残る。save は 14 キーを毎回すべて書く(:203-218)ので、現行版で保存したデータにキーの欠けは生じない。報告の言う最悪シナリオ(血液量 0 → kill)も、handleCardiacArrest(ModEvents.java:340-351)の kill が heartStatus != NORMAL の時だけなので、heartStatus キーも欠けて 0=NORMAL に読まれる状況では起きない。isConscious=false も、handleConsciousnessState(:227-245)が次の tick で正しい値に直す。古い版のデータで一部キーだけが欠けるかは git 履歴を見ていないため未確認で、仮定の話にとどまる。

以上から、real と言えるのは (1) の「再ログインで点滴が通知なしに外れる」だけで、重大度は LOW が妥当。

### 修正方針
優先して直すのは点滴の扱いだけでよい。方針は次の 2 案から選ぶ。
(a) 接続は保存しない前提にする。onPlayerLoggedOut(ModEvents.java:250)で setTransfusingFromStandPos(Optional.empty()) を呼び、再ログイン時のメッセージ等で切れたことを知らせる。
(b) 接続を保存する。saveNBTData で NbtUtils.writeBlockPos を使って "transfusingFromStandPos" を書き、load では contains で確認してから読む。ディメンションが変わった時の誤接続は、handleIVTransfusion が serverPlayer.level() 上で IVStandBlockEntity かどうかと距離を見ているため、ほぼ防げる(必要ならディメンションキーも保存する)。
load の contains ガードは、将来キーを追加した時の互換のための防御で、現状のバグ修正ではない。入れるなら、欠けていたキーにはフィールド初期値を使う。tickCounter は保存しなくてよい。

## B47 [LOW] チャンクの再読み込みやワールド再起動で除細動器のクールダウンが消える
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/blockentity/DefibrillatorBlockEntity.java:142 (gameplay)

### 状況
前提: Forge 1.20.1 では、チャンク読み込み時の BlockEntity.loadStatic→load() は setLevel より前に呼ばれ、level==null。saveAdditional(125-130) は残り tick を相対値で保存しているが、load は level!=null を条件にしているため、チャンク読み込みでは常に cooldownEndTick=0 になる。ショック直後にチャンクを離れて戻る、またはサーバーを再起動すると、DEFIBRILLATOR_COOLDOWN を無視してすぐ再充電できる。

### 検証
報告どおりの現象は起きる。ただし既定値では影響が小さいため LOW に下げる。

- 保存側: DefibrillatorBlockEntity.java:125-130 は残り tick（相対値）だけを保存し、絶対値の cooldownEndTick は保存していない。
- 復元側: load の 141-146 行は `remainingTicks > 0 && level != null` のときだけ復元し、それ以外は cooldownEndTick=0 にする。
- 前提にした Minecraft/Forge 1.20.1 の挙動: サーバーのチャンク読み込みでは BlockEntity.loadStatic(pos, state, tag) が BE を生成してすぐ load(tag) を呼ぶ。LevelChunk.setBlockEntity が setLevel するのはその後なので、load の時点では level==null になる。これはチャンクを読み直すとき（チャンクのアンロード後の再読み込み、サーバー再起動）に必ず通る経路。
- このため保存された残り tick は捨てられ、cooldownEndTick=0 になる。
- クールダウンを判定しているのはサーバー側の DefibrillatorBlock.java:153 `be.isOnCooldown()` だけ。ほかの防御は無い。DefibrillatorItem.java:29 はプレイヤーのアイテムクールダウンで、除細動器ブロックのクールダウンとは別物。
- クールダウンの開始点は ServerboundQTETResultPacket.java:60 と resetChargeAndStartCooldown の 2 か所。
- 以上より、ショック後にチャンクを読み直すと isOnCooldown()=false になり、DEFIBRILLATOR_COOLDOWN を無視してすぐ充電を始められる。
- 補足: クライアント側は onDataPacket/handleUpdateTag の時点で level が入っているので復元される（表示だけの問題）。

重大度の判断:
- 既定のクールダウンは 30 秒（Config.java:163-164、範囲 1-3600）。この不具合を使うには、その間にチャンクのアンロードと再読み込み、またはサーバー再起動が必要。
- チャンクのアンロードには、プレイヤーが描画距離の外へ離れてチケットが外れる必要がある。通常の救命の流れでは実用的な抜け道になりにくい。
- 設定で長いクールダウンにしている場合は影響が大きくなる。

### 修正方針
いちばん単純で確実な直し方は、絶対値の cooldownEndTick をそのまま保存・復元すること。
- saveAdditional: `pTag.putLong("CooldownEndTick", cooldownEndTick)`
- load: `cooldownEndTick = pTag.getLong("CooldownEndTick")`（level は不要）

この方法で問題ない理由:
- Level#getGameTime はワールド単位で level.dat に保存され、再起動後も続きから進む。
- クライアントのゲーム時刻は ClientboundSetTimePacket でサーバーと同期している。
そのため相対値に変換する必要がなく、load 時点で level が null でも成り立つ。

報告にある「setLevel/onLoad で変換する」方式でも直せるが、一時フィールドが増えるうえ、クライアントの onDataPacket 経路との整合も取る必要があり複雑になる。古い NBT（CooldownRemainingTicks）からの移行は、クールダウンが最大でも 1 時間なので無視してよい。

## B48 [LOW] 除細動器を壊した後に残った電極を使うと getValue(CHARGED) が例外になる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/item/ElectrodeItem.java:208 (gameplay)

### 状況
前提: 1.20.1 の StateHolder.getValue は、存在しないプロパティに対して IllegalArgumentException を投げる。DefibrillatorBlock には onRemove がなく、取り出した電極の DefibrillatorPos が残ったままになる。ブロックを壊す（または別のブロックに置き換える）と、その電極で患者を右クリックしたとき getBlockState(defibrillatorPos) が air などになり、.getValue(DefibrillatorBlock.CHARGED) が例外を投げる。サーバーのパケット処理で例外がログに出て操作は失敗する（ログだけで済むかサーバーが落ちるかはパケット処理の例外伝播次第なので要確認）。

### 検証
バグ自体は実在する。ただし報告の行番号が違い、重大度は下げるべき。

[場所の訂正] ElectrodeItem.java は 50 行しかなく、208 行目は存在しない（208 行目に当たるのは DefibrillatorBlock.java の getShape の末尾）。問題の箇所は ElectrodeItem.java:41 の `pPlayer.level().getBlockState(defibrillatorPos).getValue(DefibrillatorBlock.CHARGED)` である。

[確認した事実]
- 電極を取り出す処理（DefibrillatorBlock.java:115-126）は、NBT に BlockPos だけを書き込む（:120）。ディメンションは保存していない。
- DefibrillatorBlock（DefibrillatorBlock.java:36-209）には onRemove の override がなく、ブロックが消えても渡した電極は何も処理されない。ElectrodeItem にも inventoryTick など、参照先が残っているか確かめる処理はない。
- ElectrodeItem.java:34-41 では、サーバー側で、参照先のブロックの種類も BlockEntity も確かめずに CHARGED を直接読んでいる。
- 同じ NBT を読む他の箇所は安全に書かれている。ServerboundQTETResultPacket.java:55-58 と ModEvents.java:144-145 は `instanceof DefibrillatorBlockEntity` を確認している。危ないのは ElectrodeItem だけである。

[到達する手順] 電極を取り出す → 除細動器を壊す（または別のブロックに置き換える）→ その電極で医師権限のあるプレイヤーが別のプレイヤーを右クリックする。もう一つ、報告にない経路がある。電極を持ったままネザーなど別のディメンションへ行くと、同じ座標にある別のブロック（多くは空気）を読むため、除細動器を壊さなくても起きる。

[Forge 1.20.1 の前提] 存在しないプロパティを指定すると、StateHolder.getValue は IllegalArgumentException("Cannot get property ... as it does not exist in ...") を投げる。interactLivingEntity は ServerboundInteractPacket の処理の中で Player.interactOn から呼ばれる。このパケットは PacketUtils.ensureRunningOnSameThread を通ってサーバースレッドで処理され、そこで投げられた例外は BlockableEventLoop.doRunTask の catch(Exception) で FATAL ログとして記録されるだけになる。サーバーは落ちず、ほかのプレイヤーにも影響しない。一方クライアント側は isClientSide の判定（:34）で処理を飛ばすので、クライアントでは例外が出ない。

[実害] クリックするたびにサーバーログにスタックトレースが出る。電極は何のメッセージも出さずに使えなくなる。電極は捨てれば回収できる（ModEvents.java:144 で BE がないと分かれば、普通のアイテムとしてドロップされる）。クラッシュ、データ破損、権限の迂回はない。どのみち使えない電極が「例外で失敗する」か「正しく拒否される」かの違いなので、重大度は LOW とした。

### 修正方針
ElectrodeItem.java:41 で、BlockState を取得したらまず `state.is(Medicalsystemcore.DEFIBRILLATOR_BLOCK.get())` を確認する（または ServerboundQTETResultPacket と同じように `getBlockEntity(pos) instanceof DefibrillatorBlockEntity` を確認する）。除細動器でなければ「除細動器が見つかりません」と伝えて FAIL を返し、電極は無効なものとして扱う（例えば手持ちから消す）。このとき、エラーを飲み込む try-catch で済ませてはいけない。
ディメンションが違う場合も同じ不具合になるので、取り出す時（DefibrillatorBlock.java:120）にディメンションの ResourceKey も NBT に保存し、使う時に一致を確認する。
除細動器を壊した時に、各プレイヤーの持ち物にある電極を探して回収するのは難しい。参照先がまだあるかは使う時に確かめる方式で十分である。onRemove の追加は、ブロック側の状態を片付けるためのものとしてなら検討してよい。

## B49 [LOW] DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER と DEFIBRILLATOR_TRANSITION_CHANCE が未使用（ハードコード値が使われる）
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundQTETResultPacket.java:90 (gameplay)

### 状況
Config:52-53/167-170 で定義・読み込みしているのに、実際に使われる蘇生判定は VF 倍率 2(90行) と状態移行確率 0.4(108,111行) を直書きしている。設定ファイルでこれらの値を変えても挙動は変わらない。同じく BLEEDING_RECOVERY_PLATELET_MULTIPLIER(Config:123) もどこからも参照されていない。Config 値を参照しているように見える DefibrillatorItem は Medicalsystemcore に登録されておらず、デッドコードになっている。

### 検証
報告どおりで、実際に起きることを確認した。
- 定義と読み込み: Config.java:52-53 と 167-170（既定値は vf_success_multiplier=2.0、transition_chance=0.4）で定義し、336-337 で static フィールドへ代入している。
- 参照箇所: Grep で src/main/java 全体を探したが、Config.DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER と Config.DEFIBRILLATOR_TRANSITION_CHANCE を読むコードは Config.java の外に一つもない。
- 実際の判定経路: 使われる蘇生判定は ServerboundQTETResultPacket.handle で、VF のとき倍率 2 を直書き（ServerboundQTETResultPacket.java:90）、失敗時の状態移行確率 0.4 も直書き（同 108 行と 111 行）。このパケットは ModPackets.java:50-53 に登録され、ClientQTEManager.java:56 から送られるので、現役の経路である。
- BLEEDING_RECOVERY_PLATELET_MULTIPLIER: Config.java:123/249/318 以外に参照がない。出血回復の計算（ModEvents.java:375-376）が使うのは BASE_RATE と BANDAGE_MULTIPLIER だけで、Java コード内に血小板（platelet）の効果自体が実装されていない。
- DefibrillatorItem: Medicalsystemcore.java などに `new DefibrillatorItem` は無く、どこにも登録されていない。デッドコードという指摘は正しい。ただし報告の「Config 値を参照しているように見える」は不正確で、DefibrillatorItem.java も 2 と 0.4（61-79 行付近）、クールダウン 30*20 を直書きしており、Config は参照していない。
重大度の付け直し: 既定値（2.0 / 0.4）が直書き値と同じなので、既定設定のままなら挙動は変わらない。実害が出るのは、管理者が設定ファイルでこれらの値を変えたのに反映されない場合だけである。そのため MEDIUM から LOW に下げた。ただし今回のユーザー依頼は「設定値を動的に変えられるコマンドと管理パネル」なので、その実装ではこの 3 項目が「変えても効かない設定」になる。実装前に直すべき前提の不具合である。

### 修正方針
1. ServerboundQTETResultPacket.java:90 の `chance * 2` を `chance * Config.DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER` に置き換える。
2. 同 108 行と 111 行の `0.4` を `Config.DEFIBRILLATOR_TRANSITION_CHANCE` に置き換える。
3. BLEEDING_RECOVERY_PLATELET_MULTIPLIER は、対応する血小板効果が存在しない。効果を実装する予定が無ければ設定を削除する。実装するなら、ModEvents.java:375 付近の回復レート計算に組み込む。どちらにするかはユーザーの判断事項。
4. 未登録の DefibrillatorItem は Config も使っておらず、ロジックが QTE パケットと重複している。削除を検討する（Config を参照するよう直す必要はない）。
5. 管理パネルから設定を動的に変える場合、static フィールドへのキャッシュ（Config.java:316-337 の代入）が値の変更時にも再実行されるかを合わせて確認する。

## B50 [LOW] 奈落に落ちると心停止＋ダメージ無効で救助不能のまま 20 分間落下し続ける
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:81 (gameplay)

### 状況
前提: 1.20.1 の奈落ダメージは fell_out_of_world（GENERIC_KILL ではない）。NORMAL のプレイヤーが奈落で死亡すると、onLivingDeath(61-64) が死亡をキャンセルし、HP0.1・CARDIAC_ARREST・damageImmune=true にする。以後の奈落ダメージは onLivingHurt(81) が全てキャンセルするので、プレイヤーは Y 座標が下がり続けたまま、心停止タイマー（CARDIAC_ARREST_DEATH_TIME=20 分）が切れるまで生き続ける。救助は物理的に不可能。同様に、NORMAL の相手への /kill（GENERIC_KILL）も最初の 1 回は心停止化され、即死しない。

### 検証
仕組みは報告どおり実在するが、実害は報告より小さい。

【実在する部分】
- ModEvents.java:61-64: 死因を見ずに、NORMAL のプレイヤーの死亡を一律でキャンセルし、HP0.1・CARDIAC_ARREST・damageImmune=true にしている。
- ModEvents.java:81: damageImmune 中は GENERIC_KILL 以外のダメージをすべて LivingHurtEvent でキャンセルする。
- 前提（1.20.1 の jar で確認）: 奈落ダメージの登録 ID は minecraft:out_of_world（報告の fell_out_of_world は 1.20.5 以降の名前で、1.20.1 の Mojmap 定数は DamageTypes.FELL_OUT_OF_WORLD）。GENERIC_KILL とは別物で、LivingEntity.onBelowWorld が毎 tick hurt(fellOutOfWorld, 4) を呼ぶ。この hurt は 81 行で全部キャンセルされる。
- 強制死亡は handleCardiacArrest（ModEvents.java:347-359）にしかない。条件は「経過が CARDIAC_ARREST_DEATH_TIME を超える（既定 20 分、Config.java:153-154）」「血液量 0」「蘇生確率 0（既定で毎分 5% 減少）」のいずれか。それまでプレイヤーは奈落を落ち続け、救助者はたどり着けない。
- /kill: LivingEntity.kill() は hurt(genericKill, MAX) を呼ぶ。対象が NORMAL なら 81 行を通過して死亡し、61 行で心停止に置き換わる。2 回目の /kill は 81 行の例外と 45-58 行の分岐で死亡が成立する。つまり 1 回目は即死しない点も事実。

【報告が誇張している部分（重大度を下げる根拠）】
- ギブアップ手段がある。ClientGiveUpManager.java:27-57 で、意識不明中に Shift を GIVE_UP_DURATION 秒（既定 5 秒、Config.java:211-212）押し続けると ServerboundGiveUpPacket を送る。心停止中は handleConsciousnessState（ModEvents.java:233-241）で意識不明になるので、条件は満たされる。
- 既定は give_up_enabled=true（Config.java:209-210）、transport_mode=true（Config.java:213-214）。この組み合わせでは ServerboundGiveUpPacket.java:78-81 が搬送先へテレポートさせ、本人が奈落から出られる。transport_mode=false の場合も同 102-111 で damageImmune を解除し kill() して死亡が成立する。
- したがって「20 分間抜け出せない」状態になるのは、本人が操作しない（AFK や仕様を知らない）か、管理者が give_up_enabled=false にしている場合に限られる。それでも最長 20 分（設定により最大 120 分）で強制死亡に至るため、永久に詰むことはない。
- /kill が 1 回で死なない件は、81 行で GENERIC_KILL をわざわざ免疫の例外にしている設計から見て意図からのずれと考えられる。ただし 2 回打てば死ぬので、実害は小さい。

以上から、不具合としては実在し、重大度は LOW（救助不能な状態が続くのはユーザー体験・ゲームバランス上の問題で、データ破損や回復不能には至らない）とする。

### 修正方針
ModEvents.onLivingDeath（42-75 行）の先頭、61 行でキャンセルする前に死因を判定する。救助不能な死因ならキャンセルせず、そのまま死なせる。個別の DamageTypes を列挙するより、バニラのタグ DamageTypeTags.BYPASSES_INVULNERABILITY で判定するのが良い。1.20.1 の jar では、このタグの中身は minecraft:out_of_world と minecraft:generic_kill のちょうど 2 つで、奈落と /kill の両方を 1 か所で扱える。

例: `if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { 心停止中なら 46-57 行と同じ後始末（NORMAL に戻す・damageImmune 解除・姿勢解除・同期）をして return; }`

この状態は onLivingDeath の中で解消する。81 行の免疫例外に out_of_world を追加するだけでは足りない。足しても、NORMAL の場合は 61 行で結局心停止に置き換わってしまう。

ギブアップの kill()（ServerboundGiveUpPacket.java:111）や handleCardiacArrest の kill()（ModEvents.java:349, 358）は、heartStatus が NORMAL 以外の状態で呼ばれ、既存の 45-58 行の経路で死亡が成立する。上の変更を入れても、これらの動作は変わらない。

## B51 [LOW] 止血剤の Config は『%/秒』と説明されているが、実装は出血速度からの絶対値減算
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/effect/FibrinogenEffect.java:21 (gameplay)

### 状況
Config:131-134 のコメントは『出血速度減少率（%/秒）』。しかし FibrinogenEffect:21-29 と TranexamicAcidEffect:21-29 は、bleedingSpeed から毎秒 2.0（絶対値）を引いている。出血速度 9 の重傷でも 1 の軽傷でも、同じ量しか減らない。設定値を『割合』のつもりで調整すると想定と違う結果になる。

### 検証
報告どおり、説明と実装が食い違っていることを確認した。Config.java:131-134 では、fibrinogen_bleeding_reduction_rate と tranexamic_acid_bleeding_reduction_rate の説明が「出血速度減少率（%/秒）」になっている。値の範囲（defineInRange 0.0〜100.0、既定値 2.0）も割合を思わせる。しかし Config.java:321-322 でそのまま static 値に読み込まれ、FibrinogenEffect.java:21-29 と TranexamicAcidEffect.java:21-29 では reductionPerSecond/20 を絶対量として 1 tick ごとに引いている（newSpeed = max(0, currentSpeed - perTick*(amp+1))）。currentSpeed は掛け算に使われていないので、出血速度が 9 でも 1 でも、1 秒あたり 2.0×(amplifier+1) だけ減る。他の場所で比率に直したり上書きしたりする処理は無い。Grep で確認した限り、setBleedingSpeed を呼ぶのは ModEvents.java:124（ダメージによる加算）、:381（包帯による絶対量減算）、PlayerMedicalData.java:121（0 未満を切り捨てるだけ）だけである。効果は点滴台経由の ModEvents.java:329-332 で 40tick 付与され、isDurationEffectTick=true（Forge 1.20.1 では毎 tick applyEffectTick が呼ばれる前提）なので、毎 tick この絶対量減算が走る。ただし、ゲームの動作が壊れたり落ちたりするわけではない。設定値を「割合」と誤解して調整した場合に想定と違う効き方になる、という説明と実装の不一致にとどまる。仕様書（*.md）に止血剤の定義は無く、どちらが本来の意図かは確定できない。そのため重大度は LOW のままとする。

### 修正方針
まず Config の説明文を実装に合わせ、「出血速度の減少量（/秒、エフェクトレベルごと）」のような書き方に直すのが最小の修正になる。周りの出血関連設定も同じ書き方をしているので揃えられる。BLEEDING_SPEED_INCREASE_* は「増加量」、ModEvents.java:375-381 の包帯回復も絶対量で減らしている。上限 100.0 も、絶対量として妥当な上限に見直すとよい。本当に割合で減らす仕様にしたいなら、両エフェクトの 22-28 行を perTick = currentSpeed * (rate/100) / 20 * (amp+1) に変える必要がある。ただしゲームバランスが変わるので、その場合はユーザーの承認を得てから行うこと。

## B52 [LOW] 心停止に入った最初の tick に蘇生確率が 1 分ぶん減る
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:353 (gameplay)

### 状況
handleCardiacArrest は arrestTimer（加算前の値）が 0 の tick で arrestTimer % 1200 == 0 が真になり、心停止直後に RESUSCITATION_CHANCE_DECAY_RATE(5%) を差し引く。設定の『%/分』に対して、最初の 1 分を待たずに 95% から始まる。

### 検証
報告どおりの挙動を確認した。前提として Forge 1.20.1 の PlayerTickEvent は END フェーズでサーバー側 1 tick に 1 回呼ばれる（ModEvents.java:194-206）。
- ModEvents.java:344-345: arrestTimer には加算前の値を読み、その後で +1 を保存している。353 行の剰余判定は加算前の値 arrestTimer を使う。
- 最初に心停止する経路は onLivingDeath（ModEvents.java:61-64）で、ここは CARDIAC_ARREST をセットするだけでタイマーをリセットしない。タイマーの既定値は 0（PlayerMedicalData.java:20）。蘇生に成功すると 0 に戻る（DefibrillatorItem.java:89、ServerboundQTETResultPacket.java:97）。
- そのため心停止に入った次の tick では arrestTimer==0 になり、0 % 1200 == 0 が真になる。ここで RESUSCITATION_CHANCE_DECAY_RATE（説明文は「%/分」、Config.java:151）が即座に差し引かれ、100% が 95% になる。以後の減衰は 1200, 2400, ... tick に起きる。つまり経過分数 n に対して減衰は n+1 回になり、常に 1 回ぶん多い。
- 防いでいる箇所はない。setResuscitationChance は 0〜100 に clamp するだけ（PlayerMedicalData.java:141-144）。
実害は、蘇生判定に使う確率が常に 1 段（既定 5%）低いことと、chance<=0 による死亡（ModEvents.java:357-358）が 1 分早まること。致命的な破綻ではないので LOW が妥当。
補足として、VF を経由する場合も同じタイマーが VF 中に加算され続ける（343 行は NORMAL 以外すべてで加算）。この場合、CARDIAC_ARREST での減衰のタイミングは心停止開始からではなく VF 開始時刻を起点に揃う。本報告の範囲外だが関連する挙動として記録する。

### 修正方針
ModEvents.java:353 の判定を、加算後の値で行うよう変える。例: `int elapsed = arrestTimer + 1; if (currentStatus == HeartStatus.CARDIAC_ARREST && elapsed % (20 * 60) == 0)`。報告の案（`arrestTimer > 0 && arrestTimer % 1200 == 0`）でも効果は同じ。設定の「%/分」に合わせ、心停止から満 1 分ごとに減衰させる。

## B53 [LOW] 出血エフェクトのレベルが下がっても表示が最大 40tick 遅れ、出血速度が 256 以上だと byte で桁あふれする
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:416 (gameplay)

### 状況
前提: 1.20.1 の MobEffectInstance.update は、amplifier が低い新しいインスタンスを hiddenEffect に回す。包帯などで ceil(bleedingSpeed) が下がっても、表示上のレベルは既存エフェクト（duration40）が切れるまで古いまま残る。bleedingSpeed には上限がない(ModEvents:123)ので 256 以上になり得て、エフェクト同期パケットの amplifier(byte) があふれ、表示レベルが小さくなる。

### 検証
報告の 2 点はどちらもコード上で確認できた。ただし出るのは表示だけで、桁あふれが起きる閾値は 256 ではなく 128。
(1) 表示の遅れ: ModEvents.java:209 で毎 tick（ServerPlayer の END フェーズ）handleBleedingEffect を呼び、ModEvents.java:415-418 で duration=40、amplifier=ceil(bleedingSpeed)-1 のインスタンスを addEffect している。前提は 1.20.1 バニラの MobEffectInstance.update(other) の挙動。other.amplifier が既存より低く、既存の duration（毎 tick 減って 40 未満）が other より短いと isShorterDurationThan が真になり、新インスタンスは hiddenEffect に回る（既存の amplifier と duration はそのまま）。既存インスタンスは以後延長されないので最大約 40tick で切れ、そこで tick() が hiddenEffect を引き継ぐ。止血剤（TranexamicAcidEffect:29 / FibrinogenEffect:29）や包帯の回復（ModEvents.java:364-383）で出血速度が下がった場合、表示レベルは最大 2 秒古いまま残る。
(2) 上限なし: PlayerMedicalData.java:122 は下限 0 で切るだけで上限はない。ModEvents.java:123 で被ダメージのたびに加算され、増加量の設定は 1 回あたり最大 100（Config.java:111-117）。amplifier はバニラの ClientboundUpdateMobEffectPacket で byte として送られ、クライアントは符号付きの readByte で読む。NBT 保存も putByte。このため実際に化けるのは 256 以上ではなく、ceil(speed)-1 >= 128、つまり bleedingSpeed > 128 からで、負のレベルとして表示される。既定値（1 回最大 +9）では 15 回程度の大ダメージが要り、その速度では失血が速い（ModEvents.java:393、速度/60 per 秒）。到達は可能だが稀。
実害の範囲: BleedingEffect.java は applyEffectTick などを持たない空の MobEffect で、BLEEDING_EFFECT を読み返すコードもない（grep で ModEvents:417,420 だけ）。出血の計算はすべて capability の bleedingSpeed で行っている。したがってゲームロジックへの影響はなく、アイコンとレベル表示がずれるだけ。重大度は LOW が妥当。

### 修正方針
表示を出血速度に合わせる処理を handleBleedingEffect（ModEvents.java:412-422）の 1 か所に置く。
- 今のエフェクトを getEffect で取得し、amplifier が目標値と違うときは removeEffect してから addEffect する（hiddenEffect も一緒に消える）。同じときは今どおり addEffect で duration を延長する。
- amplifier は Math.min(level, 128) - 1 などで 127 以下に収め、表示用の値として扱う。bleedingSpeed 自体に上限を設けるかはゲームデザインの判断なので、表示の修正とは分ける（setBleedingSpeed に上限を足すと新しい仕様値になるため、ユーザーの承認が要る）。

## B54 [LOW] 包帯（自分に使用）とチューブがクリエイティブでも消費される
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/item/BandageItem.java:418 (gameplay)

### 状況
BandageItem.finishUsingItem:418 と TubeItem.interactLivingEntity:199 は、クリエイティブかどうかを確認せずに shrink(1) する。クリエイティブモードの医療担当者でもアイテムが減る（バニラの慣習と違う）。

### 検証
報告の行番号は間違っている。HEAD 時点の実際の位置は BandageItem.java:45 と TubeItem.java:97 で、418 行目・199 行目ではない。中身を確かめた結果、包帯は本物のバグ、チューブは誤報だった。

(1) 包帯（自分に使用）は本物。BandageItem.finishUsingItem（BandageItem.java:40-47）はクリエイティブかどうかに関係なく、45 行目で無条件に pStack.shrink(1) する。前提として、Forge 1.20.1 の LivingEntity.completeUsingItem は this.useItem.finishUsingItem(...) を実物のスタックに対して呼び、クリエイティブ用の保護をしていない。バニラの食料やポーションが減らないのは、各アイテムの finishUsingItem/eat の中で getAbilities().instabuild を見ているからである。super.finishUsingItem（Item.finishUsingItem）は食べられないアイテムならスタックをそのまま返すだけなので、ここでも防がれない。よってクリエイティブでも包帯は 1 個減る。

(2) チューブ（TubeItem.interactLivingEntity:97）は誤報。前提として、1.20.1 の Player.interactOn では、対象エンティティの interact が PASS を返したとき（プレイヤー対象はこのケース）、abilities.instabuild なら itemstack を事前に作ったコピー（itemstack1）に差し替えてから itemstack.interactLivingEntity を呼ぶ。そのため shrink(1) はコピーにしか効かず、手に持っているスタックは減らない。バニラ側ですでに防がれている。

付随する別の不具合: 同じ理由で、96 行目の nbt.remove(TAG_START_POS) もコピーの NBT にしか効かない。結果としてクリエイティブでは、手持ちのチューブに StartPos が残ったままになる（接続後も始点が消えない）。実害はごく小さい。

参考: 他人への包帯使用は別経路（ClientHealingManager.java:59 など）で減らしており、今回の報告の対象外。

重大度は LOW のまま。クリエイティブで包帯が減るだけで、ゲームの進行やデータには影響しない。

### 修正方針
BandageItem.finishUsingItem（45 行目）は、`!(pLivingEntity instanceof Player p && p.getAbilities().instabuild)` のときだけ pStack.shrink(1) するよう変える（バニラの LivingEntity.eat と同じやり方）。TubeItem:97 の shrink は直さなくてよい（Player.interactOn がクリエイティブではコピーに差し替えて呼ぶため）。直すなら別件として、クリエイティブで StartPos が手持ちスタックから消えない点を扱う（例: 接続が完了したら pPlayer.getItemInHand(pHand) 側のタグも消す）。ただし実害が小さいので、優先度は低い。

## B55 [LOW] 点滴の接続先が保存されず、再ログインで黙って切断される
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/capability/PlayerMedicalData.java:219 (gameplay)

### 状況
saveNBTData で transfusingFromStandPos を保存していないため（コメントで省略と明記）、再ログインやサーバー再起動の後、点滴は『点滴が外れました』の通知もなく切断される。医療担当者は気付かずに放置しうる。

### 検証
報告どおりの状況が起きることをコードで確認した。
- 点滴の接続先は PlayerMedicalData.java:22 の `Optional<BlockPos> blockPos` だけが持っている。saveNBTData（PlayerMedicalData.java:202-220）はこのフィールドを書き出しておらず、219 行目に「BlockPosの保存は省略（複雑になるため）」とある。loadNBTData（226-243）も復元しないので、新しく作られた capability では初期値の Optional.empty() のまま残る。
- capability の永続化は PlayerMedicalDataProvider.java:39-48 の serializeNBT/deserializeNBT から saveNBTData/loadNBTData を呼ぶ経路しかない。前提: Forge 1.20.1 ではエンティティの capability がプレイヤーデータと一緒にこの経路で保存・読み込みされ、ログアウトや再起動のあとは新しい ServerPlayer に deserializeNBT で読み戻される。
- 点滴台の側にも接続相手の記録は無い。IVStandBlockEntity.java:142-152 が保存するのはアイテムの中身だけで、患者の UUID などは持たない。チューブ側の情報も、接続した時点で TubeItem.java:96-97 が NBT を消し、アイテム自体も消費している。このため接続を復元できる情報はどこにも残らない。
- 「点滴が外れました」の通知は ModEvents.java:317-319 にしかない。これは handleIVTransfusion の `getTransfusingFromStandPos().ifPresent(...)` の中（258 行目）にあるので、再ログイン後に接続先が empty になっていると通知の分岐まで進まない。その結果、通知もなく点滴が止まる。
- 復元する処理は他の場所にも無い。Grep で調べた範囲では、setTransfusingFromStandPos を呼んでいるのは TubeItem.java:85 と 121、ModEvents.java:318 だけで、接続先をクライアントへ同期したりチューブを描画したりする処理も無い。
実害は、意図的な再ログインかサーバー再起動のあとに輸液が黙って止まることに限られる。医療担当者がチューブをもう一度つなげば元に戻り、データ破損やクラッシュは起きない。このため重大度は LOW のままとする。
補足（報告の範囲外）: PlayerEvent.Clone のハンドラも見当たらない（Grep で 0 件）。死亡してリスポーンしたときに医療データ全体がどう引き継がれるかは別の論点なので、ここでは判定しない。

### 修正方針
saveNBTData で `blockPos.ifPresent(p -> nbt.put("transfusingFromStandPos", NbtUtils.writeBlockPos(p)))` のように保存する。loadNBTData では `nbt.contains("transfusingFromStandPos", Tag.TAG_COMPOUND)` のときだけ `Optional.of(NbtUtils.readBlockPos(...))` で復元し、無ければ Optional.empty() にする。復元したあとに点滴台が無くなっていたり範囲外だったりした場合は、既存の ModEvents.java:317-319 の分岐が「点滴が外れました」を通知して切断するので、黙って切れることも無くなる。
ディメンションも保存しておくのが望ましい。handleIVTransfusion（ModEvents.java:259）は serverPlayer.level() でしか位置を引かないので、別のディメンションの同じ座標に点滴台があると、そちらにつながってしまう。ただしこれは既に今の実装にある別の問題である。対応するなら、ResourceKey<Level> も一緒に保存し、handleIVTransfusion で一致を確認する変更を、同じ修正の中で行うのが妥当である。

## B56 [LOW] noradrenaline_pack のアイテムモデルが存在せず、色の登録もない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/Medicalsystemcore.java:144 (content-cmd)

### 状況
NORADRENALINE_PACK は登録されてクリエイティブタブにも出ている(:144,:193)。しかし models/item/noradrenaline_pack.json がないため、インベントリでは紫黒の missing model になる。ItemColors の登録(:292-296)にも NORADRENALINE_PACK がないので、モデルを追加してもオーバーレイに色が付かない。

### 検証
報告どおり実在する。(1) Medicalsystemcore.java:144 で noradrenaline_pack を登録し、:193 でクリエイティブタブに追加、:283 で fluid_level の ItemProperties も登録している。しかし src/main/resources/assets/medicalsystemcore/models/item/ には adrenaline/blood/fibrinogen/glucose/tranexamic_acid_pack.json しかなく、noradrenaline_pack.json はない。リポジトリ全体を find で探しても noradren* という名前のファイルは 1 つもない。datagen の出力先 src/generated/resources（build.gradle:107,113 で resources の srcDir になっている）も空なので、生成モデルで補われることもない。Forge 1.20.1 では、モデル JSON がない登録済みアイテムは missing model（紫黒）で描画され、ログにモデル読み込みの警告が出る。(2) ItemColors の登録（:292-296）は BLOOD/ADRENALINE/TRANEXAMIC/FIBRINOGEN/GLUCOSE の 5 種だけで、NORADRENALINE_PACK がない。PackColor.java:14-18 は tintIndex==1 のときだけ色を返す。他のパックのモデルは layer1=pack_overlay で、item/generated 親では layer1 が tintindex 1 になる。このため、モデルだけ追加しても色の登録がなければオーバーレイは無着色（白）のままになる。重大度について: 機能面は影響がない。lang（en_us:23, ja_jp:25）とタグ drug_packs.json:6 は揃っており、薬効処理も ModEvents.java:335 で実装済みなので、使用・点滴の動作は正常。実害は見た目（missing model の表示と色の区別ができないこと）に限られるため、MEDIUM から LOW に下げる。

### 修正方針
src/main/resources/assets/medicalsystemcore/models/item/noradrenaline_pack.json を、adrenaline_pack.json と同じ内容で追加する（parent: item/generated、layer0=pack_base、layer1=pack_overlay、fluid_level 0.0〜0.9 の overrides で pack/pack_level_N を参照）。さらに Medicalsystemcore.java:296 付近に Minecraft.getInstance().getItemColors().register(new PackColor(<他と区別できる色>), NORADRENALINE_PACK.get()); を追加する。色の値は設計判断になる（既存の 5 色と重ならない色、例えば紫系）。補足: getItemColors() を FMLClientSetupEvent で直接呼ぶ既存のやり方より、RegisterColorHandlersEvent.Item を使う方が Forge 1.20.1 では正式な手順。ただし、それは既存の 5 種も含めた別の改善になる。

## B57 [LOW] どの除細動器の電極でも収納できてしまい、元の除細動器が永久に『使用中』になる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/DefibrillatorBlock.java:106 (content-cmd)

### 状況
除細動器 A から電極を取り出し（A.arePadsTaken=true）、除細動器 B の上部をクリックすると、B は DefibrillatorPos を確認せずに電極を消費して arePadsTaken=false にする(:106-108)。A は true のまま残るので、ブロックを壊して置き直すまで『電極は既に使用中です』となり使えない。パックが揃っている B に返した場合や、クリエイティブタブ由来で NBT のない電極を返した場合も同じく電極が消費される。電極をなくした場合（死亡・消滅）も A は復帰できない。

### 検証
報告の中心部分は再現する。DefibrillatorBlock.java:106 の収納分岐は `heldItem.is(ELECTRODE)` だけを見ている。電極 NBT の DefibrillatorPos（:120 で書き込む）とも、自分の arePadsTaken とも照合せずに、:107 で arePadsTaken=false、:108 で shrink(1)、:111 で HAS_PADS=true にする。そのため次のことが起きる。
(1) A から取り出した電極を B の上部（hitY>=11/16、:86）で右クリックすると、電極は消える。A.arePadsTaken は true のまま残る（DefibrillatorBlockEntity.java:123/138 で NBT に保存されるので、再ログインしても戻らない）。A を手ぶらで触ると :129-130「電極は既に使用中です」になる。
(2) NBT のない電極（Medicalsystemcore.java:186 でクリエイティブタブに登録）や他機の電極を、電極が出ている B に入れると、B.arePadsTaken=false になる。すると :115 から B がもう 1 本発行でき、電極が増殖する（報告にない追加の影響）。
arePadsTaken=false に戻す経路も確認した。DefibrillatorBlock:107、ModEvents.java:146（ItemTossEvent、電極を Q などで投げ捨てたとき）、DefibrillatorBlockEntity.resetChargeAndStartCooldown:51 の 3 つだけ。resetChargeAndStartCooldown は呼び出し元がない（grep で確認）。ServerboundQTETResultPacket.java:58-60 は resetCharge と startCooldown だけで、パッド状態は戻さない。

反証できた部分は「永久に」という表現。
- ModEvents.java:134-160 には投げ捨てで元の機体を復帰させる経路がある。電極が手元にある間は回復できる。
- loot_tables/blocks/defibrillator_block.json が存在するので、ブロックを壊して置き直せば新しい BE は arePadsTaken=false（:19）に戻る。
- 死亡時について（Forge 1.20.1 の前提）: Inventory.dropAll は ItemTossEvent を発火しない Player.drop(stack, true, false) を使う。そのため死亡ドロップでは復帰しないが、落ちた電極を拾い直して投げれば復帰する。電極が消滅した場合（デスポーン・溶岩など）は、壊して置き直す以外に手段がない。

実害の整理: 医療権限を持つプレイヤーが別の機体に誤って返すと、その機体が一時的に使えなくなる。回復手段は壊して置き直すことで、分かりにくいがある。データ破損やクラッシュは起きない。加えて、軽い電極増殖がある。以上から LOW に下げる。

### 修正方針
DefibrillatorBlock.handlePadInteraction の収納分岐（:106）で、次の 3 点をすべて満たす場合だけ受け付ける。
- heldItem の tag に DefibrillatorPos がある
- NbtUtils.readBlockPos(...) が be.getBlockPos() と一致する
- be.arePadsTaken==true

満たさない場合は「この除細動器の電極ではありません」と表示し、消費しない。これで、誤った機体への返却による A の固定化と、NBT のない電極や他機の電極による増殖の両方を防げる。

紛失時の回復は、既存の 2 経路（ModEvents の投げ捨て復帰と、ブロックの再設置）でとりあえず可能。手ぶら長押しで電極を再発行するなどの回復 UI を新しく作るのは仕様の追加にあたるので、別途承認を取ってから行う。

あわせて、未使用の resetChargeAndStartCooldown が arePadsTaken=false にしている点も整理の対象。ショック後にパッド状態を戻さない設計で正しいのかを確認する。

## B58 [LOW] 除細動器が撤去された後に電極を使うと getValue(CHARGED) が IllegalArgumentException になる
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/item/ElectrodeItem.java:41 (content-cmd)

### 状況
前提: 1.20.1 の StateHolder.getValue は、存在しないプロパティを指定すると IllegalArgumentException を投げる。電極を持ったまま元の除細動器を壊し（空気や別のブロックになる）、電極でプレイヤーを右クリックすると、getBlockState(defibrillatorPos).getValue(DefibrillatorBlock.CHARGED) が例外を投げる。interact パケットの処理が失敗してエラーログが出るだけで、何も起きない。別ディメンションで同じ座標に除細動器があれば、そちらを参照してしまう。

### 検証
報告どおりの状況は起きる。ただし実害は小さいので重大度は LOW に下げる。

- ElectrodeItem.java:38-41: NBT の DefibrillatorPos を読み、`pPlayer.level().getBlockState(defibrillatorPos).getValue(DefibrillatorBlock.CHARGED)` を呼んでいる。この前にブロックの種類やブロックエンティティを確かめる処理はない。
- CHARGED は DefibrillatorBlock 独自のプロパティ（DefibrillatorBlock.java:38）。DefibrillatorBlock.java:55 でしか登録されていない。
- 前提（1.20.1）: StateHolder.getValue は、ステートにないプロパティを渡すと IllegalArgumentException（"Cannot get property ... as it does not exist in ..."）を投げる。空気やほかのブロックのステートには CHARGED がないので、ここで必ず例外になる。

この状態を防ぐ処理はどこにもない。
- DefibrillatorBlock.java 全体に onRemove などの上書きがない。ブロックを壊しても、持ち出された電極の回収も NBT の無効化も行われない。
- 電極は handlePadInteraction（DefibrillatorBlock.java:115-126）で座標だけを入れて渡される。プレイヤーは電極を持ったまま除細動器を壊せる。
- ModEvents.java:138-160（電極を投げ捨てた時の処理）と ServerboundQTETResultPacket.java:55-58 は、`getBlockEntity(...) instanceof DefibrillatorBlockEntity` で安全に確認している。ElectrodeItem だけがこの確認を欠いている。

実際の経路（前提: Forge 1.20.1）:
1. クライアントが ServerboundInteractPacket を送る。
2. サーバーで ServerGamePacketListenerImpl.handleInteract から Player.interactOn、ItemStack.interactLivingEntity と進み、ElectrodeItem.interactLivingEntity:41 で例外が出る。
3. 処理は PacketUtils.ensureRunningOnSameThread 経由のサーバータスクなので、例外は PacketUtils か BlockableEventLoop.doRunTask で捕まり、エラーログに出るだけになる。サーバーは落ちず、そのプレイヤーへの返答もない。報告の「エラーログが出るだけで何も起きない」と合っている。
4. クライアント側は isClientSide なので 41 行目に来ない。クライアントは落ちない。

ディメンションの指摘も正しい。DefibrillatorBlock.java:120 は座標だけを書き込み、ElectrodeItem.java:41 は使用者が今いるディメンション（pPlayer.level()）で引いている。別ディメンションの同じ座標に充電済みの除細動器があると QTE が始まる。その後 ServerboundQTETResultPacket.java:55-60 で、別ディメンション側の除細動器がリセットされ、クールダウンに入る。

LOW とする理由:
- サーバーのクラッシュ、データの破損、悪用につながる経路がない。
- 起きるのは、使えない電極が手元に残ること、クリックのたびにスタックトレースがログに出ること、プレイヤーへの説明が何も出ないこと。
- ディメンションの取り違えは、座標が一致し、しかもそちらが充電済みの時だけ起きる。めったにない。

補足（同じ箇所の別の問題）: クリエイティブタブの電極（Medicalsystemcore.java:186）は NBT を持たない。このため ElectrodeItem.java:36 で何も表示せずに FAIL を返す。

### 修正方針
根本原因は、電極が持つ除細動器の参照が古くなっても確かめずに使っていること。直す箇所は次のとおり。

1. ElectrodeItem.java:41: CHARGED を読む前に、指している除細動器がまだあるか確かめる。ほかの箇所（ModEvents.java:145、ServerboundQTETResultPacket.java:58）と同じく `level.getBlockEntity(pos) instanceof DefibrillatorBlockEntity be` で判定し、充電状態は `be.isCharged()` かそのブロックの `getValue(CHARGED)` で読む。`state.is(Medicalsystemcore.DEFIBRILLATOR_BLOCK.get())` で確かめてもよい。外れた場合は「§c接続先の除細動器が見つかりません」と表示する。電極を消すか NBT を外して、壊れた参照を残さないようにするのが望ましい。
2. ディメンション: DefibrillatorBlock.java:120 で、座標に加えて `level.dimension().location()` を NBT に書き込む。読み取り側の ElectrodeItem:41、ServerboundQTETResultPacket:55、ModEvents:144 では、ディメンションが使用者のものと一致するかを確かめる。電極の NBT の形式が変わるため、3 か所を同時に直すこと。
3. 任意: 必要なら DefibrillatorBlock に onRemove を追加し、取り出し中の電極を無効にする。ただしインベントリ内の電極をたどる仕組みが必要になり、手順 1 の確認だけでも十分に防げる。

## B59 [LOW] StretcherItem.useOn が足パーツだけを設置し、頭パーツを置かない
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/item/StretcherItem.java:112 (content-cmd)

### 状況
前提: 1.20.1 では、メインハンドで RightClickBlock がキャンセルされなかった場合、クライアントは続けてオフハンドでも使用を試みる。担架エンティティを運搬中に、オフハンドに担架アイテムを持ち、メインハンドは空でしゃがみ右クリックする。頭側が塞がっていると StretcherPlacementHandler は『2 ブロック必要』と言って cancel せずに戻る(StretcherPlacementHandler.java:63-66)。そのため OFF_HAND で StretcherItem.useOn が走り、足元 1 マスだけを確認して足パーツのみを設置し(:112)、エンティティを discard する。孤立した足パーツは、頭方向の隣接更新が来た時点で updateShape により AIR になり、onRemove で担架アイテムを 1 個落とす。

### 検証
報告どおりの状況が起きることをコードで確認した。
(1) StretcherPlacementHandler.java:36-40 は MAIN_HAND かつメインハンドが空でしゃがんでいる時だけ処理する。:63-67 で頭側(headPos)が置換不可なら「2ブロック分のスペースが必要」と送って return し、event.setCanceled(true)(:104)には到達しない。エンティティも discard されない。
(2) 前提とした Forge 1.20.1 の挙動: Minecraft.startUseItem は手ごとに MultiPlayerGameMode.useItemOn を呼び、結果が consumesAction でも FAIL でもなければ次の手へ進む。クライアント側の RightClickBlock では本ハンドラが :33 ですぐ return するのでキャンセルされない。メインハンドは空、オフハンドに物があるので、しゃがみ中はブロックの use が飛ばされて PASS になる。そのためオフハンドへ進み、StretcherItem.useOn がクライアントで SUCCESS を返し(:84-85)、OFF_HAND の UseItemOn パケットがサーバーへ送られる。
(3) サーバーでは OFF_HAND のイベントがハンドラの :36 で素通りし、StretcherItem.useOn(:78) が実行される。条件 :99 は「運搬中・メインハンド空・しゃがみ」で、オフハンドに担架アイテムがある場合も満たされる。:104 で足元 1 マスしか確認せず、:110-112 で PART=LOWER(defaultBlockState, StretcherBlock.java:49)の足パーツだけを setBlock する。続いて乗員を移して(:115-128)、エンティティを discard する(:131)。オフハンドに担架を持つことを禁じるコードはない(grep で確認)。
(4) 孤立した足パーツは、頭方向の隣接ブロックが変わると StretcherBlock.updateShape(:249-252)で AIR になる。onRemove(:315-320)は isBeingCollected の既定値 false(StretcherBlockEntity.java:178)によりアイテムを 1 個 popResource する。
実害は小さい: 運搬中のエンティティ 1 個が、足パーツ 1 個を経て最終的にアイテム 1 個として戻るので、増殖も消失もない。孤立パーツはしゃがみ右クリックで正常に回収できる(removeBothBlocks :201 が is(this) を確認している)。主な問題は、見た目と挙動が半分だけの担架が置かれることと、「設置できません」と「設置しました」の矛盾したメッセージが続けて出ることなので LOW が妥当。頭側が空いている通常ケースでは、メインハンドのハンドラが先にエンティティを discard するので、オフハンドの useOn は運搬中の担架を見つけられず何もしない。

### 修正方針
StretcherItem.useOn の設置ブロック(:88-134)を削除して PASS を返し、設置処理を StretcherPlacementHandler に一本化する。仕様上、設置条件は「メインハンドが空」で、担架アイテムを手に持つことは必要ないので、useOn に設置経路を残す意味はない。残す場合は headPos の canBeReplaced も確認したうえで StretcherBlock.placeDoubleBlock を使い、setBeingCollected(false) の初期化もハンドラと同じにする。可能なら共通メソッドに切り出す。あわせて、ハンドラが失敗時(:65)に cancel しないために後続のオフハンド処理へ流れる点も、event.setCanceled(true)(必要なら setCancellationResult(FAIL))で止めることを検討する。

## B60 [LOW] ベッドサイドモニターを右クリックするたびにケーブルが無限に出る
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/HeadsideMonitorBlock.java:60 (content-cmd)

### 状況
コメントには『まだ取り出されていない場合のみ』とあるが、cableTaken の確認も設定もないため、右クリックのたびにケーブルが 1 本ずつ増える。Inventory.add が失敗した場合はケーブルが黙って消える。

### 検証
報告どおりの挙動を確認した。HeadsideMonitorBlock.java:49-63 の use() は、サーバー側で権限チェック（:51）を通ると、BlockEntity が HeadsideMonitorBlockEntity であれば（:54）毎回新しいケーブル ItemStack を作り（:56-58）、pPlayer.getInventory().add(cableStack) を呼ぶ（:60）。monitor.isCableTaken() の確認も setCableTaken(true) の呼び出しも無い。コード全体を grep すると、isCableTaken/setCableTaken を参照しているのは HeadsideMonitorBlockEntity.java:44-51,175,193（定義と NBT 保存・読込）と HeadsideCableItem.java:43 の setCableTaken(false) だけだった。true にする経路はどこにも無いので、:55 のコメントが言う「まだ取り出されていない場合のみ」は実装されていない。ケーブルは stacksTo(1)（Medicalsystemcore.java:122-123）なので、右クリックするたびにインベントリの空きスロットが 1 つずつ埋まる。前提として、Forge 1.20.1 の Inventory.add(ItemStack) は boolean を返し、入らなければ失敗するだけでドロップはしない（クリエイティブでは数量を 0 にして true を返す）。:60 は戻り値を無視しており、渡せなかったスタックはローカル変数のまま捨てられる。そのため、インベントリが満杯だとケーブルは黙って消える。ただし実害は小さい。ケーブルを使った時の効果は、記録したモニターの監視対象 UUID を上書きすることだけ（HeadsideCableItem.java:41,46）。同じモニター用のケーブルが何本あっても新しい能力は得られず、他の用途（レシピ等）での参照も無い。害はアイテムの無限複製とインベントリの圧迫、コメントと実装の食い違いに限られるため、LOW が妥当。

### 修正方針
(1) :60 で add の戻り値を見て、false なら pPlayer.drop(cableStack, false) で足元に落とす（あるいは ItemHandlerHelper.giveItemToPlayer を使う）。(2) 「1 モニターにつきケーブル 1 本」という仕様を守るなら、isCableTaken() が true の時は案内メッセージを出して渡さず、渡す時に setCableTaken(true) にする。ただし、元の修正方針のように単純に true にするだけでは新しい不具合が生まれる。false に戻す経路は HeadsideCableItem.java:43（プレイヤーに接続した時）しか無く、ケーブルを捨てた・デスポーンした・死亡で失った・別ディメンションで使った等の場合、モニターは二度とケーブルを出さなくなる。true にするなら、回収・リセットの経路も同時に用意する必要がある。例えば、ケーブルを手に持ってモニターを右クリックすると返却される、スニーク右クリックでリセットする、ケーブル NBT に発行 ID を入れて古いケーブルを無効化する、など。逆に複数本を許す仕様なら、:55 のコメントと使われていない isCableTaken フィールドを整理するだけで足り、修正は (1) のみでよい。どちらにするかは仕様判断なので、親がユーザーに確認するべき。

## B61 [LOW] en_us に翻訳キーが足りない（doctor_card、rescue_portal の UI、出血死メッセージ）
- src/main/resources/assets/medicalsystemcore/lang/en_us.json:11 (content-cmd)

### 状況
英語クライアントで次がキー名のまま表示される: item.medicalsystemcore.doctor_card（ja_jp にはある）、コードで使っている gui.medicalsystemcore.rescue_portal.title / memo / dispatched / treated（ja_jp のみにある）。damage_type/bleeding.json の message_id=bleeding に対応する death.attack.bleeding はどちらの言語にもないため、出血ダメージで死ぬとキー名がそのまま表示される。ja_jp の card_reader キーは、CardReaderItem が空クラスで未登録のため使われていない。

### 検証
報告は一部だけ正しい。en_us の翻訳キー不足は本当にあるが、「出血死でキー名が表示される」という部分は、プレイヤーに対しては起きない。

【確認できた点（en_us に無くて表示に出るキー）】
- item.medicalsystemcore.doctor_card: アイテムは Medicalsystemcore.java:128 で登録されている。ja_jp.json にはあるが en_us.json には無い。英語クライアントではアイテム名がキー名のまま表示される。
- gui.medicalsystemcore.rescue_portal.memo: RescuePortalScreen.java:108-109 で MultiLineEditBox の placeholder（1.20.1 のシグネチャは (font,x,y,w,h,placeholder,message)）として使われている。メモ欄が空のときに見える。
- gui.medicalsystemcore.rescue_portal.dispatched / treated: RescuePortalScreen.java:114,123 で Checkbox のラベルとして画面に描画される。
- gui.medicalsystemcore.rescue_portal.title: ClientRescuePortalHandler.java:11 で Screen の title に渡している。ただし RescuePortalScreen は title を描画していない（365-387 行は日本語のハードコード文字列）。影響はナレーション程度。
- 補足: 同じ画面の大部分（通報リスト、救急隊ポータル、保存 など）は Component.literal の日本語ハードコードである。このため、キーを追加しても英語 UI は完成しない。

【反証できた点（death.attack.bleeding）】
- death.attack.bleeding はどちらの言語ファイルにも無い。ここは事実である。
- しかし出血ダメージ（ModEvents.java:443 handleBloodDamage）を受けるのはプレイヤーだけで、しかも heartStatus == NORMAL のときだけである（428 行）。
- このダメージで死にかけると、onLivingDeath（ModEvents.java:44-62）で heartStatus が NORMAL なので event.setCanceled(true) となり、体力 0.1 の心停止状態に移る。
- 前提（Forge 1.20.1）: ServerPlayer.die() は冒頭で ForgeHooks.onLivingDeath を呼び、キャンセルされたら死亡メッセージを送らずに戻る。
- 本当の死亡は handleCardiacArrest（ModEvents.java:349,358）か ServerboundGiveUpPacket.java:111 の player.kill() で起きる。前提（1.20.1）: LivingEntity.kill() は genericKill でダメージを与える。CombatTracker の最後の記録も genericKill になるので、表示されるのはバニラの death.attack.genericKill（翻訳済み）である。
- 結論: プレイヤーの画面に death.attack.bleeding が出る経路はない。キーを追加しても害はないが、バグではない。

【その他】
- card_reader が未使用という指摘は正しい（CardReaderItem.java は空クラスで、登録もない）。実害はない。
- tooltip.medicalsystemcore.doctor_card / card_reader もコードから参照されていない。DoctorCardItem.appendHoverText（150 行〜）は literal だけを使っている。

実害は英語クライアントでのアイテム名と救急ポータル UI の一部がキー名のまま表示されることだけなので、重大度は LOW。

### 修正方針
1. en_us.json に以下の英訳を追加する（ja_jp と揃える）。
   - item.medicalsystemcore.doctor_card
   - gui.medicalsystemcore.rescue_portal.title / memo / dispatched / treated
2. death.attack.bleeding（と .player）は、現状の設計ではプレイヤーの死亡メッセージに使われない。追加は任意（将来の保険）でよく、バグ修正として扱う必要はない。
3. RescuePortalScreen の日本語ハードコード文字列（365-387 行、「保存」ボタンなど）も英語対応するなら、翻訳キーに置き換えてから両言語に追加する必要がある。これは別タスクとして扱うのが妥当。
4. 未使用の card_reader キーと doctor_card / card_reader の tooltip キーは、残すか削除するかを判断すればよい（実害はない）。

## B62 [LOW] パックの基本モデルが存在しないテクスチャパスを参照している／効果アイコンと FACING の variant が足りない
- src/main/resources/assets/medicalsystemcore/models/item/blood_pack.json:4 (content-cmd)

### 状況
5 種のパックの基本モデルは layer0/1 に medicalsystemcore:item/pack_base / pack_overlay を指定している。実際のファイルは textures/item/pack/pack_base.png なので、テクスチャ欠落の警告が出る（fluid_level は 0.0〜0.9 で overrides が必ず当たるため、通常は表示に影響しない）。textures/mob_effect には transfusion / adrenaline_effect / fibrinogen_effect / tranexamic_acid_effect がない（ModEvents.java:326-332 では visible=false で付与しているので、HUD には出ない。インベントリ画面で表示されるかは要確認）。DefibrillatorBlock(:40) と HeadsideMonitorBlock(:29) の FACING は DirectionProperty.create("facing") で上下も含むが、blockstates には水平 4 方向しかないので、モデル読み込み時に up/down の missing variant エラーがログに出る。

### 検証
3 点とも実際に起きることをコードとリソースで確かめた。どれも見た目やログの問題で、ゲームの動作には影響しない。

(1) パックの基本モデルのパス不一致
- blood_pack.json:4-5、adrenaline/glucose/fibrinogen/tranexamic_acid_pack.json:4-5 は layer0/1 に medicalsystemcore:item/pack_base / pack_overlay を指定している。
- 実在するテクスチャは textures/item/pack/pack_base.png と pack/pack_overlay.png だけで、textures/item 直下には無い。
- 前提（MC 1.20.1）: ModelManager はモデル読み込み時に "Missing textures in model" の警告を出す。
- 表示への影響は無い。理由は次のとおり。
  - SimpleFluidLevelProperty.call は FluidPackItem.getFluidLevel(既定値 9)/10 を返す（FluidPackItem.java:139-145）。
  - 返り値は 0.0 以上なので、predicate fluid_level 0.0 の override が必ず一致する。
  - 一致先の pack/pack_level_N.json は正しいパス item/pack/... を参照している。
- このため影響はログの警告だけ。

(2) mob_effect テクスチャの欠落
- textures/mob_effect にあるのは bandage_effect.png と bleeding.png だけ。
- 登録済みの transfusion / adrenaline_effect / fibrinogen_effect / tranexamic_acid_effect（Medicalsystemcore.java:163-167）のテクスチャが無い。
- ModEvents.java:326-332 では new MobEffectInstance(..., 40, 0, true, false) で付与している。
- 前提（1.20.1）: 5 引数コンストラクタは showIcon=visible=false にする。HUD の Gui.renderEffects は showIcon で絞り込むので、HUD には出ない。
- しかし、インベントリ画面には欠落テクスチャのアイコンが出る。
  - Forge 47.4.0 ソースで確認した。ForgeHooksClient.shouldRenderEffect（:768-771）は IClientMobEffectExtensions.isVisibleInInventory を呼び、その既定値は true（:41-44）。
  - 本 MOD に initializeClient の上書きは無い（grep で確認）。
  - よって点滴中にインベントリを開くと、紫黒のアイコンが出ると判断した。
- /effect コマンドで付与した場合は HUD にも出る。

(3) FACING が上下を含む
- DefibrillatorBlock.java:40 と HeadsideMonitorBlock.java:29 は DirectionProperty.create("facing") を使っている。
- 前提（1.20.1）: 絞り込み条件なしの create は 6 方向を受け付ける。
- blockstates/defibrillator_block.json と head_side_monitor.json は north/south/west/east しか定義していない。
- このため up/down の状態について、ModelBakery が "missing model for variant" の警告をログに出す。
- 設置時は getHorizontalDirection を使う（Defibrillator:62、Monitor:44）ので、通常のプレイで上下向きにはならない。ただし /setblock では上下向きを作れる（missing model になる）。
- IVStandBlock:45 と StretcherBlock:39 は既に HORIZONTAL_FACING を使っており、統一もされていない。

別件（担当外だが明確な不具合）
- noradrenaline_pack は以下で登録・使用されている。
  - アイテム登録: Medicalsystemcore.java:144
  - クリエイティブタブへの追加: :193
  - ItemProperties の登録: :283
- ところが models/item/noradrenaline_pack.json が存在しない。そのため、クリエイティブタブやインベントリで紫黒の missing model として表示される。
- また、PackColor の登録（:292-296）からも漏れている。

重大度は LOW。理由は、ゲームの動作に影響が無く、見た目やログの問題にとどまるため（ノルアドレナリンパックの見た目の欠落を含む）。

### 修正方針
(1) 5 種のパックの基本モデル（blood/adrenaline/glucose/fibrinogen/tranexamic_acid_pack.json）の layer0/layer1 を medicalsystemcore:item/pack/pack_base と medicalsystemcore:item/pack/pack_overlay に直す。
(2) textures/mob_effect/ に transfusion.png、adrenaline_effect.png、fibrinogen_effect.png、tranexamic_acid_effect.png（18x18）を追加する。インベントリでも見せたくない場合は、テクスチャを追加する代わりに、エフェクトの initializeClient で isVisibleInInventory=false を返す方法もある。どちらにするかは設計判断で決める。
(3) DefibrillatorBlock:40 と HeadsideMonitorBlock:29 を BlockStateProperties.HORIZONTAL_FACING に置き換える。プロパティ名は "facing" のままなので、既存ワールドの保存状態とも互換がある。
(別件) models/item/noradrenaline_pack.json を他のパックと同じ形（正しいパス + overrides）で追加し、ItemColors に PackColor を登録する。

## B63 [LOW] 権限チェックをクライアント側でも実行しており、エラーメッセージが二重に表示される
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/block/DefibrillatorBlock.java:69 (content-cmd)

### 状況
前提: 1.20.1 の LocalPlayer.sendSystemMessage はチャットにそのまま表示する。DefibrillatorBlock.use(:69) は isClientSide の判定より前に checkMedicalAuthorization を呼んでいる。TubeItem.useOn(TubeItem.java:41) と ElectrodeItem.interactLivingEntity(ElectrodeItem.java:29) も同様。そのため、カードのない人が操作すると、クライアントとサーバーの両方から同じ 3 行のエラーが届き、6 行表示される。TubeItem.useOn は player が null の場合（自動化 MOD など）に NPE になる。TubeItem.interactLivingEntity 側には権限チェック自体がない。

### 検証
報告は実在する。ただし影響はチャット表示だけで、権限の迂回は起きない。

【前提: Minecraft/Forge 1.20.1 の仕様】
1. クライアント側の LocalPlayer.sendSystemMessage はチャットに直接表示する。joined-1.20.1-srg.jar の LocalPlayer.m_213846_ を javap で確認し、Minecraft.gui.getChat().addMessage(component) を呼ぶだけの実装だった。
2. MultiPlayerGameMode.useItemOn と interact は、クライアント側で use / interactLivingEntity を呼ぶ。その結果にかかわらず、ServerboundUseItemOnPacket / ServerboundInteractPacket は送信される。したがって同じメソッドがクライアントとサーバーの両方で実行される。

【確認内容】
- MedicalAuthorizationUtil.java:20-34: カードがない場合、player.sendSystemMessage で 3 行を出す。判定は DoctorCardItem.hasValidDoctorCard（DoctorCardItem.java:126-147）で、インベントリの NBT だけを見る。インベントリはクライアントに同期済みなので、両側とも同じく false になる。
- DefibrillatorBlock.java:69: isClientSide の判定（:73）より前に checkMedicalAuthorization を呼んでいる。そのためクライアントとサーバーでそれぞれ 3 行、計 6 行が表示される。
- ElectrodeItem.java:29: isClientSide の判定（:34）より前にチェックしている。結果は同じく 6 行。さらに対象の型チェック（:34 の `pTarget instanceof Player`）より前にあるため、Mob など Player 以外を右クリックしても表示される。
- TubeItem.java:41: isClientSide の判定（:46）より前にチェックしている。点滴台以外の任意のブロックを右クリックすると 6 行になる。補足すると、点滴台をスニークせずにクリックした場合は経路が違う。IVStandBlock.use はクライアントで SUCCESS を返す（IVStandBlock.java:74-75）ため、クライアント側の TubeItem.useOn は呼ばれない。サーバー側では IVStandBlock:78 が 3 行を出して FAIL を返し、続いて TubeItem:41 が 3 行を出すので、やはり 6 行になる。つまり点滴台の経路ではサーバー側だけで二重になる。
- 同じユーティリティの他の呼び出し元はすべてサーバー側に限定済みで、問題はない。対象は HeadsideMonitorBlock:50-51、StretcherBlock:101/113、StretcherItem:28/32、MedicalBagItem:26-27、RescueTabletItem:26-28。上の 3 か所だけが例外。
- player が null の場合の NPE: UseOnContext.getPlayer() は @Nullable である。TubeItem:41 は null をそのまま渡し、DoctorCardItem.java:128 の player.getMainHandItem() で NPE になる。:46 の `player != null` はその後に置かれているので防げない。ただしバニラには、このアイテムに対して player=null で useOn を呼ぶ経路がない。発生は外部 MOD に限られ、現実のリスクは低い。
- TubeItem.interactLivingEntity（:62-105）に権限チェックがないのも事実。ただし StartPos の NBT は、サーバー側で権限確認を通った useOn でしか付かない。悪用には、医師が始点を設定したチューブを受け取るか、NBT を直接編集する必要がある。

【重大度】表示が二重・三重になる見た目の不具合で、権限の判定自体はサーバー側で正しく効いている。LOW が妥当。

### 修正方針
DefibrillatorBlock.use では、`if (pLevel.isClientSide) return SUCCESS;` を権限チェックより前に置く。IVStandBlock と同じ順序にする。

ElectrodeItem.interactLivingEntity では、`!pPlayer.level().isClientSide && pTarget instanceof Player` の中に権限チェックを移す。これでクライアントの表示がなくなり、Player 以外の対象でもエラーが出なくなる。クライアントで返す値はそのまま（CONSUME/PASS 系）とし、腕振りの動作は保つ。

TubeItem.useOn では、`!level.isClientSide && player != null` と `instanceof IVStandBlock` の判定の内側でチェックする。点滴台をクリックしたときだけ判定することになる。

点滴台の経路でサーバー側だけで二重になる件は、IVStandBlock:78 の判定を先にすることで直す。具体的には、チューブを持っている場合（:93）の分岐を権限チェックより前に置くか、TubeItem 側をサイレント版の checkMedicalAuthorizationSilent にする。

TubeItem.interactLivingEntity にもサーバー側で同じ checkMedicalAuthorization を追加すると、受け渡されたチューブで権限なしに接続できる経路を塞げる。

# Rejected
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ClientboundSyncAllRescueDataPacket.java:36 共通側でロードされるパケットクラスの lambda 内にクライアント専用クラスを直接書いており、Dist 分離が脆い — 記述されているコードの形は実際にある。ただし現状では専用サーバー・クライアントのどちらでも失敗する経路が無く、報告者自身も「実行されない経路」「クラッシュ未確認」と書いている。バグではなく将来の保守性の指摘なので real=false とした。

確認したこと:
(1) ClientboundSyncAllRescueDataPacket.java:36-40 と ClientboundUpdateRescueDataPacket.java:35-56 は、DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {...}) の内側で Minecraft と RescuePortalScreen を直接参照している。この 2 クラスはサーバー側でもロードされる（ServerboundRequestRescueListPacket.java:17、RescueDataManager.java:43/58/74 で new される）。
(2) 合成 lambda メソッドに出る命令は、getstatic/getfield による Minecraft.screen（Screen 型）の取得、RescuePortalScreen への instanceof/checkcast、RescuePortalScreen 自身のメソッドの invokevirtual だけ。引数の型は List と RescueData。前提: HotSpot の型検査ベリファイアは instanceof/checkcast の対象クラスを遅延解決し、レシーバ型とメソッド所有クラスが同名なら代入可能性の確認でクラスをロードしない。なのでクラスのリンク時に Screen などのクライアントクラスはロードされない。また DistExecutor が Dist.CLIENT 以外で内側の Supplier を呼ばないので、lambda 本体も実行されない。
(3) どのパケットも ModPackets.java:36-39/63-66/75-78 で NetworkDirection.PLAY_TO_CLIENT として登録されており、サーバーで handle が呼ばれることは無い。前提: Forge 1.20.1 の SimpleChannel は方向が合わないメッセージを処理しない。ClientboundStartQTEPacket.java:17 の ClientPacketHandler.handleStartQTE 呼び出しも同じ理由でサーバーでは実行されない。invokestatic は呼び出し時に初めて解決されるので、検証時にもロードされない。
(4) BandageItem.java:29-31 は isClientSide() が true のときだけ ClientHealingManager.startHealing を呼ぶので、専用サーバーでは実行されない。ClientHealingManager には static 初期化子が無く、static フィールドは int だけ（ClientHealingManager.java:13-16）なので、仮にロードされても Minecraft はすぐには参照されない。

結論: 報告どおりのクラッシュや誤動作は今のコードでは起きない。「将来の変更で NoClassDefFoundError になりやすい」という点は、Forge が unsafeRunWhenOn を "unsafe" と名付けている理由そのもので、保守性の注意事項としては妥当。重大度を付けるなら LOW 以下。
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/Medicalsystemcore.java:226 同じ Config.SPEC が SERVER と COMMON の両方に登録されており、クライアントが読む設定値の出所が不定 — 二重登録そのものは事実（Medicalsystemcore.java:226-227）。ただし「クライアントで効く値の出所が不定」という主張は、Forge 1.20.1-47.4.0 のソースで確かめると成り立たない。
(1) 読み込み順は毎回同じ。COMMON はMOD読み込み時に読まれる（ModStateProvider.java:68）。SERVER はサーバー起動時に読まれる（ServerLifecycleHooks.java:96）。リモートのクライアントでは、ログイン時のハンドシェイクで S2CConfigData を受け取る（NetworkInitialization.java:68、HandshakeHandler.java:326、ConfigSync.java:42）。ModConfig.acceptSyncedConfig は setConfigData(→spec.acceptConfig) を呼んでから Reloading を発火する。このため Config.onLoad（Config.java:294-）は常に、最後に読まれたサーバー側の値で static を上書きする。結果として、サーバーへの接続中も、専用サーバー上でも、SERVER の値が決まって勝つ。
(2) 例外になりそうな経路も実際には起きない。common.toml をゲーム中に書き換えた場合、ConfigFileTypeHandler$ConfigWatcher はそのファイル自身を load し、spec.afterReload() を呼び、Reloading を発火する。しかし spec.acceptConfig は呼ばない（javap で確認）。ForgeConfigSpec.childConfig は最後に acceptConfig された SERVER 側のデータを指したまま（ForgeConfigSpec.java:80,98-100）。したがって onLoad が読む値は SERVER の値のままで、COMMON の値に戻ることはない。
(3) 「動的変更すると値がずれる」懸念は、二重登録が原因ではない。Forge が SERVER 設定をクライアントへ送るのはログイン時だけ（ConfigSync.syncConfigs をログインパケット一覧で使うのみ）だからである。COMMON 登録を外しても、この問題は同じように残る。
実害が残るのは2点だけ。(a) common.toml はほぼ使われない（SERVER が読み込まれる前、つまりタイトル画面でだけ効く）ので、管理者が混乱する。(b) シングルプレイでワールドを抜けると、SERVER の closeConfig が setConfigData(null) を実行し、spec.childConfig が null になる。その後に common.toml を書き換えると、ファイル監視スレッドの onLoad 内で SPEC.get() が IllegalStateException を投げる。どちらもごく小さい問題である。
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/core/ModEvents.java:270 同じ種類のパックを複数スロットに入れると同時に減り、効果は重ならない — 報告の具体例（輸血パックを 2 スロットに入れる）は起こり得ない。IVStandBlockEntity.java:38-44 の isItemValid でスロット 0 は BLOOD_PACKS、スロット 1/2 は DRUG_PACKS しか受け付けない。さらに getSlotLimit=1（同 :47-49）で、tags/items/blood_packs.json に入っているのは blood_pack だけ。IVStandBlock.java:108-109 の右クリック挿入も、血液パックは必ずスロット 0 に入れ、既存のパックは押し出す。メニュー経由（SlotItemHandler は isItemValid/getSlotLimit を守る、という Forge 1.20.1 の前提）でも、輸血パックが 2 つ同時に消費されることはない。
ただし薬剤パックなら同じ現象は起きる。drug_packs.json には adrenaline/tranexamic_acid/fibrinogen/noradrenaline/glucose が入っており、同じ薬剤をスロット 1 と 2 に入れられる（IVStandBlock.java:111）。その場合 ModEvents.java:270-283 が両スロットから毎 tick 1 ずつ減らし、applyPackEffect（ModEvents.java:324-337）が同じ amplifier 0・40tick のエフェクトを付け直すだけなので、2 個目の効果は上乗せされない。
とはいえ、薬剤スロットを 2 つ設けたのは「異なる薬剤を同時に投与するため」と読むのが自然で、同じ薬剤を 2 つ入れたときの扱いは仕様判断の範囲。不正な状態・データ破損・クラッシュは起きず、損失はプレイヤーが自分でパックを 2 つ入れたときに 1 個分が無駄になるだけ。報告者自身も「意図的な仕様かどうかは要確認」としている。具体例が到達不能で、残るケースはバグかどうか確信できないため real=false とする。
- src/main/java/jp/houlab/mochidsuki/medicalsystemcore/network/ServerboundQTETResultPacket.java:82 QTE の『成功』判定で蘇生確率が 20% 下がる — 報告の計算自体は正しい。ServerboundQTETResultPacket.java:79-84 で GREAT_SUCCESS は chance+20、SUCCESS は chance-20 になる。CARDIAC_ARREST の判定 (:88) は `Math.random() < chance/100` なので、chance<=20 なら SUCCESS は必ず失敗する。VF の判定 (:90) も (chance-20)*2 なので同じ。chance が 5〜20 の状態は実際に起こる。ModEvents.java:353-358 は CARDIAC_ARREST 中、1 分ごとに Config 既定値で 5 ずつ減らし (Config.java:151-152)、<=0 になった時点で kill するだけなので、5〜20 の間は数分残る。

ただし、次の 3 点から「バグ」とは判断しない。
(1) 表示が食い違っているわけではない。「§a成功。」は ClientQTEManager.java:47-49 がクライアント側で出すメッセージで、QTE のタイミング判定の結果を示しているだけで、蘇生の結果ではない。蘇生の結果はサーバーが別のメッセージで通知している (ServerboundQTETResultPacket.java:100「蘇生成功！」/:110,113,115「蘇生失敗…」)。「QTE は成功したが蘇生は失敗」は互いに矛盾しない、ありうる結果。
(2) `if(this.result == QTEResult.SUCCESS){ chance -= 20.0f; }` は GREAT_SUCCESS のボーナスとは別のブロックに作者がわざわざ書いたコードで、git log -S で調べると導入コミットは 60d71d4。打ち間違いや条件の取り違えではなく、大成功 +20、成功 -20、失敗はショックなし (:66-70) という段階的な設計と読むのが自然。
(3) 仕様書（DEV.md 等）はリポジトリに無く、これが意図に反すると裏付ける根拠が無い。:78 のコメントが大成功のボーナスにしか触れていない点は弱い材料にしかならない。
したがってこれは仕様（バランス）の確認事項であって、欠陥ではない。

関連して、確かな問題が 1 つ見つかった（設定値を動的に変えたいというユーザー要望に直接関係する）。Config.java:167-170 / 336-337 で定義・読み込みしている DEFIBRILLATOR_VF_SUCCESS_MULTIPLIER と DEFIBRILLATOR_TRANSITION_CHANCE が、ソース中のどこからも参照されていない。ServerboundQTETResultPacket.java:90 は倍率を `chance * 2` とハードコードし、:108, :111 は移行確率を `0.4` とハードコードしている。DefibrillatorItem.java も同じ値をハードコードしている。このため、設定ファイルやこれから実装する動的変更コマンドでこれらの値を変えても、ゲームの挙動は変わらない。

# Not covered
- net-sides: 未読: client/renderer 配下(RescueTabletBEWLR は ClientTrackingManager の参照部分のみ確認)、client/model、ClientHudHandler(冒頭の参照のみ)、ClientStretcherHandler・DebugOverlayHandler・ClientPoseController・StretcherPoseManager の本体、entity/StretcherEntity、block/StretcherBlock・IVStandBlock・HeadsideMonitorBlock、blockentity の IVStand・Stretcher・HeadsideMonitor、menu/IVStandMenu・MedicalBagMenu(クライアント入力の検証)、item/StretcherItem・TubeItem・FluidPackItem・HeadsideCableItem・MedicalBagItem、effect 配下、ModEvents の 260〜545 行(心停止・出血・点滴の tick 処理)、DoctorCardCommand の本体、capability の NBT 処理、src/main/resources(mixin 設定がないことは確認済み、mods.toml のみ存在)。専用サーバーでの起動、ペイロード上限を超えたときの挙動、インタラクト時の例外の扱いは実機で検証しておらず、該当する指摘は needs-check としている。Forge 1.20.1 の consumerMainThread がハンドラを enqueueWork で包み、setPacketHandled(true) も自動で行う前提に立ち、boolean を返すハンドラの setPacketHandled 漏れは指摘していない。
- state-persist: 89ファイル中、約40ファイルを読んだ。全文を読んだのは次の通り: capability/*, core/ModEvents, RescueDataManager, RescueData, PoseController, blockentity/* 全4ファイル, entity/StretcherEntity, block/DefibrillatorBlock, HeadsideMonitorBlock, IVStandBlock, StretcherBlock, item/TubeItem, ElectrodeItem, HeadsideCableItem, StretcherItem, MedicalBagItem, BandageItem(前半), menu/MedicalBagMenu, event/StretcherPlacementHandler, network の ModPackets, ClientboundCoreStatsPacket, Rescue 系5パケット, ServerboundGiveUpPacket, ServerboundQTETResultPacket(35-131行), client の ClientMedicalDataManager, ClientMedicalData, ClientWorldHandler, ClientTrackingManager, ClientPacketHandler, ClientPoseController, ClientHealingManager, ClientQTEManager(28-89行), ClientGiveUpManager(18-60行), ClientMouseHandler(25-70行), RescuePortalScreen(1-320行), RescueDataListWidget(1-60行), HeadsideMonitorBlockEntityRenderer(一部), StretcherPoseManager(15-60行), ClientStretcherHandler(15-58行), Config.java 285-367行, Medicalsystemcore.java の登録部分, loot_tables。未読: effect/* 全部, command/DoctorCardCommand・EmergencyCallCommand(RescueData 生成箇所のみ grep), DoctorCardItem, MedicalAuthorizationUtil, FluidPackItem, DefibrillatorItem, RescueTabletItem, CardReaderItem, IVStandMenu, RescuePortalMenu, ServerboundFinishHealPacket, ClientboundStartQTEPacket, ClientInputHandler, ClientHudHandler, DebugOverlayHandler, 各 Renderer/Model(HeadsideMonitor 以外), RescueTabletBEWLR, StretcherPositionCalculator, AngleUtils, Config.java 1-284行(spec 定義と値域), resources のうち loot_tables 以外(tags, lang, models, mods.toml)。StretcherPoseManager は Grep で外部からの呼び出しが0件だったため、未使用と判断して指摘対象から外した。何も実行していない(gradle・ゲーム起動なし)。バニラ/Forge の挙動は、srg jar の javap と Forge 47.4.0 の sources jar で確認できた範囲(BlockEntity.loadStatic、PlayerList.remove、PacketUtils/BlockableEventLoop の例外処理、IForgeBlock.onDestroyedByPlayer、ForgeConfigSpec)を除き、知識に基づく前提として scenario に明記した。fmlcore の ConfigTracker のソースは見つからず、Config 二重登録時のアンロード順序は未確認。git 履歴は制約により参照していない。
- gameplay: MedicalBagItem / MedicalBagMenu、IVStandBlockEntity / IVStandMenu の内部、StretcherEntity / Stretcher 系、HeadsideMonitor 系、RescueData / RescuePortal 系、コマンド類、クライアント描画系（ClientPoseController の詳細を含む）は今回の観点の範囲外のため精読していない。DefibrillatorBlock は 100-208 行だけを確認した。Minecraft 本体（パッチ適用済み）のソースはローカルで参照できなかったため、死亡中プレイヤーの tick 継続、BlockEntity.load 時に level が null であること、MobEffectInstance.update の hiddenEffect 挙動、StateHolder.getValue の例外は、1.20.1 の既知の挙動を前提に判断した（各 scenario に明記）。ForgeConfigSpec と ConfigTracker の unload の順序は、ローカルの jar（ForgeConfigSpec はソース、ConfigTracker は javap）で確認した。その結果、Unloading イベントは setConfigData(null) より前に発火し、Config の static 値はリセットされないので問題なしと判断した。
- content-cmd: IVStandBlockEntity と StretcherBlockEntity の tick やセーブ処理全体、StretcherEntity の乗車/降車の同期の詳細、HeadsideMonitorBlockEntity、DefibrillatorBlockEntity の tick、RescueData のシリアライズとパケットサイズ上限、client/ 以下の Screen（MedicalBagScreen/IVStandScreen の描画）、ModEvents・PlayerMedicalData など生体ロジックは担当外のため精読していない。rescue_tablet（builtin/entity）の表示 transform と、block/stretcher.json が参照する entity/white（未使用モデル）が読み込まれるかどうかは未確認。advisor への確認はタイムアウトしたため受けていない。
