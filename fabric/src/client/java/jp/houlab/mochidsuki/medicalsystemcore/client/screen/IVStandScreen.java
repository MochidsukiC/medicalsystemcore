package jp.houlab.mochidsuki.medicalsystemcore.client.screen;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.menu.IVStandMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class IVStandScreen extends AbstractContainerScreen<IVStandMenu> {
    private static final Identifier TEXTURE =
             Medicalsystemcore.id("textures/gui/iv_stand_gui.png");

    public IVStandScreen(IVStandMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    // Forge の renderBg + render(renderBackground, renderTooltip)に相当。
    // 26.2 では暗い背景は super.extractBackground が、ツールチップは AbstractContainerScreen#extractRenderState が描く。
    @Override
    public void extractBackground(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.extractBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        pGuiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);
    }
}
