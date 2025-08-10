package jp.houlab.mochidsuki.medicalsystemcore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Medicalsystemcore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientHudHandler {
    // 十字キーなどのテクスチャ
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation WOOL = new ResourceLocation("minecraft", "textures/block/white_wool.png");

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Pre event) {
        // ▼▼▼ このif文の条件を修正 ▼▼▼
        // クロスヘア（十字キー）が描画されるタイミングに介入
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            if (ClientHealingManager.isHealing()) {
                float progress = ClientHealingManager.getProgress();
                rendProgressBar(event,WOOL, progress);
            } else if (ClientQTEManager.isActive()) {
                event.setCanceled(true);
                drawQTEHud(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
            }
        }
    }

    /**
     * QTEミニゲーム用のHUDを描画する専用メソッド
     */
    private static void drawQTEHud(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        // --- バーのジオメトリ定義 ---
        int barWidth = 182;
        int barHeight = 20;
        int barX = (screenWidth - barWidth) / 2;
        int barY = (screenHeight - barHeight) / 2;

        // --- 各ゾーンの描画 ---
        // 1. 背景 (濃いグレー)
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // 2. 成功ゾーン (薄いグレー)
        int successStartX = barX + (int) (barWidth * ClientQTEManager.getSuccessStart());
        int successEndX = barX + (int) (barWidth * ClientQTEManager.getSuccessEnd());
        guiGraphics.fill(successStartX, barY, successEndX, barY + barHeight, 0xFF888888);

        // 3. 大成功ゾーン (黄色)
        int greatSuccessStartX = barX + (int) (barWidth * ClientQTEManager.getGreatSuccessStart());
        int greatSuccessEndX = barX + (int) (barWidth * ClientQTEManager.getGreatSuccessEnd());
        guiGraphics.fill(greatSuccessStartX, barY, greatSuccessEndX, barY + barHeight, 0xFFFFFF55);

        // 4. 動くアンカー (赤い縦線)
        int anchorX = barX + (int) (barWidth * ClientQTEManager.getBarPosition());
        guiGraphics.fill(anchorX - 1, barY - 2, anchorX + 1, barY + barHeight + 2, 0xFFFF0000);
    }

    private static void rendProgressBar(RenderGuiOverlayEvent.Pre event,ResourceLocation barTexture, float progress) {
        // バニラのクロスヘアを非表示にする
        event.setCanceled(true);

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        // プログレスバーの描画
        RenderSystem.setShaderTexture(0, WIDGETS);

        int barWidth = 200;
        int barX = (screenWidth - barWidth) / 2;
        int barY = (screenHeight - 5) / 2 + 20; // クロスヘアより少し下

        // 背景
        guiGraphics.blit(WIDGETS, barX, barY, 0, 66, barWidth, 20);
        // 進捗

        // 1. 画面に描画したい最終的な幅と高さを定義します
        int GUIWidth = (int) (progress * (barWidth - 8));
        int GUIHeight = 10; // 画面上では縦10pxの高さにしたい

        // 2. 素材のテクスチャファイルから切り取りたい部分のサイズを定義します
        int textureCutWidth = GUIWidth/2; // 幅は画面上の幅と同じ
        int textureCutHeight = 5; // 縦は画面上の高さの2倍である20px分を切り取る

        // 3. テクスチャファイル全体のサイズを指定します (例: 16x16, 32x32, 64x64など)
        //    あなたのWOOLテクスチャが16x16ピクセルだと仮定します。
        int textureFileWidth = 16;
        int textureFileHeight = 16;


        // 4. アドバンス版blitを呼び出します
        guiGraphics.blit(
                barTexture,
                barX + 4, barY + 5,      // 画面上の描画位置 (X, Y)
                GUIWidth, GUIHeight,  // 画面上の描画サイズ (幅, 高さ)
                0, 0,                      // テクスチャから切り取る始点 (U, V)
                textureCutWidth, textureCutHeight, // テクスチャから切り取るサイズ (幅, 高さ)
                textureFileWidth, textureFileHeight // テクスチャファイル全体のサイズ
        );
    }
}