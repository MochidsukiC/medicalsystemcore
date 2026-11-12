Forge→Fabric 26.2 対応表です（jar・ソースで実在を確認済み）。対象は現行の `forge/src/main/java/jp/houlab/mochidsuki/medicalsystemcore/`。段分けと依存は `docs/fabric-port-plan.md`。

検証元: MC 26.2 の sources jar（common/clientOnly）、Fabric API 0.161.0+26.2 の pom に載っている各モジュールの class jar（sources jar はキャッシュに無いので javap で確認。ミックスインの注入位置も javap -v で確認）、fabric-loader 0.20.1、Forge 1.20.1-47.4.0 の sources jar（Forge 側フック位置の確認用）。
26.2 の全体的な変化: `ResourceLocation` は `Identifier`（`Identifier.fromNamespaceAndPath`）に、`GuiGraphics` は `GuiGraphicsExtractor` に、`DimensionDataStorage` は `SavedDataStorage` に、HUD の描画本体は `Gui` から `Hud` クラスに分かれた。

## 1. イベント・フック（46 件）

内訳: `@SubscribeEvent` 43 件、`modEventBus.addListener` 3 件（Medicalsystemcore.java:205-207）。ほかに Forge のイベントではない `MedicalConfig.addListener`（ConfigSyncHandler.java:42）が 1 件。行番号は `@SubscribeEvent` の行。同じ Fabric フックに移るものはまとめた（件数を行頭に書いた）。

### 1.1 サーバー側のゲームロジック

| Forge 使用箇所 | Forge 側でしていること | Fabric 26.2 の置き換え先（実在確認済み） | Mixin | 備考 |
|---|---|---|---|---|
| core/ModEvents.java:49 LivingDeathEvent | プレイヤーの死亡を判定。HeartStatus が NORMAL 以外、または死因が奈落（`FELL_OUT_OF_WORLD`）なら死亡を許可し、NORMAL・無敵解除・意識あり・姿勢制御のクリア・CoreStats 送信を行う。NORMAL なら**キャンセル**して HP0.1・CARDIAC_ARREST・無敵化・CoreStats 送信 | `ServerLivingEntityEvents.ALLOW_DEATH`。`boolean allowDeath(LivingEntity, DamageSource, float)` で false を返すとキャンセル | 不要 | `/kill`（`generic_kill`）は、通常時は心停止になり、心停止中なら死亡する（上の HeartStatus 判定で成立。奈落だけは常に死亡）。`LivingEntity#hurtServer` 内の `isDeadOrDying()`（ordinal=1）を Redirect する方式で、トーテム判定の前に呼ばれる。26.2 の `die()` 呼び出し元は hurtServer（LivingEntity.java:1252）と Creaking だけなので、網羅範囲は Forge と同等。false を返しても HP は戻らないので、`setHealth(0.1f)` はハンドラ側で必須（現行と同じ）。`DamageTypes.FELL_OUT_OF_WORLD` は 26.2 に実在（キー `out_of_world`）。`ServerPlayerEvents.ALLOW_DEATH` は @Deprecated |
| ModEvents.java:90 LivingHurtEvent | `damageImmune` のとき、`DamageTypeTags.BYPASSES_INVULNERABILITY` に入らないダメージを**キャンセル**（奈落・`/kill` などの無敵貫通ダメージは通す） | `ServerLivingEntityEvents.ALLOW_DAMAGE`。`boolean allowDamage(LivingEntity, DamageSource, float)` | 不要 | タグ `DamageTypeTags.BYPASSES_INVULNERABILITY` は 26.2 に実在。呼ばれる位置は hurtServer の `isSleeping()` の直前（無敵判定・死亡判定・火炎耐性判定の後）。Forge は `Player#actuallyHurt` の先頭で量を 0 にする方式で、ノックバックや赤点滅は残っていた。Fabric で false を返すと hurtServer ごと終わるので、それらも出なくなる（挙動差） |
| ModEvents.java:218 LivingDamageEvent | 読み取りのみ。BLEEDING（`ModDamageTypes.BLEEDING_KEY`）以外でプレイヤーが受けた量に応じて出血速度を加算し、システムメッセージを送る | `ServerLivingEntityEvents.AFTER_DAMAGE` は**値が違う**ので使わず、Mixin | **要** | Forge の値は防具・魔法軽減・衝撃吸収の**後**（`Player#actuallyHurt` の最終 `dmg`）。Fabric の `damageTaken` は防具の前で、`isDeadOrDying()` のときは発火しない。注入点は「要 Mixin の一覧」の 5 番（確定） |
| ModEvents.java:243 ItemTossEvent | 電極（現在発行中の電極が除細動器に紐づいているとき）を捨てたら、除細動器にパッドを戻し HAS_PADS を立て、ItemEntity を消す。意識不明中なら**キャンセル**してインベントリに戻し、メッセージを送る | 無し | **要** | Forge のフックは `Player#drop(ItemStack, boolean)` と `ServerPlayer#drop(boolean)`（Forge パッチで確認）。26.2 ではどちらも `ServerPlayer#drop(ItemStack, boolean randomly, boolean thrownFromHand)` に行き着き、`drop(removed, false, true)` と `drop(stack, false)` が randomly=false、死亡時ドロップ（`Inventory.java:448`、`EntityEquipment.java:64`）が randomly=true。この 3 引数版の HEAD に cancellable で入れ、`!randomly` で絞る。戻り値は `ItemEntity`（null 可）なので、取り消しは `cir.setReturnValue(null)`。電極のときは ItemEntity を作らずに終わればよい（Forge は作ってから discard） |
| ModEvents.java:303、GiveUpGuard.java:37 TickEvent.PlayerTickEvent（server, END）（2 件） | ModEvents: 毎 tick、輸液・心停止・出血・意識・浮上・ECG・姿勢維持を処理し、CoreStats を必要なときだけ送る。**死亡後リスポーン前は処理しない**（`isAlive()` を確認）。GiveUpGuard: ギブアップ済みのプレイヤーの意識が戻ったら記録を解除する | プレイヤー単位の tick イベントは**無し**。`ServerTickEvents.END_SERVER_TICK`（`onEndTick(MinecraftServer)`）で `server.getPlayerList().getPlayers()` を回す | 任意 | Forge は `Player#tick` の最後（`onPlayerPostTick`、Player.java.patch で確認）で呼ばれていた。順序まで合わせるなら `Player#tick` の TAIL に Mixin。GiveUpGuard の解除も同じ場所で処理できる |
| ModEvents.java:357、GiveUpGuard.java:48、PendingHealManager.java:52、PendingQteManager.java:114 PlayerLoggedOutEvent（4 件） | ModEvents: 姿勢制御をクリアし、担架に乗っていれば降ろして担架を運搬者に返す（`PlayerList.remove` が担架ごと保存・除去する前に実行する必要がある）。GiveUpGuard/PendingHealManager: 記録を消す。PendingQteManager: 記録を消し、除細動器のチャージ解除とクールダウン開始（期限切れと同じ後始末） | `ServerPlayerEvents.LEAVE`（`onLeave(ServerPlayer)`）。`PlayerList#remove` の HEAD で呼ばれる | 不要 | Forge の `firePlayerLoggedOut` も `PlayerList#remove` の先頭（PlayerList.java.patch で確認）なので位置が一致する。`ServerPlayConnectionEvents.DISCONNECT`（`onPlayDisconnect(ServerGamePacketListenerImpl, MinecraftServer)`）でも取れるが、`remove` との前後関係は未確認 |
| ModEvents.java:135、ConfigSyncHandler.java:61、RevokedLicenseSavedData.java:83 PlayerLoggedInEvent（3 件） | ModEvents: 姿勢制御を医療データから組み立て直す。ConfigSyncHandler: 設定（clientSynced キー）をそのプレイヤーへ送る。RevokedLicenseSavedData: 失効リスト全件をそのプレイヤーへ送る | 姿勢の復元は `ServerPlayerEvents.JOIN`（`onJoin(ServerPlayer)`、`PlayerList#placeNewPlayer` の RETURN。Forge の `firePlayerLoggedIn` も末尾）。パケット送信は `ServerPlayConnectionEvents.JOIN`（`onPlayReady(ServerGamePacketListenerImpl, PacketSender, MinecraftServer)`、`placeNewPlayer` の `ClientboundPlayerAbilitiesPacket` 生成の直前） | 不要 | どちらでも送れる。`ServerPlayConnectionEvents.JOIN` は `placeNewPlayer` 内の `ClientboundLoginPacket` 送信より後に呼ばれるので、payload はログインパケットの後に届く。送信側は `ServerPlayNetworking.send(handler.player, payload)` |
| ModEvents.java:142 PlayerRespawnEvent | 姿勢制御を医療データから組み立て直す（死亡後のリスポーンでは初期値、End 帰還ではコピーされた医療データ） | `ServerPlayerEvents.AFTER_RESPAWN`（`afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive)`、`PlayerList#respawn` の TAIL。`alive` は `keepAllPlayerData`） | 不要 | **attachment のコピーも同じ AFTER_RESPAWN（既定フェーズ）で行われる**ので、本 MOD のリスナーが先に走ると attachment が空のまま読む。2.1 節参照 |
| ModEvents.java:112 PlayerEvent.Clone | 医療データのコピー（End からの帰還）。**死亡時（`isWasDeath`）はコピーしない**。帰還時は旧プレイヤーの医療データを新プレイヤーへ NBT 経由でコピーする（旧プレイヤーは removed で capability が無効なので、`reviveCaps()` で一時復活させて読む） | **不要**（Data Attachment がコピーする）。`copyOnDeath()` を付けなければ、死亡では引き継がず End 帰還では引き継ぐ | 不要 | 2 節の結論。手動コピーが要るなら `ServerPlayerEvents.COPY_FROM`（`copyFromPlayer(ServerPlayer old, ServerPlayer new, boolean alive)`、`ServerPlayer#restoreFrom` の TAIL）が Clone に最も近い |
| ModEvents.java:152 PlayerChangedDimensionEvent | クライアントがレベル切替で姿勢制御の状態を消すので、CoreStats を本人へすぐ送り直す | `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`（`afterChangeLevel(ServerPlayer, ServerLevel origin, ServerLevel destination)`） | 不要 | `ServerPlayer#triggerDimensionChangeTriggers` の TAIL と、`PlayerList#respawn` で前後のレベルが違うときに呼ばれる |
| ModEvents.java:162 PlayerEvent.StartTracking | 他のプレイヤーが追跡を始めたとき、対象の現在の CoreStats をその人に 1 回送る | `EntityTrackingEvents.START_TRACKING`（`onStartTracking(Entity trackedEntity, ServerPlayer player)`。追跡する側が `player`） | 不要 | Forge では `getTarget()` が追跡される側、`getEntity()` が追跡する側 |
| ModEvents.java:638 PlayerInteractEvent（基底型なので全サブイベント） | 意識不明なら右クリック（ブロック・アイテム・エンティティ）と左クリック（ブロック）をすべて**キャンセル** | `UseBlockCallback`、`UseItemCallback`、`UseEntityCallback`、`AttackBlockCallback`、`AttackEntityCallback` の各 `.EVENT` で `InteractionResult.FAIL` を返す | 不要 | シグネチャは `interact(Player, Level, InteractionHand[, BlockHitResult / Entity, EntityHitResult / BlockPos, Direction])`。Fabric の callback はクライアントとサーバーの両方で呼ばれる。Forge の判定は capability を両側で読むが、クライアント側の capability は更新されない（CoreStats は ClientMedicalDataManager に入る）ので、実際にはサーバー側でだけ効いていた。Fabric で同じにするなら attachment だけを読む。クライアントでも止めたいなら ClientMedicalDataManager を読む分岐が要る（main ソースセットから client クラスは参照不可）。Forge の EntityInteract / EntityInteractSpecific の両方を UseEntityCallback で兼ねる。RightClickEmpty / LeftClickEmpty（クライアント限定）に相当するものは無い |
| event/StretcherPlacementHandler.java:27 PlayerInteractEvent.RightClickBlock | サーバーで素手＋しゃがみのとき、運搬中のストレッチャーを 2 ブロック設置して乗員を移し、**キャンセル**する | `UseBlockCallback.EVENT` | 不要 | PASS 以外を返すとバニラ処理が止まる。キャンセルに当たる値は `InteractionResult.SUCCESS` / `SUCCESS_SERVER` / `FAIL` |
| event/CommandEventHandler.java:12 RegisterCommandsEvent | 2 つのコマンド（DoctorCardCommand、EmergencyCallCommand）を登録 | `CommandRegistrationCallback.EVENT`。`register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)` | 不要 | |
| ModEvents.java:209 AttachCapabilitiesEvent / Medicalsystemcore.java:206 RegisterCapabilitiesEvent | Player に PlayerMedicalData を付与し、NBT で保存する | Data Attachment API（2 節） | 不要 | `getCapability(...).ifPresent(...)` は現行 14 ファイル・30 か所（うち 3 か所は PlayerMedicalDataProvider と IVStandBlockEntity の override の定義）。attachment は常に存在するので `getAttachedOrCreate(TYPE)` になる |

### 1.2 サーバーの起動・停止（設定・メモリ上の記録）

| Forge 使用箇所 | Forge 側でしていること | Fabric 26.2 の置き換え先（実在確認済み） | Mixin | 備考 |
|---|---|---|---|---|
| ConfigSyncHandler.java:45 ServerAboutToStartEvent | `config/medicalsystemcore.json` を読み込む（保管庫は全キーが置き換わる） | `ServerLifecycleEvents.SERVER_STARTING`（`onServerStarting(MinecraftServer)`） | 不要 | `MinecraftServer#runServer` の `initServer()` 呼び出しの**直前**に呼ばれる（バイトコードで確認。ワールドのロードより前）。パスは `FabricLoader.getInstance().getConfigDir().resolve("medicalsystemcore.json")`（`FMLPaths.CONFIGDIR` の代わり） |
| ConfigSyncHandler.java:51、:56 ServerStartedEvent / ServerStoppingEvent | `serverRunning` フラグの更新（変更リスナーが送信してよいか） | `ServerLifecycleEvents.SERVER_STARTED` / `SERVER_STOPPING` | 不要 | `ServerLifecycleHooks.getCurrentServer()` の代わりに、SERVER_STARTED で `MinecraftServer` を static に保持する |
| GiveUpGuard.java:53、PendingHealManager.java:57、PendingQteManager.java:124 ServerStoppedEvent（3 件） | メモリ上の記録を全消去 | `ServerLifecycleEvents.SERVER_STOPPED` | 不要 | |
| PendingQteManager.java:99 TickEvent.ServerTickEvent（END） | 期限切れの QTE 記録を破棄し、除細動器のチャージを解除する | `ServerTickEvents.END_SERVER_TICK` | 不要 | `server.overworld().getGameTime()` は同じ |
| ConfigSyncHandler.java:42 `MedicalConfig.addListener` | 設定変更時に clientSynced キーなら全員へ送る（`server.execute` でサーバースレッドに戻す） | Forge のイベントではない（common のリスナー）。`ModPackets.sendToAllClients` の代わりに `PlayerLookup.all(server)` を回して `ServerPlayNetworking.send` | 不要 | |
| Medicalsystemcore.java:238 ServerStartingEvent | 空 | （必要なら `ServerLifecycleEvents.SERVER_STARTING`） | 不要 | 削除してよい |

### 1.3 初期化・登録

| Forge 使用箇所 | Forge 側でしていること | Fabric 26.2 の置き換え先（実在確認済み） | Mixin | 備考 |
|---|---|---|---|---|
| Medicalsystemcore.java:205 FMLCommonSetupEvent | 空 | `ModInitializer#onInitialize` | 不要 | |
| Medicalsystemcore.java:207 BuildCreativeModeTabContentsEvent | 包帯を TOOLS_AND_UTILITIES タブに追加 | `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)` を `.register(out -> out.accept(...))` で使う。`modifyOutput(FabricCreativeModeTabOutput)` | 不要 | |
| Medicalsystemcore.java:249 FMLClientSetupEvent | Entity / BE レンダラー、MenuScreens（IV スタンドと医療バッグ）、RenderLayer cutout、ItemProperties fluid_level（パック 6 種）、ItemColors（パック 6 種）を登録 | `ClientModInitializer#onInitializeClient` で行う。内訳は以下 | 不要 | 内訳: `EntityRendererRegistry.register(EntityType, EntityRendererProvider)`。`BlockEntityRendererRegistry.register(BlockEntityType<E>, BlockEntityRendererProvider<? super E,? super S>)`（`BlockEntityRenderers.register` も Fabric の AW で public）。`MenuScreens.register` も AW で public。**RenderLayer の指定は不要**: 26.2 には `ItemBlockRenderTypes` も Fabric の BlockRenderLayerMap も無く、チャンクの描画レイヤーはテクスチャの透過から自動で決まる（`BakedQuad.MaterialInfo.layer()`）。半透明を強制するときだけ、モデルの material に `force_translucent` を付ける。ItemProperties と ItemColors は 8 節の items JSON に移す |
| Medicalsystemcore.java:299 EntityRenderersEvent.RegisterRenderers | ストレッチャーのレンダラーを登録（ClientSetup と二重登録） | `EntityRendererRegistry.register` を 1 回だけ | 不要 | |
| Medicalsystemcore.java:305 EntityRenderersEvent.RegisterLayerDefinitions | StretcherModel のレイヤー定義を登録 | `ModelLayerRegistry.registerModelLayer(ModelLayerLocation, TexturedLayerDefinitionProvider)`。`LayerDefinition createLayerDefinition()` | 不要 | |
| Medicalsystemcore.java:311 ModelEvent.RegisterAdditional | 独立モデル item/rescue_tablet_base を登録 | 不要（items JSON から直接参照できる）。どうしても必要なら fabric-model-loading-api-v1 の `ModelLoadingPlugin` / `ExtraModelKey` | 不要 | |

### 1.4 クライアント

| Forge 使用箇所 | Forge 側でしていること | Fabric 26.2 の置き換え先（実在確認済み） | Mixin | 備考 |
|---|---|---|---|---|
| client/ClientInputHandler.java:21 TickEvent.PlayerTickEvent（client, END） | 自分なら QTE・治療・ギブアップのタイマーを進める。**クライアント上の全プレイヤー**のうち意識不明の者に、視点固定・姿勢維持・移動停止をかける | `ClientTickEvents.END_CLIENT_TICK`（`onEndTick(Minecraft)`）で、自分のタイマー処理をしたあと `mc.level.players()` を回す | 任意 | Forge は各 Player の tick 内で呼ばれていた。厳密に合わせるなら `Player#tick` の TAIL に Mixin |
| ClientInputHandler.java:62 MovementInputUpdateEvent | 意識不明時に前後左右・ジャンプ・しゃがみの入力を 0 にする | 無し | **要** | 26.2 の入力は `ClientInput.keyPresses`（record `Input(forward, backward, left, right, jump, shift, sprint)`、public）と `moveVector`（protected Vec2）。Forge の発火位置は `LocalPlayer#aiStep` の `this.input.tick()` 直後。候補は `LocalPlayer#aiStep` の `INVOKE ClientInput#tick` に shift=AFTER で入れる（moveVector には Accessor が要る）か、`KeyboardInput#tick` の TAIL |
| ClientInputHandler.java:81 InputEvent.MouseButton.Pre | QTE 中なら右クリックを離した時点で QTE を止める。画面を開いておらず意識不明なら全クリックを**キャンセル** | 無し（`ScreenMouseEvents` は Screen 内だけ） | **要** | `MouseHandler#onButton(long, MouseButtonInfo, int action)` の HEAD に cancellable で入れる。`mc.screen` は 26.2 では `mc.gui.screen()` |
| client/ClientStretcherHandler.java:24 TickEvent.ClientTickEvent | 中身は実質何もしない（ループするだけ） | `ClientTickEvents.END_CLIENT_TICK` | 不要 | 削除してよい |
| client/ClientWorldHandler.java:18 LevelEvent.Unload（client） | クライアントの姿勢制御状態をクリア | `ClientPlayConnectionEvents.DISCONNECT`（`onPlayDisconnect(ClientPacketListener, Minecraft)`）。ディメンション移動も拾うなら `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`（`afterLevelChange(Minecraft, ClientLevel)`）も併用 | 不要 | DISCONNECT の呼ばれるスレッドは「確認できなかったもの」を参照 |
| ClientMedicalStateCleaner.java:20、ClientRescueStateCleaner.java:19 ClientPlayerNetworkEvent.LoggingOut（2 件） | 切断時に、医療・QTE・治療・ギブアップ・姿勢制御・マウス・担架姿勢のクライアント状態（前者）と、追跡中の通報・失効リスト（後者）を無条件に消す | `ClientPlayConnectionEvents.DISCONNECT` | 不要 | Fabric の DISCONNECT は `ClientPacketListener` を受け取るので、Forge の LoggingOut にあった「接続が null」の場合分けは要らない |
| client/ClientConfigHandler.java:31 ClientPlayerNetworkEvent.LoggingOut | **リモートサーバーから切断したら**、同期された設定値を既定値へ戻す（`MedicalConfig.resetToDefaults(ConfigKey::isClientSynced)`）。シングルプレイ（統合サーバー）では戻さない（同じ JVM で保管庫を共有しているため） | `ClientPlayConnectionEvents.DISCONNECT`。接続は `handler.getConnection()`（`ClientPacketListener#getConnection()` は public、ClientPacketListener.java:2436）で取り、`connection.isMemoryConnection()` で統合サーバーを判定する | 不要 | `Connection#isMemoryConnection()` は 26.2 にそのまま実在し、チャンネルの型（`LocalChannel` / `LocalServerChannel`）だけを見るので（Connection.java:402-404）、チャンネルが閉じた後でも答える。`Minecraft#hasSingleplayerServer()` は信頼できない: `Minecraft#disconnect` は `connection.close()`（Minecraft.java:2188）の後で `singleplayerServer` を null にする（:2199-2200）ので、DISCONNECT がどちらの前後で呼ばれるかによって結果が変わり、その順序は確定していない |
| client/ClientHudHandler.java:28 RenderGuiOverlayEvent.Pre（CROSSHAIR） | 治療中・ギブアップ長押し中はクロスヘアを**キャンセル**してバーを描く（ギブアップ中はバーの上に文字も）。QTE 中・結果表示中は**キャンセルせず**除細動器モニター（DefibMonitorHud）を描く。ダウン中（ギブアップ有効時）はクロスヘアを残したままヒント文字を描く | `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, orig -> (g, dt) -> {...})`。クロスヘアを残す分岐では `orig.extractRenderState(g, dt)` を呼ぶ | 不要 | 8 節の HUD の項に詳細（呼び出し形、画面サイズ、partialTick、背景テクスチャ） |
| client/DebugOverlayHandler.java:14 CustomizeGuiOverlayEvent.DebugText | 空 | 不要（必要なら fabric-debug-api-v1） | 不要 | 削除してよい |

## 2. capability PlayerMedicalData → Data Attachment API（fabric-data-attachment-api-v1 2.2.19、stable）

- `AttachmentRegistry.create(Identifier, Consumer<AttachmentRegistry.Builder<A>>)`、`createPersistent(Identifier, Codec<A>)`、`createDefaulted(Identifier, Supplier<A>)`、`builder()`
- `Builder<A>` のメソッド: `persistent(Codec<A>)`、`copyOnDeath()`、`initializer(Supplier<A>)`、`syncWith(StreamCodec<? super RegistryFriendlyByteBuf, A>, AttachmentSyncPredicate)`、`syncWith(..., AttachmentSyncPredicate, int)`、`buildAndRegister(Identifier)`
- `AttachmentSyncPredicate` は `all()` / `targetOnly()` / `allButTarget()`
- 取得・設定: `AttachmentTarget`（Entity に実装済み）の `getAttached`、`getAttachedOrCreate(type[, Supplier])`、`getAttachedOrThrow`、`getAttachedOrSet`、`getAttachedOrElse`、`getAttachedOrGet`、`setAttached`、`modifyAttached(type, UnaryOperator)`、`hasAttached`、`removeAttached`、`onAttachedSet(type)`
- **同期は `setAttached` 経路（と接続時の初回同期）でしか送られない**（AttachmentTargetsMixin のバイトコードで確認）。現行の PlayerMedicalData は毎 tick その場で書き換えるので、syncWith を付けても同期されない。推奨は `persistent(Codec)` と `initializer` だけ使い、同期は既存の CoreStats payload に任せる形
- 永続化は保存のタイミングで codec を通るので、その場の書き換えでも保存はされる
- Codec は 15 フィールド（bloodLevel、heartStatus、heartRate、hasFracture、gunshotWounds、tickCounter、bleedingSpeed、resuscitationChance、cardiacArrestTimer、previousHeartStatus、damageImmune、isConscious、cycleTime、heartVectorX、heartVectorY）と、点滴の接続先 `Optional<BlockPos>`（キー `transfusingFromStandPos`）の RecordCodecBuilder か、既存の save/load を `CompoundTag.CODEC.xmap` で包む

### 2.1 死亡と End 帰還でのコピー（確定）

結論（a）: **`copyOnDeath()` を付けない attachment は、死亡ではコピーされず、End 帰還ではコピーされる**。「死亡では引き継がない・End 帰還では引き継ぐ」は `persistent(Codec)` と `initializer` だけで満たせる（`copyOnDeath()` を付けてはいけない: 死亡でも引き継がれる）。

根拠（バイトコードと 26.2 ソース）:
1. `AttachmentEntrypoint#onInitialize` が `ServerPlayerEvents.AFTER_RESPAWN` に、`AttachmentTargetImpl.transfer(oldPlayer, newPlayer, !alive)` を呼ぶラムダを登録している。
2. `transfer(src, dst, boolean)` の第 3 引数が true のときだけ `copyOnDeath()` の attachment に絞る。false なら**全 attachment**を `dst.setAttached(type, value)` で移す。
3. `AFTER_RESPAWN` は `PlayerList#respawn` の TAIL で、第 3 引数 `alive` に `respawn(player, keepAllPlayerData, reason)` の `keepAllPlayerData` をそのまま渡す（`PlayerListMixin#afterRespawn`）。
4. 26.2 の呼び出し元は 2 つだけ: `ServerGamePacketListenerImpl.java:1858` の End 帰還（`wonGame` のとき `respawn(player, true, CHANGED_DIMENSION)`）と、`:1867` の死亡（`respawn(player, false, KILLED)`）。
5. したがって End 帰還は `alive=true` → `transfer(..., false)` で全コピー、死亡は `alive=false` → `transfer(..., true)` で `copyOnDeath` のみ。

注意（採用時に決める点）:
- **順序**: Fabric の転送リスナーは `AFTER_RESPAWN` の既定フェーズに登録されている。本 MOD の `AFTER_RESPAWN` リスナー（姿勢の復元）を既定フェーズで登録すると、先に走る保証が無い（MOD の初期化順に依存）。選択肢は 2 つ: ① 自前のフェーズを作り、`event.addPhaseOrdering(Event.DEFAULT_PHASE, 自前フェーズ)` で転送の後に走らせ、`event.register(自前フェーズ, listener)` で登録する（`Event#addPhaseOrdering(Identifier, Identifier)`、`Event#register(Identifier, T)` は実在）。② 姿勢の復元を `COPY_FROM`（`restoreFrom` の TAIL）での手動コピーの直後に行う。`COPY_FROM` は `respawn` の中で `AFTER_RESPAWN` より前に走るので、フェーズの順序に依存しない。現行の `onPlayerRespawn` は新しいインスタンスの状態を「必ず作り直す」ためにあり、`handleConsciousnessState` は意識の変化があったときしか姿勢を設定しないので、AFTER_RESPAWN での復元を単純に省くことはできない。
- **同一オブジェクト**: 転送は `setAttached(type, 旧プレイヤーの値)` で、値は**同じインスタンス**が新プレイヤーに付く（Forge は saveNBTData/loadNBTData でコピーしていた）。旧プレイヤーは破棄されるので共有は問題にならないが、永続化されない一時フィールド（`needsCoreStatsSync` の最終同期 tick など）も持ち越される。
- 手動コピーが要る場合は `ServerPlayerEvents.COPY_FROM`（`ServerPlayer#restoreFrom` の TAIL）で行う。ただし、その場合も AFTER_RESPAWN の自動転送が後から同じ値を `setAttached` するだけで、害は無い。

## 3. SimpleChannel の 14 パケット → CustomPacketPayload（fabric-networking-api-v1 6.3.4）
- payload の定義: `record X(...) implements CustomPacketPayload`。`type()` は `CustomPacketPayload.Type<? extends CustomPacketPayload>` を返す。TYPE は `new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MODID, "..."))`（`CustomPacketPayload.createType(String)` は minecraft 名前空間になるので使わない）
- CODEC: `CustomPacketPayload.codec(StreamMemberEncoder, StreamDecoder)` で `StreamCodec<RegistryFriendlyByteBuf, X>` を作る
- 登録: `PayloadTypeRegistry.clientboundPlay()` / `serverboundPlay()`（playS2C / playC2S という名前ではない）の `.register(Type<T>, StreamCodec<? super B, T>)`。大きいものは `registerLarge(..., int)`
- サーバー受信: `ServerPlayNetworking.registerGlobalReceiver(Type<T>, PlayPayloadHandler<T>)`、`receive(T, Context)`。Context は `server()` / `player()` / `responseSender()`
- クライアント受信: `ClientPlayNetworking.registerGlobalReceiver(Type<T>, PlayPayloadHandler<T>)`。Context は `client()` / `player()`
- ハンドラは impl 側で `execute` を通してメインスレッドで実行されるので、`enqueueWork` は不要
- 送信: `ServerPlayNetworking.send(ServerPlayer, payload)`、`ClientPlayNetworking.send(payload)`
- 宛先: TRACKING_ENTITY_AND_SELF は `PlayerLookup.tracking(Entity)` に**自分を足す**。ALL は `PlayerLookup.all(server)`
- ソースセット: build.gradle が `splitEnvironmentSourceSets()` なので、payload の record と PayloadTypeRegistry は main、Client 側の receiver は client に置く。これで `DistExecutor`（5 箇所: OpenRescuePortal、RemoveRescueData、RevokedLicensesSync、SyncAllRescueData、UpdateRescueData）は消える
- FriendlyByteBuf: `readUUID/readEnum/readBlockPos/readUtf(int)/readCollection/readVarInt` は残っている。`readResourceLocation`/`writeResourceLocation` は `readIdentifier`/`writeIdentifier`

現行の 14 件（`ModPackets.register()` の登録順）。フィールドの型は各クラスの `toBytes` の書き出し順。

| パケット | 方向 | 中身（書き出し順） | 備考 |
|---|---|---|---|
| ClientboundCoreStatsPacket | S→C | UUID playerUUID、float bloodLevel、Enum HeartStatus、float bleedingSpeed、float resuscitationChance、boolean isConscious | 医療データから作るコンストラクタ（IPlayerMedicalData）はファクトリにする |
| ClientboundStartQTEPacket | S→C | int targetEntityId | |
| ServerboundFinishHealPacket | C→S | int targetEntityId | |
| ServerboundQTETResultPacket | C→S | int targetEntityId、Enum QTEResult result | 位置はサーバーが保持する PendingQteManager の記録から取る（パケットに含めない） |
| ServerboundUpdateRescueDataPacket | C→S | int rescueId、Enum UpdateType、`CHECKBOX` なら boolean isDispatch・boolean isTreatment、`MEMO` なら Utf memo（最大 `RescueRules.MEMO_MAX_LENGTH`） | **型で中身が変わる**。`StreamCodec.of(encoder, decoder)` で型を見て分岐するか、2 つの record に分ける |
| ClientboundSyncAllRescueDataPacket | S→C | int size、RescueData × size | |
| ServerboundRequestRescueListPacket | C→S | 空 | |
| ClientboundUpdateRescueDataPacket | S→C | boolean isNew、RescueData | |
| ServerboundGiveUpPacket | C→S | 空 | |
| ClientboundOpenRescuePortalPacket | S→C | 空 | |
| ClientboundConfigSyncPacket | S→C | VarInt 件数、件数 × (Utf id、byte 型タグ、値)。型タグは 0=int、1=double、2=boolean、3=Utf | `Map<String, Object>`。受信側は `MedicalConfig.setAll` で検証して適用する。未知の型タグは例外にする（続行すると以降を読めないため） |
| ServerboundDeleteRescueDataPacket | C→S | int rescueId | |
| ClientboundRemoveRescueDataPacket | S→C | int rescueId | |
| ClientboundRevokedLicensesSyncPacket | S→C | コレクション（VarInt 件数 + Utf × 件数） | `Set<String>` |

RescueData の中身（`toBytes`）は、int id、UUID reporterUuid、Utf name、Enum RescueCategory category、long reportTime、Utf description、boolean isDispatch、boolean isTreatment、BlockPos location、Identifier（dimension の location）、Utf memo（null は空文字で送る）。空の payload は `StreamCodec.unit(INSTANCE)` で足りる。

## 4. 登録（DeferredRegister → バニラの Registry.register）
**Block**
- `Block.Properties` に **`setId(ResourceKey<Block>)` が必須**。無いと "Block id not set" で落ちる（BlockBehaviour.java:1155,1289）。
- `Blocks.register(ResourceKey<Block>, Function<Properties,Block>, Properties)` は Fabric の transitive AW で public になっていて、中で setId と Registry.register までやってくれる。
- `Properties.copy` は `ofFullCopy` / `ofLegacyCopy` に変わった。
- `Blocks.WHITE_WOOL` は無くなり `Blocks.WOOL.white()`（ColorCollection record）になった。

**Item**
- `Item.Properties` に **`setId(ResourceKey<Item>)` が必須**（"Item id not set"、Item.java:651）。
- `Items.registerItem` / `registerBlock` は **private のまま**（AW 無し）なので、手動で `Registry.register(BuiltInRegistries.ITEM, key, item)` する。
- BlockItem は `new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix())` で作る。
- `Item.BY_BLOCK` への登録は Fabric registry-sync の `BlockItemTracker`（RegistryEntryAddedCallback）が `registerBlocks` を自動で呼ぶ。

**BlockIds / BlockItemIds / ItemIds**（`net.minecraft.references`）
- バニラの ID 定数を並べたクラス。中身は `withDefaultNamespace` なので MOD では使えない。
- 型だけなら `BlockItemId.create(Identifier blockId, Identifier itemId)` で `record BlockItemId(ResourceKey<Block>, ResourceKey<Item>)` を作れる。block と item のキーを組で持つのに使える。

**BlockEntityType**
- 26.2 ではバニラのコンストラクタ `new BlockEntityType<>(BlockEntitySupplier, Set<Block>)` が public になった。登録は `Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type)`。
- `FabricBlockEntityTypeBuilder.create(Factory, Block...).build()` も使える（非推奨なのは `build(Type)` だけ）。
- `.build(null)` は無くなる。

**MobEffect**
- `Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Identifier, effect)` は `Holder.Reference<MobEffect>` を返す。`MobEffectInstance` は `Holder<MobEffect>` を受け取る。
- `applyEffectTick(ServerLevel, LivingEntity, int)` は boolean を返す。
- `isDurationEffectTick` は `shouldApplyEffectTickThisTick(int, int)` に変わった。

**EntityType**
- `EntityType.Builder.of(...).sized().clientTrackingRange(int clientChunkRange).updateInterval()` までは同じ。
- `setShouldReceiveVelocityUpdates` は `alwaysUpdateVelocity(boolean)`（FabricEntityType.Builder から interface-inject される）に変わった。
- `.build(ResourceKey<EntityType<?>>)` はキー必須。
- 備考: clientTrackingRange の単位はチャンク。Medicalsystemcore.java:174 のコメントの「64 ブロック」は誤り。

**CreativeModeTab**: `FabricCreativeModeTab.builder()`（バニラの `builder(Row, int)` の代わり）で作り、`Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ...)`。

**MenuType**
- データ付きは `new ExtendedMenuType<T,D>(ExtendedFactory<T,D> (int, Inventory, D), StreamCodec<? super RegistryFriendlyByteBuf, D>)` で作る。
- IVStand は D=BlockPos で `BlockPos.STREAM_CODEC`、MedicalBag は D=InteractionHand で `InteractionHand.STREAM_CODEC`。
- RescuePortal はデータ無しなので `new MenuType<>(RescuePortalMenu::new, FeatureFlags.VANILLA_SET)`（コンストラクタは transitive AW で public）。
- 開く側: `NetworkHooks.openScreen`（IVStandBlock.java:101、MedicalBagItem.java:40）は `player.openMenu(provider)` になる。provider は `ExtendedMenuProvider<D>` を実装し、`D getScreenOpeningData(ServerPlayer)` を返す。

**その他**
- `StretcherEntity#getAddEntityPacket` の override（NetworkHooks.getEntitySpawningPacket、StretcherEntity.java:299）は削除する。26.2 の署名は `getAddEntityPacket(ServerEntity)` で、バニラのままでよい。
- `EntityDataSerializers.OPTIONAL_UUID` は**無くなった**。`OPTIONAL_LIVING_ENTITY_REFERENCE`（`Optional<EntityReference<LivingEntity>>`）を使う。
- `registryOrThrow(...).getHolderOrThrow(...)` は `lookupOrThrow(...).getOrThrow(...)` になった。

## 5. ItemStackHandler / ITEM_HANDLER
- **IVStandBlockEntity**: `ItemStackHandler(3)`（onContentsChanged、isItemValid、getSlotLimit）は `SimpleContainer` の匿名サブクラスで `setChanged()` を override する形にする（26.2 の SimpleContainer に listener は無い）。または BE 自体に `Container` を実装する。`getCapability` の override（IVStandBlockEntity.java:64）は削除。
  - ホッパー連携が要るなら、`Container` を実装するか、`ItemStorage.SIDED`（fabric-transfer-api-v1 の `BlockApiLookup<Storage<ItemVariant>, Direction>`）を使う。
  - スロット制限は Slot の `mayPlace(ItemStack)` / `getMaxStackSize(ItemStack)` で行う。
  - `SlotItemHandler` は普通の `Slot` に置き換える。
  - `ItemHandlerHelper.giveItemToPlayer`（HeadsideMonitorBlock.java:90、IVStandBlock.java:130）は `player.getInventory().add(stack)` を試し、入らなければ `player.drop(stack, false)` にする（バニラの `ServerPlayer#handleExtraItemsCreatedOnUse`、ServerPlayer.java:2066 と同じ形）。
- **MedicalBagMenu**: 中身の保存は `DataComponents.CONTAINER`（`ItemContainerContents`、最大 256 スロット）が使える。
  - 読み出しは `ItemContainerContents.fromItems(List<ItemStack>)` と `copyInto(NonNullList<ItemStack>)`。
  - 書き込みは `stack.set(DataComponents.CONTAINER, ...)`。
  - `ForgeRegistries.ITEMS.getKey`（MedicalBagMenu.java:189）は `BuiltInRegistries.ITEM.getKey` に変わる。

## 6. アイテムの NBT → データコンポーネント
- 使用箇所（`getTag`/`getOrCreateTag`）: DefibrillatorBlock:143、ElectrodeItem:36,44、ServerboundQTETResultPacket:83（以上 DefibrillatorPos）。HeadsideMonitorBlock:84、HeadsideCableItem:33,40（MonitorPos）。TubeItem:52,69,108（StartPos）。FluidPackItem（FluidVolumeTicks、FluidLevel。7 か所）。DoctorCardItem（isGenuine、ownerUUID、doctorName、licenseNumber、issueDate。6 か所）と MedicalAuthorizationUtil:92-94,124（医師カードの項目の読み取り）。MedicalBagMenu:50,51,84（Inventory）。DefibrillatorBlockEntity:225 は BE の更新パケットの `getTag`（アイテムではない、7 節）。
- 26.2 での登録: `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, DataComponentType.<T>builder().persistent(Codec).networkSynchronized(StreamCodec).build())`。
- 位置系は `BlockPos.CODEC` と `BlockPos.STREAM_CODEC` で持てる。`NbtUtils.readBlockPos` / `writeBlockPos`（RescueData、PlayerMedicalData が使う）は 26.2 に**存在しない**。
- 汎用 NBT が必要なら `DataComponents.CUSTOM_DATA` を使う。`CustomData.update(type, stack, Consumer<CompoundTag>)`、`CustomData.set`、`copyTag()`。
- CompoundTag の getter は `Optional` を返す形（`getInt` / `getIntOr` / `getCompoundOrEmpty`）。

## 7. BlockEntity の保存・読込
- `saveAdditional(ValueOutput)`、`loadAdditional(ValueInput)`。`load(CompoundTag)` は無くなった。
- ValueOutput: `putInt/putFloat/putBoolean/putString/...`、`store(String, Codec<T>, T)`、`storeNullable`、`child`、`list`。
- ValueInput: `getIntOr`、`getInt`（Optional）、`read(String, Codec)`、`child`、`childOrEmpty`、`listOrEmpty`、`lookup()`。
- `ContainerHelper.saveAllItems(ValueOutput, NonNullList)` と `loadAllItems(ValueInput, NonNullList)` がある。
- `getUpdateTag(HolderLookup.Provider)` と、`getUpdatePacket()` から `ClientboundBlockEntityDataPacket.create(this)` を返す形。
- Forge 独自の `onDataPacket` / `handleUpdateTag` / `onLoad` / `invalidateCaps` は無い。クライアントでは `loadWithComponents` が自動で呼ばれる。
- Entity は `readAdditionalSaveData(ValueInput)` と `addAdditionalSaveData(ValueOutput)`。UUID は `UUIDUtil.CODEC` で store する。
- Block の override の変化:
  - `use` は `useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` か `useItemOn` に分かれた。
  - `onRemove` は `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)` か、BE 側の `preRemoveSideEffects(BlockPos, BlockState)` に移った。
- Item の override の変化:
  - `use` は `InteractionResult` を返す（InteractionResultHolder は無くなった）。
  - `inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)`
  - `appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`
  - `getUseDuration(ItemStack, LivingEntity)`
  - `getUseAnimation` は `ItemUseAnimation` を返す。

## 8. クライアント描画
**BlockEntityRenderer**（Defibrillator / HeadsideMonitor / IVStand）
- `BlockEntityRenderer<T, S extends BlockEntityRenderState>` を実装する。`S createRenderState()`、`extractRenderState(T, S, float, Vec3, CrumblingOverlay)`、`submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`。
- 即時描画（`MultiBufferSource#getBuffer` + `vertex().endVertex()`）は `submitNodeCollector.submitCustomGeometry(PoseStack, RenderType, (PoseStack.Pose, VertexConsumer) -> ...)` に置き換える。
- VertexConsumer の書き方は `addVertex(Matrix4fc|PoseStack.Pose, x, y, z).setColor(...).setUv(...).setLight(...).setNormal(...)`。endVertex は無い。
- RenderType は `RenderTypes`（rendertype パッケージ）にある。`debugQuads()`、`debugFilledBox()`、`textBackground()`、`lines()`、`entityCutout(Identifier)` などが実在。`RenderType.gui()` / `cutout()` に相当するものは RenderTypes に無い。
- 文字: `font.drawInBatch` は `submitNodeCollector.submitText(PoseStack, x, y, FormattedCharSequence, dropShadow, Font.DisplayMode, light, color, bgColor, outlineColor)` に変わる。
- IVStand のアイテム表示: `ItemRenderer.renderStatic` の代わりに、extract 側で `itemModelResolver.updateForTopItem(ItemStackRenderState, stack, ItemDisplayContext.FIXED, level, owner, seed)`、submit 側で `itemState.submit(poseStack, collector, light, overlay, outline)`（CampfireRenderer と同じ方式）。itemModelResolver は `BlockEntityRendererProvider.Context.itemModelResolver()` から取る。

**エンティティ（Stretcher）**
- `EntityRenderer<T, S extends EntityRenderState>` の `createRenderState()`、`extractRenderState(T, S, float)`、`submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`。
- `model.renderToBuffer` は `submitNodeCollector.submitModel(Model<? super S>, S, PoseStack, RenderType, light, overlay, outlineColor, crumbling)` などに変わる。
- `EntityModel<S extends EntityRenderState>`。

**RescueTabletBEWLR**
- `IClientItemExtensions` と BEWLR は廃止され、`assets/<ns>/items/rescue_tablet.json` に `"type":"minecraft:special"` を書く方式になる。
- `SpecialModelRenderer<T>` の `submit(T, PoseStack, SubmitNodeCollector, light, overlay, hasFoil, outlineColor)` と `T extractArgument(ItemStack)`。Unbaked 側の型は `MapCodec<? extends Unbaked<T>>`。
- 登録は `SpecialModelRenderers.ID_MAPPER.put(Identifier, MapCodec)`（Fabric の transitive AW で public）。
- **ItemDisplayContext は渡されない**。左手の反転や「手と GUI のときだけオーバーレイを出す」判定は、items JSON の `minecraft:select`（property `minecraft:display_context`）と `minecraft:composite`（ベースモデル＋special）で組む。
- RescueTabletBEWLR は ClientTrackingManager（追跡中の通報）を読む。special model 側からも static で読めるので、通報の同期経路は変わらない。

**流体パック**
- ItemProperties の fluid_level は、FluidLevel（整数 0〜9）を `DataComponentType<Integer>` にしたうえで、items JSON の `minecraft:select` に property `minecraft:component` を書けばクライアントコード無しで済む（`SelectItemModelProperties` に `component` の登録を確認）。
- range にしたいなら、`RangeSelectItemModelProperties.ID_MAPPER`（AW で public）に `RangeSelectItemModelProperty` を登録する。`float get(ItemStack, ClientLevel, ItemOwner, int)`。
- ItemColors（PackColor の tintIndex 1）は items JSON の `"tints":[{"type":"minecraft:constant","value":-1},{"type":"minecraft:constant","value":<色>}]`。独自のものは `ItemTintSources.ID_MAPPER`（AW で public）、`int calculate(ItemStack, ClientLevel, LivingEntity)`。

**HUD（ClientHudHandler / DefibMonitorHud）**

GuiGraphicsExtractor（`net.minecraft.client.gui`）の API（結論 c）:
- `Matrix3x2fStack pose()`。**2D**（JOML の 3x2 行列スタック）で、z は無い。使い方は `pose().pushMatrix()` / `popMatrix()` / `translate(float, float)` / `scale(float, float)` / `rotate(float)`（`Hud`、`WinScreen`、`AdvancementTab`、`GuiGraphicsExtractor` 自身で同じ形を使っている）。Forge の `pushPose()` / `popPose()` / `translate(x, y, z)` / `scale(x, y, z)` とは違う。
- `void fill(int x0, int y0, int x1, int y1, int argb)`（x0>x1、y0>y1 でも内部で入れ替える）。ほかに `fillGradient`、`outline`、`horizontalLine`、`verticalLine`。呼ぶと、その時点の `pose()` のコピーを付けた矩形が積まれる（即時描画ではない）。
- 文字: `text(Font, Component, int x, int y, int color)`（影あり）と `text(Font, Component, int x, int y, int color, boolean dropShadow)`。`String` と `FormattedCharSequence` の版もある。ほかに `centeredText`。**`color` の alpha が 0 だと何も描かれない**（`ARGB.alpha(color) != 0` の判定）ので、`0xFFFFFF` のような alpha 無しの色は `0xFFFFFFFF` にする。Forge の `drawString` は無い。
- `int guiWidth()` / `int guiHeight()`（引数なし）。画面は `Minecraft.getInstance().getWindow().getGuiScaledWidth()` と同じ値。
- `Font` は `Minecraft.getInstance().font`（public final）、`font.width(Component)` は `width(FormattedText)` として実在。
- HudElement の描画メソッドは `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`。partialTick は `deltaTracker.getGameTimeDeltaPartialTick(false)`（`Hud` が 4 か所で `false` を使っている。フリーズ中は 1.0 を返す）。

DefibMonitorHud が `fill`・文字描画・`pose()` の拡大だけで書かれていることを確認した結論: **そのまま移せる**。機械的な置き換えは次の 4 点だけ。
1. 型 `GuiGraphics` → `GuiGraphicsExtractor`。
2. `g.drawString(font, text, 0, 0, color, shadow)` → `g.text(font, text, 0, 0, color, shadow)`。
3. `g.pose().pushPose()` → `pushMatrix()`、`popPose()` → `popMatrix()`、`translate(x + ox, y, 0)` → `translate(x + ox, y)`、`scale(scale, scale, 1.0f)` → `scale(scale, scale)`。
4. 呼び出し側（HudElement のラムダ）で、画面サイズを `g.guiWidth()` / `g.guiHeight()`、partialTick を `deltaTracker.getGameTimeDeltaPartialTick(false)` で渡す。

DefibMonitorHud の色はすべて `0xFF` で始まるので alpha 判定に引っかからない。フラッシュは `(alpha << 24) | rgb` で alpha が 0 のときは先に return している。ECG は 1 列ごとに `fill` を積む（最大で幅 150 回ほど）が、これは Forge でも同じで、性能は未測定。

CROSSHAIR の置き換え（結論 d）:
- `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, dt) -> { ... })`。`HudElement#extractRenderState(GuiGraphicsExtractor, DeltaTracker)` を返す。Fabric の `HudMixin#wrapCrosshair` が `Hud#extractCrosshair` の呼び出しを包んでいる。
- `Hud#extractRenderState` は、HUD を隠している（F1、`isHidden`）間と `LevelLoadingScreen` の間は呼ばない。**バニラのクロスヘア自体の条件（一人称視点、スペクテイターの対象、3D クロスヘアのデバッグ設定）は `original` の中**にある。置き換え要素が自前でバーを描く分岐は、これらの条件に関係なく描かれる（Forge の `RenderGuiOverlayEvent.Pre` も、`ForgeGui.java:125` で各オーバーレイの `render` の前に無条件で投稿されるので同じ）。
- 現行 ClientHudHandler の分岐との対応（優先順位は現行のまま）:
  1. 治療中（`ClientHealingManager.isHealing()`）: バーを描く。`original` は呼ばない（クロスヘアを消す）。
  2. QTE 中または結果表示中: `DefibMonitorHud.render(g, g.guiWidth(), g.guiHeight(), partialTick)` を呼び、**`original` も呼ぶ**（照準を残す）。
  3. ギブアップ長押し中: バーと、バーの上に文字（`hud.medicalsystemcore.transport_hint` か `give_up_hint`）を描く。`original` は呼ばない。
  4. ダウン中で `GIVE_UP_ENABLED`: ヒント文字（`sneak_transport` か `sneak_give_up`）を描き、**`original` も呼ぶ**。
  5. それ以外: `original` を呼ぶ。
- バーの背景: 現行は `textures/gui/widgets.png`（`blit(WIDGETS, x, y, 0, 66, 200, 20)`）で、**26.2 の client jar には無い**。代わりに `minecraft:widget/button` スプライト（`assets/minecraft/textures/gui/sprites/widget/button.png` と `.mcmeta` が client jar に実在）を `blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("widget/button"), barX, barY, 200, 20)` で描くか、`fill` の枠で描く。見た目が旧 widgets.png の 66 行目と同じかは未確認。`RenderSystem.setShaderTexture` は不要（削除）。
- バーの中身（ウール）: `blit` の引数順が変わった。新しい 12 引数版は `blit(RenderPipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int srcWidth, int srcHeight, int textureWidth, int textureHeight)`（width/height が描画サイズ、srcWidth/srcHeight が切り出しサイズ）。現行の `blit(tex, barX+4, barY+5, GUIWidth, 10, 0, 0, GUIWidth/2, 5, 16, 16)` は `blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/block/white_wool.png"), barX + 4, barY + 5, 0f, 0f, GUIWidth, 10, GUIWidth / 2, 5, 16, 16)` になる（`textures/block/white_wool.png` は client jar に実在）。

**Screen**
- `GuiGraphics` は `GuiGraphicsExtractor` に変わった。
- `render(...)` は `extractRenderState(GuiGraphicsExtractor, int, int, float)` に、`renderBg` は `extractBackground(GuiGraphicsExtractor, int, int, float)` に、`renderLabels` は `extractLabels` に変わる（例: DispenserScreen）。
- `fill(x0, y0, x1, y1, col)`。`drawString` は `text(Font, Component|String, x, y, color[, shadow])` / `centeredText`。
- `blit` は **`RenderPipeline` が第 1 引数**（`blit(RenderPipelines.GUI_TEXTURED, Identifier, x, y, float u, float v, w, h, [srcW, srcH,] texW, texH[, color])`）。スプライトは `blitSprite(RenderPipeline, Identifier, x, y, w, h)`。
- `mouseClicked(MouseButtonEvent, boolean doubleClick)`。`MouseButtonEvent` は record で `(double x, double y, MouseButtonInfo)`。
- `mouseScrolled(double, double, double, double)` は同じ。
- `MultiLineEditBox` は `MultiLineEditBox.builder()` で作る。
- **`Minecraft#setScreen` は無い**。`Minecraft.getInstance().gui.setScreen(Screen)` と `gui.screen()` を使う（`Minecraft#gui` は public final の Gui）。

## 9. 設定
設定は common の `MedicalConfig`（キーと値の保管庫）と `MedicalConfigFile`（JSON の読込・保存）で、MC に依存しない。ファイルは `config/medicalsystemcore.json`。Forge の `FMLPaths.CONFIGDIR` は `FabricLoader.getInstance().getConfigDir()`（`java.nio.file.Path` を返す、loader 0.20.1 に実在）に変わる。
- サーバー: SERVER_STARTING で読込、ログイン時に clientSynced キーを送信、変更時に全員へ送信（1 節）。
- クライアント: ファイルを読まず、サーバーから同期されたキーだけを持つ。リモート切断時に既定値へ戻す（1.4 節）。

## 10. リソース
- フォルダ名: レジストリのパスがそのままディレクトリ名になる。`loot_tables/blocks` は `loot_table/blocks`、`tags/items` は `tags/item` に変える（Registries.LOOT_TABLE="loot_table"、tagsDirPath="tags/"+パス）。
- `assets/<ns>/items/<id>.json` が**全アイテムで必須**（ClientItemInfoLoader が "items" を読む）。models/item の `overrides` は 26.2 の方式では使われない。
- pack_format: SharedConstants では RESOURCE_PACK_FORMAT 88.0、DATA_PACK_FORMAT 107.1。
  - pack.mcmeta は `min_format` / `max_format`（`[major, minor]` も可）方式になった。旧 `pack_format` は互換フィールドとして残っている。
  - Fabric の MOD リソースは pack.mcmeta が無くても読み込まれる（ただし実機では未検証）。
- RenderType: RenderLayer の節（1.3 節 FMLClientSetupEvent）を参照。
- 効果アイコン: `textures/mob_effect/<id>.png` は 26.2 でも読まれる（AtlasProvider が `mob_effect` ディレクトリを `mob_effect/` 接頭辞で読む）。
- 全ファイルの配置先と、新規作成する items JSON の一覧は `docs/fabric-port-plan.md` の 6 節。

## 11. damage_type
- `bleeding.json` の形式は 26.2 でも有効。DamageType.DIRECT_CODEC は message_id、scaling、exhaustion、effects（任意）、death_message_type（任意）で、`when_caused_by_living_non_player`、`hurt`、`default` もすべて実在する。
- コード側は `level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(KEY)` に直す。

## 12. SavedData（RescueSavedData / RevokedLicenseSavedData / IssuedLicenseSavedData）

結論（b）。26.2 の形:
- 型は `SavedDataType<T extends SavedData>`（record）。`new SavedDataType<>(Identifier id, Supplier<T> constructor, Codec<T> codec, DataFixTypes dataFixType)`。**Codec が必須**で、`load(CompoundTag)` と `save(CompoundTag)` の override は無くなった（`SavedData` は `setDirty()` / `setDirty(boolean)` / `isDirty()` だけを持つ抽象クラス）。
- 取得: `SavedDataStorage#computeIfAbsent(SavedDataType<T>)`（`DimensionDataStorage` は `SavedDataStorage` に改名。引数はこの 1 つだけで、`computeIfAbsent(load, create, name)` の 3 引数版は無い）。無ければ `constructor` で作って登録する。`get(type)` は null を返しうる。
- **取り方の違い**: `MinecraftServer#getDataStorage()` は `<world>/data/`、`ServerLevel#getDataStorage()`（`server.overworld().getDataStorage()`）は `<world>/dimensions/minecraft/overworld/data/`（`DimensionType.getStorageFolder`）で**別の場所**。ワールド全体のデータ（通報・免許）は `server.getDataStorage()` に置く（スコアボード、ゲームルール、マップ番号などのバニラのワールド全体のデータも同じ）。
- ファイルは `<dataFolder>/<namespace>/<path>.dat`（`Identifier#resolveAgainst`）。ID が `medicalsystemcore:rescue` なら `<world>/data/medicalsystemcore/rescue.dat`。Forge の `medicalsystemcore_rescue.dat` とは別ファイルで、中身は `{data: <codec の出力>, DataVersion: n}` の形。
- 書き込みは `scheduleSave()` / `saveAndJoin()` / `close()`。ダーティなデータを**メインスレッドで codec にかけて**から、別スレッドでファイルに書く。別スレッドが触るのは encode 済みの `CompoundTag` だけで、データ本体（`RescueSavedData` のリストなど）には触れない（`collectDirtyTagsToSave` と `encodeUnchecked` で確認）。現行の `RescueSavedData` の `synchronized` メソッド群を残すか外すかは親の判断（スレッド前提はコメントのとおりサーバースレッドのみ）。
- 読み込みに失敗（パース失敗・例外）すると、エラーをログに出して `null` を返し、`computeIfAbsent` は**空の新しいインスタンスを作って登録する**（`readSavedData`）。`set()` は新しいインスタンスをダーティにするので、壊れたファイルは次の保存で空データに上書きされる。現行の `RescueData.load` は「壊れたデータを黙って受け入れない」ために例外を投げているが、その例外は SavedDataStorage に飲まれて**空データに置き換わる**。壊れた通報を残したいなら、Codec 側で壊れた要素を許容する設計にするか、読み込み後に空かどうかを呼び出し側で判定する必要がある（親の設計判断）。
- `dataFixType` は `null` を渡せる。Fabric API の `fabric-object-builder-api-v1` が `SavedDataStorage#readTagFromDisk` の `DataFixTypes#update` 呼び出しを WrapOperation で包み、`null` のときはタグをそのまま返す（`SavedDataStorageMixin#handleNullDataFixType` のバイトコードで確認）。Fabric 自身の attachment 保存（`MinecraftServerMixin`、`ServerLevelMixin`）も `null` を渡している。バニラの `DataFixTypes` に MOD 用の値は無いので、`null` にするのが現実的。

書き換え方（3 クラス共通）:
1. `load(CompoundTag)` と `save(CompoundTag)` を、`Codec<T>` に置き換える（`RecordCodecBuilder`）。
2. `RescueSavedData` は `nextId`（int）と `rescues`（`List<RescueData>`）の 2 フィールド。`RescueData` は 11 フィールドの Codec にする（`UUID` は `UUIDUtil.CODEC`、`BlockPos` は `BlockPos.CODEC`、`dimension` は `Level.RESOURCE_KEY_CODEC`、`RescueCategory` は common（MC 非依存）の enum なので `Codec.STRING.xmap(RescueCategory::valueOf, Enum::name)`、`memo` は `Codec.STRING.optionalFieldOf("memo")`）。
3. `RevokedLicenseSavedData` は `Set<String>`（`licenses`、LinkedHashSet）。`Codec.STRING.listOf()` を `xmap` で包む。
4. `IssuedLicenseSavedData` は `Map<String, Issue>`（`Issue` は license・ownerUuid・ownerName・issueDate の record）。`Issue` の Codec のリスト。
5. `public static XxxSavedData get(MinecraftServer server)` は `server.getDataStorage().computeIfAbsent(TYPE)` にする。`TYPE` は各クラスの `public static final SavedDataType<XxxSavedData>`。
6. `RevokedLicenseSavedData` はログイン時の送信（`@SubscribeEvent`）を持っているので、イベント登録を別の場所（`ServerPlayConnectionEvents.JOIN`）へ移す。

## 13. イベント以外の Forge 固有 API の置き換え

| Forge | 使っている場所 | Fabric 26.2 |
|---|---|---|
| `ServerPlayer#server`（public フィールド） | 21 か所: MedicalAuthorizationUtil:123、DoctorCardItem:68,69,176、RescueDataManager:62,70,82,89,113、PendingHealManager:39、RevokedLicenseSavedData:86、ServerboundDeleteRescueDataPacket:39、ServerboundFinishHealPacket:68、PendingQteManager:52,58,120、ServerboundGiveUpPacket:81,140、ServerboundRequestRescueListPacket:24、ServerboundQTETResultPacket:60,62 | 26.2 では `private final MinecraftServer server`（ServerPlayer.java:233）で、AW も無い。`player.level().getServer()`（`ServerPlayer#level()` は `ServerLevel` を返し、`ServerLevel#getServer()` は public） |
| `ServerLifecycleHooks.getCurrentServer()` | ConfigSyncHandler | SERVER_STARTED で保持した `MinecraftServer` |
| `FMLPaths.CONFIGDIR` | ConfigSyncHandler | `FabricLoader.getInstance().getConfigDir()` |
| `Connection#isMemoryConnection()` | ClientConfigHandler | 同じ名前で実在（Connection.java:402） |
| `DistExecutor.unsafeRunWhenOn` | 5 つの Clientbound パケット | 不要（受信ハンドラを client に置く） |
| `NetworkHooks.openScreen` / `getEntitySpawningPacket` | IVStandBlock、MedicalBagItem、StretcherEntity | 4 節 |
| `ForgeRegistries.ITEMS.getKey` | MedicalBagMenu | `BuiltInRegistries.ITEM.getKey` |
| `ItemHandlerHelper.giveItemToPlayer` | HeadsideMonitorBlock、IVStandBlock | 5 節 |
| `IClientItemExtensions`（BEWLR） | RescueTabletItem | 8 節 |
| `@OnlyIn(Dist.CLIENT)` | ClientboundCoreStatsPacket、ClientStretcherHandler | ソースセット分離で不要 |
| `Player#setForcedPose` / `getForcedPose`（Forge が追加） | PoseController:169,182,261 | 要 Mixin の 4 番 |

## 要 Mixin の一覧
1. **ItemTossEvent**: `ServerPlayer#drop(ItemStack, boolean randomly, boolean thrownFromHand)` の HEAD に cancellable で入れ、`!randomly` で絞る（1.1 節）。
2. **MovementInputUpdateEvent**: `LocalPlayer#aiStep` の `INVOKE ClientInput#tick()` に shift=AFTER で入れる（moveVector 用に ClientInput の Accessor を作る）。または `KeyboardInput#tick` の TAIL。
3. **InputEvent.MouseButton.Pre**: `MouseHandler#onButton(long, MouseButtonInfo, int)` の HEAD に cancellable で入れる。
4. **`Player#setForcedPose` / `getForcedPose`**（core/PoseController.java:169,182,261 から呼んでいる。Forge が `Player` に追加したメソッドで、**26.2 のバニラには存在しない**）。Forge の実装は `Player#updatePlayerPose()`（Player.java:342、26.2 でも protected）の先頭で、強制姿勢があれば `setPose(forcedPose)` して return する（Player.java.patch で確認）。同じものを `updatePlayerPose` の HEAD に cancellable で入れ、フィールドと getter/setter は Mixin 側に持たせる（duck interface）。
5. **LivingDamageEvent の防具適用後の量（注入点は確定）**: `Player#actuallyHurt(ServerLevel, DamageSource, float)`（Player.java:747）。引数 `dmg`（スロット 3 の第 3 引数）を `getDamageAfterArmorAbsorb`、`getDamageAfterMagicAbsorb`、衝撃吸収の差し引きで順に書き換えていく（バイトコードで確認）。`@Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))` と MixinExtras の `@Local(argsOnly = true) float dmg` で、最終の `dmg` を取れる。`causeFoodExhaustion` はこのメソッド内に 1 回だけで、無敵（`isInvulnerableTo`）のときと `dmg == 0` のときは通らない（`BleedingRules.speedIncrease(0)` は 0 なので Forge と同じ結果）。代わりの注入点は `INVOKE Player#setHealth(F)V`（同じ値が取れる）。`source.is(ModDamageTypes.BLEEDING_KEY)` の除外は Mixin 側で行う。`actuallyHurt` は `ServerLevel` を受け取るのでサーバー専用で、クライアント判定は要らない。
6. （任意）tick の順序を合わせるなら、`Player#tick` の TAIL。

## 26.2 で大きく形が変わる領域
- **描画**: 抽出（extract）してから送る（submit）の 2 段階になった。BER・EntityRenderer・HUD・Screen のすべてで RenderState と `SubmitNodeCollector` / `GuiGraphicsExtractor` を使う。即時に頂点を流すコードは `submitCustomGeometry` に移す。BEWLR は SpecialModelRenderer と items JSON に置き換わり、表示コンテキストによる分岐は JSON に移る。RenderLayer の手動登録は無くなった。
- **BlockEntity の保存**: CompoundTag を直接触る方式から ValueInput / ValueOutput（Codec ベース）に変わった。Forge の同期フックは無くなり、バニラの getUpdateTag(Provider) と getUpdatePacket で行う。
- **アイテムデータ**: NBT（getTag / getOrCreateTag）は全廃された。独自の DataComponentType を登録するか CUSTOM_DATA を使い、バッグは DataComponents.CONTAINER に入れる。Block / Item の Properties には setId が必須。
- **ワールドの保存**: SavedData は Codec 方式（`SavedDataType`）になり、取得は `SavedDataStorage#computeIfAbsent(type)` の 1 引数になった（12 節）。

## 確認できなかったもの
- Fabric API の各モジュールは sources jar がキャッシュに無かった（data-attachment は 1.0.2 と 1.6.2 の古い版だけにある）。シグネチャとミックスインの注入位置は javap で確認したが、javadoc の文言は見ていない。
- `syncWith(..., int)` の int 引数の意味。
- `ClientPlayConnectionEvents.DISCONNECT` が呼ばれるスレッド。Fabric は `ConnectionMixin` の `channelInactive` の HEAD（netty スレッドの可能性）と `handleDisconnection` の INVOKE（メインスレッド）の 2 経路から呼ぶ（バイトコードで確認）。どちらが先に当たるかは、バイトコードからは決まらない。クライアント状態のクリア（static の Manager 群）を `Minecraft#execute` で包むかどうかは、実機で確認する。
- `ServerPlayConnectionEvents.DISCONNECT` と `PlayerList#remove` の前後関係（本 MOD は `ServerPlayerEvents.LEAVE` を使う前提なので、必要になった時点で確認）。
- Fabric の attachment 転送リスナーと本 MOD の `AFTER_RESPAWN` リスナーの実行順（既定フェーズ同士で、MOD の初期化順による。2.1 節の対策を取れば不問）。
- `minecraft:widget/button` スプライトが旧 `widgets.png` の 66 行目（200x20）と同じ見た目かどうか。スプライトの存在だけを確認した。
- 4 つの Screen と RescueDataListWidget の細部（AbstractSelectionList の Entry の描画メソッド名など）は追っていない。RescueDataListWidget は Forge 側でも独自クラスで、AbstractSelectionList は継承していない。
- pack.mcmeta が無くても Fabric が MOD リソースを読み込むことの実機確認。
- 26.2 での loot table の書式（`minecraft:explosion_decay` などの条件・関数名が同じか）。
