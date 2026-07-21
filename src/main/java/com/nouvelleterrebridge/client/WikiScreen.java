package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.NouvelleTerreBridgeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class WikiScreen extends Screen {

    // ── Couleurs ──────────────────────────────────────────────────────────────
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

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int MAX_PW = 560;
    private static final int MAX_PH = 360;
    private static final int NAV_W  = 130;
    private static final int PAD    = 12;
    private static final int BTN_H  = 22;
    private static final int LINE_H = 12;

    private int pw, ph, px, py;
    private int contentAreaX, contentAreaY, contentAreaW, contentAreaH;

    // ── Sections ──────────────────────────────────────────────────────────────

    private record Line(String text, String command) {}  // command = null si non cliquable
    private record Section(String title, String icon, List<Line> lines) {}

    private List<Section> sections;

    private void buildSections() {
        String hudKey = NouvelleTerreBridgeClient.hudKey != null
            ? NouvelleTerreBridgeClient.hudKey.getBoundKeyLocalizedText().getString()
            : "H";

        sections = List.of(
            new Section("Introduction", "📖", List.of(
                l("§fBienvenue sur §6§lNouvelle Terre§f !"),
                l(""),
                l("Ce mod ajoute un système économique complet"),
                l("au serveur Minecraft SMP RP."),
                l(""),
                l("§7Tu commences avec §e500 ◆ §7de départ."),
                l("Gagne des §e◆ Shards §7en jouant, en"),
                l("échangeant et en réalisant des quêtes.")
            )),
            new Section("L'or — Shards ◆", "◆", List.of(
                l("§e◆ Shards§f — la monnaie du serveur."),
                l(""),
                l("§7§lComment gagner des Shards :"),
                l("  §f· §7+500 ◆ offerts à la première connexion"),
                l("  §f· §7+25 ◆ de bonus quotidien (1re connexion du jour)"),
                l("  §f· §7+5 ◆ toutes les 30 min de jeu"),
                l("  §f· §7Récompenses de kills de mobs"),
                l("  §f· §7Récompenses de quêtes §e(/quetes)"),
                l("  §f· §7Quête communautaire du serveur"),
                l("  §f· §7Ventes au Marché §e(/hdv)"),
                l("  §f· §7Virements d'autres joueurs §e(/bank)"),
                l(""),
                l("§7§lComment dépenser des Shards :"),
                l("  §f· §7Acheter des objets au Marché §e(/hdv)"),
                l("  §f· §7Accepter des quêtes avec coût d'entrée"),
                l("  §f· §7Prêter via un crédit §e(/bank)"),
                l(""),
                l("§7§lMonnaie physique :"),
                l("  §f· §7Retire tes ◆ en §6Shards physiques §7:"),
                l("    §e/bank §7→ bouton §6Retirer en Shards ◆"),
                l("  §f· §7Échange-les de main à main, puis"),
                l("    §6clic droit §7pour les redéposer au compte"),
                l(""),
                l("§7§lSolde & historique :"),
                l("  §f· §7Solde affiché dans le HUD (touche §eH§7)"),
                l("  §f· §7Historique complet dans §e/bank §7→ Compte"),
                l(""),
                l("§c§lAttention : §7les pénalités de crédit"),
                l("§7peuvent rendre le solde §cnégatif§7."),
                l("  §f· §7Remboursez vos crédits à temps !")
            )),
            new Section("Marché (/hdv)", "🏪", List.of(
                l("§fOuvrir le marché :"),
                cmd("/hdv"),
                l(""),
                l("§7Achète et vends des objets entre joueurs."),
                l(""),
                l("§7§lOnglets :"),
                l("  §f· §e Marché       §7— annonces des joueurs"),
                l("  §f· §e Vendre       §7— créer une annonce"),
                l("  §f· §e Mon Shop     §7— gérer tes annonces"),
                l("  §f· §e Shop Serveur §7— achète la production"),
                l("  §f· §e Boutiques    §7— tri par vendeur"),
                l(""),
                l("§7Achat au meilleur prix automatique."),
                l("§7La quantité peut être fractionnée."),
                l(""),
                l("§7§lShop Serveur (🏛️) :"),
                l("  §f· §7Items de la production automatique"),
                l("  §f· §7Prix augmente avec les ventes (+10% par 64 ventes)"),
                l("  §f· §7Stock illimité, progression : §e/production"),
                l(""),
                l("§7Clique §e◆ Solde §7en haut à droite"),
                l("§7pour ouvrir la Banque rapidement.")
            )),
            new Section("Banque (/bank)", "🏦", List.of(
                l("§fOuvrir la banque :"),
                cmd("/bank"),
                l(""),
                l("§7§lOnglets :"),
                l("  §f· §e Compte      §7— solde & historique"),
                l("  §f· §e Économie    §7— stats du serveur"),
                l("  §f· §e Classement  §7— top joueurs par solde"),
                l("  §f· §e Crédits     §7— prêts entre joueurs"),
                l("  §f· §e Virements   §7— transferts récurrents"),
                l(""),
                l("§7§lCrédits :"),
                l("  §f· §7Le §epreteur§7 propose un crédit"),
                l("  §f· §7L'§eemprunteur§7 doit accepter — rien ne"),
                l("    §7se fait sans son accord"),
                l("  §f· §7Transfert des fonds à l'acceptation"),
                l("  §f· §7Pénalité de retard : §caugmente §c+5 ◆/j"),
                l("  §f· §7Le solde peut devenir §cnégatif §7(pénalités)")
            )),
            new Section("Quêtes (/quetes)", "⚔", List.of(
                l("§fOuvrir les quêtes :"),
                cmd("/quetes"),
                l(""),
                l("§7Accomplis des quêtes pour gagner Shards,"),
                l("§7objets et XP de niveau."),
                l(""),
                l("§7§lTypes :"),
                l("  §c· KILL     §7— éliminer des mobs"),
                l("  §a· HARVEST  §7— récolter des blocs"),
                l("  §b· DELIVERY §7— livrer des objets"),
                l(""),
                l("§7§lJournalières : §f3 quêtes par jour"),
                l("§7(facile / moyen / difficile), gratuites,"),
                l("§7remplacées chaque nuit à §e00h§7."),
                l(""),
                l("§7§lQuête du serveur : §fobjectif commun,"),
                l("§7tout le monde y contribue automatiquement."),
                l("§7Récompense pour §echaque§7 participant !"),
                l(""),
                l("§7§lRécompenses : §fversées automatiquement"),
                l("§7à 100 %. Les objets vont dans 'À Réclamer'"),
                l("§7(livrés d'office à minuit, convertis en ◆"),
                l("§7si l'inventaire est plein)."),
                l(""),
                l("§7§lGroupe : §fquêtes à plusieurs joueurs."),
                l("§7Les actions de tous les participants"),
                l("§7avancent la progression commune.")
            )),
            new Section("Production", "⛏", List.of(
                l("§fVoir la production du serveur :"),
                cmd("/production"),
                l(""),
                l("§7Chaque bloc miné, récolte ou craft compte"),
                l("§7dans la production naturelle du serveur."),
                l(""),
                l("§7Quand un item atteint son §eseuil§7, il est"),
                l("§7mis en vente par §e$Serveur §7au §e/hdv"),
                l("§7avec un §astock illimité§7."),
                l(""),
                l("§7Le GUI montre chaque item avec sa barre"),
                l("§7de progression et son statut de vente.")
            )),
            new Section("Roleplay", "🎭", List.of(
                l("§7§lRegistre des personnages :"),
                cmd("/registre"),
                l("  §f· §7Liste des personnages RP du serveur"),
                l("  §f· §7Point vert = joueur en ligne"),
                l("  §f· §7Clique un personnage pour sa fiche"),
                l("    §7complète (métier, histoire, traits...)"),
                l(""),
                l("§7§lConflits RP :"),
                cmd("/conflit"),
                l("  §f· §7Ouvre le GUI : choisis le joueur,"),
                l("    §7écris la raison, déclare le conflit"),
                l("  §f· §7Le Conseil des Fondateurs est alerté"),
                l(""),
                l("§7§lNom RP : §fton nom de personnage s'affiche"),
                l("§7dans le chat et la liste des joueurs (Tab)."),
                l("§7Il est lié à ton personnage Discord validé.")
            )),
            new Section("Commandes", "⌨", List.of(
                l("§7§lToutes les commandes disponibles :"),
                l(""),
                cmd("/hdv"),
                l("  §7Marché — acheter et vendre entre joueurs"),
                cmd("/bank"),
                l("  §7Banque — compte, crédits, virements"),
                cmd("/quetes"),
                l("  §7Quêtes — journalières, groupe, classements"),
                cmd("/production"),
                l("  §7Production naturelle et shop $Serveur"),
                cmd("/registre"),
                l("  §7Registre des personnages RP"),
                cmd("/conflit"),
                l("  §7Déclarer un conflit RP"),
                cmd("/discord"),
                l("  §7Lier ton compte Minecraft ↔ Discord"),
                cmd("/wiki"),
                l("  §7Ce guide"),
                cmd("/economie bourse"),
                l("  §7Affiche ton solde dans le chat"),
                l(""),
                l("§7§lÉditeur HUD :"),
                l("  §fTouche §e" + hudKey + "§f pour ouvrir l'éditeur."),
                l("  §7(rebindable dans Contrôles → Nouvelle Terre)")
            )),
            new Section("Contact Admin", "✉", List.of(
                l("§fUn problème ou une question ?"),
                l(""),
                l("§7Envoie un message sur Discord à :"),
                l(""),
                l("§e§l  sacha.lxv"),
                l(""),
                l("§7Clique le bouton §fDiscord§7 ci-dessous"),
                l("§7pour copier le pseudo dans le presse-papier,"),
                l("§7puis ouvre Discord et envoie un message.")
            ))
        );
    }

    private static Line l(String text)           { return new Line(text, null); }
    private static Line cmd(String command)       { return new Line("§e" + command, command); }

    // ── State ─────────────────────────────────────────────────────────────────

    private int   selectedSection  = 0;
    private int   contentScrollY   = 0;
    private int   contentHeight    = 0;  // calculé par renderContent chaque frame

    private final List<int[]>    navBounds     = new ArrayList<>();
    private final List<Object[]> cmdBounds     = new ArrayList<>(); // [x,y,w,h,command]

    private int discordBtnX, discordBtnY, discordBtnW;

    // ── Constructor ───────────────────────────────────────────────────────────

    public WikiScreen() {
        super(Text.literal("Wiki"));
        buildSections();
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_PW, width  - 20);
        ph = Math.min(MAX_PH, height - 20);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        contentAreaX = px + NAV_W + PAD + 2;
        contentAreaY = py + 37 + PAD;
        contentAreaW = pw - NAV_W - PAD * 2 - 2;
        contentAreaH = ph - 37 - PAD * 2 - BTN_H - PAD - 1;

        // Recalcul du texte dynamique (keybinding peut changer si rebind)
        buildSections();
    }

    @Override public boolean shouldPause() { return false; }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Fond principal
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px, py, px + pw, py + 1, C_BORDER);
        ctx.fill(px, py + ph - 1, px + pw, py + ph, C_BORDER);
        ctx.fill(px, py, px + 1, py + ph, C_BORDER);
        ctx.fill(px + pw - 1, py, px + pw, py + ph, C_BORDER);

        // Header
        ctx.fill(px, py, px + pw, py + 36, C_PANEL);
        ctx.fill(px, py + 36, px + pw, py + 37, C_BORDER);
        ctx.drawText(textRenderer, "📖  Wiki — Nouvelle Terre", px + PAD, py + 8, C_GOLD, false);
        ctx.drawText(textRenderer, "Guide du serveur SMP RP", px + PAD, py + 22, C_MID, false);

        // Séparateur nav | contenu
        ctx.fill(px + NAV_W, py + 37, px + NAV_W + 1, py + ph - 1, C_BORDER);

        renderNav(ctx, mx, my);
        renderContent(ctx, mx, my);

        // Bouton Discord (en bas du panneau nav)
        int btnW = NAV_W - PAD * 2;
        int btnX = px + PAD;
        int btnY = py + ph - BTN_H - PAD;
        discordBtnX = btnX; discordBtnY = btnY; discordBtnW = btnW;
        boolean bhov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + BTN_H;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + BTN_H, bhov ? 0xFF5865F2 : 0xFF3B4098);
        ctx.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFF7289DA);
        String dlbl = "Discord";
        ctx.drawText(textRenderer, dlbl, btnX + (btnW - textRenderer.getWidth(dlbl)) / 2, btnY + 7, C_WHITE, false);

        super.render(ctx, mx, my, delta);
    }

    private void renderNav(DrawContext ctx, int mx, int my) {
        navBounds.clear();
        int navX = px + PAD;
        int navY = py + 37 + PAD;
        int navW = NAV_W - PAD * 2;

        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            boolean sel = i == selectedSection;
            boolean hov = !sel && mx >= navX && mx < navX + navW && my >= navY && my < navY + 20;
            ctx.fill(navX, navY, navX + navW, navY + 20, sel ? C_SURFACE : (hov ? C_HOVER : 0));
            if (sel) ctx.fill(navX, navY, navX + 3, navY + 20, C_GOLD);
            String lbl = s.icon() + " " + s.title();
            // Tronquer si trop long pour la nav
            while (textRenderer.getWidth(lbl) > navW - 8 && lbl.length() > 4)
                lbl = lbl.substring(0, lbl.length() - 1);
            ctx.drawText(textRenderer, lbl, navX + (sel ? 8 : 5), navY + 6, sel ? C_GOLD : C_MID, false);
            navBounds.add(new int[]{navX, navY, navW, 20, i});
            navY += 22;
        }
    }

    private void renderContent(DrawContext ctx, int mx, int my) {
        if (sections == null || selectedSection >= sections.size()) return;
        Section s = sections.get(selectedSection);

        ctx.enableScissor(contentAreaX, contentAreaY, contentAreaX + contentAreaW, contentAreaY + contentAreaH);
        cmdBounds.clear();

        int x = contentAreaX;
        int y = contentAreaY - contentScrollY;

        // Titre de section
        ctx.drawText(textRenderer, "§f§l" + s.icon() + "  " + s.title(), x, y, C_GOLD, false);
        y += 18;
        ctx.fill(x, y, x + contentAreaW, y + 1, C_BORDER);
        y += 6;

        for (Line line : s.lines()) {
            if (line.text().isEmpty()) { y += 6; continue; }

            if (line.command() != null) {
                // y est déjà en coordonnées écran (contentAreaY - contentScrollY + offset_accumulé)
                boolean cmdHov = mx >= x && mx < x + contentAreaW && my >= y && my < y + LINE_H + 2;
                if (cmdHov) ctx.fill(x, y - 1, x + contentAreaW, y + LINE_H + 1, C_HOVER);
                ctx.drawText(textRenderer, line.text(), x + 4, y + 1, cmdHov ? C_GOLD : 0xFFE8A838, false);
                if (cmdHov) {
                    int tw = textRenderer.getWidth(line.text()) + 8;
                    ctx.drawText(textRenderer, "↵", x + tw + 2, y + 1, 0xFF5BA8D4, false);
                }
                cmdBounds.add(new Object[]{y, LINE_H + 2, line.command()});
            } else {
                ctx.drawText(textRenderer, line.text(), x, y, C_WHITE, false);
            }
            y += LINE_H;
        }

        ctx.disableScissor();

        // Calculer la hauteur totale du contenu (sans scroll)
        contentHeight = (y + contentScrollY) - contentAreaY;
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;

        // Bouton Discord → copier le pseudo dans le presse-papier + toast
        if (imx >= discordBtnX && imx < discordBtnX + discordBtnW
                && imy >= discordBtnY && imy < discordBtnY + BTN_H) {
            assert client != null;
            client.keyboard.setClipboard("sacha.lxv");
            NotificationHud.push(0xFF5865F2, "§bsacha.lxv §7copié !", "Ouvre Discord et envoie un message.");
            return true;
        }

        // Nav
        for (int[] b : navBounds) {
            if (imx >= b[0] && imx < b[0] + b[2] && imy >= b[1] && imy < b[1] + b[3]) {
                selectedSection = b[4]; contentScrollY = 0; return true;
            }
        }

        // Commandes cliquables (ouvre ChatScreen pré-rempli)
        if (imx >= contentAreaX && imx < contentAreaX + contentAreaW
                && imy >= contentAreaY && imy < contentAreaY + contentAreaH) {
            for (Object[] cb : cmdBounds) {
                int screenY = (int) cb[0], h = (int) cb[1];
                String cmd   = (String) cb[2];
                if (imy >= screenY && imy < screenY + h) {
                    assert client != null;
                    client.setScreen(new ChatScreen(cmd));
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= contentAreaX) {
            int maxScroll = Math.max(0, contentHeight - contentAreaH + 10);
            contentScrollY = Math.max(0, Math.min(contentScrollY - (int)(amount * 12), maxScroll));
        }
        return true;
    }
}
