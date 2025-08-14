package jp.houlab.mochidsuki.medicalsystemcore.event;

import jp.houlab.mochidsuki.medicalsystemcore.block.StretcherBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ストレッチャーの特殊設置処理を行うイベントハンドラー
 * 新仕様: ストレッチャー持ち + 素手 + しゃがみ + 地面右クリック
 */
@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StretcherPlacementHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();

        // サーバーサイドのみで処理
        if (level.isClientSide()) return;

        // 新仕様の条件チェック: 素手 + しゃがみ
        if (event.getHand() != InteractionHand.MAIN_HAND ||
                !player.getMainHandItem().isEmpty() ||
                !player.isShiftKeyDown()) {
            return;
        }

        // プレイヤーがストレッチャーエンティティを持っているかチェック
        StretcherEntity carriedStretcher = level.getEntitiesOfClass(StretcherEntity.class,
                        player.getBoundingBox().inflate(10.0))
                .stream()
                .filter(stretcher -> stretcher.getCarrier() != null &&
                        stretcher.getCarrier().getUUID().equals(player.getUUID()))
                .findFirst()
                .orElse(null);

        if (carriedStretcher == null) {
            // ストレッチャーを持っていない場合は何もしない
            return;
        }

        // 設置位置を計算
        BlockPos clickedPos = event.getPos();
        BlockPos placePos = clickedPos.above();

        // 設置可能かチェック
        if (!level.getBlockState(placePos).canBeReplaced()) {
            player.sendSystemMessage(Component.literal("§cここには担架を設置できません。"));
            return;
        }

        // 担架ブロックを設置
        BlockState stretcherState = Medicalsystemcore.STRETCHER_BLOCK.get().defaultBlockState()
                .setValue(StretcherBlock.FACING, player.getDirection());
        level.setBlock(placePos, stretcherState, 3);

        // プレイヤーが乗っていた場合の処理
        if (carriedStretcher.getPassenger() != null) {
            ServerPlayer ridingPlayer = (ServerPlayer) carriedStretcher.getPassenger();

            // ブロックエンティティにプレイヤー情報を設定
            if (level.getBlockEntity(placePos) instanceof StretcherBlockEntity stretcherBE) {
                ridingPlayer.stopRiding();
                ridingPlayer.teleportTo(placePos.getX() + 0.5, placePos.getY() + 0.3, placePos.getZ() + 0.5);

                // プレイヤーを担架に乗せる
                stretcherBE.setOccupyingPlayer(ridingPlayer);

                ridingPlayer.sendSystemMessage(Component.literal("§e担架が設置されました。"));
            }
        }

        // エンティティを削除（アイテムは返還しない）
        carriedStretcher.discard();
        player.sendSystemMessage(Component.literal("§a担架を設置しました。"));

        // イベントをキャンセルして他の処理を防ぐ
        event.setCanceled(true);
    }
}