package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.client.hud.HudWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Éditeur du HUD (touche H).
 *
 * <p>S'ouvre directement sur le jeu : chaque widget actif se <b>glisse</b> là où
 * on le veut, s'aligne tout seul sur les bords, le centre et les autres widgets
 * (guides dorés), et se <b>redimensionne</b> à la molette ou par sa poignée.
 * Un clic droit ouvre ses options.
 *
 * <p>Le bouton central « MODS » ouvre la grille de tous les widgets — aperçu,
 * interrupteur, options, recherche.
 *
 * <p>Les zones cliquables des panneaux sont enregistrées <b>au rendu</b>
 * ({@link #zones}) et relues au clic : recalculer les positions côté clic les
 * désynchroniserait du défilement et de la recherche.
 */
public class HudEditorScreen extends Screen {

    public static final List<HudWidget> WIDGETS = new ArrayList<>();

    public static void loadAll() {
        ClientConfig cfg = ClientConfig.get();
        for (HudWidget w : WIDGETS) w.loadFromConfig(cfg);
    }

    private static void saveAll() {
        ClientConfig cfg = ClientConfig.get();
        for (HudWidget w : WIDGETS) w.saveToConfig(cfg);
        cfg.save();
    }

    public static HudWidget widget(String id) {
        for (HudWidget w : WIDGETS) if (w.id.equals(id)) return w;
        return null;
    }

    // ── Couleurs ──────────────────────────────────────────────────────────────
    private static final int C_FOND    = 0xF2101216;
    private static final int C_ENTETE  = 0xFF14171C;
    private static final int C_CARTE   = 0xFF1A1D23;
    private static final int C_CARTE_H = 0xFF242830;
    private static final int C_CREUX   = 0xFF0C0E11;
    private static final int C_BORD    = 0xFF2C313B;
    private static final int C_OFF     = 0xFF3A3F4A;
    private static final int C_ACCENT  = HudWidget.C_ACCENT;
    private static final int C_VERT    = 0xFF2EAD6B;
    private static final int C_BLANC   = 0xFFFFFFFF;
    private static final int C_GRIS    = 0xFF9096A3;
    private static final int C_SOMBRE  = 0xFF565C6A;

    // ── Réglages ──────────────────────────────────────────────────────────────
    private static final int MARGE     = 2;    // écart gardé avec les bords et entre widgets collés
    private static final int AIMANT    = 5;    // distance d'accroche, en pixels
    private static final int POIGNEE   = 5;
    private static final int COLONNES  = 3;
    private static final int CARTE_H   = 86;
    private static final int ECART     = 8;
    private static final float PAS_TAILLE = 0.05f;

    private enum Mode { PLACEMENT, MODS, OPTIONS }

    private Mode mode = Mode.PLACEMENT;
    /** Où ramène la flèche retour des options : la grille, ou le placement (clic droit). */
    private Mode retour = Mode.MODS;
    private HudWidget cible;

    // Glisser / redimensionner
    private HudWidget saisi;
    private boolean redimensionne;
    private int decalX, decalY;
    private int guideX = -1, guideY = -1;

    // Grille
    private String recherche = "";
    private int scroll = 0, maxScroll = 0;

    private record Zone(int x, int y, int w, int h, Runnable action) {}
    private final List<Zone> zones = new ArrayList<>();
    /** Piste du curseur de taille : {x, y, largeur}, ou null s'il n'est pas affiché. */
    private int[] curseur;
    private boolean glisseCurseur;

    public HudEditorScreen() {
        super(Component.literal("Éditeur HUD"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() { saveAll(); }

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        // No-op — l'éditeur se dessine par-dessus le jeu, sans flou ni fond de menu
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        zones.clear();
        curseur = null;

        HudWidget.enEdition = true;
        try {
            if (mode == Mode.PLACEMENT) {
                ctx.fill(0, 0, width, height, 0x48000000);
                renderPlacement(ctx, mc, mx, my);
            } else {
                // Les widgets restent visibles derrière, pour voir l'effet des réglages
                for (HudWidget w : WIDGETS) if (w.enabled) w.render(ctx, mc);
                // Panneau au-dessus de tout, icônes d'objets comprises
                ctx.pose().pushPose();
                ctx.pose().translate(0, 0, 300);
                ctx.fill(0, 0, width, height, 0xA0000000);
                if (mode == Mode.MODS) renderMods(ctx, mc, mx, my);
                else                   renderOptions(ctx, mc, mx, my);
                ctx.pose().popPose();
            }
        } finally {
            HudWidget.enEdition = false;
        }
        super.render(ctx, mx, my, delta);
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    private HudWidget sous(Minecraft mc, int mx, int my) {
        // Le dernier dessiné est au-dessus : c'est lui qu'on attrape
        for (int i = WIDGETS.size() - 1; i >= 0; i--) {
            HudWidget w = WIDGETS.get(i);
            if (!w.enabled) continue;
            int x = w.getPixelX(width, mc), y = w.getPixelY(height, mc);
            if (mx >= x && mx < x + w.getWidth(mc) && my >= y && my < y + w.getHeight(mc)) return w;
        }
        return null;
    }

    private void renderPlacement(GuiGraphics ctx, Minecraft mc, int mx, int my) {
        HudWidget survol = saisi != null ? saisi : sous(mc, mx, my);

        // Guides d'alignement, sous les widgets
        if (guideX >= 0) ctx.fill(guideX, 0, guideX + 1, height, 0xC0E8A838);
        if (guideY >= 0) ctx.fill(0, guideY, width, guideY + 1, 0xC0E8A838);

        for (HudWidget w : WIDGETS) {
            if (!w.enabled) continue;
            int x = w.getPixelX(width, mc), y = w.getPixelY(height, mc);
            int ww = w.getWidth(mc), hh = w.getHeight(mc);
            w.renderAt(ctx, mc, x, y, w.taille);

            boolean actif = w == survol;
            cadre(ctx, x - 1, y - 1, ww + 2, hh + 2, actif ? C_ACCENT : 0x50FFFFFF);
            if (w.redimensionnable())
                ctx.fill(x + ww - POIGNEE + 1, y + hh - POIGNEE + 1, x + ww + 1, y + hh + 1,
                    actif ? C_ACCENT : 0x90FFFFFF);
        }

        // Bouton central + mode d'emploi
        int bw = 96, bh = 24;
        int bx = (width - bw) / 2, by = (height - bh) / 2;
        boolean bhov = saisi == null && dans(mx, my, bx, by, bw, bh);
        HudWidget.fondArrondi(ctx, bx, by, bw, bh, bhov ? C_ACCENT : 0xE0101216);
        if (!bhov) cadre(ctx, bx, by, bw, bh, C_ACCENT);
        ctx.drawCenteredString(font, "§lMODS", width / 2, by + 8, bhov ? 0xFF101216 : C_ACCENT);
        ctx.drawCenteredString(font, "Glisser : déplacer  ·  Molette : taille  ·  Clic droit : options",
            width / 2, by + bh + 8, C_GRIS);
        ctx.drawCenteredString(font, "Maj : désactiver l'alignement  ·  Échap : fermer",
            width / 2, by + bh + 20, C_SOMBRE);

        // Étiquette du widget survolé, par-dessus tout
        if (survol != null) {
            int x = survol.getPixelX(width, mc), y = survol.getPixelY(height, mc);
            String nom = survol.label
                + (survol.redimensionnable() ? "  §7" + Math.round(survol.taille * 100) + " %" : "");
            int tw = font.width(nom) + 8;
            int tx = Math.max(0, Math.min(x, width - tw));
            int ty = y >= 15 ? y - 14 : y + survol.getHeight(mc) + 3;
            ctx.pose().pushPose();
            ctx.pose().translate(0, 0, 350);
            HudWidget.fondArrondi(ctx, tx, ty, tw, 12, 0xF0101216);
            ctx.drawString(font, nom, tx + 4, ty + 2, C_ACCENT, false);
            ctx.pose().popPose();
        }
    }

    /**
     * Position alignée la plus proche de {@code pos} sur un axe, et le guide à
     * tracer. {@code autres} liste les {début, taille} des autres widgets.
     * @return {position, guide} — guide à -1 si rien n'accroche
     */
    private static int[] aimanter(int pos, int taille, int total, List<int[]> autres) {
        int meilleure = pos, guide = -1, ecartMin = AIMANT + 1;
        List<int[]> candidats = new ArrayList<>();
        candidats.add(new int[]{MARGE, 0});                                   // bord proche
        candidats.add(new int[]{total - taille - MARGE, total - 1});         // bord opposé
        candidats.add(new int[]{(total - taille) / 2, total / 2});           // centre de l'écran
        for (int[] a : autres) {
            candidats.add(new int[]{a[0], a[0]});                             // mêmes débuts
            candidats.add(new int[]{a[0] + a[1] - taille, a[0] + a[1] - 1}); // mêmes fins
            candidats.add(new int[]{a[0] + a[1] + MARGE, a[0] + a[1]});      // collé après
            candidats.add(new int[]{a[0] - taille - MARGE, a[0] - 1});       // collé avant
        }
        for (int[] c : candidats) {
            int ecart = Math.abs(c[0] - pos);
            if (ecart < ecartMin) { ecartMin = ecart; meilleure = c[0]; guide = c[1]; }
        }
        return new int[]{meilleure, guide};
    }

    private void deplacer(Minecraft mc, int mx, int my) {
        int ww = saisi.getWidth(mc), hh = saisi.getHeight(mc);
        int x = mx - decalX, y = my - decalY;
        guideX = guideY = -1;

        if (!hasShiftDown()) {
            List<int[]> enX = new ArrayList<>(), enY = new ArrayList<>();
            for (HudWidget w : WIDGETS) {
                if (w == saisi || !w.enabled) continue;
                enX.add(new int[]{w.getPixelX(width, mc), w.getWidth(mc)});
                enY.add(new int[]{w.getPixelY(height, mc), w.getHeight(mc)});
            }
            int[] ax = aimanter(x, ww, width, enX);
            int[] ay = aimanter(y, hh, height, enY);
            x = ax[0]; guideX = ax[1];
            y = ay[0]; guideY = ay[1];
        }

        x = Math.max(0, Math.min(x, width - ww));
        y = Math.max(0, Math.min(y, height - hh));
        // +0,5 : la position est relue par troncature, sans ça elle dérive d'un pixel
        saisi.anchorX = (x + 0.5f) / width;
        saisi.anchorY = (y + 0.5f) / height;
    }

    private static float borner(float taille) {
        float t = Math.round(taille / PAS_TAILLE) * PAS_TAILLE;
        return Math.max(HudWidget.TAILLE_MIN, Math.min(t, HudWidget.TAILLE_MAX));
    }

    // ── Grille « MODS » ───────────────────────────────────────────────────────

    private int pw, ph, px, py;

    private void panneau(GuiGraphics ctx) {
        pw = Math.min(456, width - 40);
        ph = Math.min(308, height - 40);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        HudWidget.fondArrondi(ctx, px, py, pw, ph, C_FOND);
        HudWidget.fondArrondi(ctx, px, py, pw, 34, C_ENTETE);
        ctx.fill(px, py + 34, px + pw, py + 35, C_BORD);
    }

    private List<HudWidget> filtres() {
        String q = recherche.trim().toLowerCase();
        List<HudWidget> out = new ArrayList<>();
        for (HudWidget w : WIDGETS) if (q.isEmpty() || w.label.toLowerCase().contains(q)) out.add(w);
        return out;
    }

    private void renderMods(GuiGraphics ctx, Minecraft mc, int mx, int my) {
        panneau(ctx);

        long actifs = WIDGETS.stream().filter(w -> w.enabled).count();
        ctx.drawString(font, "§lMODS", px + 14, py + 13, C_ACCENT, false);
        ctx.drawString(font, actifs + " / " + WIDGETS.size() + " actifs",
            px + 14 + font.width("§lMODS") + 10, py + 13, C_SOMBRE, false);

        // Recherche : toujours active, on tape directement
        int sw = 150, sx = px + pw - sw - 12, sy = py + 8;
        HudWidget.fondArrondi(ctx, sx, sy, sw, 18, C_CREUX);
        boolean clignote = (System.currentTimeMillis() / 500) % 2 == 0;
        if (recherche.isEmpty()) ctx.drawString(font, "Rechercher...", sx + 6, sy + 5, C_SOMBRE, false);
        ctx.drawString(font, recherche + (clignote ? "_" : ""), sx + 6, sy + 5, C_BLANC, false);

        // Grille
        int gx = px + 12, gy = py + 44, gw = pw - 24, gh = ph - 44 - 38;
        int cw = (gw - (COLONNES - 1) * ECART) / COLONNES;
        List<HudWidget> liste = filtres();
        int rangs = (liste.size() + COLONNES - 1) / COLONNES;
        maxScroll = Math.max(0, rangs * (CARTE_H + ECART) - ECART - gh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (liste.isEmpty())
            ctx.drawCenteredString(font, "Aucun widget ne correspond.", px + pw / 2, gy + gh / 2, C_SOMBRE);

        ctx.enableScissor(gx, gy, gx + gw, gy + gh);
        boolean dansGrille = dans(mx, my, gx, gy, gw, gh);
        for (int i = 0; i < liste.size(); i++) {
            HudWidget w = liste.get(i);
            int cx = gx + (i % COLONNES) * (cw + ECART);
            int cy = gy + (i / COLONNES) * (CARTE_H + ECART) - scroll;
            if (cy + CARTE_H < gy || cy > gy + gh) continue;

            boolean hov = dansGrille && dans(mx, my, cx, cy, cw, CARTE_H);
            HudWidget.fondArrondi(ctx, cx, cy, cw, CARTE_H, hov ? C_CARTE_H : C_CARTE);

            // Aperçu
            HudWidget.fondArrondi(ctx, cx + 4, cy + 4, cw - 8, 44, C_CREUX);
            w.renderApercu(ctx, mc, cx + cw / 2, cy + 26, cw - 20, 36);
            ctx.drawString(font, w.label, cx + 7, cy + 54, w.enabled ? C_BLANC : C_GRIS, false);

            // Bandeau : interrupteur à gauche, options à droite
            int by = cy + CARTE_H - 17, ow = 20;
            boolean hovI = dansGrille && dans(mx, my, cx, by, cw - ow, 17);
            boolean hovO = dansGrille && dans(mx, my, cx + cw - ow, by, ow, 17);
            ctx.fill(cx + 1, by, cx + cw - ow, by + 16, w.enabled ? (hovI ? 0xFF38C47C : C_VERT) : (hovI ? 0xFF4A505D : C_OFF));
            ctx.drawCenteredString(font, w.enabled ? "ACTIVÉ" : "DÉSACTIVÉ",
                cx + (cw - ow) / 2, by + 4, w.enabled ? C_BLANC : C_GRIS);
            ctx.fill(cx + cw - ow, by, cx + cw - 1, by + 16, hovO ? C_ACCENT : C_CREUX);
            ctx.drawCenteredString(font, "⚙", cx + cw - ow / 2, by + 4, hovO ? C_CREUX : C_GRIS);

            // Le haut de la carte ouvre aussi les options
            zone(cx, cy, cw, CARTE_H - 17, gy, gy + gh, () -> ouvrirOptions(w, Mode.MODS));
            zone(cx, by, cw - ow, 17, gy, gy + gh, () -> w.enabled = !w.enabled);
            zone(cx + cw - ow, by, ow, 17, gy, gy + gh, () -> ouvrirOptions(w, Mode.MODS));
        }
        ctx.disableScissor();

        // Défilement
        if (maxScroll > 0) {
            int total = maxScroll + gh;
            int pouce = Math.max(16, gh * gh / total);
            int pos = gy + (gh - pouce) * scroll / maxScroll;
            ctx.fill(px + pw - 7, gy, px + pw - 4, gy + gh, C_CREUX);
            ctx.fill(px + pw - 7, pos, px + pw - 4, pos + pouce, C_ACCENT);
        }

        bouton(ctx, mx, my, px + (pw - 150) / 2, py + ph - 28, 150, "Placer les widgets", C_ACCENT,
            () -> mode = Mode.PLACEMENT);
    }

    private void ouvrirOptions(HudWidget w, Mode depuis) {
        cible  = w;
        retour = depuis;
        mode   = Mode.OPTIONS;
    }

    // ── Options d'un widget ───────────────────────────────────────────────────

    private void renderOptions(GuiGraphics ctx, Minecraft mc, int mx, int my) {
        panneau(ctx);

        boolean hovR = dans(mx, my, px + 8, py + 8, 20, 18);
        HudWidget.fondArrondi(ctx, px + 8, py + 8, 20, 18, hovR ? C_ACCENT : C_CARTE);
        ctx.drawCenteredString(font, "←", px + 18, py + 13, hovR ? C_CREUX : C_GRIS);
        zones.add(new Zone(px + 8, py + 8, 20, 18, () -> mode = retour));
        ctx.drawString(font, "§l" + cible.label, px + 36, py + 13, C_BLANC, false);

        // Aperçu à gauche
        int ax = px + 12, ay = py + 46, aw = 150, ah = ph - 46 - 12;
        HudWidget.fondArrondi(ctx, ax, ay, aw, ah, C_CREUX);
        cible.renderApercu(ctx, mc, ax + aw / 2, ay + ah / 2, aw - 20, ah - 20);

        // Réglages à droite
        int x0 = px + 174, w0 = pw - 174 - 12;
        int y = py + 46;
        y = interrupteur(ctx, mx, my, x0, y, w0, "Activé", cible.enabled, () -> cible.enabled = !cible.enabled);

        if (!cible.isDragOnly()) {
            y = interrupteur(ctx, mx, my, x0, y, w0, "Fond", cible.fond, () -> cible.fond = !cible.fond);
            y = interrupteur(ctx, mx, my, x0, y, w0, "Ombre du texte", cible.ombre, () -> cible.ombre = !cible.ombre);

            // Taille : curseur de 50 à 200 %
            HudWidget.fondArrondi(ctx, x0, y, w0, 20, C_CARTE);
            ctx.drawString(font, "Taille", x0 + 8, y + 6, C_BLANC, false);
            String pct = Math.round(cible.taille * 100) + " %";
            ctx.drawString(font, pct, x0 + w0 - 8 - font.width(pct), y + 6, C_ACCENT, false);
            int tx = x0 + 60, tw = w0 - 60 - 44;
            float t = (cible.taille - HudWidget.TAILLE_MIN) / (HudWidget.TAILLE_MAX - HudWidget.TAILLE_MIN);
            ctx.fill(tx, y + 9, tx + tw, y + 11, C_OFF);
            ctx.fill(tx, y + 9, tx + (int) (tw * t), y + 11, C_ACCENT);
            int kx = tx + (int) (tw * t);
            ctx.fill(kx - 2, y + 5, kx + 2, y + 15, C_BLANC);
            curseur = new int[]{tx, y, tw};
            y += 24;
        }

        for (HudWidget.Option o : cible.options()) {
            y = interrupteur(ctx, mx, my, x0, y, w0, o.label(), cible.opt(o.cle()),
                () -> cible.setOpt(o.cle(), !cible.opt(o.cle())));
        }

        bouton(ctx, mx, my, x0, py + ph - 28, 110, "Réinitialiser", C_OFF, cible::reinitialiser);
        bouton(ctx, mx, my, px + pw - 12 - 110, py + ph - 28, 110, "Placer", C_ACCENT,
            () -> mode = Mode.PLACEMENT);
    }

    /** Ligne de réglage avec interrupteur à bascule. @return l'ordonnée de la ligne suivante */
    private int interrupteur(GuiGraphics ctx, int mx, int my, int x, int y, int w, String label,
                             boolean valeur, Runnable action) {
        boolean hov = dans(mx, my, x, y, w, 20);
        HudWidget.fondArrondi(ctx, x, y, w, 20, hov ? C_CARTE_H : C_CARTE);
        ctx.drawString(font, label, x + 8, y + 6, C_BLANC, false);
        int bx = x + w - 32, by = y + 5;
        HudWidget.fondArrondi(ctx, bx, by, 24, 10, valeur ? C_VERT : C_OFF);
        int kx = valeur ? bx + 15 : bx + 1;
        HudWidget.fondArrondi(ctx, kx, by + 1, 8, 8, C_BLANC);
        zones.add(new Zone(x, y, w, 20, action));
        return y + 24;
    }

    private void bouton(GuiGraphics ctx, int mx, int my, int x, int y, int w, String label,
                        int couleur, Runnable action) {
        boolean hov = dans(mx, my, x, y, w, 20);
        HudWidget.fondArrondi(ctx, x, y, w, 20, hov ? couleur : C_CARTE);
        if (!hov) ctx.fill(x + 1, y + 19, x + w - 1, y + 20, couleur);
        ctx.drawCenteredString(font, label, x + w / 2, y + 6, hov && couleur == C_ACCENT ? C_CREUX : C_BLANC);
        zones.add(new Zone(x, y, w, 20, action));
    }

    /** Enregistre une zone cliquable, rognée à la partie visible de la grille. */
    private void zone(int x, int y, int w, int h, int haut, int bas, Runnable action) {
        int y1 = Math.max(y, haut), y2 = Math.min(y + h, bas);
        if (y2 > y1) zones.add(new Zone(x, y1, w, y2 - y1, action));
    }

    // ── Souris ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int mx = (int) mx0, my = (int) my0;
        Minecraft mc = Minecraft.getInstance();

        if (mode != Mode.PLACEMENT) {
            if (curseur != null && btn == 0
                    && dans(mx, my, curseur[0] - 4, curseur[1], curseur[2] + 8, 20)) {
                glisseCurseur = true;
                reglerTaille(mx);
                return true;
            }
            // Dernière zone enregistrée = dessinée au-dessus
            for (int i = zones.size() - 1; i >= 0; i--) {
                Zone z = zones.get(i);
                if (dans(mx, my, z.x(), z.y(), z.w(), z.h())) { z.action().run(); return true; }
            }
            // Clic hors du panneau : retour au placement
            if (!dans(mx, my, px, py, pw, ph)) mode = Mode.PLACEMENT;
            return true;
        }

        HudWidget w = sous(mc, mx, my);
        if (btn == 1) {
            if (w != null) ouvrirOptions(w, Mode.PLACEMENT);
            return true;
        }
        if (btn != 0) return true;

        if (w == null) {
            int bw = 96, bh = 24;
            if (dans(mx, my, (width - bw) / 2, (height - bh) / 2, bw, bh)) { mode = Mode.MODS; scroll = 0; }
            return true;
        }

        int x = w.getPixelX(width, mc), y = w.getPixelY(height, mc);
        int ww = w.getWidth(mc), hh = w.getHeight(mc);
        saisi = w;
        redimensionne = w.redimensionnable()
            && mx >= x + ww - POIGNEE - 1 && my >= y + hh - POIGNEE - 1;
        decalX = mx - x;
        decalY = my - y;
        return true;
    }

    private void reglerTaille(int mx) {
        float t = (mx - curseur[0]) / (float) curseur[2];
        t = Math.max(0f, Math.min(t, 1f));
        cible.taille = borner(HudWidget.TAILLE_MIN + t * (HudWidget.TAILLE_MAX - HudWidget.TAILLE_MIN));
    }

    @Override
    public boolean mouseDragged(double mx0, double my0, int btn, double dx, double dy) {
        int mx = (int) mx0, my = (int) my0;
        Minecraft mc = Minecraft.getInstance();
        if (glisseCurseur && curseur != null) { reglerTaille(mx); return true; }
        if (saisi == null) return true;

        if (redimensionne) {
            // La poignée suit la souris : la taille est le rapport à la largeur d'origine
            int x = saisi.getPixelX(width, mc);
            saisi.taille = borner((mx - x) / (float) saisi.baseWidth(mc));
        } else {
            deplacer(mc, mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx0, double my0, int btn) {
        saisi = null;
        redimensionne = false;
        glisseCurseur = false;
        guideX = guideY = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx0, double my0, double horizontal, double amount) {
        if (mode == Mode.MODS) {
            scroll = Math.max(0, Math.min(scroll - (int) (amount * 24), maxScroll));
            return true;
        }
        if (mode == Mode.PLACEMENT) {
            HudWidget w = sous(Minecraft.getInstance(), (int) mx0, (int) my0);
            if (w != null && w.redimensionnable())
                w.taille = borner(w.taille + (amount > 0 ? PAS_TAILLE : -PAS_TAILLE));
        }
        return true;
    }

    // ── Clavier ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) {   // Échap : on remonte d'un niveau
            if (mode == Mode.OPTIONS)   mode = retour;
            else if (mode == Mode.MODS) mode = Mode.PLACEMENT;
            else                        onClose();
            return true;
        }
        if (mode == Mode.MODS && key == 259 && !recherche.isEmpty()) {   // retour arrière
            recherche = recherche.substring(0, recherche.length() - 1);
            scroll = 0;
            return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char chr, int mod) {
        if (mode == Mode.MODS && chr >= ' ' && recherche.length() < 24) {
            recherche += chr;
            scroll = 0;
            return true;
        }
        return super.charTyped(chr, mod);
    }

    // ── Outils ────────────────────────────────────────────────────────────────

    private static boolean dans(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void cadre(GuiGraphics ctx, int x, int y, int w, int h, int couleur) {
        ctx.fill(x, y, x + w, y + 1, couleur);
        ctx.fill(x, y + h - 1, x + w, y + h, couleur);
        ctx.fill(x, y + 1, x + 1, y + h - 1, couleur);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, couleur);
    }
}
