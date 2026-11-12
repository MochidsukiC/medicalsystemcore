package jp.houlab.mochidsuki.medicalsystemcore.client.mixin;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientInputHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge の InputEvent.MouseButton.Pre に相当する。取り消すとそのクリックの処理が丸ごと行われない。 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void medicalsystemcore$onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        if (ClientInputHandler.onMouseButton(rawButtonInfo.button(), action)) {
            ci.cancel();
        }
    }
}
