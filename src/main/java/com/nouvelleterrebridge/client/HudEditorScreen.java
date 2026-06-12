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

    // Panel geometry
    private static final int PX = 10, PY = 10, PW = 228, RH = 22;

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    private HudWidget dragging    = null;
    private int       dragOX, dragOY;
    private String    settingsFor = null;
    private int       footerY     = -1;

    private record Row(HudWidget w, int y, boolean isSettings) {}
    private final List<Row> rows = new ArrayList<>();

    public HudEditorScreen() {
        super(Text.literal("Éditeur HUD"));
    }

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
        ctx.fill(0, 0, width, height, 0x50000000);

        MinecraftClient mc = MinecraftClient.getInstance();

        for (HudWidget w : WIDGETS) {
            if (!w.enabled) continue;
            w.render(ctx, mc);
            int wx = w.getPixelX(width, mc), wy = w.getPixelY(height, mc);
            int ww = w.getWidth(mc),         wh = w.getHeight(mc);
            boolean hov = dragging == null && mx >= wx && mx < wx + ww && my >= wy && my < wy + wh;
            if (hov || dragging == w) {
                int bc = (dragging == w) ? C_GOLD : 0xAAE8A838;
                ctx.fill(wx - 1, wy - 1, wx + ww + 1, wy,           bc);
                ctx.fill(wx - 1, wy + wh, wx + ww + 1, wy + wh + 1, bc);
                ctx.fill(wx - 1, wy - 1, wx,           wy + wh + 1, bc);
                ctx.fill(wx + ww, wy - 1, wx + ww + 1, wy + wh + 1, bc);
            }
            // Label au-dessus pendant le drag
            if (dragging == w) {
                String lbl = w.label;
                int lw = textRenderer.getWidth(lbl);
                ctx.fill(wx + ww / 2 - lw / 2 - 4, wy - 14, wx + ww / 2 + lw / 2 + 4, wy - 2, 0xCC14161A);
                ctx.drawText(textRenderer, lbl, wx + ww / 2 - lw / 2, wy - 12, C_GOLD, false);
            }
        }

        renderPanel(ctx, mc, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void renderPanel(DrawContext ctx, MinecraftClient mc, int mx, int my) {
        rows.clear();
        int py = PY;

        // ── Header ────────────────────────────────────────────────────────────
        ctx.fill(PX, py, PX + PW, py + 24, C_BG);
        ctx.fill(PX, py + 23, PX + PW, py + 24, C_GOLD);
        ctx.drawText(textRenderer, "ÉDITEUR HUD", PX + 10, py + 8, C_GOLD, false);
        boolean xHov = mx >= PX + PW - 20 && mx < PX + PW - 4 && my >= py + 4 && my < py + 20;
        ctx.fill(PX + PW - 20, py + 4, PX + PW - 4, py + 20, xHov ? 0xFFBF2040 : C_HOVER);
        ctx.drawCenteredTextWithShadow(textRenderer, "×", PX + PW - 12, py + 8, C_WHITE);
        py += 24;

        // ── Lignes widgets ────────────────────────────────────────────────────
        for (HudWidget w : WIDGETS) {
            rows.add(new Row(w, py, false));
            boolean rowHov = dragging == null && mx >= PX && mx < PX + PW && my >= py && my < py + RH;
            ctx.fill(PX, py, PX + PW, py + RH, rowHov ? C_HOVER : C_PANEL);
            ctx.fill(PX, py + RH - 1, PX + PW, py + RH, C_BORDER);

            // Toggle
            boolean togHov = mx >= PX + 6 && mx < PX + 20 && my >= py + 5 && my < py + RH - 5;
            ctx.fill(PX + 6, py + 5, PX + 20, py + RH - 5, C_BORDER);
            ctx.fill(PX + 7, py + 6, PX + 19, py + RH - 6,
                w.enabled ? C_GREEN : (togHov ? C_HOVER : C_BG));

            // Label
            ctx.drawText(textRenderer, w.label, PX + 26, py + (RH - textRenderer.fontHeight) / 2,
                w.enabled ? C_WHITE : C_DIM, false);

            // Icône ⚙ si paramètres disponibles
            if (w.hasSettings()) {
                boolean open  = w.id.equals(settingsFor);
                boolean gHov  = mx >= PX + PW - 22 && mx < PX + PW - 6 && my >= py + 2 && my < py + RH - 2;
                ctx.fill(PX + PW - 22, py + 2, PX + PW - 6, py + RH - 2, (gHov || open) ? C_HOVER : 0);
                ctx.drawText(textRenderer, "⚙", PX + PW - 17, py + (RH - textRenderer.fontHeight) / 2,
                    open ? C_GOLD : C_DIM, false);
            }
            py += RH;

            // Sous-panneau paramètres
            if (w.id.equals(settingsFor) && w.hasSettings()) {
                rows.add(new Row(w, py, true));
                int sh = w.settingsHeight();
                ctx.fill(PX, py, PX + PW, py + sh, C_SURFACE);
                ctx.fill(PX, py + sh - 1, PX + PW, py + sh, C_BORDER);
                ctx.fill(PX, py, PX + 2, py + sh, C_GOLD);
                w.renderSettings(ctx, mc, PX, py, PW, mx, my);
                py += sh;
            }
        }

        // ── Footer : réinitialiser ────────────────────────────────────────────
        footerY = py;
        ctx.fill(PX, py,     PX + PW, py + 1,  C_BORDER);
        ctx.fill(PX, py + 1, PX + PW, py + 29, C_PANEL);
        boolean rHov = mx >= PX + 16 && mx < PX + PW - 16 && my >= py + 6 && my < py + 23;
        ctx.fill(PX + 16, py + 6, PX + PW - 16, py + 23, rHov ? C_HOVER : C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "Réinitialiser", PX + PW / 2, py + 11,
            rHov ? C_WHITE : C_MID);

        // Bordure externe du panneau
        int bot = py + 29;
        ctx.fill(PX - 1, PY - 1,  PX + PW + 1, PY,      C_BORDER);
        ctx.fill(PX - 1, bot,      PX + PW + 1, bot + 1, C_BORDER);
        ctx.fill(PX - 1, PY,       PX,           bot,     C_BORDER);
        ctx.fill(PX + PW, PY,      PX + PW + 1,  bot,     C_BORDER);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int mx = (int)mx0, my = (int)my0;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Clics sur le panneau
        if (mx >= PX && mx < PX + PW) {
            // Bouton fermer
            if (my >= PY + 4 && my < PY + 20 && mx >= PX + PW - 20) {
                close(); return true;
            }
            for (Row r : rows) {
                HudWidget w = r.w(); int ry = r.y();
                if (r.isSettings()) {
                    if (my >= ry && my < ry + w.settingsHeight()) {
                        w.handleSettingsClick(mx, my, PX, ry, PW); return true;
                    }
                } else {
                    if (my >= ry && my < ry + RH) {
                        if (mx >= PX + 6 && mx < PX + 20) {             // toggle
                            w.enabled = !w.enabled; return true;
                        }
                        if (w.hasSettings() && mx >= PX + PW - 22) {    // ⚙
                            settingsFor = w.id.equals(settingsFor) ? null : w.id; return true;
                        }
                        return true;
                    }
                }
            }
            // Bouton réinitialiser
            if (footerY >= 0 && my >= footerY + 6 && my < footerY + 23
                    && mx >= PX + 16 && mx < PX + PW - 16) {
                for (HudWidget w : WIDGETS) w.resetToDefault();
                return true;
            }
            return true;
        }

        // Début de drag sur un widget
        for (HudWidget w : WIDGETS) {
            if (!w.enabled) continue;
            int wx = w.getPixelX(width, mc), wy = w.getPixelY(height, mc);
            if (mx >= wx && mx < wx + w.getWidth(mc) && my >= wy && my < wy + w.getHeight(mc)) {
                dragging = w; dragOX = mx - wx; dragOY = my - wy; return true;
            }
        }
        return super.mouseClicked(mx0, my0, btn);
    }

    @Override
    public boolean mouseDragged(double mx0, double my0, int btn, double dx, double dy) {
        if (dragging != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int ww = dragging.getWidth(mc), wh = dragging.getHeight(mc);
            int nx = (int)mx0 - dragOX, ny = (int)my0 - dragOY;
            // Snap aux bords
            if (nx < 8)          nx = 0;
            if (ny < 8)          ny = 0;
            if (nx + ww > width  - 8) nx = width  - ww;
            if (ny + wh > height - 8) ny = height - wh;
            nx = Math.max(0, Math.min(nx, width  - ww));
            ny = Math.max(0, Math.min(ny, height - wh));
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
    public void removed() { saveAll(); }

    @Override public boolean shouldPause()       { return false; }
    @Override public boolean shouldCloseOnEsc()  { return true; }
}
