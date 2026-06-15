package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.client.hud.HudWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class HudEditorScreen extends Screen {

    public static final List<HudWidget> WIDGETS = new ArrayList<>();

    private enum Mode { PANEL, LAYOUT }
    private Mode   mode       = Mode.PANEL;
    private String optionsFor = null;
    private int    scrollRow  = 0;

    // Panel géométrie
    private static final int PW            = 372;
    private static final int COLS          = 2;
    private static final int GAP           = 8;
    private static final int CARD_H        = 120;
    private static final int VISIBLE_ROWS  = 2;

    // Couleurs
    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    // Drag (mode placement)
    private HudWidget dragging = null;
    private int       dragOX, dragOY;

    // Positions interactives (recalculées à chaque render)
    private int panelX, panelY, panelH;
    private int gridY, gridH;
    private record Card(HudWidget w, int x, int y, int cw, int optBtnY, int togBtnY) {}
    private final List<Card> cards = new ArrayList<>();
    private int settingsY    = -1;
    private int layoutBtnX, layoutBtnY, layoutBtnW;
    private int finishBtnX, finishBtnY, finishBtnW;

    public HudEditorScreen() { super(Text.literal("Éditeur HUD")); }

    public static void loadAll() {
        ClientConfig cfg = ClientConfig.get();
        for (HudWidget w : WIDGETS) w.loadFromConfig(cfg);
    }

    private static void saveAll() {
        ClientConfig cfg = ClientConfig.get();
        for (HudWidget w : WIDGETS) w.saveToConfig(cfg);
        cfg.save();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mode == Mode.LAYOUT) renderLayout(ctx, mc, mx, my);
        else                      renderPanel(ctx, mc, mx, my);
        super.render(ctx, mx, my, delta);
    }

    // ── Mode placement ────────────────────────────────────────────────────────

    private void renderLayout(DrawContext ctx, MinecraftClient mc, int mx, int my) {
        ctx.fill(0, 0, width, height, 0x50000000);

        for (HudWidget w : WIDGETS) {
            boolean show = w.enabled || w.isDragOnly();
            if (!show) continue;

            int wx = w.getPixelX(width, mc), wy = w.getPixelY(height, mc);
            int ww = w.getWidth(mc),         wh = w.getHeight(mc);

            if (w.isDragOnly()) {
                // Zone fantôme pour les widgets de position uniquement
                ctx.fill(wx, wy, wx + ww, wy + wh, 0x30E8A838);
                ctx.fill(wx, wy, wx + ww, wy + 1, 0x80E8A838);
                ctx.fill(wx, wy, wx + 1, wy + wh, 0x80E8A838);
                ctx.drawText(mc.textRenderer, w.label, wx + 5, wy + (wh - mc.textRenderer.fontHeight) / 2, 0xAAE8A838, false);
            } else {
                w.render(ctx, mc);
            }

            boolean hov = dragging == null && mx >= wx && mx < wx + ww && my >= wy && my < wy + wh;
            if (hov || dragging == w) {
                int bc = dragging == w ? C_GOLD : 0xAAE8A838;
                ctx.fill(wx - 1, wy - 1, wx + ww + 1, wy,           bc);
                ctx.fill(wx - 1, wy + wh, wx + ww + 1, wy + wh + 1, bc);
                ctx.fill(wx - 1, wy - 1, wx,           wy + wh + 1, bc);
                ctx.fill(wx + ww, wy - 1, wx + ww + 1, wy + wh + 1, bc);
                if (!w.isDragOnly()) {
                    int lw = mc.textRenderer.getWidth(w.label);
                    ctx.fill(wx + ww/2 - lw/2 - 4, wy - 14, wx + ww/2 + lw/2 + 4, wy - 2, 0xCC14161A);
                    ctx.drawText(mc.textRenderer, w.label, wx + ww/2 - lw/2, wy - 12, C_GOLD, false);
                }
            }
        }

        // Bouton Terminer — centré en haut
        String lbl = "Terminer";
        finishBtnW = mc.textRenderer.getWidth(lbl) + 36;
        finishBtnX = (width - finishBtnW) / 2;
        finishBtnY = 12;
        boolean fHov = mx >= finishBtnX && mx < finishBtnX + finishBtnW
            && my >= finishBtnY && my < finishBtnY + 24;
        ctx.fill(finishBtnX - 1, finishBtnY - 1, finishBtnX + finishBtnW + 1, finishBtnY + 25, C_BORDER);
        ctx.fill(finishBtnX, finishBtnY, finishBtnX + finishBtnW, finishBtnY + 24,
            fHov ? C_GOLD : C_PANEL);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, lbl,
            finishBtnX + finishBtnW / 2, finishBtnY + 8, fHov ? C_BG : C_WHITE);
    }

    // ── Mode panneau ──────────────────────────────────────────────────────────

    private void renderPanel(DrawContext ctx, MinecraftClient mc, int mx, int my) {
        ctx.fill(0, 0, width, height, 0x30000000);

        int cardW     = (PW - GAP * (COLS + 1)) / COLS;
        int totalRows = (WIDGETS.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        if (scrollRow > maxScroll) scrollRow = maxScroll;
        gridH = VISIBLE_ROWS * CARD_H + (VISIBLE_ROWS - 1) * GAP;

        HudWidget optW = findWidget(optionsFor);
        int settingsH  = (optW != null && optW.hasSettings()) ? optW.settingsHeight() + 20 : 0;
        int headerH    = 36, footerH = 44;
        panelH = headerH + GAP + gridH + (settingsH > 0 ? GAP + settingsH : 0) + GAP + footerH;
        panelX = (width  - PW) / 2;
        panelY = 10;
        cards.clear();
        settingsY = -1;

        // Fond + bordure
        ctx.fill(panelX - 1, panelY - 1, panelX + PW + 1, panelY + panelH + 1, C_BORDER);
        ctx.fill(panelX, panelY, panelX + PW, panelY + panelH, C_PANEL);

        // ── Header ────────────────────────────────────────────────────────────
        ctx.fill(panelX, panelY, panelX + PW, panelY + headerH, C_BG);
        ctx.fill(panelX, panelY + headerH - 1, panelX + PW, panelY + headerH, C_GOLD);
        int hy = panelY + (headerH - mc.textRenderer.fontHeight) / 2;
        int hx = panelX + 12;
        ctx.drawText(mc.textRenderer, "◆", hx, hy, C_GOLD, false);
        hx += mc.textRenderer.getWidth("◆") + 5;
        ctx.drawText(mc.textRenderer, "Nouvelle Terre", hx, hy, C_WHITE, false);
        hx += mc.textRenderer.getWidth("Nouvelle Terre") + 8;
        ctx.fill(hx, panelY + 8, hx + 1, panelY + headerH - 8, C_BORDER);
        hx += 9;
        ctx.drawText(mc.textRenderer, "Éditeur HUD", hx, hy, C_MID, false);
        boolean xHov = mx >= panelX + PW - 26 && mx < panelX + PW - 6
            && my >= panelY + 8 && my < panelY + headerH - 8;
        ctx.fill(panelX + PW - 26, panelY + 8, panelX + PW - 6, panelY + headerH - 8,
            xHov ? C_RED : C_HOVER);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, "×", panelX + PW - 16, hy, C_WHITE);

        // ── Grille de cards (scrollable) ──────────────────────────────────────
        gridY = panelY + headerH + GAP;
        ctx.enableScissor(panelX + 1, gridY, panelX + PW - 1, gridY + gridH);
        for (int i = 0; i < WIDGETS.size(); i++) {
            int row    = i / COLS;
            int visRow = row - scrollRow;
            if (visRow < 0 || visRow >= VISIBLE_ROWS) continue;
            HudWidget w = WIDGETS.get(i);
            int col = i % COLS;
            int cx  = panelX + GAP + col * (cardW + GAP);
            int cy  = gridY  + visRow * (CARD_H + GAP);
            renderCard(ctx, mc, w, cx, cy, cardW, mx, my);
        }
        ctx.disableScissor();

        // Indicateurs de scroll
        if (scrollRow > 0) {
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "▲",
                panelX + PW - GAP - 4, gridY + 2, C_DIM);
        }
        if (scrollRow < maxScroll) {
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "▼",
                panelX + PW - GAP - 4, gridY + gridH - 10, C_DIM);
        }

        // ── Paramètres ────────────────────────────────────────────────────────
        if (optW != null && optW.hasSettings()) {
            int sy = gridY + gridH + GAP;
            settingsY = sy;
            ctx.fill(panelX + GAP, sy, panelX + PW - GAP, sy + settingsH, C_SURFACE);
            ctx.fill(panelX + GAP, sy, panelX + PW - GAP, sy + 1, C_GOLD);
            ctx.fill(panelX + GAP, sy + 1, panelX + GAP + 2, sy + settingsH, C_GOLD);
            ctx.drawText(mc.textRenderer, "Paramètres : " + optW.label,
                panelX + GAP + 8, sy + 6, C_DIM, false);
            optW.renderSettings(ctx, mc, panelX + GAP, sy + 20, PW - GAP * 2, mx, my);
        }

        // ── Footer ────────────────────────────────────────────────────────────
        int footerY = panelY + panelH - footerH;
        ctx.fill(panelX, footerY, panelX + PW, footerY + 1, C_BORDER);
        layoutBtnX = panelX + GAP * 2;
        layoutBtnW = PW - GAP * 4;
        layoutBtnY = footerY + (footerH - 26) / 2;
        boolean lbHov = mx >= layoutBtnX && mx < layoutBtnX + layoutBtnW
            && my >= layoutBtnY && my < layoutBtnY + 26;
        ctx.fill(layoutBtnX, layoutBtnY, layoutBtnX + layoutBtnW, layoutBtnY + 26,
            lbHov ? C_HOVER : C_SURFACE);
        if (lbHov) ctx.fill(layoutBtnX, layoutBtnY, layoutBtnX + layoutBtnW, layoutBtnY + 1, C_GOLD);
        int ltw = mc.textRenderer.getWidth("Placer les widgets");
        ctx.drawText(mc.textRenderer, "Placer les widgets",
            panelX + PW / 2 - ltw / 2, layoutBtnY + 9, lbHov ? C_GOLD : C_MID, false);
    }

    private void renderCard(DrawContext ctx, MinecraftClient mc, HudWidget w,
                            int cx, int cy, int cw, int mx, int my) {
        boolean hasOpt = w.hasSettings();
        int togBtnY = cy + CARD_H - 5 - 16;
        int optBtnY = hasOpt ? togBtnY - 4 - 16 : -1;
        int sepY    = (hasOpt ? optBtnY : togBtnY) - 5;
        int nameY   = sepY - 4 - mc.textRenderer.fontHeight;
        int prvTop  = cy + 6;
        int prvBot  = nameY - 4;

        boolean hov = mx >= cx && mx < cx + cw && my >= cy && my < cy + CARD_H;
        ctx.fill(cx, cy, cx + cw, cy + CARD_H, hov ? C_HOVER : C_SURFACE);
        ctx.fill(cx, cy,             cx + cw, cy + 1,         C_BORDER);
        ctx.fill(cx, cy + CARD_H - 1, cx + cw, cy + CARD_H,  C_BORDER);
        ctx.fill(cx, cy,             cx + 1,  cy + CARD_H,    C_BORDER);
        ctx.fill(cx + cw - 1, cy, cx + cw,  cy + CARD_H,     C_BORDER);
        ctx.fill(cx + 1, cy + 1, cx + cw - 1, cy + 3, w.enabled ? C_GREEN : C_BORDER);

        // Preview (ou aperçu fantôme pour isDragOnly)
        if (prvBot > prvTop) {
            if (w.isDragOnly()) {
                // Aperçu d'une notification mock
                int ph = 22, tw = cw - 16;
                int px2 = cx + 8, py2 = prvTop + (prvBot - prvTop - ph) / 2;
                ctx.fill(px2, py2, px2 + tw, py2 + ph, 0xCC1B1D22);
                ctx.fill(px2, py2, px2 + 2, py2 + ph, C_GREEN);
                ctx.drawText(mc.textRenderer, "Virement reçu", px2 + 6, py2 + 4, C_GREEN, false);
                ctx.drawText(mc.textRenderer, "+150 ◆ de Steve", px2 + 6, py2 + 13, C_MID, false);
            } else {
                int ww = w.getWidth(mc), wh = w.getHeight(mc);
                int pvX = cx + (cw - Math.min(ww, cw - 16)) / 2;
                int pvY = prvTop + Math.max(0, (prvBot - prvTop - wh) / 2);
                ctx.enableScissor(cx + 2, prvTop, cx + cw - 2, prvBot);
                float sX = w.anchorX, sY = w.anchorY;
                w.anchorX = (float)pvX / mc.getWindow().getScaledWidth();
                w.anchorY = (float)pvY / mc.getWindow().getScaledHeight();
                w.render(ctx, mc);
                w.anchorX = sX; w.anchorY = sY;
                ctx.disableScissor();
            }
        }

        // Nom
        ctx.drawText(mc.textRenderer, w.label,
            cx + cw / 2 - mc.textRenderer.getWidth(w.label) / 2, nameY,
            w.enabled ? C_WHITE : C_MID, false);

        // Séparateur
        ctx.fill(cx + 1, sepY, cx + cw - 1, sepY + 1, C_BORDER);

        // Options
        if (hasOpt) {
            boolean open = w.id.equals(optionsFor);
            boolean oHov = mx >= cx + 1 && mx < cx + cw - 1 && my >= optBtnY && my < optBtnY + 16;
            ctx.fill(cx + 1, optBtnY, cx + cw - 1, optBtnY + 16, (oHov || open) ? C_HOVER : 0);
            int oTy = optBtnY + (16 - mc.textRenderer.fontHeight) / 2;
            ctx.drawText(mc.textRenderer, "Options", cx + 8, oTy, open ? C_GOLD : C_MID, false);
            ctx.drawText(mc.textRenderer, "⚙", cx + cw - 16, oTy, open ? C_GOLD : C_DIM, false);
        }

        // Toggle : affiche l'action (ce qui se passe si on clique)
        boolean tHov = mx >= cx + 1 && mx < cx + cw - 1 && my >= togBtnY && my < togBtnY + 16;
        String togLbl;
        int togBg, togFg;
        if (w.enabled) {
            togLbl = "Désactiver";
            togBg  = tHov ? 0xFF8C1830 : 0xFF3D0A16;
            togFg  = tHov ? C_WHITE : C_RED;
        } else {
            togLbl = "Activer";
            togBg  = tHov ? 0xFF1E9A58 : C_GREEN;
            togFg  = C_BG;
        }
        ctx.fill(cx + 1, togBtnY, cx + cw - 1, togBtnY + 16, togBg);
        int togLblX = cx + cw / 2 - mc.textRenderer.getWidth(togLbl) / 2;
        int togLblY = togBtnY + (16 - mc.textRenderer.fontHeight) / 2;
        ctx.drawText(mc.textRenderer, togLbl, togLblX, togLblY, togFg, false);

        cards.add(new Card(w, cx, cy, cw, optBtnY, togBtnY));
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int mx = (int)mx0, my = (int)my0;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mode == Mode.LAYOUT) {
            if (mx >= finishBtnX && mx < finishBtnX + finishBtnW
                    && my >= finishBtnY && my < finishBtnY + 24) {
                mode = Mode.PANEL; dragging = null; return true;
            }
            for (HudWidget w : WIDGETS) {
                boolean draggable = w.enabled || w.isDragOnly();
                if (!draggable) continue;
                int wx = w.getPixelX(width, mc), wy = w.getPixelY(height, mc);
                if (mx >= wx && mx < wx + w.getWidth(mc) && my >= wy && my < wy + w.getHeight(mc)) {
                    dragging = w; dragOX = mx - wx; dragOY = my - wy; return true;
                }
            }
            return true;
        }

        // Bouton [×]
        if (mx >= panelX + PW - 26 && mx < panelX + PW - 6
                && my >= panelY + 8 && my < panelY + 36 - 8) {
            close(); return true;
        }

        // Clic dans les paramètres
        if (settingsY >= 0 && my >= settingsY + 20) {
            HudWidget optW = findWidget(optionsFor);
            if (optW != null) {
                optW.handleSettingsClick(mx, my, panelX + GAP, settingsY + 20, PW - GAP * 2);
                return true;
            }
        }

        // Clics sur les cards
        for (Card c : cards) {
            if (mx < c.x() || mx >= c.x() + c.cw() || my < c.y() || my >= c.y() + CARD_H) continue;
            if (c.optBtnY() >= 0 && my >= c.optBtnY() && my < c.optBtnY() + 16
                    && mx >= c.x() + 1 && mx < c.x() + c.cw() - 1) {
                optionsFor = c.w().id.equals(optionsFor) ? null : c.w().id; return true;
            }
            if (my >= c.togBtnY() && my < c.togBtnY() + 16
                    && mx >= c.x() + 1 && mx < c.x() + c.cw() - 1) {
                c.w().enabled = !c.w().enabled; return true;
            }
            return true;
        }

        // Bouton Placer les widgets
        if (mx >= layoutBtnX && mx < layoutBtnX + layoutBtnW
                && my >= layoutBtnY && my < layoutBtnY + 26) {
            mode = Mode.LAYOUT; return true;
        }

        return super.mouseClicked(mx0, my0, btn);
    }

    @Override
    public boolean mouseDragged(double mx0, double my0, int btn, double dx, double dy) {
        if (dragging != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int ww = dragging.getWidth(mc), wh = dragging.getHeight(mc);
            int nx = Math.max(0, Math.min((int)mx0 - dragOX, width  - ww));
            int ny = Math.max(0, Math.min((int)my0 - dragOY, height - wh));
            if (nx < 8)           nx = 0;
            if (ny < 8)           ny = 0;
            if (nx + ww > width  - 8) nx = width  - ww;
            if (ny + wh > height - 8) ny = height - wh;
            dragging.anchorX = (float)nx / width;
            dragging.anchorY = (float)ny / height;
            return true;
        }
        return super.mouseDragged(mx0, my0, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = null;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mode == Mode.PANEL) {
            int totalRows = (WIDGETS.size() + COLS - 1) / COLS;
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
            scrollRow = Math.max(0, Math.min(scrollRow - (int)Math.signum(amount), maxScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && mode == Mode.LAYOUT) { // ESC → retour panneau
            mode = Mode.PANEL; dragging = null; return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() { saveAll(); }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    private HudWidget findWidget(String id) {
        if (id == null) return null;
        return WIDGETS.stream().filter(w -> w.id.equals(id)).findFirst().orElse(null);
    }
}
