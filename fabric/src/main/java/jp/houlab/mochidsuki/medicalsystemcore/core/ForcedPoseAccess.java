package jp.houlab.mochidsuki.medicalsystemcore.core;

import net.minecraft.world.entity.Pose;
import org.jspecify.annotations.Nullable;

/**
 * Player に追加する強制姿勢のアクセサ(Forge が Player に足していた setForcedPose / getForcedPose に相当)。
 * 実装は PlayerMixin。Mixin パッケージの外に置くのは、Mixin 対象でないクラスから直接参照できるようにするため。
 */
public interface ForcedPoseAccess {
    /** 強制姿勢を設定する。null で解除。 */
    void medicalsystemcore$setForcedPose(@Nullable Pose pose);

    /** 現在の強制姿勢。無ければ null。 */
    @Nullable Pose medicalsystemcore$getForcedPose();
}
