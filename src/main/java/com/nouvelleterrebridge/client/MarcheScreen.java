package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.network.ServiceNetworking;
import com.nouvelleterrebridge.service.ServiceImages;
import com.nouvelleterrebridge.service.ServiceManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * LeBonCube — petites annonces de services entre joueurs (/leboncube).
 *
 * Cinq onglets : le journal des annonces, ses propres annonces, les prestations
 * qu'on rend, les commandes qu'on a passées, et les archives.
 */
@Environment(EnvType.CLIENT)
public class MarcheScreen extends Screen {

    // ── Données ───────────────────────────────────────────────────────────────

    public record AnnonceData(int id, String auteur, String titre, String description,
                              String imageUrl, int prix, String contact, String categorie,
                              long creeLe, float noteMoyenne, int nbNotes) {}

    public record MessageData(String auteur, String texte, long envoyeLe) {}

    public record CommandeData(int id, String titre, String client, String prestataire,
                               int prix, int acompte, int sequestre, String statut,
                               boolean valideParPrestataire, boolean valideParClient,
                               String annulationDemandeePar, long creeLe, long termineeLe,
                               int note, String avis, List<MessageData> messages) {}

    // ── Couleurs ──────────────────────────────────────────────────────────────

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_BLUE    = 0xFF5BA8D4;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int MAX_PW = 660;
    private static final int MAX_PH = 460;
    private static final int TITRE_H  = 34;   // rangée titre + solde
    private static final int ONGLET_H = 20;   // rangée des onglets
    private static final int TOP_H  = TITRE_H + ONGLET_H;
    private static final int PAD    = 12;
    private static final int ROW_H  = 62;
    private static final int ROW_GAP = 4;
    private static final int BTN_H  = 18;

    private int pw, ph, px, py, rowW;

    private enum Tab {
        ANNONCES("📰  Annonces"), MES_ANNONCES("📌  Mes annonces"),
        PRESTATIONS("🔨  Mes prestations"), COMMANDES("📦  Mes commandes"),
        ARCHIVES("🗄  Archives");
        final String label;
        Tab(String l) { this.label = l; }
    }

    // ── État ──────────────────────────────────────────────────────────────────

    private int balance;
    private List<AnnonceData>  annonces    = new ArrayList<>();
    private List<CommandeData> prestations = new ArrayList<>();
    private List<CommandeData> commandes   = new ArrayList<>();
    private List<CommandeData> archives    = new ArrayList<>();

    private Tab tab = Tab.ANNONCES;
    private int scroll = 0;
    private int maxScroll = 0;
    private String categorieFiltre = "Toutes";
    private int tabsStartX = 0;

    private TextFieldWidget rechercheField;

    /** Bounds cliquables recalculés au rendu : {x, y, w, h, action, id}. */
    private final List<int[]> bounds = new ArrayList<>();
    private static final int CLIC_ANNONCE   = 0;
    private static final int CLIC_COMMANDER = 1;
    private static final int CLIC_RETIRER   = 2;
    private static final int CLIC_LIVREE    = 3;
    private static final int CLIC_VALIDER   = 4;
    private static final int CLIC_ANNULER   = 5;
    private static final int CLIC_OUVRIR_CHAT = 6;
    private static final int CLIC_CATEGORIE = 7;
    private static final int CLIC_ARCHIVE   = 8;

    /** Annonce ouverte en détail (null = liste). */
    private AnnonceData detail = null;
    /** Commande dont le chat est ouvert (null = aucun). */
    private CommandeData chatOuvert = null;
    /** Prestation archivée consultée en détail (null = liste). */
    private CommandeData archiveOuverte = null;
    /** Défilement de la conversation dans le détail d'archive. */
    private int scrollArchive = 0;

    // Formulaire de publication
    private boolean formOuvert = false;
    private TextFieldWidget fTitre, fDescription, fImage;
    private final NumberInput fPrix = new NumberInput(0, 1, 999_999);
    private int fContact = 0;
    /** Catégorie choisie : "" tant que rien n'est sélectionné ni saisi. */
    private String fCategorie = "";
    private TextFieldWidget fNouvelleCategorie;

    /** Catégories déjà utilisées sur le serveur, envoyées par le serveur. */
    private List<String> categories = new ArrayList<>();
    /** Ordre des pastilles de catégorie du formulaire, réarmé à chaque rendu. */
    private final List<String> categoriesCliquables = new ArrayList<>();

    // Validation avec note
    private CommandeData validationEnCours = null;
    private int noteChoisie = 0;
    private TextFieldWidget fAvis;

    // Chat
    private TextFieldWidget fMessage;

    private String toastMsg;
    private boolean toastOk;
    private long toastEnd;

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    public MarcheScreen(int balance, List<String> categories, List<AnnonceData> annonces,
                        List<CommandeData> prestations,
                        List<CommandeData> commandes, List<CommandeData> archives) {
        super(Text.literal("LeBonCube"));
        maj(balance, categories, annonces, prestations, commandes, archives);
    }

    public void maj(int balance, List<String> categories, List<AnnonceData> annonces,
                    List<CommandeData> prestations,
                    List<CommandeData> commandes, List<CommandeData> archives) {
        this.balance     = balance;
        this.categories  = new ArrayList<>(categories);
        this.annonces    = new ArrayList<>(annonces);
        this.prestations = new ArrayList<>(prestations);
        this.commandes   = new ArrayList<>(commandes);
        this.archives    = new ArrayList<>(archives);

        // Rafraîchir les vues ouvertes : leurs données viennent d'être remplacées
        if (chatOuvert != null) chatOuvert = retrouver(chatOuvert.id());
        if (validationEnCours != null) validationEnCours = retrouver(validationEnCours.id());
    }

    private CommandeData retrouver(int id) {
        for (CommandeData c : prestations) if (c.id() == id) return c;
        for (CommandeData c : commandes)   if (c.id() == id) return c;
        return null;
    }

    public void handleResult(boolean ok, String msg, int balance, List<String> categories,
                             List<AnnonceData> annonces,
                             List<CommandeData> prestations, List<CommandeData> commandes,
                             List<CommandeData> archives) {
        maj(balance, categories, annonces, prestations, commandes, archives);
        if (ok) {
            if (formOuvert) viderFormulaire();
            formOuvert = false;
            validationEnCours = null;
            if (fMessage != null) fMessage.setText("");
        }
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", "");
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 3500;
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_PW, width  - 20);
        ph = Math.min(MAX_PH, height - 20);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;
        rowW = pw - PAD * 2 - 8;

        rechercheField = champ(0, -200, 200, "Rechercher un service...");
        fTitre         = champ(0, -200, 200, "Titre de l'annonce...");
        fDescription   = champ(0, -200, 200, "Décrivez votre prestation...");
        fImage         = champ(0, -200, 200, "Lien image (facultatif)");
        fAvis          = champ(0, -200, 200, "Votre avis (facultatif)");
        fMessage       = champ(0, -200, 200, "Votre message...");
        fTitre.setMaxLength(60);
        // Une URL Discord porte des paramètres de signature : ~200 caractères.
        // Sans ça le champ reste au défaut de TextFieldWidget, soit 32.
        fImage.setMaxLength(500);
        fDescription.setMaxLength(500);
        fAvis.setMaxLength(200);
        fMessage.setMaxLength(200);
        fNouvelleCategorie = champ(0, -200, 200, "Nouvelle catégorie...");
        fNouvelleCategorie.setMaxLength(20);
        fPrix.setPlaceholder("Prix ◆...");
        rechercheField.setChangedListener(s -> scroll = 0);
    }

    /**
     * Remet le formulaire à blanc.
     *
     * Sans ça, publier une annonce puis en ouvrir une nouvelle réaffichait tout le
     * texte de la précédente, qu'il fallait effacer à la main.
     */
    private void viderFormulaire() {
        if (fTitre != null)       fTitre.setText("");
        if (fDescription != null) fDescription.setText("");
        if (fImage != null)       fImage.setText("");
        if (fNouvelleCategorie != null) fNouvelleCategorie.setText("");
        fPrix.clear();
        fContact   = 0;
        fCategorie = "";
    }

    /**
     * Envoie hors écran tous les champs que la vue courante n'utilise pas.
     *
     * Un champ non rendu garde sa dernière position et continue de capter les
     * clics : la barre de recherche, restée sur la zone de liste, avalait le clic
     * du bouton « ← Retour » du formulaire, qui semblait alors mort.
     */
    private void parquerChamps(TextFieldWidget... utilises) {
        List<TextFieldWidget> gardes = List.of(utilises);
        for (TextFieldWidget f : new TextFieldWidget[]{rechercheField, fTitre, fDescription,
                fImage, fNouvelleCategorie, fAvis, fMessage}) {
            if (f != null && !gardes.contains(f)) { f.setY(-200); f.setFocused(false); }
        }
    }

    private TextFieldWidget champ(int x, int y, int w, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setDrawsBackground(false);
        f.setPlaceholder(Text.literal(placeholder));
        addSelectableChild(f);
        return f;
    }

    @Override public boolean shouldPause() { return false; }

    private String moi() {
        return client != null && client.player != null ? client.player.getName().getString() : "";
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        cadre(ctx, px, py, pw, ph, C_BORDER);

        bounds.clear();
        renderTopBar(ctx, mx, my);

        int contentY = py + TOP_H + 1;
        int contentH = ph - TOP_H - 1;

        if (formOuvert)                  renderFormulaire(ctx, mx, my, contentY, contentH);
        else if (validationEnCours != null) renderValidation(ctx, mx, my, contentY, contentH);
        else if (archiveOuverte != null) renderArchiveDetail(ctx, mx, my, contentY, contentH);
        else if (chatOuvert != null)     renderChat(ctx, mx, my, contentY, contentH);
        else if (detail != null)         renderDetail(ctx, mx, my, contentY, contentH);
        else switch (tab) {
            case ANNONCES     -> renderAnnonces(ctx, mx, my, contentY, contentH, false);
            case MES_ANNONCES -> renderAnnonces(ctx, mx, my, contentY, contentH, true);
            case PRESTATIONS  -> renderCommandes(ctx, mx, my, contentY, contentH, prestations, true);
            case COMMANDES    -> renderCommandes(ctx, mx, my, contentY, contentH, commandes, false);
            case ARCHIVES     -> renderCommandes(ctx, mx, my, contentY, contentH, archives, false);
        }

        renderToast(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void cadre(DrawContext ctx, int x, int y, int w, int h, int couleur) {
        ctx.fill(x, y, x + w, y + 1, couleur);
        ctx.fill(x, y + h - 1, x + w, y + h, couleur);
        ctx.fill(x, y, x + 1, y + h, couleur);
        ctx.fill(x + w - 1, y, x + w, y + h, couleur);
    }

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(px, py, px + pw, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H - 1, px + pw, py + TOP_H, C_BORDER);

        // ── Rangée 1 : retour, titre, solde ──
        int tx = px + PAD;
        HubBackButton.render(ctx, textRenderer, tx, py + (TITRE_H - HubBackButton.H) / 2, mx, my);
        tx += HubBackButton.W + 8;

        ctx.drawText(textRenderer, "LeBonCube", tx, py + 8, C_GOLD, false);
        ctx.drawText(textRenderer, "§8Services entre joueurs", tx, py + 21, C_DIM, false);

        String bal = balance + " ◆";
        int bw = textRenderer.getWidth(bal) + 16;
        int bx = px + pw - bw - PAD;
        ctx.fill(bx, py + 8, bx + bw, py + 28, C_SURFACE);
        ctx.fill(bx, py + 8, bx + 2, py + 28, C_GOLD);
        ctx.drawText(textRenderer, bal, bx + 9, py + 14, C_GOLD, false);

        // ── Rangée 2 : onglets sur toute la largeur ──
        // Ils étaient sur la même ligne que le titre et passaient sous le solde dès
        // que le libellé du dernier onglet dépassait. Une rangée dédiée, à largeur
        // répartie, garantit qu'ils restent tous atteignables.
        tabsStartX = px + PAD;
        int dispo = pw - PAD * 2;
        int tabW  = dispo / Tab.values().length;
        int tabY  = py + TITRE_H;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            int bxT = tabsStartX + i * tabW;
            int bwT = (i == Tab.values().length - 1) ? (px + pw - PAD - bxT) : tabW - 2;
            boolean actif = tab == t && detail == null && chatOuvert == null && !formOuvert;
            boolean hov = mx >= bxT && mx < bxT + bwT && my >= tabY && my < tabY + ONGLET_H;
            ctx.fill(bxT, tabY, bxT + bwT, tabY + ONGLET_H, actif ? C_GOLD : (hov ? C_HOVER : C_SURFACE));
            if (actif) ctx.fill(bxT, tabY, bxT + bwT, tabY + 1, C_WHITE);
            centre(ctx, tronquer(t.label, bwT - 6), bxT + bwT / 2, tabY + 5,
                actif ? C_BG : (hov ? C_WHITE : C_MID));
        }
    }

    /** Texte centré sans ombre — l'ombre rend les libellés gras et illisibles. */
    private void centre(DrawContext ctx, String texte, int cx, int y, int couleur) {
        ctx.drawText(textRenderer, texte, cx - textRenderer.getWidth(texte) / 2, y, couleur, false);
    }

    // ── Liste d'annonces ──────────────────────────────────────────────────────

    private List<AnnonceData> annoncesFiltrees(boolean seulementMoi) {
        String q = rechercheField != null ? rechercheField.getText().trim().toLowerCase() : "";
        String me = moi();
        return annonces.stream()
            .filter(a -> !seulementMoi || a.auteur().equalsIgnoreCase(me))
            .filter(a -> "Toutes".equals(categorieFiltre) || categorieFiltre.equals(a.categorie()))
            .filter(a -> q.isEmpty()
                || a.titre().toLowerCase().contains(q)
                || a.description().toLowerCase().contains(q)
                || a.auteur().toLowerCase().contains(q)
                || a.categorie().toLowerCase().contains(q))
            .toList();
    }

    private void renderAnnonces(DrawContext ctx, int mx, int my, int cy, int ch, boolean mesAnnonces) {
        parquerChamps(rechercheField);
        // Barre de recherche, et bouton publier réservé au journal des annonces
        int barreH = 26;
        ctx.fill(px + PAD, cy + 6, px + pw - PAD, cy + 6 + 20, C_SURFACE);
        int finBarre = px + pw - PAD;
        if (!mesAnnonces) {
            String pubLbl = "+ Publier une annonce";
            int pubW = textRenderer.getWidth(pubLbl) + 18;
            int pubX = px + pw - PAD - pubW;
            boolean pubHov = mx >= pubX && mx < pubX + pubW && my >= cy + 6 && my < cy + 26;
            ctx.fill(pubX, cy + 6, pubX + pubW, cy + 26, pubHov ? 0xFF1A8050 : C_GREEN);
            centre(ctx, pubLbl, pubX + pubW / 2, cy + 12, C_WHITE);
            bounds.add(new int[]{pubX, cy + 6, pubW, 20, 100, 0});
            finBarre = pubX;
        }

        if (rechercheField != null) {
            rechercheField.setX(px + PAD + 8);
            rechercheField.setY(cy + 11);
            rechercheField.setWidth(finBarre - px - PAD - 24);
            rechercheField.render(ctx, mx, my, 0);
        }
        cy += barreH + 4;

        // Filtres de catégorie
        int fx = px + PAD;
        int fy = cy;
        List<String> cats = filtresDisponibles();
        for (int i = 0; i < cats.size(); i++) {
            String c = cats.get(i);
            int cw = textRenderer.getWidth(c) + 12;
            if (fx + cw > px + pw - PAD) break;
            boolean actif = categorieFiltre.equals(c);
            boolean hov = mx >= fx && mx < fx + cw && my >= fy && my < fy + 16;
            ctx.fill(fx, fy, fx + cw, fy + 16, actif ? C_GOLD : (hov ? C_HOVER : C_SURFACE));
            centre(ctx, c, fx + cw / 2, fy + 4, actif ? C_BG : C_MID);
            bounds.add(new int[]{fx, fy, cw, 16, CLIC_CATEGORIE, i});
            fx += cw + 4;
        }
        cy += 22;

        List<AnnonceData> list = annoncesFiltrees(mesAnnonces);
        int listH = ph - (cy - py) - PAD;
        int visRows = Math.max(1, listH / (ROW_H + ROW_GAP));
        maxScroll = Math.max(0, list.size() - visRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (list.isEmpty()) {
            centre(ctx, mesAnnonces ? "Vous n'avez aucune annonce en ligne."
                                    : "Aucune annonce ne correspond.",
                px + pw / 2, cy + listH / 2 - 4, C_DIM);
            return;
        }

        ctx.enableScissor(px + PAD, cy, px + PAD + rowW, cy + listH);
        for (int i = scroll; i < Math.min(scroll + visRows + 1, list.size()); i++) {
            int ry = cy + (i - scroll) * (ROW_H + ROW_GAP);
            renderAnnonceRow(ctx, list.get(i), px + PAD, ry, mx, my, mesAnnonces);
        }
        ctx.disableScissor();

        renderScrollbar(ctx, px + PAD + rowW + 2, cy, listH, visRows, list.size());
    }

    private void renderAnnonceRow(DrawContext ctx, AnnonceData a, int x, int y,
                                  int mx, int my, boolean mienne) {
        boolean hov = mx >= x && mx < x + rowW && my >= y && my < y + ROW_H;
        ctx.fill(x, y, x + rowW, y + ROW_H, hov ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + ROW_H, couleurCategorie(a.categorie()));
        ctx.fill(x, y + ROW_H - 1, x + rowW, y + ROW_H, C_BORDER);

        // Vignette : image si chargée, sinon aplat avec l'initiale de la catégorie
        int vigX = x + 8, vigY = y + 7, vigW = 68, vigH = ROW_H - 14;
        renderVignette(ctx, a.imageUrl(), vigX, vigY, vigW, vigH, a.categorie());

        int tx = vigX + vigW + 10;
        int dispo = rowW - (tx - x) - 120;
        ctx.drawText(textRenderer, tronquer(a.titre(), dispo), tx, y + 8, C_WHITE, false);

        String meta = a.categorie() + "  ·  par " + a.auteur() + "  ·  " + ancienntete(a.creeLe());
        ctx.drawText(textRenderer, tronquer(meta, dispo), tx, y + 21, C_DIM, false);

        String desc = a.description().replace("\n", " ");
        ctx.drawText(textRenderer, "§7" + tronquer(desc, dispo), tx, y + 34, C_MID, false);

        if (a.nbNotes() > 0) {
            String etoiles = etoiles(Math.round(a.noteMoyenne())) + " §8(" + a.nbNotes() + ")";
            ctx.drawText(textRenderer, etoiles, tx, y + 46, C_GOLD, false);
        } else {
            ctx.drawText(textRenderer, "§8Pas encore noté", tx, y + 46, C_DIM, false);
        }

        // Prix + action à droite
        String prix = a.prix() + " ◆";
        int prixW = textRenderer.getWidth(prix);
        ctx.drawText(textRenderer, prix, x + rowW - prixW - 12, y + 10, C_GOLD, false);

        String lbl = mienne ? "Retirer" : "Voir";
        int lw = textRenderer.getWidth(lbl) + 16;
        int lx = x + rowW - lw - 12;
        int ly = y + ROW_H - BTN_H - 8;
        boolean lhov = mx >= lx && mx < lx + lw && my >= ly && my < ly + BTN_H;
        int couleur = mienne ? C_RED : C_GOLD;
        ctx.fill(lx, ly, lx + lw, ly + BTN_H, lhov ? couleur : C_SURFACE);
        ctx.fill(lx, ly, lx + lw, ly + 1, couleur);
        centre(ctx, lbl, lx + lw / 2, ly + 5,
            lhov ? C_WHITE : C_MID);
        bounds.add(new int[]{lx, ly, lw, BTN_H, mienne ? CLIC_RETIRER : CLIC_ANNONCE, a.id()});

        // Toute la ligne ouvre le détail (le bouton reste prioritaire, ajouté avant)
        bounds.add(new int[]{x, y, rowW, ROW_H, CLIC_ANNONCE, a.id()});
    }

    /** Vignette d'annonce : image distante si disponible, pastille sinon. */
    private void renderVignette(DrawContext ctx, String url, int x, int y, int w, int h, String categorie) {
        ctx.fill(x, y, x + w, y + h, C_BG);
        if (url != null && !url.isEmpty()) {
            Identifier tex = RemoteImage.texture(url);
            if (tex != null) {
                // Proportions conservées : une photo de téléphone (portrait) étirée
                // dans un cadre paysage devient méconnaissable.
                int iw = Math.max(1, RemoteImage.largeur(url));
                int ih = Math.max(1, RemoteImage.hauteur(url));
                float k = Math.min(w / (float) iw, h / (float) ih);
                int dw = Math.max(1, Math.round(iw * k));
                int dh = Math.max(1, Math.round(ih * k));
                ctx.drawTexture(tex, x + (w - dw) / 2, y + (h - dh) / 2, 0, 0, dw, dh, dw, dh);
                return;
            }
            if (RemoteImage.enCours(url)) {
                centre(ctx, "…", x + w / 2, y + h / 2 - 4, C_DIM);
                return;
            }
        }
        ctx.fill(x, y, x + w, y + h, 0x30000000 | (couleurCategorie(categorie) & 0xFFFFFF));
        centre(ctx, categorie.substring(0, 1),
            x + w / 2, y + h / 2 - 4, couleurCategorie(categorie));
    }

    /** Palette des pastilles de catégorie — teintes distinctes et lisibles sur fond sombre. */
    private static final int[] TEINTES_CATEGORIE = {
        0xFFE8A838, 0xFF5BA8D4, 0xFF2EAD6B, 0xFFB07A3C,
        0xFF8E7CC3, 0xFFD46A9F, 0xFF4FB3A5, 0xFFC8894A
    };

    /**
     * Couleur d'une catégorie, dérivée de son nom.
     *
     * Les catégories sont créées par les joueurs : impossible de les associer à
     * l'avance. Un hachage donne une teinte stable — la même catégorie garde
     * toujours la même couleur, sur tous les clients.
     */
    private int couleurCategorie(String cat) {
        if (cat == null || cat.isEmpty()) return C_MID;
        int h = Math.abs(cat.toLowerCase().hashCode());
        return TEINTES_CATEGORIE[h % TEINTES_CATEGORIE.length];
    }

    /** « Toutes » suivi des catégories réellement utilisées par les annonces. */
    private List<String> filtresDisponibles() {
        List<String> out = new ArrayList<>();
        out.add("Toutes");
        out.addAll(categories);
        return out;
    }

    // ── Détail d'une annonce ──────────────────────────────────────────────────

    private void renderDetail(DrawContext ctx, int mx, int my, int cy, int ch) {
        parquerChamps();
        AnnonceData a = detail;
        int x = px + PAD, w = pw - PAD * 2;

        renderBoutonRetour(ctx, mx, my, x, cy + 8);
        cy += 32;

        ctx.fill(x, cy, x + w, cy + ch - 44, C_PANEL);
        ctx.fill(x, cy, x + 3, cy + ch - 44, couleurCategorie(a.categorie()));

        int ix = x + 12, iy = cy + 12;
        int imgW = 180, imgH = 110;
        renderVignette(ctx, a.imageUrl(), ix, iy, imgW, imgH, a.categorie());
        if (a.imageUrl() != null && !a.imageUrl().isEmpty() && RemoteImage.echec(a.imageUrl()))
            ctx.drawText(textRenderer, "§8Image indisponible", ix, iy + imgH + 4, C_DIM, false);

        int tx = ix + imgW + 14;
        int tw = w - (tx - x) - 16;
        ctx.drawText(textRenderer, tronquer(a.titre(), tw), tx, iy, C_WHITE, false);
        ctx.drawText(textRenderer, a.categorie() + " · par " + a.auteur(), tx, iy + 14, C_MID, false);
        ctx.drawText(textRenderer, "Publiée " + ancienntete(a.creeLe()), tx, iy + 26, C_DIM, false);
        ctx.drawText(textRenderer, a.nbNotes() > 0
            ? etoiles(Math.round(a.noteMoyenne())) + " §8(" + a.nbNotes() + " avis)"
            : "§8Pas encore noté", tx, iy + 40, C_GOLD, false);

        ctx.drawText(textRenderer, "§6" + a.prix() + " ◆ §7la prestation", tx, iy + 58, C_GOLD, false);
        ctx.drawText(textRenderer, "§7Contact : §f" + a.contact(), tx, iy + 72, C_MID, false);

        // Description sur plusieurs lignes
        int dy = iy + imgH + 20;
        ctx.drawText(textRenderer, "DESCRIPTION", ix, dy, C_DIM, false);
        dy += 14;
        for (String ligne : decouper(a.description(), w - 28)) {
            if (dy > cy + ch - 70) break;
            ctx.drawText(textRenderer, "§f" + ligne, ix, dy, C_WHITE, false);
            dy += 11;
        }

        // Commander
        boolean mienne = a.auteur().equalsIgnoreCase(moi());
        boolean assez  = balance >= a.prix();
        String lbl = mienne ? "Votre annonce"
                   : assez ? "Commander — " + a.prix() + " ◆"
                           : "Solde insuffisant";
        int bw = Math.max(180, textRenderer.getWidth(lbl) + 24);
        int bx = px + pw - PAD - bw - 12;
        int by = py + ph - PAD - 26;
        boolean actif = !mienne && assez;
        boolean hov = actif && mx >= bx && mx < bx + bw && my >= by && my < by + 24;
        ctx.fill(bx, by, bx + bw, by + 24, actif ? (hov ? 0xFF1A8050 : C_GREEN) : C_SURFACE);
        centre(ctx, lbl, bx + bw / 2, by + 8,
            actif ? C_WHITE : C_DIM);
        if (actif) bounds.add(new int[]{bx, by, bw, 24, CLIC_COMMANDER, a.id()});

        ctx.drawText(textRenderer, "§8La moitié est versée à la commande, le reste à la validation.",
            px + PAD + 12, by + 8, C_DIM, false);
    }

    private void renderBoutonRetour(DrawContext ctx, int mx, int my, int x, int y) {
        String lbl = "← Retour";
        int w = textRenderer.getWidth(lbl) + 16;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 18;
        ctx.fill(x, y, x + w, y + 18, hov ? C_HOVER : C_SURFACE);
        ctx.drawText(textRenderer, lbl, x + 8, y + 5, hov ? C_GOLD : C_MID, false);
        bounds.add(new int[]{x, y, w, 18, 101, 0});
    }

    // ── Commandes / prestations ───────────────────────────────────────────────

    private void renderCommandes(DrawContext ctx, int mx, int my, int cy, int ch,
                                 List<CommandeData> list, boolean cotePrestataire) {
        parquerChamps();
        cy += 8;
        int listH = ph - (cy - py) - PAD;

        if (list.isEmpty()) {
            centre(ctx, cotePrestataire ? "Aucune prestation en cours."
                : (tab == Tab.ARCHIVES ? "Aucune archive." : "Aucune commande en cours."),
                px + pw / 2, cy + listH / 2 - 4, C_DIM);
            return;
        }

        int rh = 56;
        int visRows = Math.max(1, listH / (rh + ROW_GAP));
        maxScroll = Math.max(0, list.size() - visRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ctx.enableScissor(px + PAD, cy, px + PAD + rowW, cy + listH);
        for (int i = scroll; i < Math.min(scroll + visRows + 1, list.size()); i++) {
            renderCommandeRow(ctx, list.get(i), px + PAD, cy + (i - scroll) * (rh + ROW_GAP),
                rh, mx, my, cotePrestataire);
        }
        ctx.disableScissor();
        renderScrollbar(ctx, px + PAD + rowW + 2, cy, listH, visRows, list.size());
    }

    private void renderCommandeRow(DrawContext ctx, CommandeData c, int x, int y, int rh,
                                   int mx, int my, boolean cotePrestataire) {
        boolean hov = mx >= x && mx < x + rowW && my >= y && my < y + rh;
        ctx.fill(x, y, x + rowW, y + rh, hov ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + rh, couleurStatut(c));
        ctx.fill(x, y + rh - 1, x + rowW, y + rh, C_BORDER);

        int tx = x + 12;
        ctx.drawText(textRenderer, tronquer(c.titre(), rowW - 240), tx, y + 8, C_WHITE, false);

        String autre = cotePrestataire ? "Client : " + c.client() : "Prestataire : " + c.prestataire();
        ctx.drawText(textRenderer, autre + "  ·  " + c.prix() + " ◆  ·  " + ancienntete(c.creeLe()),
            tx, y + 22, C_DIM, false);
        ctx.drawText(textRenderer, libelleStatut(c), tx, y + 36, couleurStatut(c), false);

        int droite = x + rowW - 10;

        // Chat, toujours disponible tant que la commande vit
        if (!"TERMINEE".equals(c.statut()) && !"ANNULEE".equals(c.statut())) {
            String chatLbl = "💬 " + c.messages().size();
            droite = bouton(ctx, chatLbl, droite, y + (rh - BTN_H) / 2, mx, my,
                C_BLUE, CLIC_OUVRIR_CHAT, c.id()) - 6;

            if (cotePrestataire && !c.valideParPrestataire())
                droite = bouton(ctx, "Terminé", droite, y + (rh - BTN_H) / 2, mx, my,
                    C_GREEN, CLIC_LIVREE, c.id()) - 6;

            if (!cotePrestataire && c.valideParPrestataire())
                droite = bouton(ctx, "Valider", droite, y + (rh - BTN_H) / 2, mx, my,
                    C_GREEN, CLIC_VALIDER, c.id()) - 6;

            String annulLbl = c.annulationDemandeePar().isEmpty() ? "Annuler"
                : (c.annulationDemandeePar().equalsIgnoreCase(moi()) ? "Demandé…" : "Accepter l'annulation");
            bouton(ctx, annulLbl, droite, y + (rh - BTN_H) / 2, mx, my, C_RED, CLIC_ANNULER, c.id());
        } else {
            // Archive : la ligne entière ouvre le dossier complet
            if (c.note() > 0) {
                String e = etoiles(c.note());
                ctx.drawText(textRenderer, e, x + rowW - textRenderer.getWidth(e) - 12, y + 8, C_GOLD, false);
            }
            String voir = "Consulter";
            int vw = textRenderer.getWidth(voir) + 14;
            int vx = x + rowW - vw - 12;
            int vy = y + rh - BTN_H - 8;
            boolean vhov = mx >= x && mx < x + rowW && my >= y && my < y + rh;
            ctx.fill(vx, vy, vx + vw, vy + BTN_H, vhov ? C_GOLD : C_SURFACE);
            ctx.fill(vx, vy, vx + vw, vy + 1, C_GOLD);
            centre(ctx, voir, vx + vw / 2, vy + 5, vhov ? C_BG : C_MID);
            bounds.add(new int[]{x, y, rowW, rh, CLIC_ARCHIVE, c.id()});
        }
    }

    private int bouton(DrawContext ctx, String label, int droite, int y, int mx, int my,
                       int couleur, int action, int id) {
        int w = textRenderer.getWidth(label) + 14;
        int x = droite - w;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + BTN_H;
        ctx.fill(x, y, x + w, y + BTN_H, hov ? couleur : C_SURFACE);
        ctx.fill(x, y, x + w, y + 1, couleur);
        centre(ctx, label, x + w / 2, y + 5, hov ? C_WHITE : C_MID);
        bounds.add(new int[]{x, y, w, BTN_H, action, id});
        return x;
    }

    private int couleurStatut(CommandeData c) {
        return switch (c.statut()) {
            case "TERMINEE" -> C_GREEN;
            case "ANNULEE"  -> C_RED;
            case "LIVREE"   -> C_GOLD;
            default         -> C_BLUE;
        };
    }

    private String libelleStatut(CommandeData c) {
        if (!c.annulationDemandeePar().isEmpty() && !c.statut().equals("TERMINEE")
            && !c.statut().equals("ANNULEE"))
            return "⚠ Annulation demandée par " + c.annulationDemandeePar();
        return switch (c.statut()) {
            case "TERMINEE" -> "✅ Terminée — solde versé";
            case "ANNULEE"  -> "✖ Annulée — client remboursé";
            case "LIVREE"   -> "📦 Livrée — en attente de validation du client";
            default         -> "🔨 En cours  ·  acompte versé : " + c.acompte() + " ◆"
                             + "  ·  en séquestre : " + c.sequestre() + " ◆";
        };
    }

    // ── Détail d'une prestation archivée ─────────────────────────────────────

    /**
     * Dossier complet d'une prestation close : ce qu'elle était, ce qu'elle a
     * coûté, l'avis laissé, et toute la conversation.
     *
     * Le chat disparaissait avec la commande une fois close : l'historique
     * existait toujours dans leboncube.json mais n'était plus consultable.
     */
    private void renderArchiveDetail(DrawContext ctx, int mx, int my, int cy, int ch) {
        parquerChamps();
        CommandeData c = archiveOuverte;
        if (c == null) return;
        int x = px + PAD, w = pw - PAD * 2;

        renderBoutonRetour(ctx, mx, my, x, cy + 8);
        boolean annulee = "ANNULEE".equals(c.statut());
        ctx.drawText(textRenderer, tronquer(c.titre(), w - 200), x + 90, cy + 13, C_WHITE, false);
        String etat = annulee ? "✖ Annulée" : "✅ Terminée";
        ctx.drawText(textRenderer, etat, x + w - textRenderer.getWidth(etat) - 4, cy + 13,
            annulee ? C_RED : C_GREEN, false);
        cy += 32;

        // ── Fiche : les deux parties, l'argent, les dates ──
        int ficheH = 66;
        ctx.fill(x, cy, x + w, cy + ficheH, C_PANEL);
        ctx.fill(x, cy, x + 3, cy + ficheH, annulee ? C_RED : C_GREEN);

        boolean jeSuisClient = c.client().equalsIgnoreCase(moi());
        ctx.drawText(textRenderer, "§7Prestataire : §f" + c.prestataire(), x + 12, cy + 10, C_MID, false);
        ctx.drawText(textRenderer, "§7Client : §f" + c.client(), x + 12, cy + 24, C_MID, false);
        ctx.drawText(textRenderer, "§8" + (jeSuisClient ? "Vous étiez le client"
                                                       : "Vous étiez le prestataire"),
            x + 12, cy + 38, C_DIM, false);

        int cx2 = x + w / 2 + 10;
        ctx.drawText(textRenderer, "§7Prix : §6" + c.prix() + " ◆", cx2, cy + 10, C_GOLD, false);
        ctx.drawText(textRenderer, "§8Acompte versé : " + c.acompte() + " ◆", cx2, cy + 24, C_DIM, false);
        ctx.drawText(textRenderer, "§8Commandée le " + dateComplete(c.creeLe()), cx2, cy + 38, C_DIM, false);
        ctx.drawText(textRenderer, "§8" + (annulee ? "Annulée le " : "Terminée le ")
            + dateComplete(c.termineeLe()), cx2, cy + 50, C_DIM, false);
        cy += ficheH + 6;

        // ── L'avis, s'il y en a un ──
        if (c.note() > 0) {
            List<String> lignes = c.avis().isBlank() ? List.of() : decouper(c.avis(), w - 24);
            int avisH = 26 + lignes.size() * 11;
            ctx.fill(x, cy, x + w, cy + avisH, C_SURFACE);
            ctx.fill(x, cy, x + 3, cy + avisH, C_GOLD);
            String note = etoiles(c.note()) + " §8· laissé par " + c.client()
                + " le " + dateComplete(c.termineeLe());
            ctx.drawText(textRenderer, note, x + 12, cy + 8, C_GOLD, false);
            int ay = cy + 22;
            for (String l : lignes) {
                ctx.drawText(textRenderer, "§f" + l, x + 12, ay, C_WHITE, false);
                ay += 11;
            }
            cy += avisH + 6;
        } else {
            ctx.drawText(textRenderer, "§8Aucun avis laissé sur cette prestation.", x + 12, cy + 2, C_DIM, false);
            cy += 16;
        }

        // ── La conversation, en lecture seule ──
        int zoneH = py + ph - PAD - cy;
        if (zoneH < 40) return;
        ctx.fill(x, cy, x + w, cy + zoneH, C_PANEL);
        ctx.drawText(textRenderer, "CONVERSATION (" + c.messages().size() + ")", x + 12, cy + 6, C_DIM, false);

        List<MessageData> msgs = c.messages();
        if (msgs.isEmpty()) {
            centre(ctx, "Aucun message échangé.", x + w / 2, cy + zoneH / 2, C_DIM);
            return;
        }

        // Du plus récent vers le haut, comme le chat vivant ; la molette remonte
        int haut = cy + 20;
        int y = cy + zoneH - 6;
        int ignores = scrollArchive;
        ctx.enableScissor(x, haut, x + w, cy + zoneH);
        for (int i = msgs.size() - 1; i >= 0; i--) {
            MessageData m = msgs.get(i);
            if (ignores > 0) { ignores--; continue; }
            boolean deMoi = m.auteur().equalsIgnoreCase(moi());
            List<String> lignes = decouper(m.texte(), w - 60);
            int blocH = lignes.size() * 11 + 22;
            y -= blocH + 3;
            if (y < haut - blocH) break;

            int bulleW = textRenderer.getWidth(m.auteur() + "  " + dateCourte(m.envoyeLe()));
            for (String l : lignes) bulleW = Math.max(bulleW, textRenderer.getWidth(l));
            bulleW = Math.min(w - 40, bulleW + 16);
            int bx = deMoi ? x + w - bulleW - 10 : x + 10;

            ctx.fill(bx, y, bx + bulleW, y + blocH, deMoi ? 0xFF23343F : C_SURFACE);
            ctx.fill(bx, y, bx + 2, y + blocH, deMoi ? C_BLUE : C_GOLD);
            ctx.drawText(textRenderer, "§8" + m.auteur() + "  " + dateCourte(m.envoyeLe()),
                bx + 8, y + 4, C_DIM, false);
            int ly = y + 16;
            for (String l : lignes) {
                ctx.drawText(textRenderer, "§f" + l, bx + 8, ly, C_WHITE, false);
                ly += 11;
            }
        }
        ctx.disableScissor();
        maxScroll = Math.max(0, msgs.size() - 1);
    }

    /** « 19 août 2026 » — l'année compte dans une archive. */
    private String dateComplete(long ms) {
        if (ms <= 0) return "—";
        return new java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRENCH)
            .format(new java.util.Date(ms));
    }

    /** « 19/08 14:32 » — pour horodater un message sans encombrer la bulle. */
    private String dateCourte(long ms) {
        if (ms <= 0) return "";
        return new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(ms));
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    private void renderChat(DrawContext ctx, int mx, int my, int cy, int ch) {
        parquerChamps(fMessage);
        CommandeData c = chatOuvert;
        if (c == null) { chatOuvert = null; return; }
        int x = px + PAD, w = pw - PAD * 2;

        renderBoutonRetour(ctx, mx, my, x, cy + 8);
        String titre = c.titre() + " — avec "
            + (c.client().equalsIgnoreCase(moi()) ? c.prestataire() : c.client());
        ctx.drawText(textRenderer, tronquer(titre, w - 140), x + 90, cy + 13, C_WHITE, false);
        cy += 32;

        int zoneH = ch - 32 - 40;
        ctx.fill(x, cy, x + w, cy + zoneH, C_PANEL);

        // Messages du plus ancien au plus récent, ancrés en bas
        List<MessageData> msgs = c.messages();
        int y = cy + zoneH - 8;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            MessageData m = msgs.get(i);
            boolean deMoi = m.auteur().equalsIgnoreCase(moi());
            List<String> lignes = decouper(m.texte(), w - 40);
            int blocH = lignes.size() * 11 + 12;
            y -= blocH + 3;
            if (y < cy + 4) break;

            // La largeur doit tenir compte du pseudo, écrit au-dessus du texte :
            // ne mesurer que les lignes du message le laissait déborder de la bulle.
            int bulleW = textRenderer.getWidth(m.auteur());
            for (String l : lignes) bulleW = Math.max(bulleW, textRenderer.getWidth(l));
            bulleW = Math.min(w - 40, bulleW + 16);
            int bx = deMoi ? x + w - bulleW - 10 : x + 10;

            ctx.fill(bx, y, bx + bulleW, y + blocH, deMoi ? 0xFF23343F : C_SURFACE);
            ctx.fill(bx, y, bx + 2, y + blocH, deMoi ? C_BLUE : C_GOLD);
            ctx.drawText(textRenderer, "§8" + m.auteur(), bx + 8, y + 3, C_DIM, false);
            int ly = y + 14;
            for (String l : lignes) {
                ctx.drawText(textRenderer, "§f" + l, bx + 8, ly, C_WHITE, false);
                ly += 11;
            }
        }
        if (msgs.isEmpty())
            centre(ctx, "Aucun message — dites bonjour !",
                x + w / 2, cy + zoneH / 2, C_DIM);

        // Saisie
        int sy = cy + zoneH + 6;
        ctx.fill(x, sy, x + w - 80, sy + 22, C_SURFACE);
        if (fMessage != null) {
            fMessage.setX(x + 8);
            fMessage.setY(sy + 7);
            fMessage.setWidth(w - 100);
            fMessage.render(ctx, mx, my, 0);
        }
        int envX = x + w - 74;
        boolean envHov = mx >= envX && mx < envX + 74 && my >= sy && my < sy + 22;
        ctx.fill(envX, sy, envX + 74, sy + 22, envHov ? 0xFF1A8050 : C_GREEN);
        centre(ctx, "Envoyer", envX + 37, sy + 7, C_WHITE);
        bounds.add(new int[]{envX, sy, 74, 22, 102, c.id()});
    }

    // ── Validation avec note ──────────────────────────────────────────────────

    private void renderValidation(DrawContext ctx, int mx, int my, int cy, int ch) {
        parquerChamps(fAvis);
        CommandeData c = validationEnCours;
        if (c == null) return;
        int w = 360, h = 190;
        int x = px + (pw - w) / 2, y = py + (ph - h) / 2;

        ctx.fill(px, py + TOP_H, px + pw, py + ph, 0x99000000);
        ctx.fill(x, y, x + w, y + h, C_SURFACE);
        cadre(ctx, x, y, w, h, C_GREEN);

        ctx.drawText(textRenderer, "VALIDER LA PRESTATION", x + PAD, y + 10, C_GREEN, false);
        ctx.drawText(textRenderer, tronquer(c.titre(), w - 24), x + PAD, y + 26, C_WHITE, false);
        ctx.drawText(textRenderer, "§7Solde à verser : §6" + c.sequestre() + " ◆", x + PAD, y + 40, C_MID, false);

        ctx.drawText(textRenderer, "Votre note :", x + PAD, y + 60, C_DIM, false);
        int ex = x + PAD + 70;
        for (int i = 1; i <= 5; i++) {
            boolean hov = mx >= ex && mx < ex + 16 && my >= y + 56 && my < y + 72;
            boolean plein = i <= noteChoisie;
            ctx.drawText(textRenderer, plein ? "★" : "☆", ex + 3, y + 60,
                plein || hov ? C_GOLD : C_DIM, false);
            bounds.add(new int[]{ex, y + 56, 16, 16, 103, i});
            ex += 16;
        }
        if (noteChoisie == 0)
            ctx.drawText(textRenderer, "§8(facultatif)", ex + 8, y + 60, C_DIM, false);

        ctx.fill(x + PAD, y + 84, x + w - PAD, y + 104, C_BG);
        if (fAvis != null) {
            fAvis.setX(x + PAD + 6);
            fAvis.setY(y + 90);
            fAvis.setWidth(w - PAD * 2 - 12);
            fAvis.render(ctx, mx, my, 0);
        }

        int by = y + h - 34;
        int bw = (w - PAD * 3) / 2;
        boolean annHov = mx >= x + PAD && mx < x + PAD + bw && my >= by && my < by + 24;
        ctx.fill(x + PAD, by, x + PAD + bw, by + 24, annHov ? C_HOVER : C_BORDER);
        centre(ctx, "Annuler", x + PAD + bw / 2, by + 8, C_MID);
        bounds.add(new int[]{x + PAD, by, bw, 24, 104, 0});

        int vx = x + w - PAD - bw;
        boolean vHov = mx >= vx && mx < vx + bw && my >= by && my < by + 24;
        ctx.fill(vx, by, vx + bw, by + 24, vHov ? 0xFF1A8050 : C_GREEN);
        centre(ctx, "Valider et payer", vx + bw / 2, by + 8, C_WHITE);
        bounds.add(new int[]{vx, by, bw, 24, 105, c.id()});
    }

    // ── Formulaire de publication ─────────────────────────────────────────────

    private void renderFormulaire(DrawContext ctx, int mx, int my, int cy, int ch) {
        int x = px + PAD, w = pw - PAD * 2;
        parquerChamps(fTitre, fDescription, fImage, fNouvelleCategorie);
        categoriesCliquables.clear();
        renderBoutonRetour(ctx, mx, my, x, cy + 8);
        ctx.drawText(textRenderer, "PUBLIER UNE ANNONCE", x + 90, cy + 13, C_GOLD, false);
        cy += 34;

        int gauche = x, largeurG = w / 2 - 8;

        cy = champLabel(ctx, mx, my, "TITRE", fTitre, gauche, cy, largeurG);
        cy = champLabel(ctx, mx, my, "DESCRIPTION", fDescription, gauche, cy, largeurG);
        int cyApresColonne = cy;

        // Colonne droite : prix, contact, catégorie
        int droite = x + w / 2 + 8, largeurD = w / 2 - 8;
        int dy = py + TOP_H + 44;
        ctx.drawText(textRenderer, "PRIX DE LA PRESTATION", droite, dy, C_DIM, false);
        dy += 12;
        fPrix.render(ctx, textRenderer, droite, dy, largeurD, mx, my);
        dy += NumberInput.H + 8;

        ctx.drawText(textRenderer, "COMMENT VOUS CONTACTER", droite, dy, C_DIM, false);
        dy += 14;
        int cx = droite;
        for (int i = 0; i < ServiceManager.CONTACTS.length; i++) {
            String s = ServiceManager.CONTACTS[i];
            int cw = textRenderer.getWidth(s) + 16;
            boolean actif = fContact == i;
            boolean hov = mx >= cx && mx < cx + cw && my >= dy && my < dy + 18;
            ctx.fill(cx, dy, cx + cw, dy + 18, actif ? C_GOLD : (hov ? C_HOVER : C_SURFACE));
            centre(ctx, s, cx + cw / 2, dy + 5, actif ? C_BG : C_MID);
            bounds.add(new int[]{cx, dy, cw, 18, 106, i});
            cx += cw + 4;
        }
        dy += 26;

        // Catégories : celles déjà utilisées sur le serveur, plus un champ pour en
        // créer une nouvelle. Ce sont les joueurs qui font vivre cette liste.
        ctx.drawText(textRenderer, "CATÉGORIE", droite, dy, C_DIM, false);
        dy += 14;
        int gx = droite, gy = dy;
        for (String c : categories) {
            int cw = textRenderer.getWidth(c) + 14;
            if (gx + cw > droite + largeurD) { gx = droite; gy += 22; }
            boolean actif = c.equalsIgnoreCase(fCategorie);
            boolean hov = mx >= gx && mx < gx + cw && my >= gy && my < gy + 18;
            ctx.fill(gx, gy, gx + cw, gy + 18, actif ? couleurCategorie(c) : (hov ? C_HOVER : C_SURFACE));
            centre(ctx, c, gx + cw / 2, gy + 5, actif ? C_BG : C_MID);
            categoriesCliquables.add(c);
            bounds.add(new int[]{gx, gy, cw, 18, 107, categoriesCliquables.size() - 1});
            gx += cw + 4;
        }
        if (categories.isEmpty()) {
            ctx.drawText(textRenderer, "§8Aucune encore — créez la première", droite, gy + 4, C_DIM, false);
        }
        gy += 24;

        ctx.drawText(textRenderer, "§8ou créez-en une :", droite, gy, C_DIM, false);
        gy += 11;
        ctx.fill(droite, gy, droite + largeurD, gy + 18, C_BG);
        cadre(ctx, droite, gy, largeurD, 18, fNouvelleCategorie.isFocused() ? C_GOLD : C_BORDER);
        fNouvelleCategorie.setX(droite + 6);
        fNouvelleCategorie.setY(gy + 5);
        fNouvelleCategorie.setWidth(largeurD - 12);
        fNouvelleCategorie.render(ctx, mx, my, 0);
        gy += 24;

        // Le champ libre prend le pas sur la sélection : taper une catégorie est un
        // choix plus explicite que la pastille restée active.
        String saisie = ServiceManager.normaliserCategorie(fNouvelleCategorie.getText());
        String categorieRetenue = !saisie.isEmpty() ? saisie : fCategorie;

        // Image : de retour en colonne gauche, taille normale. Seule sa longueur
        // maximale comptait — un lien Discord fait ~200 caractères, le champ était
        // resté au défaut de 32.
        int iy = cyApresColonne;
        iy = champLabel(ctx, mx, my, "IMAGE (facultatif)", fImage, gauche, iy, largeurG);
        for (String ligne : decouper("Lien direct " + ServiceImages.hotesLisibles()
                + " (" + ServiceImages.extensionsLisibles() + ")", largeurG)) {
            ctx.drawText(textRenderer, "§8" + ligne, gauche, iy, C_DIM, false);
            iy += 10;
        }

        // Taxe + publier
        int prix = fPrix.getValue();
        int taxe = ServiceManager.taxePublication(Math.max(1, prix));
        int by = py + ph - PAD - 26;
        ctx.drawText(textRenderer, "§7Taxe de publication : §6" + taxe + " ◆ §8(reversée au serveur)",
            x, by + 8, C_MID, false);

        boolean ok = !fTitre.getText().isBlank() && !fDescription.getText().isBlank()
                  && prix > 0 && balance >= taxe && !categorieRetenue.isEmpty();
        String lbl = balance < taxe ? "Taxe insuffisante"
                   : categorieRetenue.isEmpty() ? "Choisis une catégorie" : "Publier";
        int bw = 150;
        int bx = px + pw - PAD - bw;
        boolean hov = ok && mx >= bx && mx < bx + bw && my >= by && my < by + 24;
        ctx.fill(bx, by, bx + bw, by + 24, ok ? (hov ? 0xFF1A8050 : C_GREEN) : C_SURFACE);
        centre(ctx, lbl, bx + bw / 2, by + 8, ok ? C_WHITE : C_DIM);
        if (ok) bounds.add(new int[]{bx, by, bw, 24, 108, 0});
    }

    private int champLabel(DrawContext ctx, int mx, int my, String label,
                           TextFieldWidget f, int x, int y, int w) {
        ctx.drawText(textRenderer, label, x, y, C_DIM, false);
        y += 12;
        ctx.fill(x, y, x + w, y + 20, C_BG);
        cadre(ctx, x, y, w, 20, f.isFocused() ? C_GOLD : C_BORDER);
        f.setX(x + 6);
        f.setY(y + 6);
        f.setWidth(w - 12);
        f.render(ctx, mx, my, 0);
        return y + 28;
    }

    // ── Communs ───────────────────────────────────────────────────────────────

    private void renderScrollbar(DrawContext ctx, int trackX, int trackY, int trackH,
                                 int visUnits, int total) {
        if (total <= visUnits) return;
        ctx.fill(trackX, trackY, trackX + 6, trackY + trackH, C_BORDER);
        int thumbH = Math.max(18, trackH * visUnits / total);
        int thumbY = trackY + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
        ctx.fill(trackX, thumbY, trackX + 6, thumbY + thumbH, C_GOLD);
    }

    private void renderToast(DrawContext ctx) {
        if (toastMsg == null) return;
        if (System.currentTimeMillis() > toastEnd) { toastMsg = null; return; }
        int tw = textRenderer.getWidth(toastMsg) + 28;
        int th = 26;
        int tx = px + pw - tw - 12;
        int ty = py + ph - th - 12;
        ctx.fill(tx, ty, tx + tw, ty + th, C_SURFACE);
        ctx.fill(tx, ty, tx + 3, ty + th, toastOk ? C_GREEN : C_RED);
        ctx.drawText(textRenderer, toastMsg, tx + 11, ty + (th - textRenderer.fontHeight) / 2, C_WHITE, false);
    }

    private String etoiles(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) sb.append(i <= n ? "★" : "☆");
        return sb.toString();
    }

    /** « il y a 5 jours », « aujourd'hui » — l'ancienneté parle plus qu'une date. */
    private String ancienntete(long ms) {
        long d = System.currentTimeMillis() - ms;
        long minutes = d / 60_000;
        if (minutes < 1)  return "à l'instant";
        if (minutes < 60) return "il y a " + minutes + " min";
        long heures = minutes / 60;
        if (heures < 24)  return "il y a " + heures + " h";
        long jours = heures / 24;
        return jours == 1 ? "hier" : "il y a " + jours + " jours";
    }

    private String tronquer(String s, int maxPx) {
        if (s == null) return "";
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private List<String> decouper(String texte, int maxPx) {
        List<String> out = new ArrayList<>();
        if (texte == null || texte.isBlank()) return out;
        StringBuilder ligne = new StringBuilder();
        for (String mot : texte.split("\\s+")) {
            String essai = ligne.isEmpty() ? mot : ligne + " " + mot;
            if (textRenderer.getWidth(essai) > maxPx && !ligne.isEmpty()) {
                out.add(ligne.toString());
                ligne = new StringBuilder(mot);
            } else {
                ligne = new StringBuilder(essai);
            }
        }
        if (!ligne.isEmpty()) out.add(ligne.toString());
        return out;
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;
        if (x < px || x > px + pw || y < py || y > py + ph) { close(); return true; }

        if (HubBackButton.clicked(px + PAD, py + (TITRE_H - HubBackButton.H) / 2, x, y)) return true;

        // Onglets : même répartition qu'au rendu (largeur égale sur toute la fenêtre)
        int tabY = py + TITRE_H;
        if (y >= tabY && y < tabY + ONGLET_H) {
            int tabW = (pw - PAD * 2) / Tab.values().length;
            int idx  = (x - tabsStartX) / Math.max(1, tabW);
            if (idx >= 0 && idx < Tab.values().length) {
                tab = Tab.values()[idx];
                scroll = 0; detail = null; chatOuvert = null;
                formOuvert = false; validationEnCours = null;
            }
            return true;
        }
        if (y < tabY) return true;   // reste de l'en-tête : non cliquable

        // Champs texte d'abord : sinon ils ne prennent jamais le focus clavier
        if (super.mouseClicked(mx0, my0, btn)) return true;
        if (formOuvert && fPrix.mouseClicked(x, y)) return true;

        // Bounds enregistrés au rendu (le premier inscrit gagne : boutons avant lignes)
        for (int[] b : bounds) {
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                gererClic(b[4], b[5]);
                return true;
            }
        }
        return true;
    }

    private void gererClic(int action, int id) {
        switch (action) {
            case CLIC_ANNONCE -> annonces.stream().filter(a -> a.id() == id).findFirst()
                .ifPresent(a -> detail = a);
            case CLIC_COMMANDER -> envoyer(ServiceNetworking.ACTION_COMMANDER, "", "", "", "", "", id, 0);
            case CLIC_RETIRER   -> envoyer(ServiceNetworking.ACTION_RETIRER, "", "", "", "", "", id, 0);
            case CLIC_LIVREE    -> envoyer(ServiceNetworking.ACTION_LIVREE, "", "", "", "", "", id, 0);
            case CLIC_ANNULER   -> envoyer(ServiceNetworking.ACTION_ANNULER, "", "", "", "", "", id, 0);
            case CLIC_VALIDER   -> {
                validationEnCours = retrouver(id);
                noteChoisie = 0;
                if (fAvis != null) fAvis.setText("");
            }
            case CLIC_OUVRIR_CHAT -> { chatOuvert = retrouver(id); detail = null; }
            case CLIC_ARCHIVE -> {
                archives.stream().filter(a -> a.id() == id).findFirst()
                    .ifPresent(a -> { archiveOuverte = a; scrollArchive = 0; });
                detail = null; chatOuvert = null;
            }
            case CLIC_CATEGORIE -> {
                List<String> cats = filtresDisponibles();
                if (id < cats.size()) { categorieFiltre = cats.get(id); scroll = 0; }
            }
            case 100 -> { viderFormulaire(); formOuvert = true; detail = null; chatOuvert = null; }
            case 101 -> { detail = null; chatOuvert = null; formOuvert = false;
                          validationEnCours = null; archiveOuverte = null; }
            case 102 -> {
                if (fMessage != null && !fMessage.getText().isBlank())
                    envoyer(ServiceNetworking.ACTION_MESSAGE, fMessage.getText(), "", "", "", "", id, 0);
            }
            case 103 -> noteChoisie = id;
            case 104 -> validationEnCours = null;
            case 105 -> envoyer(ServiceNetworking.ACTION_VALIDER,
                fAvis != null ? fAvis.getText() : "", "", "", "", "", id, noteChoisie);
            case 106 -> fContact = id;
            case 107 -> {
                if (id < categoriesCliquables.size()) {
                    fCategorie = categoriesCliquables.get(id);
                    if (fNouvelleCategorie != null) fNouvelleCategorie.setText("");
                }
            }
            case 108 -> {
                String saisie = ServiceManager.normaliserCategorie(fNouvelleCategorie.getText());
                envoyer(ServiceNetworking.ACTION_PUBLIER,
                    fTitre.getText(), fDescription.getText(), fImage.getText(),
                    ServiceManager.CONTACTS[fContact],
                    !saisie.isEmpty() ? saisie : fCategorie,
                    fPrix.getValue(), 0);
            }
            default -> {}
        }
    }

    private void envoyer(int action, String s1, String s2, String s3, String s4, String s5,
                         int i1, int i2) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(action);
        buf.writeString(s1); buf.writeString(s2); buf.writeString(s3);
        buf.writeString(s4); buf.writeString(s5);
        buf.writeInt(i1); buf.writeInt(i2);
        NtNet.versServeur(ServiceNetworking.MARCHE_ACTION, buf);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double amount) {
        if (archiveOuverte != null) {
            scrollArchive = Math.max(0, Math.min(scrollArchive + (int) Math.signum(amount),
                Math.max(0, archiveOuverte.messages().size() - 1)));
            return true;
        }
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) {   // Échap ferme d'abord la vue courante
            if (validationEnCours != null) { validationEnCours = null; return true; }
            if (archiveOuverte != null)    { archiveOuverte = null; return true; }
            if (chatOuvert != null)        { chatOuvert = null; return true; }
            if (formOuvert)                { formOuvert = false; return true; }
            if (detail != null)            { detail = null; return true; }
        }
        // Entrée envoie le message quand le chat est ouvert
        if (key == 257 && chatOuvert != null && fMessage != null && fMessage.isFocused()
            && !fMessage.getText().isBlank()) {
            envoyer(ServiceNetworking.ACTION_MESSAGE, fMessage.getText(), "", "", "", "", chatOuvert.id(), 0);
            return true;
        }
        if (formOuvert && fPrix.keyPressed(key)) return true;
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char chr, int mod) {
        if (formOuvert && fPrix.charTyped(chr)) return true;
        return super.charTyped(chr, mod);
    }
}
