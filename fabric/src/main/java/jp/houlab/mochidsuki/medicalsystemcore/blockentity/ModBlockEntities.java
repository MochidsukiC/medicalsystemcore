package jp.houlab.mochidsuki.medicalsystemcore.blockentity;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/**
 * BlockEntityType の登録ホルダ(Forge の Medicalsystemcore.IV_STAND_BLOCK_ENTITY などに相当)。ID は Forge と同じ。
 * ModBlocks のクラスロード後(= ブロックの登録後)に登録される。
 */
public final class ModBlockEntities {
    private ModBlockEntities() {
    }

    public static final BlockEntityType<IVStandBlockEntity> IV_STAND_BLOCK_ENTITY =
            register("iv_stand_block_entity", IVStandBlockEntity::new, ModBlocks.IV_STAND);
    public static final BlockEntityType<DefibrillatorBlockEntity> DEFIBRILLATOR_BLOCK_ENTITY =
            register("defibrillator_block_entity", DefibrillatorBlockEntity::new, ModBlocks.DEFIBRILLATOR_BLOCK);
    public static final BlockEntityType<HeadsideMonitorBlockEntity> HEAD_SIDE_MONITOR_BLOCK_ENTITY =
            register("head_side_monitor_block_entity", HeadsideMonitorBlockEntity::new, ModBlocks.HEAD_SIDE_MONITOR_BLOCK);
    public static final BlockEntityType<StretcherBlockEntity> STRETCHER_BLOCK_ENTITY =
            register("stretcher_block_entity", StretcherBlockEntity::new, ModBlocks.STRETCHER_BLOCK);

    private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Medicalsystemcore.id(name), new BlockEntityType<>(factory, Set.of(block)));
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
