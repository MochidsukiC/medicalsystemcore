package jp.houlab.mochidsuki.medicalsystemcore.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.screen.widget.RescueDataListWidget;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import jp.houlab.mochidsuki.medicalsystemcore.menu.RescuePortalMenu;
import jp.houlab.mochidsuki.medicalsystemcore.network.ModPackets;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundRequestRescueListPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundUpdateRescueDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class RescuePortalScreen extends AbstractContainerScreen<RescuePortalMenu> {
    // 固定サイズのテクスチャは使用しないため、ResourceLocationは不要になります
    private static final ResourceLocation RESCUE_ICON_TEXTURE = new ResourceLocation(Medicalsystemcore.MODID, "textures/gui/rescue_icon.png");


    private int LEFT_X ;
    private int LEFT_Y ;
    private int LEFT_WIDTH ;
    private int LEFT_HEIGHT;

    private int RIGHT_X ;
    private int RIGHT_Y ;
    private int RIGHT_WIDTH ;
    private int RIGHT_HEIGHT;



    private enum View { HOME, DETAILS }
    private View currentView = View.HOME;

    // UIウィジェット
    private EditBox memoBox;
    private Checkbox dispatchedCheckbox;
    private Checkbox treatedCheckbox;

    private RescueDataListWidget rescueListWidget;
    private RescueData selectedData = null;

    // サーバーからの更新中にパケットを再送信しないようにするためのフラグ
    private boolean isUpdatingFromServer = false;



    public RescuePortalScreen(RescuePortalMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        // 固定サイズの指定を削除
        // this.imageWidth = 256;
        // this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // 全画面になるため、leftPosやtopPosは使わず、this.widthとthis.heightを基準に配置します
        ModPackets.sendToServer(new ServerboundRequestRescueListPacket());



        LEFT_X = 5;
        LEFT_Y = 5+(this.width/4-40)/2;
        LEFT_WIDTH = this.width / 4;
        LEFT_HEIGHT = this.height - 5;
        RIGHT_X = LEFT_WIDTH+10;
        RIGHT_Y = LEFT_Y;
        RIGHT_WIDTH = this.width - 5;
        RIGHT_HEIGHT = LEFT_HEIGHT;


        int listX = 5;
        int listY = 5+10+(this.width/4-40);
        int listWidth = this.width / 4-listX;
        int listHeight = (this.height - 5)-listY;

        this.rescueListWidget = new RescueDataListWidget(listX, listY, listWidth, listHeight, RescueData.RESCUE_DATA_LIST , (clickedData) -> {
            // リストの項目がクリックされたときの処理
            this.selectedData = clickedData;
            this.currentView = View.DETAILS;
            updateDetailsView();
            updateWidgetVisibility();
        });


        // --- 詳細画面用のウィジェット ---
        int detailsLeft = this.width / 4 + 10; // 画面の左側1/4をリスト用とし、その右側に配置
        int detailsWidth = (this.width * 3 / 4) - 20;

        // メモ帳
        this.memoBox = new EditBox(this.font, RIGHT_X+5, this.height - 60, detailsWidth-5, 55, Component.translatable("gui.medicalsystemcore.rescue_portal.memo"));

        this.dispatchedCheckbox = new Checkbox(detailsLeft, 100, 20, 20, Component.empty(), false) {
            @Override
            public void onPress() {
                super.onPress();
                if (selectedData != null && !isUpdatingFromServer) { // サーバー更新中でなければパケット送信
                    selectedData.setDispatch(this.selected());
                    ModPackets.sendToServer(new ServerboundUpdateRescueDataPacket(selectedData.getId(), this.selected(), selectedData.isTreatment()));
                }
            }
        };

        this.treatedCheckbox = new Checkbox(detailsLeft + 100, 100, 20, 20, Component.empty(), false) {
            @Override
            public void onPress() {
                super.onPress();
                if (selectedData != null && !isUpdatingFromServer) { // サーバー更新中でなければパケット送信
                    selectedData.setTreatment(this.selected());
                    ModPackets.sendToServer(new ServerboundUpdateRescueDataPacket(selectedData.getId(), selectedData.isDispatch(), this.selected()));
                }
            }
        };

        this.addRenderableWidget(this.memoBox);
        this.addRenderableWidget(this.dispatchedCheckbox);
        this.addRenderableWidget(this.treatedCheckbox);

        updateWidgetVisibility();
    }

    // マウスクリック処理（座標を画面基準に変更）
    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        // ホームボタン（赤い十字アイコン）のクリック判定
        if (pMouseX >= 20 && pMouseX < 20+this.width/4-40 && pMouseY >= 5 && pMouseY < 5+this.width/4-40) {
            this.currentView = View.HOME;
            updateWidgetVisibility();
            return true;
        }

        // TODO: 通報リストのクリック判定も同様に this.width を基準に実装します
        if (this.rescueListWidget.mouseClicked(pMouseX, pMouseY, pButton)) {
            return true;
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollY) {
        if (this.rescueListWidget.mouseScrolled(pMouseX, pMouseY, pScrollY)) {
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, pScrollY);
    }

    /**
     * サーバーからの同期パケットを受信したときに呼び出されるメソッド
     * @param updatedData 更新された通報データ
     */
    public void onRescueDataUpdated(RescueData updatedData) {
        if (selectedData != null && selectedData.getId() == updatedData.getId()) {
            isUpdatingFromServer = true; // フラグを立てて、onPress内でのパケット送信を抑制

            // ### エラー修正箇所 ###
            // 現在の状態とサーバーからの状態が違う場合のみ、onPress()を呼んで状態をトグルさせる
            if (this.dispatchedCheckbox.selected() != updatedData.isDispatch()) {
                this.dispatchedCheckbox.onPress();
            }
            if (this.treatedCheckbox.selected() != updatedData.isTreatment()) {
                this.treatedCheckbox.onPress();
            }

            isUpdatingFromServer = false; // フラグを戻す

            if (!this.memoBox.isFocused()) {
                this.memoBox.setValue(updatedData.getMemo());
            }
        }
    }

    private void updateDetailsView() {
        if (selectedData != null) {
            isUpdatingFromServer = true;

            // ### エラー修正箇所 ###
            // こちらも同様に、状態が違う場合のみonPress()を呼ぶ
            if (this.dispatchedCheckbox.selected() != selectedData.isDispatch()) {
                this.dispatchedCheckbox.onPress();
            }
            if (this.treatedCheckbox.selected() != selectedData.isTreatment()) {
                this.treatedCheckbox.onPress();
            }

            isUpdatingFromServer = false;

            this.memoBox.setValue(selectedData.getMemo() != null ? selectedData.getMemo() : "");
        }
    }

    private void updateWidgetVisibility() {
        boolean detailsVisible = (this.currentView == View.DETAILS);
        this.memoBox.setVisible(detailsVisible);
        this.dispatchedCheckbox.visible = detailsVisible;
        this.treatedCheckbox.visible = detailsVisible;
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        // 最初に背景を描画することで、半透明のオーバーレイが全画面に表示される
        //this.renderBackground(pGuiGraphics);
        int i = 0;
        pGuiGraphics.fill(0+i, 0+i, this.width-i, this.height-i, 0x99FFFFFF); // 0xFFFFFFFFは不透明の白


        // UI要素の描画
        renderUI(pGuiGraphics, pMouseX, pMouseY);

        // ウィジェット（ボタン、テキストボックス等）の描画
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        // ツールチップの描画
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    // UI要素の描画ロジックをまとめる
    private void renderUI(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        // 左側のリストエリアの背景
        pGuiGraphics.fill(LEFT_X, LEFT_Y, LEFT_WIDTH, LEFT_HEIGHT, 0x80000000); // 半透明の黒

        // 右側
        pGuiGraphics.fill(RIGHT_X,RIGHT_Y , RIGHT_WIDTH, RIGHT_HEIGHT, 0x80000000); // 半透明の黒


        // 赤い十字（ホームボタン）のダミー表示
        RenderSystem.setShaderTexture(0, RESCUE_ICON_TEXTURE);
        int iconX = 20;
        int iconY = 5;
        int iconSize = this.width/4-40;
        // blit(テクスチャ, 描画X, 描画Y, テクスチャ切り取り開始U, V, 切り取り幅, 高さ)
        pGuiGraphics.blit(RESCUE_ICON_TEXTURE, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);

        // TODO: スクロール可能なリストの描画処理をここに記述
        this.rescueListWidget.render(pGuiGraphics, pMouseX, pMouseY);

        pGuiGraphics.drawString(this.font, "通報リスト", 10, iconY+iconSize, 0xFFFFFF, true);

        // 右側の表示
        if (currentView == View.HOME) {
            renderHomeScreen(pGuiGraphics);
        } else {
            renderDetailsScreen(pGuiGraphics);
        }
    }

    private void renderHomeScreen(GuiGraphics pGuiGraphics) {
        pGuiGraphics.drawString(this.font, "救急隊ポータル", RIGHT_X+5, RIGHT_Y+10, 0xFFFFFF, true);
        pGuiGraphics.drawString(this.font, "稼働状況: §a平常運転", RIGHT_X+5, RIGHT_Y+30, 0xFFFFFF, false);
    }

    private void renderDetailsScreen(GuiGraphics pGuiGraphics) {
        pGuiGraphics.drawString(this.font, "通報詳細", RIGHT_X+5, RIGHT_Y+5, 0xFFFFFF, true);
        pGuiGraphics.drawString(this.font, "通報者: " + selectedData.getName(), RIGHT_X+5, RIGHT_Y+20, 0xFFFFFF, false);
        pGuiGraphics.drawString(this.font, "通報カテゴリー: " + selectedData.getCategory().getName(), RIGHT_X+5, RIGHT_Y+30, 0xFFFFFF, false);
        pGuiGraphics.drawString(this.font, "通報時刻: " + new SimpleDateFormat("HH:mm:ss").format(new Date(selectedData.getReportTime())), RIGHT_X+5, RIGHT_Y+40, 0xFFFFFF, false);
        pGuiGraphics.drawString(this.font, "通報地点: " + selectedData.getLocation(), RIGHT_X+5, RIGHT_Y+50, 0xFFFFFF, false);
        pGuiGraphics.drawString(this.font, "通報内容: " + selectedData.getDescription(), RIGHT_X+5, RIGHT_Y+60, 0xFFFFFF, false);



        if (selectedData != null) {
            selectedData.setDispatch(this.dispatchedCheckbox.selected());
            selectedData.setTreatment(this.treatedCheckbox.selected());
            if(dispatchedCheckbox.selected() != selectedData.isDispatch())this.dispatchedCheckbox.onPress();
            if(treatedCheckbox.selected() != selectedData.isTreatment())this.treatedCheckbox.onPress();
            //this.memoBox.setValue(selectedData.getMemo() != null ? selectedData.getMemo() : "");

        }

        // チェックボックスのラベル
        //pGuiGraphics.drawString(this.font, "出動", RIGHT_X + 22, 106, 0xFFFFFF, false);
        //pGuiGraphics.drawString(this.font, "処置", RIGHT_X + 122, 106, 0xFFFFFF, false);
    }

    // renderBgは不要なので削除
    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        // このメソッドは空にする
    }

    // renderLabelsは描画座標が変わるため、renderUIメソッドにロジックを統合
    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        // このメソッドも空にする
    }
}
