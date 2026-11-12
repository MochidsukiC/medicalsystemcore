Forge→Fabric 26.2 対応表です（jar・ソースで実在を確認済み）。

検証元: MC 26.2 の sources jar（common/clientOnly）、Fabric API 0.161.0+26.2 の pom に載っている各モジュールの class jar（sources jar はキャッシュに無いので javap で確認）、fabric-loader 0.20.1、Forge 1.20.1-47.4.0 の sources jar（Forge 側フック位置の確認用）。
26.2 の全体的な変化: `ResourceLocation` は `Identifier`（`Identifier.fromNamespaceAndPath`）に、`GuiGraphics` は `GuiGraphicsExtractor` に変わった。

## 1. イベント（@SubscribeEvent / addListener 全 26 件）

| Forge 使用箇所 | Forge 側でしていること | Fabric 26.2 の置き換え先（実在確認済み） | Mixin | 備考 |
|---|---|---|---|---|
| core/ModEvents.java:41 LivingDeathEvent | プレイヤーの死亡を判定。既に非 NORMAL なら NORMAL に戻して死亡を許可し CoreStats を送信。NORMAL なら**キャンセル**して HP0.1・CARDIAC_ARREST・無敵化・送信 | `ServerLivingEntityEvents.ALLOW_DEATH`。`boolean allowDeath(LivingEntity, DamageSource, float)` で false を返すとキャンセル | 不要 | `LivingEntity#hurtServer` 内の `isDeadOrDying()`（ordinal=1）を Redirect する方式で、トーテム判定の前に呼ばれる。26.2 の `die()` 呼び出し元は hurtServer（LivingEntity.java:1252）と Creaking だけなので、網羅範囲は Forge と同等。false を返しても HP は戻らないので、`setHealth(0.1f)` はハンドラ側で必須（現行と同じ）。`ServerPlayerEvents.ALLOW_DEATH` は @Deprecated |
| ModEvents.java:77 LivingHurtEvent | damageImmune なら GENERIC_KILL 以外のダメージを**キャンセル** | `ServerLivingEntityEvents.ALLOW_DAMAGE`。`boolean allowDamage(LivingEntity, DamageSource, float)` | 不要 | 呼ばれる位置は hurtServer の `isSleeping()` の直前（無敵判定・死亡判定・火炎耐性判定の後）。Forge は `Player#actuallyHurt` の先頭で量を 0 にする方式で、ノックバックや赤点滅は残っていた。Fabric で false を返すと hurtServer ごと終わるので、それらも出なくなる（挙動差）。/kill は `LivingEntity#kill` → `hurtServer(genericKill, MAX)` なので、GENERIC_KILL を除外する判定はそのまま使える |
| ModEvents.java:97 LivingDamageEvent | 読み取りのみ。BLEEDING 以外でプレイヤーが受けた量に応じて出血速度を加算する | `ServerLivingEntityEvents.AFTER_DAMAGE`。`void afterDamage(LivingEntity, DamageSource, float baseDamageTaken, float damageTaken, boolean blocked)` | 厳密互換なら要 | **値の意味が違う**: Forge の値は防具・魔法軽減・衝撃吸収の**後**（Forge パッチで確認）。Fabric の damageTaken は盾・ヘルメット補正の後で、防具の**前**（防具計算は `Player#actuallyHurt` 内）。さらに `isDeadOrDying()` のときは発火しない。閾値 2/5/10 を同じ効き方にするなら、`Player#actuallyHurt` の `setHealth` INVOKE の直前でローカルの dmg を取る Mixin が必要 |
| ModEvents.java:88 AttachCapabilitiesEvent / Medicalsystemcore.java:243 RegisterCapabilitiesEvent | Player に PlayerMedicalData を付与し、NBT で保存する | Data Attachment API（2 節） | 不要 | |
| ModEvents.java:133 ItemTossEvent | 電極（DefibrillatorPos 付き）を捨てたら除細動器にパッドを戻して ItemEntity を消す。意識不明中なら**キャンセル**してインベントリに戻す | 無し | **要** | Forge のフック位置（パッチで確認）は `Player#drop(ItemStack,boolean)` と `ServerPlayer#drop(boolean)`。26.2 ではどちらも `ServerPlayer#drop(ItemStack, boolean randomly, boolean thrownFromHand)` に行き着く。候補は、この 3 引数版の HEAD に cancellable で入れ、`!randomly` で絞る方法（死亡時ドロップの `Inventory#dropAll` は randomly=true なので除外できる） |
| ModEvents.java:193 TickEvent.PlayerTickEvent（server, END） | 毎 tick、輸液・心停止・出血・意識・浮上・ECG・姿勢維持を処理し、CoreStats を全追跡者へ送る | プレイヤー単位の tick イベントは**無し**。`ServerTickEvents.END_SERVER_TICK`（`onEndTick(MinecraftServer)`）で `server.getPlayerList().getPlayers()` を回す | 任意 | Forge は各プレイヤーの tick の最後で呼ばれていた。順序まで合わせるなら `ServerPlayer#tick` の TAIL に Mixin |
| ModEvents.java:249 PlayerEvent.PlayerLoggedOutEvent | 姿勢制御をクリアする | `ServerPlayConnectionEvents.DISCONNECT`（`onPlayDisconnect(ServerGamePacketListenerImpl, MinecraftServer)`、`handler.player` は public）または `ServerPlayerEvents.LEAVE`（`onLeave(ServerPlayer)`） | 不要 | |
| ModEvents.java:564 PlayerInteractEvent（基底型なので全サブイベント） | 意識不明なら右クリック（ブロック・アイテム・エンティティ）と左クリック（ブロック）をすべて**キャンセル** | `UseBlockCallback`、`UseItemCallback`、`UseEntityCallback`、`AttackBlockCallback`、`AttackEntityCallback` の各 `.EVENT` で `InteractionResult.FAIL` を返す | 不要 | シグネチャは `interact(Player, Level, InteractionHand[, BlockHitResult / Entity, EntityHitResult / BlockPos, Direction])`。Fabric の callback はクライアントとサーバーの両方で呼ばれる。サーバーでは attachment、クライアントでは ClientMedicalDataManager を見る分岐が必要（main ソースセットから client クラスは参照不可）。Forge の EntityInteract / EntityInteractSpecific の両方を UseEntityCallback で兼ねる。RightClickEmpty / LeftClickEmpty（クライアント限定・キャンセル不可）に相当するものは無い |
| event/StretcherPlacementHandler.java:27 PlayerInteractEvent.RightClickBlock | サーバーで素手＋しゃがみのとき、運搬中のストレッチャーを 2 ブロック設置して乗員を移し、**キャンセル**する | `UseBlockCallback.EVENT` | 不要 | PASS 以外を返すとバニラ処理が止まる。キャンセルに当たる値は `InteractionResult.SUCCESS` / `SUCCESS_SERVER` / `FAIL` |
| event/CommandEventHandler.java:12 RegisterCommandsEvent | 2 つのコマンドを登録 | `CommandRegistrationCallback.EVENT`。`register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)` | 不要 | |
| Medicalsystemcore.java:239 ServerStartingEvent | 空 | （必要なら `ServerLifecycleEvents.SERVER_STARTING`、`onServerStarting(MinecraftServer)`） | 不要 | 削除してよい |
| Medicalsystemcore.java:230 FMLCommonSetupEvent | 空 | `ModInitializer#onInitialize` | 不要 | |
| Medicalsystemcore.java:233 BuildCreativeModeTabContentsEvent | 包帯を TOOLS_AND_UTILITIES タブに追加 | `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)` を `.register(out -> out.accept(...))` で使う。`modifyOutput(FabricCreativeModeTabOutput)` | 不要 | |
| Config.java:293 ModConfigEvent | ForgeConfigSpec の値を static 変数に写す | 自前 JSON。`FabricLoader.getInstance().getConfigDir()` は `java.nio.file.Path` を返す（loader 0.20.1 に実在） | 不要 | |
| Medicalsystemcore.java:250 FMLClientSetupEvent | Entity / BE レンダラー、MenuScreens、RenderLayer cutout、ItemProperties fluid_level、ItemColors を登録 | `ClientModInitializer#onInitializeClient` で行う。内訳は以下 | 不要 | 内訳: `EntityRendererRegistry.register(EntityType, EntityRendererProvider)`。`BlockEntityRendererRegistry.register(BlockEntityType<E>, BlockEntityRendererProvider<? super E,? super S>)`（`BlockEntityRenderers.register` も Fabric の AW で public）。`MenuScreens.register` も AW で public。**RenderLayer の指定は不要**: 26.2 には `ItemBlockRenderTypes` も Fabric の BlockRenderLayerMap も無く、チャンクの描画レイヤーはテクスチャの透過から自動で決まる（`BakedQuad.MaterialInfo.layer()`）。半透明を強制するときだけ、モデルの material に `force_translucent` を付ける。ItemProperties と ItemColors は 8 節の items JSON に移す |
| Medicalsystemcore.java:299 EntityRenderersEvent.RegisterRenderers | ストレッチャーのレンダラーを登録（ClientSetup と二重登録） | `EntityRendererRegistry.register` を 1 回だけ | 不要 | |
| Medicalsystemcore.java:305 EntityRenderersEvent.RegisterLayerDefinitions | StretcherModel のレイヤー定義を登録 | `ModelLayerRegistry.registerModelLayer(ModelLayerLocation, TexturedLayerDefinitionProvider)`。`LayerDefinition createLayerDefinition()` | 不要 | |
| Medicalsystemcore.java:311 ModelEvent.RegisterAdditional | 独立モデル item/rescue_tablet_base を登録 | 不要（items JSON から直接参照できる）。どうしても必要なら fabric-model-loading-api-v1 の `ModelLoadingPlugin` / `ExtraModelKey` | 不要 | |
| client/ClientInputHandler.java:21 TickEvent.PlayerTickEvent（client, END） | 自分なら QTE・治療・ギブアップのタイマーを進める。**クライアント上の全プレイヤー**のうち意識不明の者に、視点固定・姿勢維持・移動停止をかける | `ClientTickEvents.END_CLIENT_TICK`（`onEndTick(Minecraft)`）で、自分のタイマー処理をしたあと `mc.level.players()` を回す | 任意 | Forge は各 Player の tick 内で呼ばれていた。厳密に合わせるなら `Player#tick` の TAIL に Mixin |
| ClientInputHandler.java:62 MovementInputUpdateEvent | 意識不明時に前後左右・ジャンプ・しゃがみの入力を 0 にする | 無し | **要** | 26.2 の入力は `ClientInput.keyPresses`（record `Input(forward, backward, left, right, jump, shift, sprint)`、public）と `moveVector`（protected Vec2）。Forge の発火位置は `LocalPlayer#aiStep` の `this.input.tick()` 直後。候補は `LocalPlayer#aiStep` の `INVOKE ClientInput#tick` に shift=AFTER で入れる（moveVector には Accessor が要る）か、`KeyboardInput#tick` の TAIL |
| ClientInputHandler.java:81 InputEvent.MouseButton.Pre | QTE 中なら右クリックを離した時点で QTE を止める。画面を開いておらず意識不明なら全クリックを**キャンセル** | 無し（`ScreenMouseEvents` は Screen 内だけ） | **要** | `MouseHandler#onButton(long, MouseButtonInfo, int action)` の HEAD に cancellable で入れる。`mc.screen` は 26.2 では `mc.gui.screen()` |
| client/ClientStretcherHandler.java:24 TickEvent.ClientTickEvent | 中身は実質何もしない（ループするだけ） | `ClientTickEvents.END_CLIENT_TICK` | 不要 | 削除してよい |
| client/ClientWorldHandler.java:18 LevelEvent.Unload（client） | クライアントの姿勢制御状態をクリア | `ClientPlayConnectionEvents.DISCONNECT`（`onPlayDisconnect(ClientPacketListener, Minecraft)`）。ディメンション移動も拾うなら `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`（`afterLevelChange(Minecraft, ClientLevel)`）も併用 | 不要 | |
| client/ClientHudHandler.java:26 RenderGuiOverlayEvent.Pre（CROSSHAIR） | 治療中・ギブアップ中はクロスヘアを**キャンセル**してバーを描く。QTE 中もキャンセルして QTE バー。ダウン中はクロスヘアを残したままヒント文字を追加 | `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, orig -> (g, dt) -> {...})`。条件に合わなければ `orig.extractRenderState(g, dt)` を呼んで元の描画を残す | 不要 | `HudElement#extractRenderState(GuiGraphicsExtractor, DeltaTracker)`。画面サイズは `g.guiWidth()` / `g.guiHeight()`。**`textures/gui/widgets.png` は 26.2 の client jar に存在しない**（`gui/sprites/...` 方式）。8 節も参照 |
| client/DebugOverlayHandler.java:14 CustomizeGuiOverlayEvent.DebugText | 空 | 不要（必要なら fabric-debug-api-v1） | 不要 | 削除してよい |
| PlayerEvent.Clone | **Forge 側に該当なし** | — | — | 現状、死亡後や End からの帰還後に医療データは引き継がれていない（2 節参照） |

## 2. capability PlayerMedicalData → Data Attachment API（fabric-data-attachment-api-v1 2.2.19、stable）
- `AttachmentRegistry.create(Identifier, Consumer<AttachmentRegistry.Builder<A>>)`、`createPersistent(Identifier, Codec<A>)`、`createDefaulted(Identifier, Supplier<A>)`、`builder()`
- `Builder<A>` のメソッド: `persistent(Codec<A>)`、`copyOnDeath()`、`initializer(Supplier<A>)`、`syncWith(StreamCodec<? super RegistryFriendlyByteBuf, A>, AttachmentSyncPredicate)`、`syncWith(..., AttachmentSyncPredicate, int)`、`buildAndRegister(Identifier)`
- `AttachmentSyncPredicate` は `all()` / `targetOnly()` / `allButTarget()`
- 取得・設定: `AttachmentTarget`（Entity に実装済み）の `getAttached`、`getAttachedOrCreate(type[, Supplier])`、`setAttached`、`modifyAttached(type, UnaryOperator)`、`hasAttached`、`removeAttached`
- **同期は `setAttached` 経路（と接続時の初回同期）でしか送られない**（AttachmentTargetsMixin のバイトコードで確認）。現行の PlayerMedicalData は毎 tick その場で書き換えるので、syncWith を付けても同期されない。推奨は `persistent(Codec)` と `initializer` だけ使い、同期は既存の CoreStats payload に任せる形
- 永続化は保存のタイミングで codec を通るので、その場の書き換えでも保存はされる
- Codec は 15 フィールドの RecordCodecBuilder か、既存の save/load を `CompoundTag.CODEC.xmap` で包む
- Forge 側に Clone ハンドラが無いので、現行と互換にするなら `copyOnDeath()` は付けない

## 3. SimpleChannel の 10 パケット → CustomPacketPayload（fabric-networking-api-v1 6.3.4）
- payload の定義: `record X(...) implements CustomPacketPayload`。`type()` は `CustomPacketPayload.Type<? extends CustomPacketPayload>` を返す。TYPE は `new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MODID, "..."))`（`CustomPacketPayload.createType(String)` は minecraft 名前空間になるので使わない）
- CODEC: `CustomPacketPayload.codec(StreamMemberEncoder, StreamDecoder)` で `StreamCodec<RegistryFriendlyByteBuf, X>` を作る
- 登録: `PayloadTypeRegistry.clientboundPlay()` / `serverboundPlay()`（playS2C / playC2S という名前ではない）の `.register(Type<T>, StreamCodec<? super B, T>)`。大きいものは `registerLarge(..., int)`
- サーバー受信: `ServerPlayNetworking.registerGlobalReceiver(Type<T>, PlayPayloadHandler<T>)`、`receive(T, Context)`。Context は `server()` / `player()` / `responseSender()`
- クライアント受信: `ClientPlayNetworking.registerGlobalReceiver(Type<T>, PlayPayloadHandler<T>)`。Context は `client()` / `player()`
- ハンドラは impl 側で `execute` を通してメインスレッドで実行されるので、`enqueueWork` は不要
- 送信: `ServerPlayNetworking.send(ServerPlayer, payload)`、`ClientPlayNetworking.send(payload)`
- 宛先: TRACKING_ENTITY_AND_SELF は `PlayerLookup.tracking(Entity)` に**自分を足す**。ALL は `PlayerLookup.all(server)`
- ソースセット: build.gradle が `splitEnvironmentSourceSets()` なので、payload の record と PayloadTypeRegistry は main、Client 側の receiver は client に置く。これで `DistExecutor`（3 箇所）は消える
- FriendlyByteBuf: `readUUID/readEnum/readBlockPos/readUtf` は残っている。`readResourceLocation` は `readIdentifier`

| パケット | 方向 | 中身 |
|---|---|---|
| ClientboundCoreStatsPacket | S→C | UUID・Enum・float×3・boolean |
| ClientboundStartQTEPacket | S→C | int |
| ServerboundFinishHealPacket | C→S | int |
| ServerboundQTETResultPacket | C→S | Enum・int・BlockPos |
| ServerboundUpdateRescueDataPacket | C→S | int・Enum・Utf・boolean×2 |
| ClientboundSyncAllRescueDataPacket | S→C | int サイズ＋RescueData×n |
| ServerboundRequestRescueListPacket | C→S | 空 |
| ClientboundUpdateRescueDataPacket | S→C | boolean＋RescueData |
| ServerboundGiveUpPacket | C→S | 空 |
| ClientboundOpenRescuePortalPacket | S→C | 空 |

RescueData の中身は int、Utf、Enum、long、Utf、bool×2、BlockPos、ResourceLocation、Utf。空の payload は `StreamCodec.unit(INSTANCE)` で足りる。

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
- 備考: clientTrackingRange の単位はチャンク。コメントの「64 ブロック」は誤り。

**CreativeModeTab**: `FabricCreativeModeTab.builder()`（バニラの `builder(Row, int)` の代わり）で作り、`Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ...)`。

**MenuType**
- データ付きは `new ExtendedMenuType<T,D>(ExtendedFactory<T,D> (int, Inventory, D), StreamCodec<? super RegistryFriendlyByteBuf, D>)` で作る。
- IVStand は D=BlockPos で `BlockPos.STREAM_CODEC`、MedicalBag は D=InteractionHand で `InteractionHand.STREAM_CODEC`。
- RescuePortal はデータ無しなので `new MenuType<>(RescuePortalMenu::new, FeatureFlags.VANILLA_SET)`（コンストラクタは transitive AW で public）。
- 開く側: `NetworkHooks.openScreen` は `player.openMenu(provider)` になる。provider は `ExtendedMenuProvider<D>` を実装し、`D getScreenOpeningData(ServerPlayer)` を返す。

**その他**
- `StretcherEntity#getAddEntityPacket` の override（NetworkHooks）は削除する。26.2 の署名は `getAddEntityPacket(ServerEntity)` で、バニラのままでよい。
- `EntityDataSerializers.OPTIONAL_UUID` は**無くなった**。`OPTIONAL_LIVING_ENTITY_REFERENCE`（`Optional<EntityReference<LivingEntity>>`）を使う。
- `registryOrThrow(...).getHolderOrThrow(...)` は `lookupOrThrow(...).getOrThrow(...)` になった。

## 5. ItemStackHandler / ITEM_HANDLER
- **IVStandBlockEntity**: `ItemStackHandler(3)`（onContentsChanged、isItemValid、getSlotLimit）は `SimpleContainer` の匿名サブクラスで `setChanged()` を override する形にする（26.2 の SimpleContainer に listener は無い）。または BE 自体に `Container` を実装する。
  - ホッパー連携が要るなら、`Container` を実装するか、`ItemStorage.SIDED`（fabric-transfer-api-v1 の `BlockApiLookup<Storage<ItemVariant>, Direction>`）を使う。
  - スロット制限は Slot の `mayPlace(ItemStack)` / `getMaxStackSize(ItemStack)` で行う。
  - `SlotItemHandler` は普通の `Slot` に置き換える。
- **MedicalBagMenu**: 中身の保存は `DataComponents.CONTAINER`（`ItemContainerContents`、最大 256 スロット）が使える。
  - 読み出しは `ItemContainerContents.fromItems(List<ItemStack>)` と `copyInto(NonNullList<ItemStack>)`。
  - 書き込みは `stack.set(DataComponents.CONTAINER, ...)`。
  - `ForgeRegistries.ITEMS.getKey` は `BuiltInRegistries.ITEM.getKey` に変わる。

## 6. アイテムの NBT → データコンポーネント
- 使用箇所: ModEvents:139、DefibrillatorBlock:119、ElectrodeItem:35、ServerboundQTETResultPacket:52（以上 DefibrillatorPos）。HeadsideMonitorBlock:58、HeadsideCableItem:31（MonitorPos）。TubeItem:48,65（StartPos）。FluidPackItem:26–140（FluidVolumeTicks、FluidLevel）。DoctorCardItem:47–151（isGenuine、ownerUUID、doctorName、licenseNumber、issueDate）。MedicalBagMenu:37,72（Inventory）。
- 26.2 での登録: `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, DataComponentType.<T>builder().persistent(Codec).networkSynchronized(StreamCodec).build())`。
- 位置系は `BlockPos.CODEC` と `BlockPos.STREAM_CODEC` で持てる。`NbtUtils.readBlockPos` / `writeBlockPos` は 26.2 に**存在しない**。
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

**流体パック**
- ItemProperties の fluid_level は、FluidLevel を `DataComponentType<Integer>` にしたうえで、items JSON の `minecraft:select` に property `minecraft:component` を書けばクライアントコード無しで済む。
- range にしたいなら、`RangeSelectItemModelProperties.ID_MAPPER`（AW で public）に `RangeSelectItemModelProperty` を登録する。`float get(ItemStack, ClientLevel, ItemOwner, int)`。
- ItemColors（PackColor の tintIndex 1）は items JSON の `"tints":[{"type":"minecraft:constant","value":-1},{"type":"minecraft:constant","value":<色>}]`。独自のものは `ItemTintSources.ID_MAPPER`（AW で public）、`int calculate(ItemStack, ClientLevel, LivingEntity)`。

**HUD**: 1 節を参照。

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
`FabricLoader.getInstance().getConfigDir()` は `Path` を返す（実在を確認済み）。

## 10. リソース
- フォルダ名: レジストリのパスがそのままディレクトリ名になる。`loot_tables/blocks` は `loot_table/blocks`、`tags/items` は `tags/item` に変える（Registries.LOOT_TABLE="loot_table"、tagsDirPath="tags/"+パス）。
- `assets/<ns>/items/<id>.json` が**全アイテムで必須**（ClientItemInfoLoader が "items" を読む）。models/item の `overrides` は 26.2 の方式では使われない。
- pack_format: SharedConstants では RESOURCE_PACK_FORMAT 88.0、DATA_PACK_FORMAT 107.1。
  - pack.mcmeta は `min_format` / `max_format`（`[major, minor]` も可）方式になった。旧 `pack_format` は互換フィールドとして残っている。
  - Fabric の MOD リソースは pack.mcmeta が無くても読み込まれる（ただし実機では未検証）。
- RenderType: RenderLayer の節（1 節 FMLClientSetupEvent）を参照。

## 11. damage_type
- `bleeding.json` の形式は 26.2 でも有効。DamageType.DIRECT_CODEC は message_id、scaling、exhaustion、effects（任意）、death_message_type（任意）で、`when_caused_by_living_non_player`、`hurt`、`default` もすべて実在する。
- コード側は `level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(KEY)` に直す。

## 要 Mixin の一覧
1. **ItemTossEvent**: `ServerPlayer#drop(ItemStack, boolean, boolean)` の HEAD に cancellable で入れ、`!randomly` で絞る。
2. **MovementInputUpdateEvent**: `LocalPlayer#aiStep` の `INVOKE ClientInput#tick()` に shift=AFTER で入れる（moveVector 用に ClientInput の Accessor を作る）。または `KeyboardInput#tick` の TAIL。
3. **InputEvent.MouseButton.Pre**: `MouseHandler#onButton(long, MouseButtonInfo, int)` の HEAD に cancellable で入れる。
4. **`Player#setForcedPose` / `getForcedPose`**（core/PoseController.java:169,182,261 から呼んでいる。Forge が追加したメソッドで、**26.2 のバニラには存在しない**）。`Player#updatePlayerPose()`（protected、Player.java:342）の HEAD で強制ポーズを `setPose` してキャンセルするか、`getDesiredPose()`（private）を書き換える。フィールドは Mixin 側に持たせる。
5. （厳密互換を取るなら）**LivingDamageEvent** の防具適用後の量: `Player#actuallyHurt` の `setHealth` INVOKE の直前でローカル dmg を取る。
6. （任意）tick の順序を合わせるなら、`ServerPlayer#tick` / `Player#tick` の TAIL。

## 26.2 で大きく形が変わる領域
- **描画**: 抽出（extract）してから送る（submit）の 2 段階になった。BER・EntityRenderer・HUD・Screen のすべてで RenderState と `SubmitNodeCollector` / `GuiGraphicsExtractor` を使う。即時に頂点を流すコードは `submitCustomGeometry` に移す。BEWLR は SpecialModelRenderer と items JSON に置き換わり、表示コンテキストによる分岐は JSON に移る。RenderLayer の手動登録は無くなった。
- **BlockEntity の保存**: CompoundTag を直接触る方式から ValueInput / ValueOutput（Codec ベース）に変わった。Forge の同期フックは無くなり、バニラの getUpdateTag(Provider) と getUpdatePacket で行う。
- **アイテムデータ**: NBT（getTag / getOrCreateTag）は全廃された。独自の DataComponentType を登録するか CUSTOM_DATA を使い、バッグは DataComponents.CONTAINER に入れる。Block / Item の Properties には setId が必須。

## 確認できなかったもの
- Fabric API の各モジュールは sources jar がキャッシュに無かった。シグネチャは javap で確認したが、javadoc の文言は見ていない。
- `syncWith(..., int)` の int 引数の意味。
- Attachment の**死亡以外のリスポーン**（End からの帰還）で常にコピーされるか。確認できたのは、`AttachmentTargetImpl.transfer(src, dst, boolean)` が copyOnDeath を参照していることだけ。
- `textures/gui/widgets.png` の代わりに使う具体的なスプライト名（client jar には widgets.png が無いことだけ確認した）。
- 4 つの Screen と RescueDataListWidget の細部（AbstractSelectionList の Entry の描画メソッド名など）は追っていない。RescueDataListWidget は Forge 側でも独自クラスで、AbstractSelectionList は継承していない。
- pack.mcmeta が無くても Fabric が MOD リソースを読み込むことの実機確認。