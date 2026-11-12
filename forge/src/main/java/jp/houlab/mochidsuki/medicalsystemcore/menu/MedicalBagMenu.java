package jp.houlab.mochidsuki.medicalsystemcore.menu;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

public class MedicalBagMenu extends AbstractContainerMenu {
    private static final int BAG_SLOT_COUNT = 27;

    private static final int OFFHAND_INVENTORY_INDEX = 40;

    private final ItemStackHandler bagInventory;
    private final InteractionHand hand;
    private final boolean serverSide;
    /** 開いた時点のバッグの ItemStack。サーバー側では参照の同一性でバッグを追跡する。 */
    private final ItemStack bagStack;
    /** プレイヤーインベントリ上でバッグが入っているインデックス（メインハンドは選択中スロット、オフハンドは 40）。 */
    private final int bagInventoryIndex;

    // Client constructor
    public MedicalBagMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        this(windowId, inv, InteractionHand.values()[data.readVarInt()]);
    }

    // Server constructor
    public MedicalBagMenu(int windowId, Inventory inv, InteractionHand hand) {
        super(Medicalsystemcore.MEDICAL_BAG_MENU.get(), windowId);
        this.hand = hand;
        this.serverSide = !inv.player.level().isClientSide();
        this.bagStack = inv.player.getItemInHand(hand);
        this.bagInventoryIndex = hand == InteractionHand.MAIN_HAND ? inv.selected : OFFHAND_INVENTORY_INDEX;
        this.bagInventory = new ItemStackHandler(BAG_SLOT_COUNT) {
            @Override
            protected void onContentsChanged(int slot) {
                MedicalBagMenu.this.saveToBag();
            }
        };

        // Load inventory from NBT
        if (bagStack.hasTag() && bagStack.getTag().contains("Inventory")) {
            bagInventory.deserializeNBT(bagStack.getTag().getCompound("Inventory"));
        }

        // Bag inventory slots (3 rows × 9 cols)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new MedicalBagSlot(bagInventory, j + i * 9, 8 + j * 18, 18 + i * 18, this::saveToBag));
            }
        }

        // Player inventory slots (3 rows × 9 cols)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 85 + i * 18));
            }
        }

        // Player hotbar slots（メインハンドで開いた場合、バッグが入っているスロットは動かせない）
        for (int k = 0; k < 9; k++) {
            if (hand == InteractionHand.MAIN_HAND && k == bagInventoryIndex) {
                this.addSlot(new LockedSlot(inv, k, 8 + k * 18, 143));
            } else {
                this.addSlot(new Slot(inv, k, 8 + k * 18, 143));
            }
        }
    }

    /**
     * 中身を開いたバッグの ItemStack へ書き戻す（サーバーのみ。クライアントはサーバーの同期結果を受けるだけ）。
     * 書き込み先は getItemInHand ではなく開いた時点の参照なので、バッグが別スロットへ動いても正しい ItemStack に入る。
     */
    private void saveToBag() {
        if (serverSide) {
            bagStack.getOrCreateTag().put("Inventory", bagInventory.serializeNBT());
        }
    }

    @Override
    public void removed(Player pPlayer) {
        super.removed(pPlayer);
        // バニラの shift クリック・スタック合流はスタックを直接書き換えて Slot#setChanged だけを呼ぶ経路があるため、閉じるときにも最終保存する
        saveToBag();
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        // サーバーでは、開いたバッグと同一の ItemStack が今も開いた手にある場合だけ有効。
        // 別のバッグとの入れ替えも検出するため instanceof ではなく参照で比較する。
        return !serverSide || pPlayer.getItemInHand(hand) == bagStack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // バッグが入っているインベントリスロットとの SWAP（数字キー／F キー）は、バッグを手から外すので拒否する
        if (type == ClickType.SWAP && button == bagInventoryIndex) {
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        if (index < 0 || index >= slots.size() || slots.get(index) instanceof LockedSlot) return ItemStack.EMPTY;
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        final int PLAYER_INVENTORY_START = BAG_SLOT_COUNT;
        final int PLAYER_INVENTORY_END = BAG_SLOT_COUNT + 27;
        final int PLAYER_HOTBAR_END = PLAYER_INVENTORY_END + 9;

        if (index < BAG_SLOT_COUNT) {
            // Bag -> Player inventory
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> Bag
            if (!moveItemStackTo(sourceStack, 0, BAG_SLOT_COUNT, false)) {
                // Fallback: player inventory <-> hotbar
                if (index < PLAYER_INVENTORY_END) {
                    if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_END, PLAYER_HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }

        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    // バッグ自身が入っているスロット。取り出し・置き換えを一切許可しない
    private static class LockedSlot extends Slot {
        public LockedSlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    // Custom slot that only accepts mod items (excluding the bag itself)
    private static class MedicalBagSlot extends SlotItemHandler {
        private final Runnable onChanged;

        public MedicalBagSlot(ItemStackHandler handler, int index, int x, int y, Runnable onChanged) {
            super(handler, index, x, y);
            this.onChanged = onChanged;
        }

        // moveItemStackTo / quickMoveStack はスタックを直接書き換えて setChanged() だけを呼ぶことがある
        // (SlotItemHandler は setChanged を handler へ伝えない)ので、ここでも書き戻す
        @Override
        public void setChanged() {
            super.setChanged();
            onChanged.run();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null
                    && key.getNamespace().equals(Medicalsystemcore.MODID)
                    && !stack.is(Medicalsystemcore.MEDICAL_BAG.get());
        }
    }
}
