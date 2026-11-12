package jp.houlab.mochidsuki.medicalsystemcore.client;

import jp.houlab.mochidsuki.medicalsystemcore.client.hud.DefibMonitorHud;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfig;
import jp.houlab.mochidsuki.medicalsystemcore.common.config.MedicalConfigKeys;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ClientHudHandler {
    // バーの背景(Forge は textures/gui/widgets.png の 66 行目。26.2 にその画像は無いので、ボタンのスプライトで代用する)
    private static final Identifier BAR_BACKGROUND = Identifier.withDefaultNamespace("widget/button");
    // バーの中身のウール
    private static final Identifier WOOL = Identifier.withDefaultNamespace("textures/block/white_wool.png");

    /**
     * クロスヘアの HUD 要素を包む(HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, ...) に渡す)。
     * Forge の RenderGuiOverlayEvent.Pre(CROSSHAIR)に相当する。{@code setCanceled(true)} していた分岐は
     * original を呼ばず、キャンセルしなかった分岐は自前の描画のあとに original(バニラのクロスヘア)を呼ぶ。
     * Forge の Pre は描画前に発火していたので、自前の描画を先にして照準を上に重ねる。
     */
    public static HudElement wrapCrosshair(HudElement original) {
        return (g, deltaTracker) -> onRenderHud(original, g, deltaTracker);
    }

    private static void onRenderHud(HudElement original, GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (ClientHealingManager.isHealing()) {
            float progress = ClientHealingManager.getProgress();
            // クロスヘアを消す(original は呼ばない)
            rendProgressBar(g, WOOL, progress);
        } else if (ClientQTEManager.isActive() || ClientQTEManager.getDisplayedResult() != null) {
            // 照準は消さない(original を呼ぶ)。モニターは下寄りに置き、照準とは重ならない。
            DefibMonitorHud.render(g, g.guiWidth(), g.guiHeight(), deltaTracker.getGameTimeDeltaPartialTick(false));
            original.extractRenderState(g, deltaTracker);
        } else if (ClientGiveUpManager.isHolding()) {
            float progress = ClientGiveUpManager.getProgress();
            // クロスヘアを消す(original は呼ばない)
            rendProgressBar(g, WOOL, progress);
            // バーの上に「リスポーンします...」テキストを描画
            int screenWidth = g.guiWidth();
            int screenHeight = g.guiHeight();
            int barY = (screenHeight - 5) / 2 + 20;
            Component text = Component.translatable(MedicalConfig.get(MedicalConfigKeys.TRANSPORT_MODE) ? "hud.medicalsystemcore.transport_hint" : "hud.medicalsystemcore.give_up_hint");
            int textWidth = Minecraft.getInstance().font.width(text);
            g.text(Minecraft.getInstance().font, text, (screenWidth - textWidth) / 2, barY - 12, 0xFFFFFFFF);
        } else if (MedicalConfig.get(MedicalConfigKeys.GIVE_UP_ENABLED) && Minecraft.getInstance().player != null
                && ClientMedicalDataManager.isPlayerUnconscious(Minecraft.getInstance().player)) {
            // ダウン中は常に「スニークで搬送/リスポーン」ヒントを表示(クロスヘアは残す)
            int screenWidth = g.guiWidth();
            int screenHeight = g.guiHeight();
            Component hint = Component.translatable(MedicalConfig.get(MedicalConfigKeys.TRANSPORT_MODE) ? "hud.medicalsystemcore.sneak_transport" : "hud.medicalsystemcore.sneak_give_up");
            int textWidth = Minecraft.getInstance().font.width(hint);
            int textY = screenHeight / 2 + 20;
            g.text(Minecraft.getInstance().font, hint, (screenWidth - textWidth) / 2, textY, 0xFFFFFFFF);
            original.extractRenderState(g, deltaTracker);
        } else {
            original.extractRenderState(g, deltaTracker);
        }
    }

    private static void rendProgressBar(GuiGraphicsExtractor g, Identifier barTexture, float progress) {
        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();

        // プログレスバーの描画
        int barWidth = 200;
        int barX = (screenWidth - barWidth) / 2;
        int barY = (screenHeight - 5) / 2 + 20; // クロスヘアより少し下

        // 背景
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND, barX, barY, barWidth, 20);
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

        // 4. アドバンス版blitを呼び出します(26.2 は RenderPipeline が第 1 引数、画面上の位置・(u, v)・描画サイズ・切り取りサイズ・全体サイズの順)
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                barTexture,
                barX + 4, barY + 5,      // 画面上の描画位置 (X, Y)
                0f, 0f,                  // テクスチャから切り取る始点 (U, V)
                GUIWidth, GUIHeight,     // 画面上の描画サイズ (幅, 高さ)
                textureCutWidth, textureCutHeight, // テクスチャから切り取るサイズ (幅, 高さ)
                textureFileWidth, textureFileHeight // テクスチャファイル全体のサイズ
        );
    }
}
