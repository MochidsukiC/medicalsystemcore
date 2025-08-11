package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModTags;
import jp.houlab.mochidsuki.medicalsystemcore.menu.IVStandMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class IVStandBlockEntity extends BlockEntity implements MenuProvider {
    // 1スロットだけのインベントリ（アイテムを保持する場所）を作成
    public final ItemStackHandler itemHandler = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case 0 -> stack.is(ModTags.Items.BLOOD_PACKS);
                case 1, 2 -> stack.is(ModTags.Items.DRUG_PACKS);
                default -> false;
            };
        }
    };

    public IVStandBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(Medicalsystemcore.IV_STAND_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    /**
     * このスロットに、このアイテムを入れることは有効か？を判定するメソッド
     */
    public boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            // スロット0は「輸血パック」タグを持つアイテムのみ許可
            case 0 -> stack.is(ModTags.Items.BLOOD_PACKS);
            // スロット1と2は「薬剤パック」タグを持つアイテムのみ許可
            case 1, 2 -> stack.is(ModTags.Items.DRUG_PACKS);
            // それ以外のスロット（今回は存在しない）は全て拒否
            default -> false;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("点滴スタンド");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new IVStandMenu(pContainerId, pPlayerInventory, this);
    }

    // NBT（セーブデータ）にインベントリの内容を書き込む
    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.put("inventory", itemHandler.serializeNBT());
        super.saveAdditional(pTag);
    }

    // NBT（セーブデータ）からインベントリの内容を読み込む
    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        itemHandler.deserializeNBT(pTag.getCompound("inventory"));
    }
}