package jp.houlab.mochidsuki.medicalsystemcore.client.screen;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientTrackingManager;
import jp.houlab.mochidsuki.medicalsystemcore.client.screen.widget.RescueDataListWidget;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.RescueRules;
import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundDeleteRescueDataPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundRequestRescueListPacket;
import jp.houlab.mochidsuki.medicalsystemcore.network.ServerboundUpdateRescueDataPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class RescuePortalScreen extends Screen {
    // 固定サイズのテクスチャは使用しないため、背景用のテクスチャは不要です
    private static final Identifier RESCUE_ICON_TEXTURE = Medicalsystemcore.id("textures/gui/rescue_icon.png");

    private List<RescueData> rescueDataList = new ArrayList<>();


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
    private MultiLineEditBox memoBox;
    private Checkbox dispatchedCheckbox;
    private Checkbox treatedCheckbox;
    private Button saveButton; // ★ 保存ボタンを追加
    private Button trackButton;
    private Button deleteButton;
    // 削除の 2 段階確認: true の間は 1 回目が押された状態(次の押下で確定)
    private boolean deleteConfirmPending = false;

    private RescueDataListWidget rescueListWidget;
    private RescueData selectedData = null;

    // サーバーからの更新中にパケットを再送信しないようにするためのフラグ
    private boolean isUpdatingFromServer = false;
    private boolean hasChanges = false; // ★ 変更フラグを追加


    public RescuePortalScreen(Component pTitle) {
        super(pTitle);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        // 全画面になるため、leftPosやtopPosは使わず、this.widthとthis.heightを基準に配置します
        ClientPlayNetworking.send(new ServerboundRequestRescueListPacket());



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

        this.rescueListWidget = new RescueDataListWidget(listX, listY, listWidth, listHeight, rescueDataList , (clickedData) -> {
            // リストの項目がクリックされたときの処理
            this.selectedData = clickedData;
            this.currentView = View.DETAILS;
            updateDetailsView();
            updateWidgetVisibility();
        });


        // --- 詳細画面用のウィジェット ---
        int detailsLeft = this.width / 4 + 10; // 画面の左側1/4をリスト用とし、その右側に配置
        int detailsWidth = (this.width * 3 / 4) - 20;

        // メモ帳 (値が変更されたら checkForChanges() を呼ぶ)
        this.memoBox = MultiLineEditBox.builder()
                .setX(RIGHT_X + 5)
                .setY(this.height - 60)
                .setPlaceholder(Component.translatable("gui.medicalsystemcore.rescue_portal.memo"))
                .build(this.font, detailsWidth - 5, 35, Component.literal(""));
        // 値が変更されたときに呼び出されるリスナーを設定
        // サーバーと同じ上限を入力欄にも設定する
        this.memoBox.setCharacterLimit(RescueRules.MEMO_MAX_LENGTH);
        this.memoBox.setValueListener(text -> this.checkForChanges());

        // 出動チェックボックス (切り替わったら markAsChanged() を呼ぶ)
        // 26.2 の Checkbox は大きさを指定できない(ボックス 17px + ラベル)。Forge は 20x20 だった。
        this.dispatchedCheckbox = Checkbox.builder(Component.translatable("gui.medicalsystemcore.rescue_portal.dispatched"), this.font)
                .pos(RIGHT_X + 5, this.height - 85)
                .selected(false)
                .onValueChange((checkbox, value) -> markAsChanged())
                .build();

        // 処置チェックボックス (切り替わったら markAsChanged() を呼ぶ)
        this.treatedCheckbox = Checkbox.builder(Component.translatable("gui.medicalsystemcore.rescue_portal.treated"), this.font)
                .pos(RIGHT_X + 5 + 100, this.height - 85)
                .selected(false)
                .onValueChange((checkbox, value) -> markAsChanged())
                .build();

        // 保存ボタンの初期化
        this.saveButton = Button.builder(Component.literal("保存"), button -> saveChanges())
                .bounds(this.width - 50, this.height - 25, 40, 20)
                .build();

        // 追跡ボタンの初期化
        this.trackButton = Button.builder(Component.translatable("gui.medicalsystemcore.rescue_portal.track"), button -> toggleTracking())
                .bounds(this.width - 100, this.height - 25, 45, 20)
                .build();


        // 削除ボタンの初期化(1 回目で「本当に削除」に変わり、2 回目で確定)
        this.deleteConfirmPending = false;
        this.deleteButton = Button.builder(Component.translatable("gui.medicalsystemcore.rescue_portal.delete"), button -> onDeletePressed())
                .bounds(this.width - 175, this.height - 25, 70, 20)
                .build();

        this.addRenderableWidget(this.memoBox);
        this.addRenderableWidget(this.deleteButton);
        this.addRenderableWidget(this.dispatchedCheckbox);
        this.addRenderableWidget(this.treatedCheckbox);
        this.addRenderableWidget(this.saveButton);
        this.addRenderableWidget(this.trackButton);

        updateWidgetVisibility();
    }

    // マウスクリック処理（座標を画面基準に変更）
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double pMouseX = event.x();
        double pMouseY = event.y();
        // ホームボタン（赤い十字アイコン）のクリック判定
        if (pMouseX >= 20 && pMouseX < 20+this.width/4-40 && pMouseY >= 5 && pMouseY < 5+this.width/4-40) {
            this.currentView = View.HOME;
            this.rescueListWidget.setSelectedId(-1);
            this.selectedData = null;
            resetDeleteConfirm();
            updateWidgetVisibility();
            return true;
        }

        // TODO: 通報リストのクリック判定も同様に this.width を基準に実装します
        if (this.rescueListWidget.mouseClicked(pMouseX, pMouseY, event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        if (this.rescueListWidget.mouseScrolled(pMouseX, pMouseY, pScrollY)) {
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY);
    }


    /**
     * UI操作によってデータが変更されたことをマークし、保存ボタンの状態を更新する
     */
    private void markAsChanged() {
        if (!isUpdatingFromServer) {
            this.hasChanges = true;
            updateSaveButtonState();
        }
    }

    /**
     * 保存ボタンの状態（アクティブ/非アクティブ）を更新する
     */
    private void updateSaveButtonState() {
        if (this.saveButton != null) {
            this.saveButton.active = this.hasChanges;
        }
    }

    /**
     * UIの状態と保存済みデータを比較し、変更があればhasChangesフラグを更新する
     */
    private void checkForChanges() {
        if (isUpdatingFromServer || selectedData == null) {
            return;
        }

        boolean dispatchStateChanged = this.dispatchedCheckbox.selected() != selectedData.isDispatch();
        boolean treatmentStateChanged = this.treatedCheckbox.selected() != selectedData.isTreatment();

        String currentMemo = this.memoBox.getValue();
        String savedMemo = selectedData.getMemo() != null ? selectedData.getMemo() : "";
        boolean memoChanged = !Objects.equals(currentMemo, savedMemo);

        this.hasChanges = dispatchStateChanged || treatmentStateChanged || memoChanged;
        updateSaveButtonState();
    }

    private void toggleTracking() {
        if (selectedData == null) return;
        RescueData currentlyTracked = ClientTrackingManager.getTrackedData();
        if (currentlyTracked != null && currentlyTracked.getId() == selectedData.getId()) {
            ClientTrackingManager.clearTracking();
        } else {
            ClientTrackingManager.setTrackedData(selectedData);
        }
        updateTrackButtonLabel();
    }

    /**
     * 削除ボタンの押下。1 回目は確認状態に入り(表示が「本当に削除」に変わる)、2 回目でサーバーへ削除を要求する。
     * 画面の更新は、サーバーからの削除通知(onRescueDataRemoved)で行う。
     */
    private void onDeletePressed() {
        if (selectedData == null) return;
        if (!deleteConfirmPending) {
            deleteConfirmPending = true;
            deleteButton.setMessage(Component.translatable("gui.medicalsystemcore.rescue_portal.delete_confirm"));
            return;
        }
        ClientPlayNetworking.send(new ServerboundDeleteRescueDataPacket(selectedData.getId()));
        resetDeleteConfirm();
    }

    /** 削除ボタンを 1 段階目(通常表示)に戻す。選択が変わったときなどに呼ぶ。 */
    private void resetDeleteConfirm() {
        deleteConfirmPending = false;
        if (deleteButton != null) {
            deleteButton.setMessage(Component.translatable("gui.medicalsystemcore.rescue_portal.delete"));
        }
    }

    /**
     * サーバーから通報の削除が通知されたときに呼ばれる。
     * 選択中の通報が消えた場合は、詳細表示が存在しない通報を参照しないよう、選択を外してホームへ戻す。
     */
    public void onRescueDataRemoved(int rescueId) {
        // ウィジェットも同じリストを参照している
        rescueDataList.removeIf(d -> d.getId() == rescueId);
        rescueListWidget.onListChanged();
        if (selectedData != null && selectedData.getId() == rescueId) {
            selectedData = null;
            currentView = View.HOME;
            rescueListWidget.setSelectedId(-1);
            resetDeleteConfirm();
            updateWidgetVisibility();
        }
    }

    private void updateTrackButtonLabel() {
        if (this.trackButton == null || this.selectedData == null) return;
        RescueData currentlyTracked = ClientTrackingManager.getTrackedData();
        boolean isTracking = currentlyTracked != null && currentlyTracked.getId() == selectedData.getId();
        this.trackButton.setMessage(Component.translatable(
                isTracking ? "gui.medicalsystemcore.rescue_portal.tracking" : "gui.medicalsystemcore.rescue_portal.track"
        ));
    }

    /**
     * 変更内容をサーバーに送信する
     */
    private void saveChanges() {
        if (selectedData != null && hasChanges) {
            // チェックボックスとメモの両方の更新パケットを送信
            ClientPlayNetworking.send(new ServerboundUpdateRescueDataPacket(selectedData.getId(), this.dispatchedCheckbox.selected(), this.treatedCheckbox.selected()));
            ClientPlayNetworking.send(new ServerboundUpdateRescueDataPacket(selectedData.getId(), this.memoBox.getValue()));

            // ローカルのデータも更新して、次の比較に備える
            this.selectedData.setDispatch(this.dispatchedCheckbox.selected());
            this.selectedData.setTreatment(this.treatedCheckbox.selected());
            this.selectedData.setMemo(this.memoBox.getValue());

            this.hasChanges = false;
            updateSaveButtonState();
        }
    }

    /**
     * チェックボックスを押された状態にする。26.2 の Checkbox は選択状態を直接設定する手段が無いので、
     * Forge の onPress() と同じく押下処理でトグルさせる(呼び出し側が isUpdatingFromServer で再送を抑制する)。
     */
    private static void toggle(Checkbox checkbox) {
        checkbox.onPress(new MouseButtonEvent(0, 0, new MouseButtonInfo(0, 0)));
    }

    /**
     * サーバーからの同期パケットを受信したときに呼び出されるメソッド
     * @param updatedData 更新された通報データ
     */
    public void onRescueDataUpdated(RescueData updatedData) {
        if (selectedData != null && selectedData.getId() == updatedData.getId()) {
            isUpdatingFromServer = true; // フラグを立てて、onPress内でのパケット送信を抑制

            // ### エラー修正箇所 ###
            // 現在の状態とサーバーからの状態が違う場合のみ、押下処理を呼んで状態をトグルさせる
            if (this.dispatchedCheckbox.selected() != updatedData.isDispatch()) {
                toggle(this.dispatchedCheckbox);
            }
            if (this.treatedCheckbox.selected() != updatedData.isTreatment()) {
                toggle(this.treatedCheckbox);
            }

            isUpdatingFromServer = false; // フラグを戻す

            if (!this.memoBox.isFocused()) {
                this.memoBox.setValue(updatedData.getMemo());
            }
        }
    }

    /**
     * サーバーから受信した新しいリストでUIを更新するためのメソッド
     * @param newDataList サーバーから送られてきた最新の通報リスト
     */
    public void updateRescueDataList(List<RescueData> newDataList) {
        this.rescueDataList = newDataList;
        this.rescueListWidget.setDataList(newDataList); // ウィジェットにも新しいリストを渡す
    }

    public List<RescueData> getRescueDataList() {
        return rescueDataList;
    }


    private void updateDetailsView() {
        if (selectedData != null) {
            // 選択が変わったので、削除の確認は 1 段階目に戻す
            resetDeleteConfirm();
            isUpdatingFromServer = true;

            // ### エラー修正箇所 ###
            // こちらも同様に、状態が違う場合のみ押下処理を呼ぶ
            if (this.dispatchedCheckbox.selected() != selectedData.isDispatch()) {
                toggle(this.dispatchedCheckbox);
            }
            if (this.treatedCheckbox.selected() != selectedData.isTreatment()) {
                toggle(this.treatedCheckbox);
            }
            this.memoBox.setValue(selectedData.getMemo() != null ? selectedData.getMemo() : "");

            // ★ 修正点3: 全てのUI更新が終わった後にフラグを戻す
            isUpdatingFromServer = false;

            // 最後に変更状態をリセットし、ボタンを非アクティブ化する
            hasChanges = false;
            updateSaveButtonState();
            updateTrackButtonLabel();
        }
    }

    private void updateWidgetVisibility() {
        boolean detailsVisible = (this.currentView == View.DETAILS);
        this.memoBox.visible =(detailsVisible);
        this.dispatchedCheckbox.visible = detailsVisible;
        this.treatedCheckbox.visible = detailsVisible;
        this.saveButton.visible = detailsVisible;
        this.trackButton.visible = detailsVisible;
        this.deleteButton.visible = detailsVisible;
    }

    /**
     * Forge は背景(renderBackground)を呼んでいなかった(下の extractRenderState で全面を白で塗る)。
     * 26.2 は画面の背景を必ず extractBackground で描かせ、既定ではワールドを暗くぼかす背景が付くので、何も描かないようにする(ChatScreen と同じ)。
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        // 最初に背景を描画することで、半透明のオーバーレイが全画面に表示される
        int i = 0;
        pGuiGraphics.fill(0+i, 0+i, this.width-i, this.height-i, 0x99FFFFFF); // 0xFFFFFFFFは不透明の白


        // UI要素の描画
        renderUI(pGuiGraphics, pMouseX, pMouseY);

        // ウィジェット（ボタン、テキストボックス等）の描画
        super.extractRenderState(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

    }

    // UI要素の描画ロジックをまとめる
    private void renderUI(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY) {
        // 左側のリストエリアの背景
        pGuiGraphics.fill(LEFT_X, LEFT_Y, LEFT_WIDTH, LEFT_HEIGHT, 0x80000000); // 半透明の黒

        // 右側
        pGuiGraphics.fill(RIGHT_X,RIGHT_Y , RIGHT_WIDTH, RIGHT_HEIGHT, 0x80000000); // 半透明の黒


        // 赤い十字（ホームボタン）のダミー表示
        int iconX = 20;
        int iconY = 5;
        int iconSize = this.width/4-40;
        // blit(パイプライン, テクスチャ, 描画X, 描画Y, テクスチャ切り取り開始U, V, 描画幅, 高さ, テクスチャ幅, 高さ)
        // Forge と同じく、テクスチャの大きさを iconSize として扱い、テクスチャ全体を iconSize の正方形に引き伸ばす
        pGuiGraphics.blit(RenderPipelines.GUI_TEXTURED, RESCUE_ICON_TEXTURE, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize);

        this.rescueListWidget.render(pGuiGraphics, pMouseX, pMouseY);

        // 26.2 の文字描画は alpha が 0 だと何も描かれないので、Forge の RGB 色に不透明の alpha を付ける
        pGuiGraphics.text(this.font, "通報リスト", 10, iconY+iconSize, 0xFFFFFFFF, true);

        // 右側の表示
        if (currentView == View.HOME) {
            renderHomeScreen(pGuiGraphics);
        } else {
            renderDetailsScreen(pGuiGraphics);
        }
    }

    private void renderHomeScreen(GuiGraphicsExtractor pGuiGraphics) {
        pGuiGraphics.text(this.font, "救急隊ポータル", RIGHT_X+5, RIGHT_Y+10, 0xFFFFFFFF, true);
        pGuiGraphics.text(this.font, "稼働状況: §a平常運転", RIGHT_X+5, RIGHT_Y+30, 0xFFFFFFFF, false);
    }

    private void renderDetailsScreen(GuiGraphicsExtractor pGuiGraphics) {
        pGuiGraphics.text(this.font, "通報詳細", RIGHT_X+5, RIGHT_Y+5, 0xFFFFFFFF, true);
        pGuiGraphics.text(this.font, "通報者: " + selectedData.getName(), RIGHT_X+5, RIGHT_Y+20, 0xFFFFFFFF, false);
        pGuiGraphics.text(this.font, "通報カテゴリー: " + selectedData.getCategory().getName(), RIGHT_X+5, RIGHT_Y+30, 0xFFFFFFFF, false);
        pGuiGraphics.text(this.font, "通報時刻: " + new SimpleDateFormat("HH:mm:ss").format(new Date(selectedData.getReportTime())), RIGHT_X+5, RIGHT_Y+40, 0xFFFFFFFF, false);
        pGuiGraphics.text(this.font, "通報地点: " + selectedData.getLocation(), RIGHT_X+5, RIGHT_Y+50, 0xFFFFFFFF, false);
        pGuiGraphics.text(this.font, "ディメンション: " + selectedData.getDimensionDisplayName(), RIGHT_X+5, RIGHT_Y+60, 0xFF55FFFF, false);
        pGuiGraphics.text(this.font, "通報内容: " + selectedData.getDescription(), RIGHT_X+5, RIGHT_Y+70, 0xFFFFFFFF, false);

        //updateSaveButtonState();
        // チェックボックスのラベル
        //pGuiGraphics.drawString(this.font, "出動", RIGHT_X + 22, 106, 0xFFFFFF, false);
        //pGuiGraphics.drawString(this.font, "処置", RIGHT_X + 122, 106, 0xFFFFFF, false);
    }

}
