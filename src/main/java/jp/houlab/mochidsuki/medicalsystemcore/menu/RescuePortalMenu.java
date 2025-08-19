package jp.houlab.mochidsuki.medicalsystemcore.menu;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RescuePortalMenu extends AbstractContainerMenu {
    public RescuePortalMenu(int pContainerId, Inventory pPlayerInventory) {
        super(Medicalsystemcore.RESCUE_PORTAL_MENU.get(), pContainerId);
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return true; // アイテムベースのUIなので常に有効
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return ItemStack.EMPTY; // スロットがないため空
    }
}