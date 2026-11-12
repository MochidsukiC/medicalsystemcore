package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.block.IVStandBlock;
import jp.houlab.mochidsuki.medicalsystemcore.menu.IVStandMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * 点滴スタンド(下半身)のブロックエンティティ。
 * Forge の ItemStackHandler(3) は SimpleContainer に置き換えた。スロットの受け入れ条件と 1 個制限は Slot 側(IVStandMenu)が持つ。
 * 開くメニューの引数(BlockPos)は ExtendedMenuProvider で渡す。
 */
public class IVStandBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    // 3 スロット(0: 輸血パック、1・2: 薬剤パック)のインベントリ。中身が変わったら保存とクライアント同期を行う
    public final SimpleContainer inventory = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            IVStandBlockEntity.this.setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };

    public IVStandBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.IV_STAND_BLOCK_ENTITY, pPos, pBlockState);
    }

    /**
     * ブロックが壊れる直前に中身をドロップする(Forge では IVStandBlock#onRemove)。
     * 26.2 では onRemove が無く、BlockEntity#preRemoveSideEffects が同じ位置(BE が消える前、サーバーのみ)で呼ばれる。
     * BE を持つ下半身だけが 1 回だけ行う。
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (state.getValue(IVStandBlock.HALF) == DoubleBlockHalf.LOWER && !level.isClientSide()) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    // BE 側のスタックと ItemEntity が同じインスタンスを共有しないようコピーを渡し、スロットは空にする
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy()));
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
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
     * 上記パケットに含まれるデータ(クライアントでは loadAdditional が自動で呼ばれる)
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.medicalsystemcore.iv_stand_menu");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new IVStandMenu(pContainerId, pPlayerInventory, this);
    }

    /** メニューを開くときにクライアントへ渡すデータ(このブロックエンティティの座標)。 */
    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    /**
     * 手動でクライアント同期を強制実行するメソッド
     * 残量変化時に呼び出される
     */
    public void forceClientSync() {
        if (level != null && !level.isClientSide()) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // 保存処理
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, inventory.getItems());
    }

    // 読み込み処理。setItem 経由だとレベル未設定のチャンク読み込み時に setChanged の同期処理が level を参照してしまうので、
    // スロットを直接書き換える(クリアしてから読み込むのは、クライアントに古い中身が残らないようにするため)。
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.getItems().clear();
        ContainerHelper.loadAllItems(input, inventory.getItems());
    }

}
