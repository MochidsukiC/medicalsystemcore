package jp.houlab.mochidsuki.medicalsystemcore.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ClientInput#moveVector(protected)の書き込み口。意識不明時の移動入力のゼロ化(ClientInputHandler)で使う。 */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector")
    void medicalsystemcore$setMoveVector(Vec2 moveVector);
}
