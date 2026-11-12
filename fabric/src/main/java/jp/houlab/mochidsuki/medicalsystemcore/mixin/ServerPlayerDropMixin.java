package jp.houlab.mochidsuki.medicalsystemcore.mixin;

import jp.houlab.mochidsuki.medicalsystemcore.core.ModEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge の ItemTossEvent の代わり。プレイヤーが手から捨てた(Q キーなど)アイテムを取り消せるようにする。
 * 26.2 では ServerPlayer#drop(boolean) と Player#drop(ItemStack, boolean) のどちらも、この 3 引数版に行き着く。
 * randomly=true は死亡時ドロップなので対象外(Forge の ItemTossEvent も出ない)。空のスタックは ItemEntity が作られないので対象外。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {
    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void medicalsystemcore$onItemToss(ItemStack itemStack, boolean randomly, boolean thrownFromHand,
                                              CallbackInfoReturnable<ItemEntity> cir) {
        if (randomly || itemStack.isEmpty()) {
            return;
        }
        if (ModEvents.onItemToss((ServerPlayer) (Object) this, itemStack)) {
            cir.setReturnValue(null);
        }
    }
}
