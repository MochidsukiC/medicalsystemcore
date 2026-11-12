package jp.houlab.mochidsuki.medicalsystemcore.event;

import jp.houlab.mochidsuki.medicalsystemcore.block.StretcherBlock;
import jp.houlab.mochidsuki.medicalsystemcore.blockentity.StretcherBlockEntity;
import jp.houlab.mochidsuki.medicalsystemcore.entity.StretcherEntity;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
        Direction playerDirection = player.getDirection();
        BlockPos headPos = placePos.relative(playerDirection);

        // 設置可能かチェック（足と頭の両方）
        if (!level.getBlockState(placePos).canBeReplaced() ||
                !level.getBlockState(headPos).canBeReplaced()) {
            player.sendSystemMessage(Component.literal("§cここには担架を設置できません。設置には2ブロック分のスペースが必要です。"));
            return;
        }

        // 担架ブロック状態を作成
        BlockState stretcherState = Medicalsystemcore.STRETCHER_BLOCK.get().defaultBlockState()
                .setValue(StretcherBlock.FACING, playerDirection);

        // ヘルパーメソッドを使用して2ブロック分設置
        StretcherBlock.placeDoubleBlock(level, placePos, stretcherState);

        // プレイヤーが乗っていた場合の処理
        if (carriedStretcher.getPassenger() != null) {
            ServerPlayer ridingPlayer = (ServerPlayer) carriedStretcher.getPassenger();

            // 足パーツのブロックエンティティにプレイヤー情報を設定
            if (level.getBlockEntity(placePos) instanceof StretcherBlockEntity stretcherBE) {
                ridingPlayer.stopRiding();
                ridingPlayer.teleportTo(placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5);

                // プレイヤーを担架に乗せる
                stretcherBE.setOccupyingPlayer(ridingPlayer);
                // 設置時は回収フラグをfalseに設定
                stretcherBE.setBeingCollected(false);

                ridingPlayer.sendSystemMessage(Component.literal("§e担架が設置されました。"));
            }
        } else {
            // 空の担架の場合も回収フラグを初期化
            if (level.getBlockEntity(placePos) instanceof StretcherBlockEntity stretcherBE) {
                stretcherBE.setBeingCollected(false);
            }
        }

        // エンティティを削除（アイテムは返還しない）
        carriedStretcher.discard();
        player.sendSystemMessage(Component.literal("§a担架を設置しました。"));

        // イベントをキャンセルして他の処理を防ぐ
        event.setCanceled(true);
    }
}