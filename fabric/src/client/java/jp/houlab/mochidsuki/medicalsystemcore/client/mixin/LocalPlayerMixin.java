package jp.houlab.mochidsuki.medicalsystemcore.client.mixin;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientInputHandler;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge の MovementInputUpdateEvent に相当する。
 * Forge は LocalPlayer#aiStep の {@code input.tick()} の直後で発火していたので、同じ位置(ClientInput#tick の直後)に入れる。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    public ClientInput input;

    @Inject(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER)
    )
    private void medicalsystemcore$afterInputTick(CallbackInfo ci) {
        ClientInputHandler.onMovementInputUpdate((LocalPlayer) (Object) this, this.input);
    }
}
