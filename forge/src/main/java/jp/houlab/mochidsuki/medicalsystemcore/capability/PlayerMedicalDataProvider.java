package jp.houlab.mochidsuki.medicalsystemcore.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerMedicalDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    // Capabilityのインスタンスを登録するためのトークン
    public static final Capability<IPlayerMedicalData> PLAYER_MEDICAL_DATA =
            CapabilityManager.get(new CapabilityToken<>() {});

    private IPlayerMedicalData medicalData = null;
    private final LazyOptional<IPlayerMedicalData> optional = LazyOptional.of(this::createPlayerMedicalData);

    private IPlayerMedicalData createPlayerMedicalData() {
        if (this.medicalData == null) {
            this.medicalData = new PlayerMedicalData();
        }
        return this.medicalData;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PLAYER_MEDICAL_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createPlayerMedicalData().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createPlayerMedicalData().loadNBTData(nbt);
    }
}
