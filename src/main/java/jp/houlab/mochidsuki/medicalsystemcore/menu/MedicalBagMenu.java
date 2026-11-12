package jp.houlab.mochidsuki.medicalsystemcore.menu;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.item.MedicalBagItem;
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

    private final ItemStackHandler bagInventory;
    private final InteractionHand hand;

    // Client constructor
    public MedicalBagMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        this(windowId, inv, InteractionHand.values()[data.readVarInt()]);
    }

    // Server constructor
    public MedicalBagMenu(int windowId, Inventory inv, InteractionHand hand) {
        super(Medicalsystemcore.MEDICAL_BAG_MENU.get(), windowId);
        this.hand = hand;
        this.bagInventory = new ItemStackHandler(BAG_SLOT_COUNT);

        // Load inventory from NBT
        ItemStack bagStack = inv.player.getItemInHand(hand);
        if (bagStack.hasTag() && bagStack.getTag().contains("Inventory")) {
            bagInventory.deserializeNBT(bagStack.getTag().getCompound("Inventory"));
        }

        // Bag inventory slots (3 rows × 9 cols)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new MedicalBagSlot(bagInventory, j + i * 9, 8 + j * 18, 18 + i * 18));
            }
        }

        // Player inventory slots (3 rows × 9 cols)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 85 + i * 18));
            }
        }

        // Player hotbar slots
        for (int k = 0; k < 9; k++) {
            this.addSlot(new Slot(inv, k, 8 + k * 18, 143));
        }
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return pPlayer.getItemInHand(hand).getItem() instanceof MedicalBagItem;
    }

    @Override
    public void removed(Player pPlayer) {
        super.removed(pPlayer);
        if (!pPlayer.level().isClientSide()) {
            ItemStack stack = pPlayer.getItemInHand(hand);
            if (stack.getItem() instanceof MedicalBagItem) {
                stack.getOrCreateTag().put("Inventory", bagInventory.serializeNBT());
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // Prevent interaction with the slot holding the bag itself
        if (hand == InteractionHand.MAIN_HAND && slotId >= 0 && slotId < this.slots.size()) {
            int bagHotbarIndex = BAG_SLOT_COUNT + 27 + player.getInventory().selected;
            if (slotId == bagHotbarIndex) {
                return;
            }
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
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

    // Custom slot that only accepts mod items (excluding the bag itself)
    private static class MedicalBagSlot extends SlotItemHandler {
        public MedicalBagSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
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
