package jp.houlab.mochidsuki.medicalsystemcore.menu;

import jp.houlab.mochidsuki.medicalsystemcore.block.ModBlocks;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.IVStandBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class IVStandMenu extends AbstractContainerMenu {
    public final IVStandBlockEntity blockEntity;
    private final ContainerLevelAccess containerAccess;

    /** クライアント側のコンストラクタ。メニューを開くときにサーバーから渡された BlockPos から BE を引く(ExtendedMenuType)。 */
    public IVStandMenu(int pContainerId, Inventory inv, BlockPos pos) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(pos));
    }

    public IVStandMenu(int pContainerId, Inventory inv, BlockEntity entity) {
        super(ModMenus.IV_STAND_MENU, pContainerId);
        this.blockEntity = ((IVStandBlockEntity) entity);
        this.containerAccess = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());

        // 点滴スタンドのインベントリスロット (スロット番号 0-2)
        // スロット番号0：輸血パック用（左側）
        this.addSlot(new IVStandSlot(blockEntity.inventory, 0, 44, 18));
        // スロット番号1：薬剤パック用（右側の一つ目）
        this.addSlot(new IVStandSlot(blockEntity.inventory, 1, 98, 18));
        // スロット番号2：薬剤パック用（右側の二つ目）
        this.addSlot(new IVStandSlot(blockEntity.inventory, 2, 116, 18));

        // プレイヤーのインベントリスロット (スロット番号 3-29)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        // プレイヤーのホットバースロット (スロット番号 30-38)
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(inv, k, 8 + k * 18, 142));
        }
    }

    // ▼▼▼ stillValidメソッドを修正 ▼▼▼
    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(this.containerAccess, pPlayer, ModBlocks.IV_STAND);
    }

    // ▼▼▼ quickMoveStackメソッドを、完全に機能するロジックに置き換え ▼▼▼
    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // スロット番号の範囲を定義
        final int BE_SLOT_COUNT = 3;
        final int PLAYER_INVENTORY_START_INDEX = 3;
        final int PLAYER_INVENTORY_END_INDEX = 29;
        final int PLAYER_HOTBAR_START_INDEX = 30;
        final int PLAYER_HOTBAR_END_INDEX = 38;

        // クリックされたのが点滴スタンドのスロットか
        if (index < BE_SLOT_COUNT) {
            // 点滴スタンド -> プレイヤーのインベントリ
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START_INDEX, PLAYER_HOTBAR_END_INDEX + 1, true)) {
                return ItemStack.EMPTY;
            }
        }
        // クリックされたのがプレイヤーのインベントリか
        else {
            // プレイヤーのインベントリ -> 点滴スタンド
            // IVStandSlot#mayPlace でフィルタリングされるため、適切なスロットにのみ移動する
            if (!moveItemStackTo(sourceStack, 0, BE_SLOT_COUNT, false)) {
                // もし点滴スタンドへの移動に失敗した場合のフォールバック
                if (index < PLAYER_INVENTORY_END_INDEX + 1) {
                    // メインインベントリ -> ホットバー
                    if (!moveItemStackTo(sourceStack, PLAYER_HOTBAR_START_INDEX, PLAYER_HOTBAR_END_INDEX + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // ホットバー -> メインインベントリ
                    if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START_INDEX, PLAYER_INVENTORY_END_INDEX + 1, false)) {
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

    /**
     * 点滴スタンドのスロット。Forge の ItemStackHandler が持っていた isItemValid と getSlotLimit をここで再現する。
     * スロット 0 は輸血パック、1・2 は薬剤パックだけ受け付け、どのスロットも 1 個まで。
     */
    private static class IVStandSlot extends Slot {
        private final int slotIndex;

        public IVStandSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.slotIndex = slot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return switch (slotIndex) {
                case 0 -> stack.is(ModTags.Items.BLOOD_PACKS);
                case 1, 2 -> stack.is(ModTags.Items.DRUG_PACKS);
                default -> false;
            };
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
