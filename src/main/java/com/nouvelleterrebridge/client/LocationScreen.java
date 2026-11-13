package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.location.LocationManager;
import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.market.ItemComponentCodec;
import com.nouvelleterrebridge.network.LocationNetworking;
import com.nouvelleterrebridge.network.NtNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Location d'objets entre joueurs — tout se passe ici : proposer un objet, le
 * louer, le rendre, et récupérer celui qui revient.
 *
 * Trois onglets : <b>Louer</b> (offres des autres), <b>Mes locations</b> (ce que
 * je loue, ce que je fais louer, ce qui m'est revenu) et <b>Proposer</b> (mes
 * objets louables).
 */
public class LocationScreen extends Screen {

    public record OffreVue(int id, String proprietaire, String item, String nbt,
                           int prixJour, int caution, boolean mienne) {}
    public record ContratVue(int id, String proprietaire, String locataire, String item,
                             int prixJour, int caution, long resteMs, int etat,
                             boolean resilie, boolean jeSuisLocataire) {}
    public record RetourVue(int id, String item, String nbt, String origine) {}
    public record Etat(int balance, List<OffreVue> offres, List<ContratVue> contrats, List<RetourVue> retours) {}

    /** Lecture du contenu commun à LOC_OPEN et LOC_RESULT. */
    public static Etat lire(FriendlyByteBuf buf) {
        int balance = buf.readInt();
        List<OffreVue> offres = new ArrayList<>();
        for (int i = 0, n = buf.readInt(); i < n; i++)
            offres.add(new OffreVue(buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readInt(), buf.readBoolean()));
        List<ContratVue> contrats = new ArrayList<>();
        for (int i = 0, n = buf.readInt(); i < n; i++)
            contrats.add(new ContratVue(buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readInt(), buf.readLong(), buf.readInt(),
                buf.readBoolean(), buf.readBoolean()));
        List<RetourVue> retours = new ArrayList<>();
        for (int i = 0, n = buf.readInt(); i < n; i++)
            retours.add(new RetourVue(buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
        return new Etat(balance, offres, contrats, retours);
    }

    private enum Tab {
        LOUER("Louer"), MIENNES("Mes locations"), PROPOSER("Proposer");
        final String label;
        Tab(String label) { this.label = label; }
    }

    /** Bouton d'une ligne. {@code confirmer} = deuxième clic exigé (action sans retour). */
    private record Btn(String label, int couleur, int action, int id, boolean confirmer) {}
    private record Ligne(ItemStack pile, String titre, String sous, String droite, List<Btn> btns) {}

    // ── Couleurs (alignées sur les autres écrans) ─────────────────────────────
    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_STRIP   = 0xFF1E2128;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int WIN_MAX_W = 720;
    private static final int WIN_MAX_H = 480;
    private static final int TOP_H     = 44;
    private static final int PAD       = 12;
    private static final int ROW_H     = 46;
    private static final int ROW_GAP   = 4;
    private static final int SCROLL_W  = 6;
    private static final int AIDE_H    = 18;
    private static final int BTN_H     = 22;
    private static final int MODAL_W   = 300;

    /** Action interne (jamais envoyée) : ouvre la fenêtre de confirmation de location. */
    private static final int OUVRIR_LOUER = -1;
    /** Action interne : ouvre la fenêtre de saisie du loyer et de la caution. */
    private static final int OUVRIR_PROPOSER = -2;

    private int winX, winY, winW, winH;
    private Etat etat;
    private Tab tab = Tab.LOUER;
    private List<Ligne> lignes = new ArrayList<>();

    private int scroll = 0, maxScroll = 0, tabsStartX = 0;
    private int scrollTrackX, scrollTrackY, scrollTrackH, scrollThumbH;
    private boolean draggingScroll = false;

    /** Boutons dessinés à la dernière image : {x, y, w} + le bouton, relus par mouseClicked. */
    private final List<int[]> btnBounds = new ArrayList<>();
    private final List<Btn> btnCibles = new ArrayList<>();
    private ItemStack survol = ItemStack.EMPTY;

    private Btn enAttente = null;
    private long enAttenteFin = 0;

    // Fenêtres modales
    private OffreVue aLouer = null;
    private int slotAProposer = -1;
    private ItemStack pileAProposer = ItemStack.EMPTY;
    private final NumberInput prixInput    = new NumberInput(1, 1, LocationManager.PRIX_MAX);
    private final NumberInput cautionInput = new NumberInput(1, 1, LocationManager.CAUTION_MAX);

    private String toastMsg = null;
    private boolean toastOk = true;
    private long toastEnd = 0;

    public LocationScreen(Etat etat) {
        super(Component.literal("Location"));
        this.etat = etat;
        // S'il y a quelque chose à faire chez soi, c'est là qu'on arrive
        if (!etat.retours().isEmpty() || etat.contrats().stream().anyMatch(c -> c.etat() != LocationManager.ETAT_OK))
            tab = Tab.MIENNES;
    }

    @Override
    protected void init() {
        prixInput.setPlaceholder("Loyer par jour...");
        cautionInput.setPlaceholder("Caution...");
        prixInput.clear();
        cautionInput.clear();
        construire();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public void handleResult(boolean ok, String msg, Etat nouvelEtat) {
        etat = nouvelEtat;
        fermerModales();
        enAttente = null;
        construire();
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", "");
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 4000;
    }

    private void fermerModales() {
        aLouer = null;
        slotAProposer = -1;
        pileAProposer = ItemStack.EMPTY;
    }

    private boolean modaleOuverte() {
        return aLouer != null || slotAProposer >= 0;
    }

    // ── Contenu des onglets ───────────────────────────────────────────────────

    private void construire() {
        lignes = new ArrayList<>();
        scroll = 0;
        switch (tab) {
            case LOUER -> {
                for (OffreVue o : etat.offres()) {
                    if (o.mienne()) continue;
                    lignes.add(new Ligne(pile(o.item(), o.nbt()), nom(o.item()),
                        "§8Proposé par §7" + o.proprietaire() + " §8· caution §7" + o.caution() + " ◆",
                        o.prixJour() + " ◆ / jour",
                        List.of(new Btn("Louer", C_GOLD, OUVRIR_LOUER, o.id(), false))));
                }
            }
            case MIENNES -> {
                for (RetourVue r : etat.retours())
                    lignes.add(new Ligne(pile(r.item(), r.nbt()), nom(r.item()),
                        "§aRevenu de location §8· " + r.origine(), "",
                        List.of(new Btn("Récupérer", C_GREEN, LocationNetworking.ACTION_RECUPERER, r.id(), false))));

                for (ContratVue c : etat.contrats()) {
                    if (c.jeSuisLocataire()) {
                        String sous = c.etat() == LocationManager.ETAT_OK
                            ? "§8Loué à §7" + c.proprietaire() + (c.resilie()
                                ? " §e· résilié, à rendre dans " + duree(c.resteMs())
                                : " §8· prochain loyer dans " + duree(c.resteMs()))
                            : "§cVerrouillé §8· " + (c.resilie() ? "à rendre" : "règle le loyer ou rends-le")
                                + ", reprise dans " + duree(c.resteMs());
                        lignes.add(new Ligne(pile(c.item(), ""), nom(c.item()), sous,
                            c.prixJour() + " ◆ / jour",
                            List.of(new Btn("Rendre", C_GREEN, LocationNetworking.ACTION_RENDRE, c.id(), false),
                                    new Btn("Perdu", C_RED, LocationNetworking.ACTION_PERDU, c.id(), true))));
                    } else {
                        String sous = switch (c.etat()) {
                            case LocationManager.ETAT_OK -> "§8Loué par §7" + c.locataire() + (c.resilie()
                                ? " §e· résilié, retour dans " + duree(c.resteMs())
                                : " §8· prochain loyer dans " + duree(c.resteMs()));
                            case LocationManager.ETAT_BLOQUE -> "§eVerrouillé chez §7" + c.locataire()
                                + " §e· reprise dans " + duree(c.resteMs());
                            default -> "§cJamais rendu par §7" + c.locataire() + " §c· caution encaissée";
                        };
                        List<Btn> btns = c.etat() != LocationManager.ETAT_PERDU && !c.resilie()
                            ? List.of(new Btn("Résilier", C_RED, LocationNetworking.ACTION_RESILIER, c.id(), true))
                            : List.of();
                        lignes.add(new Ligne(pile(c.item(), ""), nom(c.item()), sous,
                            c.prixJour() + " ◆ / jour", btns));
                    }
                }

                for (OffreVue o : etat.offres()) {
                    if (!o.mienne()) continue;
                    lignes.add(new Ligne(pile(o.item(), o.nbt()), nom(o.item()),
                        "§8En attente d'un locataire · caution §7" + o.caution() + " ◆",
                        o.prixJour() + " ◆ / jour",
                        List.of(new Btn("Retirer", C_RED, LocationNetworking.ACTION_RETIRER, o.id(), false))));
                }
            }
            case PROPOSER -> {
                if (minecraft == null || minecraft.player == null) return;
                var items = minecraft.player.getInventory().items;
                for (int slot = 0; slot < items.size(); slot++) {
                    ItemStack s = items.get(slot);
                    if (s.isEmpty() || LocationManager.refusDeLouer(s) != null) continue;
                    lignes.add(new Ligne(s.copy(), s.getHoverName().getString(),
                        "§8Fixe un loyer par jour et une caution", "",
                        List.of(new Btn("Proposer", C_GOLD, OUVRIR_PROPOSER, slot, false))));
                }
            }
        }
    }

    private String aide() {
        return switch (tab) {
            case LOUER    -> "Le loyer est prélevé chaque jour. La caution est rendue quand l'objet est rendu.";
            case MIENNES  -> "Loyer impayé : l'objet se verrouille, puis il est repris au bout de 24 h.";
            case PROPOSER -> "Seuls les objets non empilables se louent : outils, armes, armures…";
        };
    }

    private String vide() {
        return switch (tab) {
            case LOUER    -> "Aucun objet à louer pour le moment.";
            case MIENNES  -> "Aucune location en cours.";
            case PROPOSER -> "Aucun objet louable dans ton inventaire.";
        };
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    private void computeWin() {
        winW = Math.min(WIN_MAX_W, width  - 40);
        winH = Math.min(WIN_MAX_H, height - 40);
        winX = (width  - winW) / 2;
        winY = (height - winH) / 2;
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

        if (enAttente != null && System.currentTimeMillis() > enAttenteFin) enAttente = null;

        renderTopBar(ctx, mx, my);
        ctx.drawString(font, aide(), winX + PAD, winY + TOP_H + PAD, C_DIM, false);
        renderListe(ctx, mx, my);

        if (aLouer != null) renderModaleLouer(ctx, mx, my);
        else if (slotAProposer >= 0) renderModaleProposer(ctx, mx, my);
        else if (!survol.isEmpty()) ctx.renderTooltip(font, survol, mx, my);

        renderToast(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void renderTopBar(GuiGraphics ctx, int mx, int my) {
        ctx.fill(winX, winY, winX + winW, winY + TOP_H, 0xE01B1D22);
        ctx.fill(winX, winY + TOP_H - 1, winX + winW, winY + TOP_H, C_BORDER);

        int ty = winY + (TOP_H - font.lineHeight) / 2;
        int tx = winX + PAD;
        HubBackButton.render(ctx, font, tx, winY + (TOP_H - HubBackButton.H) / 2, mx, my);
        tx += HubBackButton.W + 8;

        ctx.drawString(font, "LOCATION", tx, ty, C_GOLD, false);
        tx += font.width("LOCATION") + 20;
        tabsStartX = tx;

        for (Tab t : Tab.values()) {
            int tw = font.width(t.label) + 18;
            boolean hov = mx >= tx && mx <= tx + tw && my >= winY && my <= winY + TOP_H - 1;
            int tabY = winY + (TOP_H - 22) / 2;
            if (tab == t) {
                ctx.fill(tx, tabY, tx + tw, tabY + 22, C_GOLD);
                ctx.drawString(font, t.label, tx + tw / 2 - font.width(t.label) / 2, tabY + 7, C_BG, false);
            } else {
                ctx.drawCenteredString(font, t.label, tx + tw / 2, tabY + 7, hov ? C_WHITE : C_DIM);
            }
            tx += tw + 4;
        }

        String bal = etat.balance() + " ◆";
        int bw = font.width(bal) + 18;
        int bx = winX + winW - bw - PAD;
        int by = winY + (TOP_H - 20) / 2;
        ctx.fill(bx, by, bx + bw, by + 20, C_STRIP);
        ctx.fill(bx, by, bx + 2, by + 20, C_GOLD);
        ctx.drawString(font, bal, bx + 10, by + 6, C_GOLD, false);
    }

    private void renderListe(GuiGraphics ctx, int mx, int my) {
        int gx = winX + PAD;
        int gy = winY + TOP_H + PAD + AIDE_H;
        int gw = winW - PAD * 2 - SCROLL_W - 4;
        int gh = winY + winH - PAD - gy;

        btnBounds.clear();
        btnCibles.clear();
        survol = ItemStack.EMPTY;

        if (lignes.isEmpty()) {
            ctx.drawCenteredString(font, vide(), winX + winW / 2, gy + gh / 2, C_DIM);
            maxScroll = 0;
            return;
        }

        int visRows = Math.max(1, gh / (ROW_H + ROW_GAP));
        maxScroll = Math.max(0, lignes.size() - visRows);
        scroll    = Math.max(0, Math.min(scroll, maxScroll));

        // Scrollbar : piste + pouce or, position mémorisée pour le drag
        scrollTrackX = gx + gw + 2; scrollTrackY = gy; scrollTrackH = gh;
        if (maxScroll > 0) {
            scrollThumbH = Math.max(18, gh * visRows / (visRows + maxScroll));
            int thumbY = gy + (gh - scrollThumbH) * scroll / maxScroll;
            ctx.fill(scrollTrackX, gy, scrollTrackX + SCROLL_W, gy + gh, C_BORDER);
            ctx.fill(scrollTrackX, thumbY, scrollTrackX + SCROLL_W, thumbY + scrollThumbH, C_GOLD);
        } else {
            scrollThumbH = 0;
        }

        boolean libre = !modaleOuverte();
        ctx.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = scroll; i < lignes.size(); i++) {
            int ry = gy + (i - scroll) * (ROW_H + ROW_GAP);
            if (ry + ROW_H > gy + gh) break;
            Ligne l = lignes.get(i);
            boolean hov = libre && mx >= gx && mx < gx + gw && my >= ry && my < ry + ROW_H;
            if (hov) survol = l.pile();

            ctx.fill(gx, ry, gx + gw, ry + ROW_H, hov ? C_HOVER : C_PANEL);
            ctx.fill(gx, ry, gx + gw, ry + 1, C_BORDER);
            ctx.fill(gx, ry + ROW_H - 1, gx + gw, ry + ROW_H, C_BORDER);

            ctx.fill(gx + 1, ry + 1, gx + 41, ry + ROW_H - 1, C_BG);
            drawItemScaled(ctx, l.pile(), gx + 21, ry + ROW_H / 2, 2.0f);

            // Boutons chaînés de droite à gauche, puis le prix à leur gauche
            int bx = gx + gw - 10;
            int by = ry + (ROW_H - BTN_H) / 2;
            for (int b = l.btns().size() - 1; b >= 0; b--) {
                Btn btn = l.btns().get(b);
                boolean attente = btn.equals(enAttente);
                String label = attente ? "Confirmer ?" : btn.label();
                int bw = Math.max(70, font.width(label) + 18);
                bx -= bw;
                boolean bh = libre && mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H;
                ctx.fill(bx, by, bx + bw, by + BTN_H, bh || attente ? btn.couleur() : C_STRIP);
                ctx.fill(bx, by, bx + bw, by + 1, btn.couleur());
                ctx.drawCenteredString(font, label, bx + bw / 2, by + 7, bh || attente ? C_BG : C_WHITE);
                btnBounds.add(new int[]{bx, by, bw});
                btnCibles.add(btn);
                bx -= 6;
            }
            if (!l.droite().isEmpty()) {
                bx -= 8 + font.width(l.droite());
                ctx.drawString(font, l.droite(), bx, ry + (ROW_H - font.lineHeight) / 2, C_GOLD, false);
            }

            int texteW = bx - (gx + 50) - 8;
            ctx.drawString(font, truncate(l.titre(), texteW), gx + 50, ry + 9, C_WHITE, false);
            ctx.drawString(font, truncate(l.sous(), texteW), gx + 50, ry + 24, C_DIM, false);
        }
        ctx.disableScissor();
    }

    private int modaleX() { return winX + (winW - MODAL_W) / 2; }

    private void renderModaleLouer(GuiGraphics ctx, int mx, int my) {
        int mh = 192;
        int ox = modaleX(), oy = winY + (winH - mh) / 2;
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 300);
        ctx.fill(ox, oy, ox + MODAL_W, oy + mh, C_SURFACE);
        ctx.fill(ox, oy, ox + MODAL_W, oy + 2, C_GOLD);

        ctx.drawString(font, "Louer", ox + 14, oy + 12, C_WHITE, false);
        drawItemScaled(ctx, pile(aLouer.item(), aLouer.nbt()), ox + 30, oy + 50, 2.0f);
        ctx.drawString(font, truncate(nom(aLouer.item()), MODAL_W - 70), ox + 52, oy + 38, C_WHITE, false);
        ctx.drawString(font, "à " + aLouer.proprietaire(), ox + 52, oy + 52, C_MID, false);

        int total = aLouer.prixJour() + aLouer.caution();
        ctx.drawString(font, "§7Premier jour : §6" + aLouer.prixJour() + " ◆", ox + 14, oy + 78, C_MID, false);
        ctx.drawString(font, "§7Caution (rendue au retour) : §6" + aLouer.caution() + " ◆", ox + 14, oy + 92, C_MID, false);
        ctx.drawString(font, "§fÀ payer maintenant : §6" + total + " ◆", ox + 14, oy + 110, C_WHITE, false);
        ctx.drawString(font, "§8Puis " + aLouer.prixJour() + " ◆ par jour, prélevés tout seuls", ox + 14, oy + 124, C_DIM, false);
        if (LocationManager.aRaccommodage(pile(aLouer.item(), aLouer.nbt())))
            ctx.drawString(font, "§eRaccommodage : à rendre réparé, comme reçu", ox + 14, oy + 138, C_DIM, false);

        int cbY = oy + mh - 34;
        drawWideButton(ctx, ox + 14, cbY, 120, "Annuler", C_RED, mx, my);
        drawWideButton(ctx, ox + MODAL_W - 134, cbY, 120,
            etat.balance() >= total ? "Louer" : "Solde insuffisant", C_GOLD, mx, my);
        ctx.pose().popPose();
    }

    private void renderModaleProposer(GuiGraphics ctx, int mx, int my) {
        int mh = 232;
        int ox = modaleX(), oy = winY + (winH - mh) / 2;
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 300);
        ctx.fill(ox, oy, ox + MODAL_W, oy + mh, C_SURFACE);
        ctx.fill(ox, oy, ox + MODAL_W, oy + 2, C_GOLD);

        ctx.drawString(font, "Proposer à la location", ox + 14, oy + 12, C_WHITE, false);
        drawItemScaled(ctx, pileAProposer, ox + 30, oy + 48, 2.0f);
        ctx.drawString(font, truncate(pileAProposer.getHoverName().getString(), MODAL_W - 70),
            ox + 52, oy + 42, C_WHITE, false);

        ctx.drawString(font, "Loyer par jour (◆)", ox + 14, oy + 70, C_MID, false);
        prixInput.render(ctx, font, ox + 14, oy + 82, MODAL_W - 28, mx, my);

        // La caution couvre au moins un jour de loyer : c'est elle qui paie un impayé
        cautionInput.setBounds(Math.max(1, prixInput.getValue()), LocationManager.CAUTION_MAX);
        ctx.drawString(font, "Caution (◆) — gardée si l'objet ne revient pas", ox + 14, oy + 132, C_MID, false);
        cautionInput.render(ctx, font, ox + 14, oy + 144, MODAL_W - 28, mx, my);

        int cbY = oy + mh - 34;
        drawWideButton(ctx, ox + 14, cbY, 120, "Annuler", C_RED, mx, my);
        drawWideButton(ctx, ox + MODAL_W - 134, cbY, 120, "Proposer", C_GOLD, mx, my);
        ctx.pose().popPose();
    }

    private void drawWideButton(GuiGraphics ctx, int x, int y, int w, String label, int color, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 22;
        ctx.fill(x, y, x + w, y + 22, hov ? color : C_STRIP);
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.drawCenteredString(font, label, x + w / 2, y + 7, hov ? C_BG : C_WHITE);
    }

    private void renderToast(GuiGraphics ctx) {
        if (toastMsg == null) return;
        if (System.currentTimeMillis() > toastEnd) { toastMsg = null; return; }
        String msg = truncate(toastMsg, winW - 60);
        int tw = font.width(msg) + 28;
        int tx = winX + winW - tw - 12;
        int ty = winY + winH - 38;
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 350);
        ctx.fill(tx, ty, tx + tw, ty + 26, C_SURFACE);
        ctx.fill(tx, ty, tx + 3, ty + 26, toastOk ? C_GREEN : C_RED);
        ctx.drawString(font, msg, tx + 11, ty + 9, C_WHITE, false);
        ctx.pose().popPose();
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;

        if (aLouer != null) { clicModaleLouer(x, y); return true; }
        if (slotAProposer >= 0) { clicModaleProposer(x, y); return true; }

        if (x < winX || x > winX + winW || y < winY || y > winY + winH) { onClose(); return true; }

        if (y <= winY + TOP_H - 1) {
            if (HubBackButton.clicked(winX + PAD, winY + (TOP_H - HubBackButton.H) / 2, x, y)) return true;
            int tx = tabsStartX;
            for (Tab t : Tab.values()) {
                int tw = font.width(t.label) + 18;
                if (x >= tx && x <= tx + tw) { tab = t; enAttente = null; construire(); return true; }
                tx += tw + 4;
            }
            return true;
        }

        if (maxScroll > 0 && x >= scrollTrackX - 2 && x <= scrollTrackX + SCROLL_W + 2
                && y >= scrollTrackY && y <= scrollTrackY + scrollTrackH) {
            draggingScroll = true;
            applyScrollFromMouse(y - scrollThumbH / 2);
            return true;
        }

        for (int i = 0; i < btnBounds.size(); i++) {
            int[] b = btnBounds.get(i);
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + BTN_H) {
                actionner(btnCibles.get(i));
                return true;
            }
        }
        return true;
    }

    private void actionner(Btn b) {
        if (b.action() == OUVRIR_LOUER) {
            for (OffreVue o : etat.offres()) if (o.id() == b.id()) aLouer = o;
            return;
        }
        if (b.action() == OUVRIR_PROPOSER) {
            if (minecraft == null || minecraft.player == null) return;
            slotAProposer = b.id();
            pileAProposer = minecraft.player.getInventory().items.get(b.id()).copy();
            prixInput.setBounds(1, LocationManager.PRIX_MAX);
            prixInput.clear();
            cautionInput.clear();
            prixInput.setFocused(true);
            cautionInput.setFocused(false);
            return;
        }
        if (b.confirmer() && !b.equals(enAttente)) {
            enAttente = b;
            enAttenteFin = System.currentTimeMillis() + 3000;
            return;
        }
        enAttente = null;
        envoyer(b.action(), b.id(), 0, 0);
    }

    private void clicModaleLouer(int x, int y) {
        int mh = 192;
        int ox = modaleX(), oy = winY + (winH - mh) / 2;
        if (x < ox || x > ox + MODAL_W || y < oy || y > oy + mh) { aLouer = null; return; }
        int cbY = oy + mh - 34;
        if (y < cbY || y >= cbY + 22) return;
        if (x >= ox + 14 && x < ox + 134) { aLouer = null; return; }
        if (x >= ox + MODAL_W - 134 && x < ox + MODAL_W - 14) {
            if (etat.balance() < aLouer.prixJour() + aLouer.caution()) return;
            envoyer(LocationNetworking.ACTION_LOUER, aLouer.id(), 0, 0);
            aLouer = null;
        }
    }

    private void clicModaleProposer(int x, int y) {
        int mh = 232;
        int ox = modaleX(), oy = winY + (winH - mh) / 2;
        if (x < ox || x > ox + MODAL_W || y < oy || y > oy + mh) { fermerModales(); return; }

        // Les deux champs sont interrogés : celui qui n'est pas cliqué perd le focus
        boolean a = prixInput.mouseClicked(x, y);
        boolean b = cautionInput.mouseClicked(x, y);
        if (a || b) return;

        int cbY = oy + mh - 34;
        if (y < cbY || y >= cbY + 22) return;
        if (x >= ox + 14 && x < ox + 134) { fermerModales(); return; }
        if (x >= ox + MODAL_W - 134 && x < ox + MODAL_W - 14) {
            if (prixInput.isEmpty()) { prixInput.setFocused(true); return; }
            envoyer(LocationNetworking.ACTION_PROPOSER, slotAProposer,
                prixInput.getValue(), cautionInput.getValue());
            fermerModales();
        }
    }

    private void applyScrollFromMouse(int mouseY) {
        if (maxScroll <= 0) { scroll = 0; return; }
        int usable = Math.max(1, scrollTrackH - scrollThumbH);
        float ratio = (float) (mouseY - scrollTrackY) / usable;
        scroll = Math.max(0, Math.min(Math.round(ratio * maxScroll), maxScroll));
    }

    @Override
    public boolean mouseDragged(double mx0, double my0, int btn, double dx, double dy) {
        if (draggingScroll) { applyScrollFromMouse((int) my0 - scrollThumbH / 2); return true; }
        return super.mouseDragged(mx0, my0, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx0, double my0, int btn) {
        draggingScroll = false;
        return super.mouseReleased(mx0, my0, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double amount) {
        if (modaleOuverte()) return true;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256 && modaleOuverte()) { fermerModales(); return true; }
        if (slotAProposer >= 0 && (prixInput.keyPressed(key) || cautionInput.keyPressed(key))) return true;
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char chr, int mod) {
        if (slotAProposer >= 0 && (prixInput.charTyped(chr) || cautionInput.charTyped(chr))) return true;
        return super.charTyped(chr, mod);
    }

    private void envoyer(int action, int id, int prix, int caution) {
        FriendlyByteBuf buf = NtNet.buffer();
        buf.writeInt(action);
        buf.writeInt(id);
        buf.writeInt(prix);
        buf.writeInt(caution);
        NtNet.versServeur(LocationNetworking.LOC_ACTION, buf);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static String nom(String itemId) {
        return FrenchItemNames.toDisplay(itemId);
    }

    private static String duree(long ms) {
        long minutes = Math.max(1, ms / 60_000);
        return minutes >= 60 ? (minutes / 60) + " h" : minutes + " min";
    }

    /** Reconstitue la pile d'une offre, enchantements compris, pour l'icône et l'infobulle. */
    private ItemStack pile(String itemId, String nbt) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) return new ItemStack(Items.BARRIER);
            ItemStack s = new ItemStack(item);
            if (nbt != null && !nbt.isEmpty() && minecraft != null && minecraft.level != null)
                ItemComponentCodec.appliquer(s, nbt, minecraft.level.registryAccess());
            return s;
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private void drawItemScaled(GuiGraphics ctx, ItemStack stack, int cx, int cy, float scale) {
        ctx.pose().pushPose();
        ctx.pose().translate(cx - 8 * scale, cy - 8 * scale, 0);
        ctx.pose().scale(scale, scale, 1.0f);
        ctx.renderItem(stack, 0, 0);
        ctx.pose().popPose();
    }

    private String truncate(String s, int maxW) {
        if (maxW <= 0) return "";
        if (font.width(s) <= maxW) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && font.width(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
        return sb + "…";
    }
}
