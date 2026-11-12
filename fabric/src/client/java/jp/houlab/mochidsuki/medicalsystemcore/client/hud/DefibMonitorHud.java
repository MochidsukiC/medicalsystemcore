package jp.houlab.mochidsuki.medicalsystemcore.client.hud;

import jp.houlab.mochidsuki.medicalsystemcore.client.ClientMedicalDataManager;
import jp.houlab.mochidsuki.medicalsystemcore.client.ClientQTEManager;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.HeartStatus;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.QTEResult;
import jp.houlab.mochidsuki.medicalsystemcore.common.rules.QteRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 除細動 QTE の HUD(案 A「除細動器モニター」)。
 *
 * <p>テクスチャは使わず {@link GuiGraphicsExtractor#fill} と文字だけで描く(Fabric 26.2 版へそのまま移せるようにするため)。
 * 時間で変わる表示(波形のスクロール・明滅・結果のフラッシュ)は、フレーム数ではなく
 * {@link ClientQTEManager#getAnimationTime} の tick と partialTick から求める(フレームレートに依存しない)。
 * バーの位置は判定に使う tick 単位の値をそのまま表示し、補間しない(表示と判定を一致させるため)。</p>
 *
 * <p>文言はすべて lang キー。「DEFIB」「VF」「J」などの機器表示も、リソースパックや別言語で差し替えられるよう
 * キーにしてある(既定では ja/en とも同じ略称)。</p>
 */
public final class DefibMonitorHud {
    // ---- パネルの寸法(GUI スケール座標) ----
    private static final int PANEL_W = 160;
    private static final int PANEL_H = 60;
    /**
     * 画面下端からパネル下端までの余白。ホットバー(高さ 22)と体力・満腹度の行(下から 39、防具の行は 49)に
     * 重ならないよう、防具の行の上に 3 の隙間を空ける。
     */
    private static final int BOTTOM_MARGIN = 52;
    /** スクリーン内側の余白(立体枠 2 + 余白 3)。 */
    private static final int PAD = 5;
    private static final int SEGMENTS = 40;
    private static final int SEGMENT_GAP = 1;
    private static final int BAR_TOP = 32;   // sy からのオフセット
    private static final int BAR_H = 9;
    private static final int ECG_CENTER = 20; // sy からのオフセット
    /** 満充電のエネルギー(J)。バー位置 1.0 がこの値。 */
    private static final int MAX_JOULES = 200;

    // ---- 色(0xAARRGGBB) ----
    private static final int BEVEL_OUTLINE = 0xFF000000;
    private static final int BEVEL_BASE = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int SCREEN_BG = 0xFF0B1410;
    private static final int SCREEN_TEXT = 0xFF7DFFB0;
    private static final int GOLD = 0xFFF2C94C;
    private static final int GREEN = 0xFF5FD38D;
    private static final int RED = 0xFFFF6B5E;
    private static final int WHITE = 0xFFF4F7F5;

    private DefibMonitorHud() {
    }

    /** QTE 実行中、または結果表示中に呼ぶ。 */
    public static void render(GuiGraphicsExtractor g, int screenW, int screenH, float partialTick) {
        Font font = Minecraft.getInstance().font;
        float time = ClientQTEManager.getAnimationTime(partialTick);
        QTEResult result = ClientQTEManager.getDisplayedResult();

        int px = (screenW - PANEL_W) / 2;
        int py = screenH - BOTTOM_MARGIN - PANEL_H;

        drawBevelPanel(g, px, py);
        drawScreen(g, font, px + PAD, py + PAD, PANEL_W - PAD * 2, time);

        if (result != null) {
            drawFlash(g, screenW, screenH, result, ClientQTEManager.getResultAge(partialTick));
            drawResultText(g, font, screenW / 2, py, result);
        } else {
            drawText(g, font, Component.translatable("hud.medicalsystemcore.qte.hint"), screenW / 2, py - 11, WHITE, Align.CENTER, 1.0f, true);
        }
    }

    // ------------------------------------------------------------------ パネルと枠

    /** バニラ GUI と同じ立体枠(明るい上・左辺、暗い下・右辺)の中に、暗い緑のスクリーンを置く。 */
    private static void drawBevelPanel(GuiGraphicsExtractor g, int x, int y) {
        int w = PANEL_W, h = PANEL_H;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BEVEL_OUTLINE);
        g.fill(x, y, x + w, y + h, BEVEL_BASE);
        g.fill(x, y, x + w - 1, y + 1, BEVEL_LIGHT);
        g.fill(x, y, x + 1, y + h - 1, BEVEL_LIGHT);
        g.fill(x + 1, y + h - 1, x + w, y + h, BEVEL_DARK);
        g.fill(x + w - 1, y + 1, x + w, y + h, BEVEL_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, SCREEN_BG);
    }

    /** スクリーンの中身。(sx, sy) は内側の左上、sw は内側の幅。 */
    private static void drawScreen(GuiGraphicsExtractor g, Font font, int sx, int sy, int sw, float time) {
        float pos = Math.min(ClientQTEManager.getBarPosition(), 1.0f);
        boolean great = QteRules.judge(pos) == QTEResult.GREAT_SUCCESS;
        HeartStatus heart = patientHeart();

        // 左上: 機器名と心リズム / 右上: 充電エネルギー
        Component header = Component.translatable("hud.medicalsystemcore.qte.device")
                .append("  ").append(rhythmLabel(heart));
        drawText(g, font, header, sx, sy, SCREEN_TEXT, Align.LEFT, 1.0f, false);
        int joules = Math.round(pos * MAX_JOULES);
        drawText(g, font, Component.translatable("hud.medicalsystemcore.qte.energy", joules),
                sx + sw, sy, great ? GOLD : SCREEN_TEXT, Align.RIGHT, 1.0f, false);

        drawEcg(g, sx, sy + ECG_CENTER, sw, time, heart);
        drawSegmentBar(g, sx, sy + BAR_TOP, sw, pos, time);

        // 大成功区間の下に「SHOCK」
        float greatCenter = (QteRules.GREAT_SUCCESS_START + QteRules.GREAT_SUCCESS_END) / 2;
        drawText(g, font, Component.translatable("hud.medicalsystemcore.qte.shock"),
                sx + Math.round(sw * greatCenter), sy + BAR_TOP + BAR_H + 3, GOLD, Align.CENTER, 0.75f, false);
    }

    // ------------------------------------------------------------------ 心電図

    /**
     * 患者の心臓の状態。QTE の対象エンティティ ID からプレイヤーを引き、CoreStats で同期された
     * {@link ClientMedicalDataManager} の値を使う。
     *
     * <p>対象が見つからない(範囲外でエンティティが消えた・プレイヤー以外)場合は null を返し、呼び出し側は
     * 波形を平坦にして心リズムを「--」と表示する。NORMAL も同様に平坦で「--」: 除細動の対象になるのは VF か心停止だけで、
     * 同期が間に合っていない初期値(NORMAL)を VF と偽って動く波形で見せないため。</p>
     */
    private static HeartStatus patientHeart() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity target = mc.level.getEntity(ClientQTEManager.getDisplayTargetId());
        if (!(target instanceof Player player)) return null;
        return ClientMedicalDataManager.getPlayerData(player).map(d -> d.heartStatus).orElse(null);
    }

    private static Component rhythmLabel(HeartStatus status) {
        if (status == HeartStatus.VF) return Component.translatable("hud.medicalsystemcore.qte.rhythm.vf");
        if (status == HeartStatus.CARDIAC_ARREST) return Component.translatable("hud.medicalsystemcore.qte.rhythm.asystole");
        return Component.translatable("hud.medicalsystemcore.qte.rhythm.unknown");
    }

    /** 波形の 1 列ごとのオフセット(y 方向、ピクセル)。 */
    private static float ecgOffset(HeartStatus status, float time, int column) {
        float tt = time * 0.12f + column * 0.35f;
        if (status == HeartStatus.VF) {
            return (float) (Math.sin(tt) * 4 + Math.sin(tt * 2.7 + 1) * 2.5);
        }
        // 心停止・不明: ほぼ平坦な線。ときどき 1 ピクセルだけ揺れる程度。
        return (float) (Math.sin(tt * 0.6) * 0.7 + Math.sin(tt * 1.9 + 2) * 0.3);
    }

    private static void drawEcg(GuiGraphicsExtractor g, int sx, int centerY, int sw, float time, HeartStatus status) {
        int prev = Integer.MIN_VALUE;
        for (int i = 0; i < sw; i++) {
            int y = Math.round(centerY + ecgOffset(status, time, i));
            // 前の列との間を縦につないで途切れさせない
            int from = prev == Integer.MIN_VALUE ? y : Math.min(prev, y);
            int to = prev == Integer.MIN_VALUE ? y : Math.max(prev, y);
            g.fill(sx + i, from, sx + i + 1, to + 1, SCREEN_TEXT);
            prev = y;
        }
    }

    // ------------------------------------------------------------------ セグメントバー

    private static void drawSegmentBar(GuiGraphicsExtractor g, int sx, int by, int bw, float pos, float time) {
        float segW = (bw - SEGMENT_GAP * (SEGMENTS - 1)) / (float) SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float mid = (i + 0.5f) / SEGMENTS;
            boolean lit = mid <= pos;
            int color = segmentColor(QteRules.judge(mid), lit, time);
            int x = Math.round(sx + i * (segW + SEGMENT_GAP));
            g.fill(x, by, x + (int) Math.ceil(segW), by + BAR_H, color);
        }
    }

    /** 区間(失敗=暗い灰緑、成功=緑、大成功=金)と点灯状態から色を決める。未到達の大成功区間はゆっくり明滅する。 */
    private static int segmentColor(QTEResult zone, boolean lit, float time) {
        return switch (zone) {
            case GREAT_SUCCESS -> {
                if (lit) yield 0xFFFFF2A8;
                // 約 1.25 秒周期で 2 色を切り替える
                yield Math.sin(time * 0.25) > 0 ? 0xFF8A7524 : 0xFF6B5A1C;
            }
            case SUCCESS -> lit ? 0xFF7DFFB0 : 0xFF1F4A33;
            case FAIL -> lit ? 0xFFB8C4BD : 0xFF1A2420;
        };
    }

    // ------------------------------------------------------------------ 結果表示

    private static int resultColor(QTEResult result) {
        return switch (result) {
            case GREAT_SUCCESS -> GOLD;
            case SUCCESS -> GREEN;
            case FAIL -> RED;
        };
    }

    /** 画面全体のフラッシュ。成功は白、失敗は薄い赤。どちらも経過とともに薄くする。 */
    private static void drawFlash(GuiGraphicsExtractor g, int screenW, int screenH, QTEResult result, float age) {
        boolean fail = result == QTEResult.FAIL;
        float duration = fail ? ClientQTEManager.RESULT_DISPLAY_TICKS : 6.0f;
        float peak = fail ? 0.18f : 0.35f;
        int alpha = (int) (255 * peak * Math.max(0.0f, 1.0f - age / duration));
        if (alpha <= 0) return;
        int rgb = fail ? (RED & 0xFFFFFF) : 0xFFFFFF;
        g.fill(0, 0, screenW, screenH, (alpha << 24) | rgb);
    }

    private static void drawResultText(GuiGraphicsExtractor g, Font font, int cx, int panelTop, QTEResult result) {
        int color = resultColor(result);
        String headline = result == QTEResult.FAIL
                ? "hud.medicalsystemcore.qte.result.none" : "hud.medicalsystemcore.qte.result.delivered";
        String label = switch (result) {
            case GREAT_SUCCESS -> "hud.medicalsystemcore.qte.result.great";
            case SUCCESS -> "hud.medicalsystemcore.qte.result.success";
            case FAIL -> "hud.medicalsystemcore.qte.result.fail";
        };
        drawText(g, font, Component.translatable(headline), cx, panelTop - 26, color, Align.CENTER, 1.5f, true);
        drawText(g, font, Component.translatable(label), cx, panelTop - 12, color, Align.CENTER, 1.0f, true);
    }

    // ------------------------------------------------------------------ 文字

    private enum Align { LEFT, CENTER, RIGHT }

    /** (x, y) を文字の左上(LEFT)・上中央(CENTER)・右上(RIGHT)として、scale 倍で描く。 */
    private static void drawText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, Align align, float scale, boolean shadow) {
        int width = font.width(text);
        float ox = switch (align) {
            case LEFT -> 0;
            case CENTER -> -width * scale / 2.0f;
            case RIGHT -> -width * scale;
        };
        g.pose().pushMatrix();
        g.pose().translate(x + ox, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, shadow);
        g.pose().popMatrix();
    }
}
