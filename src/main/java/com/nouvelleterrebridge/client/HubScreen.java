package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.HubNetworking;
import com.nouvelleterrebridge.network.NtNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hub du Parchemin — porte d'entrée vers toutes les fenêtres du mod.
 *
 * Même habillage que le reste (fond sombre, barre de titre, accent or) : le hub
 * avait sa propre direction artistique « carte électronique » verte, et c'était
 * le seul écran du mod à ne ressembler à aucun autre.
 */
public class HubScreen extends Screen {

    // ── Couleurs communes aux écrans du mod ───────────────────────────────────
    private static final int C_BG     = 0xFF14161A;
    private static final int C_PANEL  = 0xFF1B1D22;
    private static final int C_HOVER  = 0xFF282B34;
    private static final int C_STRIP  = 0xFF1E2128;
    private static final int C_BORDER = 0xFF2A2D38;
    private static final int C_GOLD   = 0xFFE8A838;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_MID    = 0xFF9096A3;
    private static final int C_DIM    = 0xFF565C6A;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int WIN_MAX_W = 580;
    private static final int TOP_H     = 44;
    private static final int PAD       = 12;
    private static final int GAP       = 8;
    private static final int CARTE_H   = 76;
    private static final int PIED_H    = 26;
    /** Largeur en dessous de laquelle une carte devient illisible : on retire une colonne. */
    private static final int CARTE_MIN_W = 92;

    private int winX, winY, winW, winH, colonnes;

    /** Une entrée du hub : action réseau, libellé, description, glyphe. */
    private record Entry(int action, String label, String hint, String glyph) {}

    private static final List<Entry> ENTRIES = List.of(
        new Entry(HubNetworking.ACTION_HDV,        "Marché",     "Entre joueurs",   "⇄"),
        new Entry(HubNetworking.ACTION_SHOP,       "Shop",       "Boutique serveur", "🏛"),
        new Entry(HubNetworking.ACTION_MARCHE,     "LeBonCube",  "Services",        "📰"),
        new Entry(HubNetworking.ACTION_LOCATION,   "Location",   "Louer un objet",  "⌛"),
        new Entry(HubNetworking.ACTION_BANK,       "Banque",     "Solde, virements", "◆"),
        new Entry(HubNetworking.ACTION_QUETES,     "Quêtes",     "Objectifs du jour", "⚔"),
        new Entry(HubNetworking.ACTION_PRODUCTION, "Production", "Ressources",      "⛏"),
        new Entry(HubNetworking.ACTION_REGISTRE,   "Registre",   "Personnages RP",  "☰"),
        new Entry(HubNetworking.ACTION_CONFLIT,    "Conflit",    "Signaler un litige", "⚠"),
        new Entry(HubNetworking.ACTION_WIKI,       "Guide",      "Aide et règles",  "?")
    );

    /** Cartes dessinées à la dernière image : {x, y, largeur, action}, relues par mouseClicked. */
    private final List<int[]> cartes = new ArrayList<>();

    public HubScreen() {
        super(Component.literal("Parchemin — Nouvelle Terre"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void computeWin() {
        winW = Math.min(WIN_MAX_W, width - 40);
        // Cinq colonnes si la fenêtre le permet, sinon moins : jamais de carte écrasée
        colonnes = 5;
        while (colonnes > 2 && largeurCarte() < CARTE_MIN_W) colonnes--;
        int rangs = (ENTRIES.size() + colonnes - 1) / colonnes;
        winH = TOP_H + PAD + rangs * (CARTE_H + GAP) - GAP + PAD + PIED_H;
        winX = (width - winW) / 2;
        winY = Math.max(4, (height - winH) / 2);
    }

    private int largeurCarte() {
        return (winW - PAD * 2 - (colonnes - 1) * GAP) / colonnes;
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        // No-op — cet écran dessine son propre fond (voir « Flou par-dessus l'UI » dans CLAUDE.md)
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        computeWin();

        ctx.fill(winX + 3, winY + 3, winX + winW + 3, winY + winH + 3, 0x40000000);
        ctx.fill(winX, winY, winX + winW, winY + winH, 0xCC14161A);

        renderTopBar(ctx);
        renderCartes(ctx, mx, my);

        ctx.fill(winX, winY + winH - PIED_H, winX + winW, winY + winH - PIED_H + 1, C_BORDER);
        ctx.drawCenteredString(font, "Clic droit sur le Parchemin pour revenir ici",
            winX + winW / 2, winY + winH - PIED_H + 9, C_DIM);

        super.render(ctx, mx, my, delta);
    }

    private void renderTopBar(GuiGraphics ctx) {
        ctx.fill(winX, winY, winX + winW, winY + TOP_H, 0xE01B1D22);
        ctx.fill(winX, winY + TOP_H - 1, winX + winW, winY + TOP_H, C_BORDER);

        int ty = winY + (TOP_H - font.lineHeight) / 2;
        int tx = winX + PAD;
        ctx.drawString(font, "PARCHEMIN", tx, ty, C_GOLD, false);
        tx += font.width("PARCHEMIN") + 8;
        ctx.fill(tx, winY + (TOP_H - 16) / 2, tx + 1, winY + (TOP_H + 16) / 2, C_BORDER);
        ctx.drawString(font, "Nouvelle Terre", tx + 9, ty, C_MID, false);

        // Solde, comme sur les autres écrans
        String bal = BalanceHudOverlay.cachedBalance < 0 ? "? ◆" : BalanceHudOverlay.cachedBalance + " ◆";
        int bw = font.width(bal) + 18;
        int bx = winX + winW - bw - PAD;
        int by = winY + (TOP_H - 20) / 2;
        ctx.fill(bx, by, bx + bw, by + 20, C_STRIP);
        ctx.fill(bx, by, bx + 2, by + 20, C_GOLD);
        ctx.drawString(font, bal, bx + 10, by + 6, C_GOLD, false);
    }

    private void renderCartes(GuiGraphics ctx, int mx, int my) {
        cartes.clear();
        int cw = largeurCarte();
        int gx = winX + PAD;
        int gy = winY + TOP_H + PAD;

        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            int cx = gx + (i % colonnes) * (cw + GAP);
            int cy = gy + (i / colonnes) * (CARTE_H + GAP);
            boolean hov = mx >= cx && mx < cx + cw && my >= cy && my < cy + CARTE_H;

            ctx.fill(cx, cy, cx + cw, cy + CARTE_H, hov ? C_HOVER : C_PANEL);
            cadre(ctx, cx, cy, cw, CARTE_H, hov ? C_GOLD : C_BORDER);

            // Pastille du glyphe
            int ps = 26, pxg = cx + (cw - ps) / 2, pyg = cy + 9;
            ctx.fill(pxg, pyg, pxg + ps, pyg + ps, C_BG);
            ctx.fill(pxg, pyg + ps - 2, pxg + ps, pyg + ps, hov ? C_GOLD : C_BORDER);
            ctx.drawCenteredString(font, e.glyph(), cx + cw / 2, pyg + 8, hov ? C_GOLD : C_MID);

            ctx.drawCenteredString(font, e.label(), cx + cw / 2, cy + 43, hov ? C_WHITE : C_MID);
            ctx.drawCenteredString(font, truncate(e.hint(), cw - 8), cx + cw / 2, cy + 57, C_DIM);

            cartes.add(new int[]{cx, cy, cw, e.action()});
        }
    }

    private void cadre(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String truncate(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && font.width(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
        return sb + "…";
    }

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;
        if (x < winX || x > winX + winW || y < winY || y > winY + winH) { onClose(); return true; }

        for (int[] c : cartes) {
            if (x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + CARTE_H) {
                FriendlyByteBuf buf = NtNet.buffer();
                buf.writeInt(c[3]);
                NtNet.versServeur(HubNetworking.HUB_ACTION, buf);
                return true;
            }
        }
        return super.mouseClicked(mx0, my0, btn);
    }
}
