package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModTags;
import jp.houlab.mochidsuki.medicalsystemcore.menu.IVStandMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IVStandBlockEntity extends BlockEntity implements MenuProvider {
    // 1スロットだけのインベントリ（アイテムを保持する場所）を作成
    public final ItemStackHandler itemHandler = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case 0 -> stack.is(ModTags.Items.BLOOD_PACKS);
                case 1, 2 -> stack.is(ModTags.Items.DRUG_PACKS);
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int i){
            return 1;
        }
    };

    private LazyOptional<ItemStackHandler> lazyItemHandler = LazyOptional.empty();


    public IVStandBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(Medicalsystemcore.IV_STAND_BLOCK_ENTITY.get(), pPos, pBlockState);
    }


    /**
     * 外部からインベントリ(Capability)を要求されたときに、それを提供するメソッド
     */
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // 要求がアイテムハンドラーに関するものであれば、自身のインベントリを返す
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    /**
     * BlockEntityがワールドに読み込まれたときに呼ばれる
     */
    @Override
    public void onLoad() {
        super.onLoad();
        // lazyItemHandlerを初期化
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    /**
     * BlockEntityが無効になったとき(ワールドから削除されるなど)に呼ばれる
     */
    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        // lazyItemHandlerを無効化
        lazyItemHandler.invalidate();
    }

    /**
     * ブロックが更新されたときにクライアントに送るデータパケット
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 上記パケットに含まれるNBTデータ
     */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    /**
     * クライアント側でパケットを受信した際の処理
     */
    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        load(tag);
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

    /**
     * 手動でクライアント同期を強制実行するメソッド
     * 残量変化時に呼び出される
     */
    public void forceClientSync() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // NBTへの保存処理
    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.put("inventory", itemHandler.serializeNBT());
        super.saveAdditional(pTag);
    }

    // NBTからの読み込み処理
    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        itemHandler.deserializeNBT(pTag.getCompound("inventory"));
    }

}