package jp.houlab.mochidsuki.medicalsystemcore.client.screen;

import jp.houlab.mochidsuki.medicalsystemcore.menu.MedicalBagMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class MedicalBagScreen extends AbstractContainerScreen<MedicalBagMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 3;

    public MedicalBagScreen(MedicalBagMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        // 26.2 では imageWidth/imageHeight が final なので、Forge のコンストラクタ内の代入をコンストラクタ引数で渡す(幅 176 は既定値)
        super(pMenu, pPlayerInventory, pTitle, 176, 114 + ROWS * 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    // Forge の renderBg + render(renderBackground, renderTooltip)に相当。
    // 26.2 では暗い背景は super.extractBackground が、ツールチップは AbstractContainerScreen#extractRenderState が描く。
    @Override
    public void extractBackground(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.extractBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // Upper part (title + container slots)
        pGuiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f, this.imageWidth, ROWS * 18 + 17, 256, 256);
        // Lower part (player inventory)
        pGuiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + ROWS * 18 + 17, 0f, 126f, this.imageWidth, 96, 256, 256);
    }
}
