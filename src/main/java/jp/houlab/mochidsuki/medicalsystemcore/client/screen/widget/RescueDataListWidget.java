// jp/houlab/mochidsuki/medicalsystemcore/client/screen/widget/RescueDataListWidget.java
package jp.houlab.mochidsuki.medicalsystemcore.client.screen.widget;

import jp.houlab.mochidsuki.medicalsystemcore.core.RescueData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * 通報リストを表示し、操作するためのカスタムウィジェット
 */
public class RescueDataListWidget {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<RescueData> dataList;
    private final Consumer<RescueData> onClick;

    private double scrollAmount = 0.0;
    private int selectedIndex = -1;
    private static final int ROW_HEIGHT = 15;

    public RescueDataListWidget(int x, int y, int width, int height, Consumer<RescueData> onClick) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.dataList = RescueData.RESCUE_DATA_LIST; // 静的リストを参照
        this.onClick = onClick;
    }

    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        // Scissorテストで描画領域を矩形内に制限
        pGuiGraphics.enableScissor(x, y, x + width, y + height);

        for (int i = 0; i < dataList.size(); i++) {
            RescueData data = dataList.get(i);
            int rowTopY = y - (int)scrollAmount + (i * ROW_HEIGHT);

            // 描画領域内にあれば描画
            if (rowTopY >= y - ROW_HEIGHT && rowTopY <= y + height) {
                // 背景色の決定
                boolean isHovered = pMouseX >= x && pMouseX < x + width && pMouseY >= rowTopY && pMouseY < rowTopY + ROW_HEIGHT;
                int bgColor = 0;
                if (i == selectedIndex) {
                    bgColor = 0xFFBE0000; // 選択色 (濃い青)
                } else if (isHovered) {
                    bgColor = 0x55AAAAAA; // ホバー色 (半透明の白)
                }
                if (bgColor != 0) {
                    pGuiGraphics.fill(x, rowTopY, x + width, rowTopY + ROW_HEIGHT, bgColor);
                }

                // テキスト描画
                String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(data.getReportTime()));
                // 表示テキストに時刻を追加
                String displayText = String.format("[%s] %s", timeStr, data.getName());
                pGuiGraphics.drawString(Minecraft.getInstance().font, displayText, x + 5, rowTopY + 4, 0xFFFFFF, false);
            }
        }

        // Scissorテストを無効化
        pGuiGraphics.disableScissor();
    }

    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0 && pMouseX >= x && pMouseX < x + width && pMouseY >= y && pMouseY < y + height) {
            double relativeY = pMouseY - y + this.scrollAmount;
            int clickedIndex = (int)(relativeY / ROW_HEIGHT);

            if (clickedIndex >= 0 && clickedIndex < dataList.size()) {
                this.selectedIndex = clickedIndex;
                // コールバック関数を実行して、選択されたデータをScreenに渡す
                this.onClick.accept(dataList.get(clickedIndex));
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollY) {
        if (pMouseX >= x && pMouseX < x + width && pMouseY >= y && pMouseY < y + height) {
            int maxScroll = Math.max(0, dataList.size() * ROW_HEIGHT - height);
            this.scrollAmount -= pScrollY * ROW_HEIGHT;
            if (this.scrollAmount < 0) this.scrollAmount = 0;
            if (this.scrollAmount > maxScroll) this.scrollAmount = maxScroll;
            return true;
        }
        return false;
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
    }
}