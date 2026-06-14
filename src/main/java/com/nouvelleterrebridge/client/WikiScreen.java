package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

@Environment(EnvType.CLIENT)
public class WikiScreen extends Screen {

    // ── Couleurs (identiques aux autres screens) ──────────────────────────────
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
    private static final int C_RED     = 0xFFBF2040;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int MAX_PW  = 560;
    private static final int MAX_PH  = 360;
    private static final int NAV_W   = 130;
    private static final int PAD     = 12;
    private static final int BTN_H   = 22;

    private int pw, ph, px, py;

    // ── Sections ──────────────────────────────────────────────────────────────

    private record Section(String title, String icon, List<String> lines) {}

    private static final List<Section> SECTIONS = List.of(
        new Section("Introduction", "📖", List.of(
            "§fBienvenue sur §6§lNouvelle Terre§f !",
            "",
            "Ce mod ajoute un système économique complet",
            "au serveur Minecraft SMP RP.",
            "",
            "§7Tu commences avec §e500 ◆ §7de départ.",
            "Gagne des §e◆ Shards§7 en jouant,",
            "en échangeant et en réalisant des quêtes."
        )),
        new Section("Économie", "◆", List.of(
            "§e◆ Shards§f — la monnaie du serveur.",
            "",
            "§7Gagner des Shards :",
            "  §f· §7+5 ◆ toutes les 30 min de jeu",
            "  §f· §7Récompenses de kills",
            "  §f· §7Compléter des quêtes",
            "  §f· §7Vendre des objets au Marché",
            "",
            "§7Consulter ton solde : §f/economie bourse"
        )),
        new Section("Marché (/hdv)", "🏪", List.of(
            "§fCommande : §e/hdv",
            "",
            "§7Achète et vends des objets entre joueurs.",
            "",
            "§7Onglets disponibles :",
            "  §f· §7Marché — parcourir les annonces",
            "  §f· §7Vendre — créer une annonce",
            "  §f· §7Mon Shop — gérer tes annonces",
            "  §f· §7Boutiques — tri par vendeur",
            "",
            "§7Le prix est fixé par le vendeur.",
            "§7Achat au meilleur prix automatique."
        )),
        new Section("Banque (/bank)", "🏦", List.of(
            "§fCommande : §e/bank",
            "",
            "§7Onglets disponibles :",
            "  §f· §7Compte — solde & historique",
            "  §f· §7Économie — stats serveur",
            "  §f· §7Classement — top joueurs",
            "  §f· §7Crédits — prêts entre joueurs",
            "  §f· §7Virements — transferts récurrents",
            "",
            "§7Les pénalités augmentent de §c5 ◆/j",
            "§7passé la date d'échéance d'un crédit."
        )),
        new Section("Quêtes (/quetes)", "⚔", List.of(
            "§fCommande : §e/quetes",
            "",
            "§7Accomplis des quêtes pour gagner",
            "§7des Shards, objets et XP.",
            "",
            "§7Types de quêtes :",
            "  §f· §cKILL §7— éliminer des mobs",
            "  §f· §aHARVEST §7— récolter des blocs",
            "  §f· §bDELIVERY §7— livrer des objets",
            "",
            "§7Certaines quêtes sont en §bGROUPE§7.",
            "§7Tu montes en niveau en complétant des quêtes."
        )),
        new Section("Commandes", "⌨", List.of(
            "§e/hdv          §7— Marché des joueurs",
            "§e/bank         §7— Banque & virements",
            "§e/quetes       §7— Interface des quêtes",
            "§e/wiki         §7— Ce guide",
            "§e/discord      §7— Lier ton compte Discord",
            "§e/conflit      §7— Déclarer un conflit RP",
            "§e/economie bourse §7— Voir ton solde",
            "",
            "§8Appuie sur §7H §8pour l'éditeur HUD."
        )),
        new Section("Contact Admin", "✉", List.of(
            "§fUn problème ou une question ?",
            "",
            "§7Contacte l'administrateur du serveur",
            "§7directement sur Discord :",
            "",
            "§e§l  sacha.lxv",
            "",
            "§7Clique le bouton §fDiscord§7 ci-dessous",
            "§7pour ouvrir Discord et m'envoyer un message."
        ))
    );

    // ── State ─────────────────────────────────────────────────────────────────

    private int selectedSection = 0;
    private int contentScrollY  = 0;
    private final List<int[]> navBounds = new java.util.ArrayList<>();

    // ── Bouton Discord ────────────────────────────────────────────────────────
    private int discordBtnX, discordBtnY, discordBtnW;

    // ── Constructor ───────────────────────────────────────────────────────────

    public WikiScreen() { super(Text.literal("Wiki")); }

    @Override
    protected void init() {
        pw = Math.min(MAX_PW, width  - 20);
        ph = Math.min(MAX_PH, height - 20);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;
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
        int navRight = px + NAV_W;
        ctx.fill(navRight, py + 37, navRight + 1, py + ph - 1, C_BORDER);

        // Nav gauche
        renderNav(ctx, mx, my);

        // Contenu droit
        renderContent(ctx, mx, my);

        // Bouton Discord (bas du panneau de navigation)
        int btnY = py + ph - BTN_H - PAD;
        int btnW = NAV_W - PAD * 2;
        int btnX = px + PAD;
        discordBtnX = btnX;
        discordBtnY = btnY;
        discordBtnW = btnW;
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

        for (int i = 0; i < SECTIONS.size(); i++) {
            Section s = SECTIONS.get(i);
            boolean sel = i == selectedSection;
            boolean hov = !sel && mx >= navX && mx < navX + navW && my >= navY && my < navY + 20;
            ctx.fill(navX, navY, navX + navW, navY + 20, sel ? C_SURFACE : (hov ? C_HOVER : 0));
            if (sel) ctx.fill(navX, navY, navX + 3, navY + 20, C_GOLD);
            String lbl = s.icon() + " " + s.title();
            ctx.drawText(textRenderer, lbl, navX + (sel ? 8 : 5), navY + 6, sel ? C_GOLD : C_MID, false);
            navBounds.add(new int[]{navX, navY, navW, 20, i});
            navY += 22;
        }
    }

    private void renderContent(DrawContext ctx, int mx, int my) {
        Section s = SECTIONS.get(selectedSection);
        int cx  = px + NAV_W + PAD + 2;
        int cy  = py + 37 + PAD;
        int cw  = pw - NAV_W - PAD * 2 - 2;
        int ch  = ph - 37 - PAD * 2 - 1;

        // Scissor pour ne pas déborder
        ctx.enableScissor(cx, cy, cx + cw, cy + ch);

        int lineH = 12;
        int y = cy - contentScrollY;

        // Titre
        ctx.drawText(textRenderer, "§f§l" + s.icon() + "  " + s.title(), cx, y, C_GOLD, false);
        y += 18;
        ctx.fill(cx, y, cx + cw, y + 1, C_BORDER);
        y += 6;

        for (String line : s.lines()) {
            if (line.isEmpty()) { y += 6; continue; }
            ctx.drawText(textRenderer, line, cx, y, C_WHITE, false);
            y += lineH;
        }

        ctx.disableScissor();
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;

        // Bouton Discord
        if (imx >= discordBtnX && imx < discordBtnX + discordBtnW
                && imy >= discordBtnY && imy < discordBtnY + BTN_H) {
            // Copie le nom Discord dans le presse-papier et tente d'ouvrir Discord
            assert client != null;
            client.keyboard.setClipboard("sacha.lxv");
            // Ouvrir Discord via lien
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://discord.com/users/sacha.lxv"));
            } catch (Exception ignored) {}
            return true;
        }

        // Nav
        for (int[] b : navBounds) {
            if (imx >= b[0] && imx < b[0] + b[2] && imy >= b[1] && imy < b[1] + b[3]) {
                selectedSection  = b[4];
                contentScrollY   = 0;
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int contentX = px + NAV_W + PAD + 2;
        if (mx >= contentX) {
            contentScrollY = Math.max(0, contentScrollY - (int)(amount * 12));
        }
        return true;
    }
}
