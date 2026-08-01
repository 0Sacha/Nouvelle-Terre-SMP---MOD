package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.HubNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Hub du Parchemin — carte électronique donnant accès aux fenêtres du mod.
 * Chaque entrée est une « puce » reliée au bus central par des pistes.
 */
@Environment(EnvType.CLIENT)
public class HubScreen extends Screen {

    // ── Couleurs (DA carte électronique) ──────────────────────────────────────
    private static final int C_BG      = 0xFF0E1512;   // substrat vert très sombre
    private static final int C_PANEL   = 0xFF14201B;
    private static final int C_CHIP    = 0xFF1B2A23;
    private static final int C_HOVER   = 0xFF24382E;
    private static final int C_TRACE   = 0xFF2F6B4F;   // pistes cuivre-vert
    private static final int C_TRACE_H = 0xFF57C08A;   // piste active
    private static final int C_BORDER  = 0xFF2A3D33;
    private static final int C_GOLD    = 0xFFE8A838;   // pastilles / accents
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PW_MAX  = 420;
    private static final int PH_MAX  = 300;
    private static final int TOP_H   = 40;
    private static final int PAD     = 14;
    private static final int COLS    = 3;
    private static final int CHIP_H  = 58;
    private static final int GAP     = 10;

    private int pw, ph, px, py;

    /** Une entrée du hub : action réseau, libellé, glyphe dessiné sur la puce. */
    private record Entry(int action, String label, String hint, String glyph) {}

    private static final List<Entry> ENTRIES = List.of(
        new Entry(HubNetworking.ACTION_HDV,        "Marché",     "Entre joueurs", "⇄"),
        new Entry(HubNetworking.ACTION_SHOP,       "Shop",       "Boutique du serveur", "🏛"),
        new Entry(HubNetworking.ACTION_BANK,       "Banque",     "Solde, virements", "◆"),
        new Entry(HubNetworking.ACTION_QUETES,     "Quêtes",     "Objectifs du jour", "⚔"),
        new Entry(HubNetworking.ACTION_PRODUCTION, "Production", "Ressources du serveur", "⛏"),
        new Entry(HubNetworking.ACTION_REGISTRE,   "Registre",   "Personnages RP", "☰"),
        new Entry(HubNetworking.ACTION_CONFLIT,    "Conflit",    "Signaler un litige", "⚠"),
        new Entry(HubNetworking.ACTION_WIKI,       "Guide",      "Aide et règles", "?")
    );

    private final List<int[]> chipBounds = new ArrayList<>();

    public HubScreen() {
        super(Text.literal("Parchemin — Nouvelle Terre"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void computePanel() {
        pw = Math.min(PW_MAX, width  - 40);
        ph = Math.min(PH_MAX, height - 40);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        computePanel();

        // Substrat + liseré
        ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x40000000);
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        drawBorder(ctx, px, py, pw, ph, C_BORDER);

        renderHeader(ctx);
        renderTraces(ctx);
        renderChips(ctx, mx, my);

        ctx.drawCenteredTextWithShadow(textRenderer,
            "§8Clic droit sur le parchemin à tout moment",
            px + pw / 2, py + ph - 16, C_DIM);

        super.render(ctx, mx, my, delta);
    }

    private void renderHeader(DrawContext ctx) {
        ctx.fill(px + 1, py + 1, px + pw - 1, py + TOP_H, C_PANEL);
        ctx.fill(px + 1, py + TOP_H - 1, px + pw - 1, py + TOP_H, C_TRACE);

        // Pastilles de connecteur, à gauche du titre
        for (int i = 0; i < 3; i++) {
            int cx = px + PAD + i * 7;
            ctx.fill(cx, py + 15, cx + 4, py + 19, C_GOLD);
        }

        ctx.drawText(textRenderer, "PARCHEMIN", px + PAD + 28, py + 10, C_GOLD, false);
        ctx.drawText(textRenderer, "Terminal Nouvelle Terre", px + PAD + 28, py + 22, C_DIM, false);
    }

    /** Bus central + dérivations vers chaque rangée de puces. */
    private void renderTraces(DrawContext ctx) {
        int busY = py + TOP_H + 6;
        ctx.fill(px + PAD, busY, px + pw - PAD, busY + 1, C_TRACE);
        for (int i = 0; i <= COLS; i++) {
            int tx = px + PAD + i * ((pw - PAD * 2) / COLS);
            tx = Math.min(tx, px + pw - PAD - 1);
            ctx.fill(tx, busY, tx + 1, busY + 6, C_TRACE);
        }
    }

    private void renderChips(DrawContext ctx, int mx, int my) {
        chipBounds.clear();
        int gridX = px + PAD;
        int gridY = py + TOP_H + 16;
        int chipW = (pw - PAD * 2 - (COLS - 1) * GAP) / COLS;

        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cx  = gridX + col * (chipW + GAP);
            int cy  = gridY + row * (CHIP_H + GAP);
            boolean hov = mx >= cx && mx < cx + chipW && my >= cy && my < cy + CHIP_H;

            ctx.fill(cx, cy, cx + chipW, cy + CHIP_H, hov ? C_HOVER : C_CHIP);
            drawBorder(ctx, cx, cy, chipW, CHIP_H, hov ? C_TRACE_H : C_BORDER);

            // Broches sur les flancs de la puce
            int pins = 3;
            for (int p = 0; p < pins; p++) {
                int pinY = cy + 12 + p * ((CHIP_H - 24) / Math.max(1, pins - 1));
                int pinC = hov ? C_TRACE_H : C_TRACE;
                ctx.fill(cx - 3, pinY, cx, pinY + 2, pinC);
                ctx.fill(cx + chipW, pinY, cx + chipW + 3, pinY + 2, pinC);
            }

            // Glyphe + libellés
            ctx.drawCenteredTextWithShadow(textRenderer, e.glyph(),
                cx + chipW / 2, cy + 10, hov ? C_GOLD : C_TRACE_H);
            ctx.drawCenteredTextWithShadow(textRenderer, e.label(),
                cx + chipW / 2, cy + 26, hov ? C_WHITE : C_MID);
            String hint = truncate(e.hint(), chipW - 8);
            ctx.drawCenteredTextWithShadow(textRenderer, hint,
                cx + chipW / 2, cy + 40, C_DIM);

            chipBounds.add(new int[]{cx, cy, chipW, CHIP_H, e.action()});
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String truncate(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && textRenderer.getWidth(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
        return sb + "…";
    }

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;
        if (x < px || x > px + pw || y < py || y > py + ph) { close(); return true; }

        for (int[] b : chipBounds) {
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeInt(b[4]);
                ClientPlayNetworking.send(HubNetworking.HUB_ACTION, buf);
                return true;
            }
        }
        return super.mouseClicked(mx0, my0, btn);
    }
}
