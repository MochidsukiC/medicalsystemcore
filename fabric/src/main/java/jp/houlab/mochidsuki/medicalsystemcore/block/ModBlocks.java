package jp.houlab.mochidsuki.medicalsystemcore.block;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

/**
 * Block の登録ホルダ(Forge の Medicalsystemcore.IV_STAND などに相当)。ID は Forge と同じ。
 * Block.Properties には setId が必須なので、登録のたびにここで ResourceKey を渡す。
 * Forge の RegistryObject と違い、値そのもの(.get() は不要)を公開する。
 */
public final class ModBlocks {
    private ModBlocks() {
    }

    public static final Block IV_STAND = register("iv_stand", IVStandBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5f).sound(SoundType.METAL).noOcclusion());
    public static final Block DEFIBRILLATOR_BLOCK = register("defibrillator_block", DefibrillatorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL));
    public static final Block HEAD_SIDE_MONITOR_BLOCK = register("head_side_monitor", HeadsideMonitorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL));
    public static final Block STRETCHER_BLOCK = register("stretcher", StretcherBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.WOOL.white()).strength(0.3f));

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Medicalsystemcore.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    /** クラスのロード(= 登録)を ModInitializer から確実に起こすための呼び出し口。 */
    public static void register() {
    }
}
