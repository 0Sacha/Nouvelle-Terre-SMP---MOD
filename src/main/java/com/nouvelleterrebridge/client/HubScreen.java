package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.network.HubNetworking;
import com.nouvelleterrebridge.network.NtNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Supplier;

/**
 * Hub du Parchemin — porte d'entrée vers toutes les fenêtres du mod.
 *
 * Même construction que le Guide : un cadre, une liste à gauche, un volet de
 * détail à droite. Survoler une entrée la présente dans le volet (icône, à quoi
 * elle sert, sa commande) ; un clic l'ouvre. Les entrées portent une vraie icône
 * d'objet : les symboles de la police ne faisaient que quelques pixels.
 */
public class HubScreen extends Screen {

    // ── Couleurs communes aux écrans du mod ───────────────────────────────────
    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_STRIP   = 0xFF1E2128;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;

    // ── Layout (aligné sur WikiScreen) ────────────────────────────────────────
    private static final int MAX_PW   = 520;
    private static final int ENTETE_H = 36;
    private static final int NAV_W    = 160;
    private static final int PAD      = 12;
    private static final int RANG_H   = 22;
    private static final int BTN_H    = 22;

    private int pw, ph, px, py;

    /** Une entrée du hub : action réseau, icône, nom, commande, et ce qu'on y fait. */
    private record Entry(int action, Supplier<ItemStack> icone, String label, String commande,
                         String accroche, List<String> points) {}

    private static final List<Entry> ENTRIES = List.of(
        new Entry(HubNetworking.ACTION_HDV, () -> new ItemStack(Items.EMERALD), "Marché", "/hdv",
            "Achète et vends des objets aux autres joueurs.",
            List.of("Mets tes objets en vente au prix que tu veux",
                    "Achat au meilleur prix, automatiquement",
                    "Les enchantements sont conservés")),
        new Entry(HubNetworking.ACTION_SHOP, () -> new ItemStack(Items.GOLD_INGOT), "Shop", "/shop",
            "La boutique du serveur : stock illimité.",
            List.of("Les articles se débloquent avec la production",
                    "Les prix bougent selon l'offre et la demande",
                    "Le serveur rachète aussi tes objets")),
        new Entry(HubNetworking.ACTION_MARCHE, () -> new ItemStack(Items.WRITABLE_BOOK), "LeBonCube", "/leboncube",
            "Les petites annonces de services.",
            List.of("Propose un chantier, un transport, un stand…",
                    "Paiement retenu jusqu'à la fin de la prestation",
                    "Notes et avis sur les prestataires")),
        new Entry(HubNetworking.ACTION_LOCATION, () -> new ItemStack(Items.CLOCK), "Location", "/location",
            "Loue un objet au lieu de l'acheter.",
            List.of("Loyer prélevé chaque jour, caution rendue au retour",
                    "Loyer impayé : l'objet devient inutilisable",
                    "On loue et on rend depuis cet écran")),
        new Entry(HubNetworking.ACTION_BANK, () -> new ItemStack(NouvelleTerreBridge.SHARD.get()), "Banque", "/bank",
            "Ton compte, tes virements, tes crédits.",
            List.of("Historique de tes transactions",
                    "Virements ponctuels ou récurrents",
                    "Retrait et dépôt de Shards physiques")),
        new Entry(HubNetworking.ACTION_QUETES, () -> new ItemStack(Items.IRON_SWORD), "Quêtes", "/quetes",
            "Des objectifs chaque jour, seul ou à plusieurs.",
            List.of("Trois quêtes journalières, renouvelées à minuit",
                    "Une quête communautaire pour tout le serveur",
                    "Récompenses en ◆, en objets et en expérience")),
        new Entry(HubNetworking.ACTION_PRODUCTION, () -> new ItemStack(Items.IRON_PICKAXE), "Production", "/production",
            "Ce que le serveur a produit, ressource par ressource.",
            List.of("Chaque récolte fait avancer les compteurs",
                    "Un seuil atteint débloque l'article au Shop",
                    "Plus une ressource abonde, moins elle coûte")),
        new Entry(HubNetworking.ACTION_REGISTRE, () -> new ItemStack(Items.BOOK), "Registre", "/registre",
            "Les personnages de Nouvelle Terre.",
            List.of("Qui joue qui, et qui est en ligne",
                    "La fiche complète de chaque personnage")),
        new Entry(HubNetworking.ACTION_CONFLIT, () -> new ItemStack(Items.SHIELD), "Conflit", "/conflit",
            "Déclare un conflit RP contre un joueur.",
            List.of("Choisis le joueur et donne ta raison",
                    "Le Conseil des Fondateurs est prévenu sur Discord")),
        new Entry(HubNetworking.ACTION_WIKI, () -> new ItemStack(Items.KNOWLEDGE_BOOK), "Guide", "/wiki",
            "Tout ce qu'il faut savoir pour bien démarrer.",
            List.of("L'économie, le marché, les quêtes expliqués",
                    "Les commandes utiles, cliquables",
                    "Comment contacter un administrateur"))
    );

    /** Entrée présentée dans le volet : la dernière survolée. */
    private int courante = 0;

    public HubScreen() {
        super(Component.literal("Parchemin — Nouvelle Terre"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void computeWin() {
        pw = Math.min(MAX_PW, width - 20);
        ph = Math.min(ENTETE_H + 1 + PAD + ENTRIES.size() * RANG_H + PAD, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        // No-op — cet écran dessine son propre fond (voir « Flou par-dessus l'UI » dans CLAUDE.md)
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        computeWin();

        // Cadre
        ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x40000000);
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        cadre(ctx, px, py, pw, ph, C_BORDER);

        // En-tête
        ctx.fill(px + 1, py + 1, px + pw - 1, py + ENTETE_H, C_PANEL);
        ctx.fill(px, py + ENTETE_H, px + pw, py + ENTETE_H + 1, C_BORDER);
        ctx.renderItem(new ItemStack(NouvelleTerreBridge.PARCHEMIN.get()), px + PAD, py + 10);
        ctx.drawString(font, "§lParchemin", px + PAD + 24, py + 8, C_GOLD, false);
        ctx.drawString(font, "Terminal de Nouvelle Terre", px + PAD + 24, py + 21, C_MID, false);

        String bal = BalanceHudOverlay.cachedBalance < 0 ? "? ◆" : fmt(BalanceHudOverlay.cachedBalance) + " ◆";
        int bw = font.width(bal) + 18;
        int bx = px + pw - bw - PAD, by = py + (ENTETE_H - 20) / 2;
        ctx.fill(bx, by, bx + bw, by + 20, C_STRIP);
        ctx.fill(bx, by, bx + 2, by + 20, C_GOLD);
        ctx.drawString(font, bal, bx + 10, by + 6, C_GOLD, false);

        // Séparateur liste | volet
        ctx.fill(px + NAV_W, py + ENTETE_H + 1, px + NAV_W + 1, py + ph - 1, C_BORDER);

        renderListe(ctx, mx, my);
        renderVolet(ctx, mx, my);

        super.render(ctx, mx, my, delta);
    }

    private int navX() { return px + PAD; }
    private int navY() { return py + ENTETE_H + 1 + PAD; }
    private int navW() { return NAV_W - PAD * 2; }

    private void renderListe(GuiGraphics ctx, int mx, int my) {
        int x = navX(), w = navW();
        for (int i = 0; i < ENTRIES.size(); i++) {
            int y = navY() + i * RANG_H;
            if (y + RANG_H > py + ph) break;
            Entry e = ENTRIES.get(i);
            if (mx >= x && mx < x + w && my >= y && my < y + RANG_H - 2) courante = i;
            boolean sel = i == courante;

            if (sel) {
                ctx.fill(x, y, x + w, y + RANG_H - 2, C_SURFACE);
                ctx.fill(x, y, x + 3, y + RANG_H - 2, C_GOLD);
            }
            ctx.renderItem(e.icone().get(), x + 7, y + 2);
            ctx.drawString(font, e.label(), x + 29, y + 6, sel ? C_GOLD : C_MID, false);
            if (sel) ctx.drawString(font, "›", x + w - 9, y + 6, C_GOLD, false);
        }
    }

    private int voletX() { return px + NAV_W + PAD + 2; }
    private int voletW() { return px + pw - PAD - voletX(); }
    private int boutonY() { return py + ph - PAD - BTN_H; }

    private void renderVolet(GuiGraphics ctx, int mx, int my) {
        Entry e = ENTRIES.get(courante);
        int x = voletX(), w = voletW();
        int y = navY();

        // Grande icône dans sa vignette
        int vs = 52;
        ctx.fill(x, y, x + vs, y + vs, C_PANEL);
        cadre(ctx, x, y, vs, vs, C_BORDER);
        ctx.fill(x, y + vs - 2, x + vs, y + vs, C_GOLD);
        ctx.pose().pushPose();
        ctx.pose().translate(x + (vs - 48) / 2f, y + (vs - 48) / 2f - 1, 0);
        ctx.pose().scale(3f, 3f, 1f);
        ctx.renderItem(e.icone().get(), 0, 0);
        ctx.pose().popPose();

        // Titre, commande, accroche
        int tx = x + vs + 12;
        ctx.pose().pushPose();
        ctx.pose().translate(tx, y + 4, 0);
        ctx.pose().scale(1.5f, 1.5f, 1f);
        ctx.drawString(font, "§l" + e.label(), 0, 0, C_WHITE, false);
        ctx.pose().popPose();
        ctx.drawString(font, e.commande(), tx, y + 22, C_GOLD, false);
        dessinerParagraphe(ctx, e.accroche(), tx, y + 36, x + w - tx, C_MID);

        // Ce qu'on y fait
        y += vs + 14;
        ctx.fill(x, y, x + w, y + 1, C_BORDER);
        y += 10;
        for (String point : e.points()) {
            ctx.fill(x + 2, y + 3, x + 5, y + 6, C_GOLD);
            y = dessinerParagraphe(ctx, point, x + 12, y, w - 12, C_WHITE) + 5;
        }

        // Ouvrir
        int by = boutonY();
        boolean hov = mx >= x && mx < x + w && my >= by && my < by + BTN_H;
        ctx.fill(x, by, x + w, by + BTN_H, hov ? C_GOLD : C_SURFACE);
        ctx.fill(x, by, x + w, by + 1, C_GOLD);
        ctx.drawCenteredString(font, "Ouvrir " + e.label() + "  ›", x + w / 2, by + 7, hov ? C_BG : C_GOLD);
    }

    /** Écrit un texte en le repliant sur la largeur donnée. @return l'ordonnée sous la dernière ligne */
    private int dessinerParagraphe(GuiGraphics ctx, String texte, int x, int y, int largeur, int couleur) {
        for (var ligne : font.split(Component.literal(texte), Math.max(40, largeur))) {
            ctx.drawString(font, ligne, x, y, couleur, false);
            y += 11;
        }
        return y;
    }

    private void cadre(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String fmt(int n) {
        return String.format("%,d", n).replace(',', ' ');
    }

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;
        if (x < px || x > px + pw || y < py || y > py + ph) { onClose(); return true; }

        // Une entrée de la liste
        if (x >= navX() && x < navX() + navW()) {
            int i = (y - navY()) / RANG_H;
            if (y >= navY() && i >= 0 && i < ENTRIES.size()) { ouvrir(ENTRIES.get(i)); return true; }
        }
        // Le bouton du volet
        if (x >= voletX() && x < voletX() + voletW() && y >= boutonY() && y < boutonY() + BTN_H) {
            ouvrir(ENTRIES.get(courante));
            return true;
        }
        return super.mouseClicked(mx0, my0, btn);
    }

    private void ouvrir(Entry e) {
        FriendlyByteBuf buf = NtNet.buffer();
        buf.writeInt(e.action());
        NtNet.versServeur(HubNetworking.HUB_ACTION, buf);
    }
}
