package jp.houlab.mochidsuki.medicalsystemcore.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import jp.houlab.mochidsuki.medicalsystemcore.core.ModEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 防具・魔法軽減・衝撃吸収の適用後のダメージ量を取る(Forge の LivingDamageEvent#getAmount に相当)。
 * Fabric の AFTER_DAMAGE は防具の前の量なので使えない。
 * Player#actuallyHurt の引数 dmg は防具・魔法軽減・衝撃吸収の順に書き換えられ、causeFoodExhaustion の直前で最終値になる。
 * causeFoodExhaustion はダメージを受けない場合(無敵・最終量 0)には通らない(最終量 0 の出血加算は 0 なので Forge と同じ結果)。
 */
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
    @Inject(
            method = "actuallyHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V")
    )
    private void medicalsystemcore$onDamageApplied(ServerLevel level, DamageSource source, float originalDamage, CallbackInfo ci,
                                                   @Local(argsOnly = true) float dmg) {
        ModEvents.onPlayerDamaged((Player) (Object) this, source, dmg);
    }
}
