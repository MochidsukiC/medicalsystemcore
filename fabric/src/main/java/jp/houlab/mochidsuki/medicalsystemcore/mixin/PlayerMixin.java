package jp.houlab.mochidsuki.medicalsystemcore.mixin;

import jp.houlab.mochidsuki.medicalsystemcore.core.ForcedPoseAccess;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player の強制姿勢(PoseController が使う)。
 * Forge は Player に setForcedPose / getForcedPose を追加し、updatePlayerPose の先頭で
 * 「強制姿勢があればそれを setPose して終了」としていた。それと同じものを Mixin で足す。
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements ForcedPoseAccess {
    @Unique
    private @Nullable Pose medicalsystemcore$forcedPose;

    @Override
    public void medicalsystemcore$setForcedPose(@Nullable Pose pose) {
        this.medicalsystemcore$forcedPose = pose;
    }

    @Override
    public @Nullable Pose medicalsystemcore$getForcedPose() {
        return this.medicalsystemcore$forcedPose;
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void medicalsystemcore$applyForcedPose(CallbackInfo ci) {
        if (this.medicalsystemcore$forcedPose != null) {
            ((Player) (Object) this).setPose(this.medicalsystemcore$forcedPose);
            ci.cancel();
        }
    }
}
