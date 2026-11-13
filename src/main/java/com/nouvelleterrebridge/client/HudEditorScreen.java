package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.client.hud.HudWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu du HUD (touche H).
 *
 * <p>S'ouvre sur la <b>grille des widgets</b> : profils à gauche, filtres par
 * catégorie et recherche en haut, une carte par widget avec son aperçu, un bouton
 * Options et une barre Activé / Désactivé.
 *
 * <p>« Disposition du HUD » passe sur le jeu : chaque widget actif se glisse là où
 * on le veut, s'aligne sur les bords, le centre et les autres widgets (guides
 * dorés), et se redimensionne à la molette ou par sa poignée.
 *
 * <p>Les zones cliquables des panneaux sont enregistrées <b>au rendu</b>
 * ({@link #zones}) et relues au clic : recalculer les positions côté clic les
 * désynchroniserait du défilement, des filtres et de la recherche.
 */
public class HudEditorScreen extends Screen {

    public static final List<HudWidget> WIDGETS = new ArrayList<>();

    private static final String PROFIL_DEFAUT = "Principal";

    public static void loadAll() {
        ClientConfig cfg = ClientConfig.get();
        for (HudWidget w : WIDGETS) w.loadFromConfig(cfg);
    }

    private static void saveAll() {
        ClientConfig cfg = ClientConfig.get();
        for (HudWidget w : WIDGETS) w.saveToConfig(cfg);
        // Le profil actif est toujours le reflet de la disposition courante
        cfg.profils.put(profilActif(), copie(cfg.widgets));
        cfg.save();
    }

    public static HudWidget widget(String id) {
        for (HudWidget w : WIDGETS) if (w.id.equals(id)) return w;
        return null;
    }

    // ── Profils ───────────────────────────────────────────────────────────────

    private static String profilActif() {
        ClientConfig cfg = ClientConfig.get();
        if (cfg.profilActif == null || cfg.profilActif.isBlank()) cfg.profilActif = PROFIL_DEFAUT;
        return cfg.profilActif;
    }

    private static Map<String, ClientConfig.WidgetState> copie(Map<String, ClientConfig.WidgetState> source) {
        Map<String, ClientConfig.WidgetState> out = new HashMap<>();
        if (source == null) return out;
        for (var e : source.entrySet()) {
            ClientConfig.WidgetState s = e.getValue(), c = new ClientConfig.WidgetState();
            c.enabled = s.enabled; c.x = s.x; c.y = s.y;
            c.taille = s.taille; c.fond = s.fond; c.ombre = s.ombre;
            c.options = s.options == null ? new HashMap<>() : new HashMap<>(s.options);
            out.put(e.getKey(), c);
        }
        return out;
    }

    private static List<String> profils() {
        ClientConfig cfg = ClientConfig.get();
        if (!cfg.profils.containsKey(profilActif())) cfg.profils.put(profilActif(), copie(cfg.widgets));
        return new ArrayList<>(cfg.profils.keySet());
    }

    private static void changerProfil(String nom) {
        ClientConfig cfg = ClientConfig.get();
        if (nom.equals(profilActif()) || !cfg.profils.containsKey(nom)) return;
        saveAll();                                   // range la disposition courante dans son profil
        cfg.widgets = copie(cfg.profils.get(nom));
        cfg.profilActif = nom;
        loadAll();
        cfg.save();
    }

    private static void nouveauProfil() {
        ClientConfig cfg = ClientConfig.get();
        if (cfg.profils.size() >= PROFILS_MAX) return;
        saveAll();
        int n = cfg.profils.size() + 1;
        String nom = "Profil " + n;
        while (cfg.profils.containsKey(nom)) nom = "Profil " + (++n);
        // Part de la disposition courante : on duplique puis on ajuste
        cfg.profils.put(nom, copie(cfg.widgets));
        cfg.profilActif = nom;
        cfg.save();
    }

    private static void supprimerProfil(String nom) {
        ClientConfig cfg = ClientConfig.get();
        if (nom.equals(profilActif())) return;
        cfg.profils.remove(nom);
        cfg.save();
    }

    // ── Catégories ────────────────────────────────────────────────────────────

    private static final String[] CATEGORIES = {"TOUS", "INFOS", "JOUEUR", "SERVEUR"};

    /** Catégorie par identifiant de widget ; un widget absent d'ici tombe dans INFOS. */
    private static final Map<String, String> CATEGORIE = new LinkedHashMap<>();
    static {
        CATEGORIE.put("armure",  "JOUEUR");
        CATEGORIE.put("effets",  "JOUEUR");
        CATEGORIE.put("balance", "SERVEUR");
        CATEGORIE.put("quest",   "SERVEUR");
        CATEGORIE.put("notif",   "SERVEUR");
    }

    // ── Couleurs ──────────────────────────────────────────────────────────────
    private static final int C_FOND    = 0xEE15171B;
    private static final int C_ENTETE  = 0xFF101216;
    private static final int C_CARTE   = 0xFF23262C;
    private static final int C_CARTE_H = 0xFF2B2F36;
    private static final int C_BARRE   = 0xFF30343C;
    private static final int C_BARRE_H = 0xFF3D424C;
    private static final int C_CREUX   = 0xFF0C0E11;
    private static final int C_BORD    = 0xFF353A44;
    private static final int C_OFF     = 0xFF3A3F4A;
    private static final int C_ACCENT  = HudWidget.C_ACCENT;
    private static final int C_VERT    = 0xFF2EAD6B;
    private static final int C_VERT_H  = 0xFF38C47C;
    private static final int C_ROUGE   = 0xFFA8243E;
    private static final int C_ROUGE_H = 0xFFC02C49;
    private static final int C_BLANC   = 0xFFFFFFFF;
    private static final int C_GRIS    = 0xFFA6ACB8;
    private static final int C_SOMBRE  = 0xFF656B78;

    // ── Réglages ──────────────────────────────────────────────────────────────
    private static final int MARGE       = 2;    // écart gardé avec les bords et entre widgets collés
    private static final int AIMANT      = 5;    // distance d'accroche, en pixels
    private static final int POIGNEE     = 5;
    private static final int COLONNES    = 3;
    private static final int CARTE_H     = 112;
    private static final int ECART       = 8;
    private static final int ENTETE_H    = 40;
    private static final int LATERAL_W   = 124;
    private static final int PROFILS_MAX = 6;
    private static final float PAS_TAILLE = 0.05f;

    private enum Mode { MODS, REGLAGES, OPTIONS, PLACEMENT }

    private Mode mode = Mode.MODS;
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
    private String categorie = "TOUS";
    private int scroll = 0, maxScroll = 0;

    private record Zone(int x, int y, int w, int h, Runnable action) {}
    private final List<Zone> zones = new ArrayList<>();
    /** Piste du curseur de taille : {x, y, largeur}, ou null s'il n'est pas affiché. */
    private int[] curseur;
    private boolean glisseCurseur;

    private int pw, ph, px, py;

    public HudEditorScreen() {
        super(Component.literal("HUD"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() { saveAll(); }

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        // No-op — le menu se dessine par-dessus le jeu, sans flou ni fond de menu
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
                ctx.fill(0, 0, width, height, 0x90000000);
                renderCadre(ctx, mx, my);
                switch (mode) {
                    case MODS     -> { renderLateral(ctx, mx, my); renderMods(ctx, mc, mx, my); }
                    case REGLAGES -> { renderLateral(ctx, mx, my); renderReglages(ctx, mx, my); }
                    default       -> renderOptions(ctx, mc, mx, my);
                }
                ctx.pose().popPose();
            }
        } finally {
            HudWidget.enEdition = false;
        }
        super.render(ctx, mx, my, delta);
    }

    // ── Cadre commun : fond, en-tête, onglets ─────────────────────────────────

    private void renderCadre(GuiGraphics ctx, int mx, int my) {
        pw = Math.min(640, width - 30);
        ph = Math.min(372, height - 30);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        HudWidget.fondArrondi(ctx, px, py, pw, ph, C_FOND);
        HudWidget.fondArrondi(ctx, px, py, pw, ENTETE_H, C_ENTETE);
        ctx.fill(px, py + ENTETE_H, px + pw, py + ENTETE_H + 1, C_BORD);

        int ty = py + (ENTETE_H - 8) / 2;
        ctx.drawString(font, "◆", px + 14, ty, C_ACCENT, false);
        ctx.drawString(font, "§lNOUVELLE", px + 26, ty, C_BLANC, false);
        int tx = px + 26 + font.width("§lNOUVELLE") + 5;
        ctx.drawString(font, "TERRE", tx, ty, C_GRIS, false);
        tx += font.width("TERRE") + 22;

        if (mode == Mode.OPTIONS) {
            ctx.drawString(font, espace("OPTIONS") + "   §f" + cible.label, tx, ty, C_SOMBRE, false);
        } else {
            tx = onglet(ctx, mx, my, tx, "MODS", mode == Mode.MODS, () -> mode = Mode.MODS);
            onglet(ctx, mx, my, tx, "RÉGLAGES", mode == Mode.REGLAGES, () -> mode = Mode.REGLAGES);
        }

        // Fermer
        int fx = px + pw - 30, fy = py + 10;
        boolean hov = dans(mx, my, fx, fy, 20, 20);
        HudWidget.fondArrondi(ctx, fx, fy, 20, 20, hov ? C_ROUGE : C_CARTE);
        ctx.drawCenteredString(font, "✕", fx + 10, fy + 6, hov ? C_BLANC : C_GRIS);
        zones.add(new Zone(fx, fy, 20, 20, this::onClose));
    }

    /** Onglet de l'en-tête. @return l'abscisse du suivant */
    private int onglet(GuiGraphics ctx, int mx, int my, int x, String label, boolean actif, Runnable action) {
        String t = espace(label);
        int w = font.width(t) + 20, y = py + 10;
        boolean hov = dans(mx, my, x, y, w, 20);
        HudWidget.fondArrondi(ctx, x, y, w, 20, actif ? C_BARRE_H : (hov ? C_BARRE : C_CARTE));
        cadre(ctx, x, y, w, 20, actif ? C_GRIS : C_BORD);
        ctx.drawCenteredString(font, t, x + w / 2, y + 6, actif || hov ? C_BLANC : C_GRIS);
        zones.add(new Zone(x, y, w, 20, action));
        return x + w + 8;
    }

    // ── Barre latérale : profils et accès à la disposition ────────────────────

    private void renderLateral(GuiGraphics ctx, int mx, int my) {
        int x = px + 10, y = py + ENTETE_H + 11, w = LATERAL_W;
        int bas = py + ph - 10;

        ctx.drawString(font, espace("PROFILS"), x + 2, y, C_SOMBRE, false);
        y += 14;

        String actif = profilActif();
        for (String nom : profils()) {
            boolean estActif = nom.equals(actif);
            boolean hov = dans(mx, my, x, y, w, 18);
            HudWidget.fondArrondi(ctx, x, y, w, 18, estActif ? C_BARRE_H : (hov ? C_BARRE : C_CARTE));
            if (estActif) ctx.fill(x, y + 1, x + 2, y + 17, C_ACCENT);
            ctx.drawString(font, nom, x + 9, y + 5, estActif ? C_BLANC : C_GRIS, false);
            zones.add(new Zone(x, y, w, 18, () -> changerProfil(nom)));

            // Suppression : sur les autres profils seulement, enregistrée après → prioritaire
            if (!estActif && hov) {
                boolean hovX = dans(mx, my, x + w - 16, y, 16, 18);
                ctx.drawCenteredString(font, "✕", x + w - 8, y + 5, hovX ? HudWidget.C_RED : C_SOMBRE);
                zones.add(new Zone(x + w - 16, y, 16, 18, () -> supprimerProfil(nom)));
            }
            y += 21;
        }

        // Bas de la barre
        int by = bas - 20;
        boolean hov = dans(mx, my, x, by, w, 20);
        HudWidget.fondArrondi(ctx, x, by, w, 20, hov ? 0xFFF2B94F : C_ACCENT);
        ctx.drawCenteredString(font, "§lDISPOSITION DU HUD", x + w / 2, by + 6, C_CREUX);
        zones.add(new Zone(x, by, w, 20, () -> mode = Mode.PLACEMENT));

        by -= 26;
        boolean plein = ClientConfig.get().profils.size() >= PROFILS_MAX;
        boolean hovN = !plein && dans(mx, my, x, by, w, 18);
        HudWidget.fondArrondi(ctx, x, by, w, 18, hovN ? C_BARRE : C_CREUX);
        cadre(ctx, x, by, w, 18, C_BORD);
        ctx.drawCenteredString(font, plein ? "Maximum atteint" : "NOUVEAU PROFIL",
            x + w / 2, by + 5, plein ? C_SOMBRE : (hovN ? C_BLANC : C_GRIS));
        if (!plein) zones.add(new Zone(x, by, w, 18, HudEditorScreen::nouveauProfil));

        // Séparation avec le contenu
        ctx.fill(px + 10 + LATERAL_W + 9, py + ENTETE_H + 8, px + 10 + LATERAL_W + 10, bas, C_BORD);
    }

    private int contenuX() { return px + 10 + LATERAL_W + 20; }
    private int contenuW() { return px + pw - 10 - contenuX(); }

    // ── Grille des widgets ────────────────────────────────────────────────────

    private List<HudWidget> filtres() {
        String q = recherche.trim().toLowerCase();
        List<HudWidget> out = new ArrayList<>();
        for (HudWidget w : WIDGETS) {
            if (!categorie.equals("TOUS") && !categorie.equals(CATEGORIE.getOrDefault(w.id, "INFOS"))) continue;
            if (!q.isEmpty() && !w.label.toLowerCase().contains(q)) continue;
            out.add(w);
        }
        return out;
    }

    private void renderMods(GuiGraphics ctx, Minecraft mc, int mx, int my) {
        int x0 = contenuX(), w0 = contenuW();
        int y0 = py + ENTETE_H + 10;

        // Filtres par catégorie
        int cx = x0;
        for (String cat : CATEGORIES) {
            String t = espace(cat);
            int cw = font.width(t) + 16;
            boolean actif = cat.equals(categorie);
            boolean hov = dans(mx, my, cx, y0, cw, 18);
            HudWidget.fondArrondi(ctx, cx, y0, cw, 18, actif ? C_ACCENT : (hov ? C_BARRE_H : C_BARRE));
            ctx.drawCenteredString(font, t, cx + cw / 2, y0 + 5, actif ? C_CREUX : (hov ? C_BLANC : C_GRIS));
            zones.add(new Zone(cx, y0, cw, 18, () -> { categorie = cat; scroll = 0; }));
            cx += cw + 6;
        }

        // Recherche : toujours active, on tape directement
        int sw = Math.min(140, x0 + w0 - cx - 8);
        if (sw >= 70) {
            int sx = x0 + w0 - sw;
            HudWidget.fondArrondi(ctx, sx, y0, sw, 18, C_CREUX);
            cadre(ctx, sx, y0, sw, 18, C_BORD);
            boolean clignote = (System.currentTimeMillis() / 500) % 2 == 0;
            if (recherche.isEmpty()) ctx.drawString(font, "rechercher...", sx + 6, y0 + 5, C_SOMBRE, false);
            ctx.drawString(font, recherche + (clignote ? "_" : ""), sx + 6, y0 + 5, C_BLANC, false);
        }

        // Grille
        int gx = x0, gy = y0 + 28, gw = w0 - 8, gh = py + ph - 10 - gy;
        int cw = (gw - (COLONNES - 1) * ECART) / COLONNES;
        List<HudWidget> liste = filtres();
        int rangs = (liste.size() + COLONNES - 1) / COLONNES;
        maxScroll = Math.max(0, rangs * (CARTE_H + ECART) - ECART - gh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (liste.isEmpty())
            ctx.drawCenteredString(font, "Aucun widget ne correspond.", gx + gw / 2, gy + gh / 2, C_SOMBRE);

        ctx.enableScissor(gx, gy, gx + gw, gy + gh);
        boolean dansGrille = dans(mx, my, gx, gy, gw, gh);
        for (int i = 0; i < liste.size(); i++) {
            HudWidget w = liste.get(i);
            int x = gx + (i % COLONNES) * (cw + ECART);
            int y = gy + (i / COLONNES) * (CARTE_H + ECART) - scroll;
            if (y + CARTE_H < gy || y > gy + gh) continue;
            renderCarte(ctx, mc, mx, my, w, x, y, cw, dansGrille, gy, gy + gh);
        }
        ctx.disableScissor();

        // Défilement
        if (maxScroll > 0) {
            int pouce = Math.max(18, gh * gh / (maxScroll + gh));
            int pos = gy + (gh - pouce) * scroll / maxScroll;
            ctx.fill(gx + gw + 4, gy, gx + gw + 8, gy + gh, C_CREUX);
            ctx.fill(gx + gw + 4, pos, gx + gw + 8, pos + pouce, C_GRIS);
        }
    }

    /** Une carte : aperçu, nom, rangée Options + placement, barre Activé / Désactivé. */
    private void renderCarte(GuiGraphics ctx, Minecraft mc, int mx, int my, HudWidget w,
                             int x, int y, int cw, boolean dansGrille, int haut, int bas) {
        boolean hov = dansGrille && dans(mx, my, x, y, cw, CARTE_H);
        HudWidget.fondArrondi(ctx, x, y, cw, CARTE_H, hov ? C_CARTE_H : C_CARTE);

        // Aperçu réel du widget, à la place d'une icône
        w.renderApercu(ctx, mc, x + cw / 2, y + 28, cw - 20, 40);
        ctx.drawCenteredString(font, w.label, x + cw / 2, y + 56, w.enabled ? C_BLANC : C_GRIS);

        // Rangée Options + placement
        int ry = y + 72, pwBtn = 22;
        boolean hovO = dansGrille && dans(mx, my, x, ry, cw - pwBtn, 19);
        boolean hovP = dansGrille && dans(mx, my, x + cw - pwBtn, ry, pwBtn, 19);
        ctx.fill(x, ry, x + cw - pwBtn, ry + 19, hovO ? C_BARRE_H : C_BARRE);
        ctx.drawCenteredString(font, espace("OPTIONS"), x + (cw - pwBtn) / 2, ry + 6, hovO ? C_BLANC : C_GRIS);
        ctx.fill(x + cw - pwBtn + 1, ry, x + cw, ry + 19, hovP ? C_ACCENT : C_BARRE);
        ctx.drawCenteredString(font, "✥", x + cw - pwBtn / 2, ry + 6, hovP ? C_CREUX : C_GRIS);

        // Barre d'état, sur toute la largeur
        int by = y + 92;
        boolean hovB = dansGrille && dans(mx, my, x, by, cw, 20);
        int couleur = w.enabled ? (hovB ? C_VERT_H : C_VERT) : (hovB ? C_ROUGE_H : C_ROUGE);
        ctx.fill(x, by, x + cw, by + 19, couleur);
        ctx.fill(x + 1, by + 19, x + cw - 1, by + 20, couleur);
        ctx.drawCenteredString(font, espace(w.enabled ? "ACTIVÉ" : "DÉSACTIVÉ"), x + cw / 2, by + 6, C_BLANC);

        zone(x, ry, cw - pwBtn, 19, haut, bas, () -> ouvrirOptions(w, Mode.MODS));
        zone(x + cw - pwBtn, ry, pwBtn, 19, haut, bas, () -> { w.enabled = true; mode = Mode.PLACEMENT; });
        zone(x, by, cw, 20, haut, bas, () -> w.enabled = !w.enabled);
    }

    private void ouvrirOptions(HudWidget w, Mode depuis) {
        cible  = w;
        retour = depuis;
        mode   = Mode.OPTIONS;
    }

    // ── Réglages généraux ─────────────────────────────────────────────────────

    private void renderReglages(GuiGraphics ctx, int mx, int my) {
        int x0 = contenuX(), w0 = contenuW();
        int y = py + ENTETE_H + 10;
        ClientConfig cfg = ClientConfig.get();

        y = interrupteur(ctx, mx, my, x0, y, w0, "Présence Discord", cfg.discordRPCEnabled,
            () -> { cfg.discordRPCEnabled = !cfg.discordRPCEnabled; cfg.save(); });
        ctx.drawString(font, "Affiche « Nouvelle Terre » sur ton profil Discord. Pris en compte",
            x0 + 8, y, C_SOMBRE, false);
        ctx.drawString(font, "à la prochaine connexion au serveur.", x0 + 8, y + 10, C_SOMBRE, false);
        y += 30;

        bouton(ctx, mx, my, x0, y, 170, "Tout activer", C_VERT,
            () -> { for (HudWidget w : WIDGETS) w.enabled = true; });
        bouton(ctx, mx, my, x0 + 178, y, 170, "Tout désactiver", C_ROUGE,
            () -> { for (HudWidget w : WIDGETS) w.enabled = false; });
        y += 28;
        bouton(ctx, mx, my, x0, y, 170, "Réinitialiser les positions", C_OFF,
            () -> { for (HudWidget w : WIDGETS) w.reinitialiser(); });
        y += 34;

        ctx.drawString(font, "Ce menu s'ouvre avec la touche du HUD (H par défaut),", x0 + 8, y, C_SOMBRE, false);
        ctx.drawString(font, "à changer dans Options → Contrôles.", x0 + 8, y + 10, C_SOMBRE, false);
    }

    // ── Options d'un widget ───────────────────────────────────────────────────

    private void renderOptions(GuiGraphics ctx, Minecraft mc, int mx, int my) {
        int haut = py + ENTETE_H + 10, bas = py + ph - 10;

        // Aperçu à gauche
        int ax = px + 10, aw = 180, ah = bas - haut - 28;
        HudWidget.fondArrondi(ctx, ax, haut, aw, ah, C_CREUX);
        cible.renderApercu(ctx, mc, ax + aw / 2, haut + ah / 2, aw - 24, ah - 24);
        bouton(ctx, mx, my, ax, bas - 20, aw, "← Retour", C_OFF, () -> mode = retour);

        // Réglages à droite
        int x0 = ax + aw + 12, w0 = px + pw - 10 - x0;
        int y = haut;
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

        bouton(ctx, mx, my, x0, bas - 20, 120, "Réinitialiser", C_OFF, cible::reinitialiser);
        bouton(ctx, mx, my, px + pw - 10 - 120, bas - 20, 120, "Placer", C_ACCENT,
            () -> { cible.enabled = true; mode = Mode.PLACEMENT; });
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

    // ── Disposition sur le jeu ────────────────────────────────────────────────

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
        ctx.drawCenteredString(font, "§l" + espace("MODS"), width / 2, by + 8, bhov ? C_CREUX : C_ACCENT);
        ctx.drawCenteredString(font, "Glisser : déplacer  ·  Molette : taille  ·  Clic droit : options",
            width / 2, by + bh + 8, C_GRIS);
        ctx.drawCenteredString(font, "Maj : désactiver l'alignement  ·  Échap : revenir au menu",
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
            // Clic hors du panneau : on ferme
            if (!dans(mx, my, px, py, pw, ph)) onClose();
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
            scroll = Math.max(0, Math.min(scroll - (int) (amount * 30), maxScroll));
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
            if (mode == Mode.OPTIONS)        mode = retour;
            else if (mode == Mode.PLACEMENT) mode = Mode.MODS;
            else                             onClose();
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
        if (mode == Mode.MODS && chr >= ' ' && recherche.length() < 20) {
            recherche += chr;
            scroll = 0;
            return true;
        }
        return super.charTyped(chr, mod);
    }

    // ── Outils ────────────────────────────────────────────────────────────────

    /** Capitales espacées, pour les libellés de boutons et d'onglets. */
    private static String espace(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

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
