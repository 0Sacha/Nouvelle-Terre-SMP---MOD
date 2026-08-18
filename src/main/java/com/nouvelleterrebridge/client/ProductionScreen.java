package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.network.ProductionNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GUI /production : liste des productions naturelles avec barres de progression.
 * Quand un item atteint son seuil, il est mis en vente au shop auto $Serveur.
 * Les boutons admin (reset / recheck / reload) ne sont visibles que pour les op.
 */
@Environment(EnvType.CLIENT)
public class ProductionScreen extends Screen {

    public record ProdEntry(String itemId, long count, long seuil, int prix, int quantite,
                            boolean enVente, boolean desactive) {}

    // ── Couleurs (palette commune) ─────────────────────────────────────────────

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    // ── Layout ─────────────────────────────────────────────────────────────────

    private static final int MAX_PW = 620;
    private static final int MAX_PH = 460;
    private static final int TOP_H  = 40;
    private static final int SEARCH_H = 30;
    private static final int PAD    = 12;
    private static final int ROW_H  = 40;

    private int pw, ph, px, py;

    // ── State ──────────────────────────────────────────────────────────────────

    private boolean isOp;
    private List<ProdEntry> entries;
    private int scroll = 0;
    private boolean draggingScroll = false;

    private TextFieldWidget searchField;
    /** Item dont les contrôles admin sont dépliés (null = aucun). */
    private String expanded = null;
    private final NumberInput priceInput = new NumberInput(1, 1, 999_999);
    /** Suppression armée : deux clics requis, comme le Reset global. */
    private long deleteConfirmUntil = 0;

    // Bounds des boutons par ligne : {x, y, w, h, action}
    private final List<int[]> rowBtnBounds = new ArrayList<>();

    private String  toastMsg;
    private boolean toastOk;
    private long    toastEnd;

    // Confirmation du Reset : premier clic arme, second clic (dans les 3 s) exécute
    private long resetConfirmUntil = 0;

    // Bounds boutons admin : {x, y, w, h, action}
    private final List<int[]> adminBtnBounds = new ArrayList<>();

    public ProductionScreen(boolean isOp, List<ProdEntry> entries) {
        super(Text.literal("Production naturelle"));
        update(isOp, entries);
    }

    public void update(boolean isOp, List<ProdEntry> entries) {
        this.isOp = isOp;
        List<ProdEntry> sorted = new ArrayList<>(entries);
        // En vente d'abord, puis par progression décroissante, puis alphabétique
        sorted.sort(Comparator
            .comparing((ProdEntry e) -> e.enVente() ? 0 : 1)
            .thenComparing(e -> -progressRatio(e))
            .thenComparing(e -> FrenchItemNames.toDisplay(e.itemId()), String.CASE_INSENSITIVE_ORDER));
        this.entries = sorted;
    }

    public void handleResult(boolean ok, String msg, boolean isOp, List<ProdEntry> entries) {
        update(isOp, entries);
        scroll = 0;
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", "");
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 3200;
    }

    private static float progressRatio(ProdEntry e) {
        return e.seuil() > 0 ? Math.min(1f, (float) e.count() / e.seuil()) : 0f;
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_PW, width  - 20);
        ph = Math.min(MAX_PH, height - 20);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        searchField = new TextFieldWidget(textRenderer, px + PAD, py + TOP_H + 6, pw - PAD * 2 - 8, 18,
            Text.literal(""));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Rechercher un item..."));
        searchField.setChangedListener(s -> scroll = 0);
        addSelectableChild(searchField);
    }

    /** Entrées filtrées par la recherche (nom FR ou identifiant). */
    private List<ProdEntry> filtered() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        if (q.isEmpty()) return entries;
        return entries.stream()
            .filter(e -> FrenchItemNames.toDisplay(e.itemId()).toLowerCase().contains(q)
                      || e.itemId().toLowerCase().contains(q))
            .toList();
    }

    @Override public boolean shouldPause() { return false; }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px, py, px + pw, py + 1, C_BORDER);
        ctx.fill(px, py + ph - 1, px + pw, py + ph, C_BORDER);
        ctx.fill(px, py, px + 1, py + ph, C_BORDER);
        ctx.fill(px + pw - 1, py, px + pw, py + ph, C_BORDER);

        // Header
        ctx.fill(px, py, px + pw, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H, px + pw, py + TOP_H + 1, C_BORDER);
        HubBackButton.render(ctx, textRenderer, px + PAD, py + (TOP_H - HubBackButton.H) / 2, mx, my);
        int titleX = px + PAD + HubBackButton.W + 8;
        ctx.drawText(textRenderer, "⛏  Production naturelle", titleX, py + 9, C_GOLD, false);
        long enVente = entries.stream().filter(ProdEntry::enVente).count();
        ctx.drawText(textRenderer, "§a" + enVente + " en vente§7 / " + entries.size(),
            titleX, py + 23, C_DIM, false);

        adminBtnBounds.clear();
        rowBtnBounds.clear();
        if (isOp) renderAdminButtons(ctx, mx, my);

        // Barre de recherche
        int sy = py + TOP_H + 6;
        ctx.fill(px + PAD - 4, sy - 4, px + pw - PAD + 4, sy + 22, C_PANEL);
        ctx.fill(px + PAD - 4, sy + 21, px + pw - PAD + 4, sy + 22, C_BORDER);
        if (searchField != null) {
            searchField.setX(px + PAD);
            searchField.setY(sy);
            searchField.setWidth(pw - PAD * 2 - 8);
            searchField.render(ctx, mx, my, delta);
        }

        List<ProdEntry> list = filtered();

        // Liste
        int listY = py + TOP_H + SEARCH_H + 4;
        int listH = ph - TOP_H - SEARCH_H - 8;
        int visRows = Math.max(1, listH / ROW_H);
        int maxScroll = Math.max(0, list.size() - visRows);
        scroll = Math.min(scroll, maxScroll);

        ctx.enableScissor(px + 4, listY, px + pw - 4, listY + listH);
        for (int i = scroll; i < Math.min(scroll + visRows, list.size()); i++) {
            renderRow(ctx, list.get(i), i, px + 6, listY + (i - scroll) * ROW_H, pw - 18, mx, my);
        }
        ctx.disableScissor();

        if (list.isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer, "Aucun item ne correspond.",
                px + pw / 2, listY + listH / 2, C_DIM);

        // Scrollbar
        if (list.size() > visRows) {
            int trackX = px + pw - 8;
            int trackH = visRows * ROW_H - 4;
            ctx.fill(trackX, listY, trackX + 4, listY + trackH, C_BORDER);
            float ratio  = (float) visRows / list.size();
            int   thumbH = Math.max(16, (int)(trackH * ratio));
            int   thumbY = maxScroll > 0 ? listY + (int)((trackH - thumbH) * ((float) scroll / maxScroll)) : listY;
            ctx.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, C_GOLD);
        }

        if (expanded != null) renderManageModal(ctx, mx, my);

        renderToast(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void renderAdminButtons(DrawContext ctx, int mx, int my) {
        String[] labels  = {"Recheck", "Recharger", "Reset"};
        int[]    actions = {ProductionNetworking.ACTION_RECHECK, ProductionNetworking.ACTION_RELOAD, ProductionNetworking.ACTION_RESET};
        boolean resetArmed = System.currentTimeMillis() < resetConfirmUntil;
        int bx = px + pw - PAD;
        for (int i = labels.length - 1; i >= 0; i--) {
            boolean danger = actions[i] == ProductionNetworking.ACTION_RESET;
            String  label  = danger && resetArmed ? "Confirmer ?" : labels[i];
            int bw = textRenderer.getWidth(label) + 14;
            bx -= bw;
            int by = py + 11;
            boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + 18;
            int base  = danger ? (resetArmed ? C_RED : 0xFF3D0A16) : C_SURFACE;
            int hover = danger ? C_RED : C_HOVER;
            ctx.fill(bx, by, bx + bw, by + 18, hov ? hover : base);
            ctx.fill(bx, by, bx + bw, by + 1, danger ? C_RED : C_BORDER);
            ctx.drawText(textRenderer, label, bx + 7, by + 5, danger ? C_WHITE : C_MID, false);
            adminBtnBounds.add(new int[]{bx, by, bw, 18, actions[i]});
            bx -= 6;
        }
    }

    private void renderRow(DrawContext ctx, ProdEntry e, int index, int x, int y, int w, int mx, int my) {
        int rowH = ROW_H - 3;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
        ctx.fill(x, y, x + w, y + rowH, hover ? C_HOVER : C_PANEL);

        // Liseré d'état : vert en vente, gris barré si désactivé, or en progression
        int etatCouleur = e.desactive() ? C_RED : (e.enVente() ? C_GREEN : C_BORDER);
        ctx.fill(x, y, x + 3, y + rowH, etatCouleur);

        ctx.fill(x + 5, y + 4, x + 33, y + rowH - 4, C_BG);
        renderItemIcon(ctx, e.itemId(), x + 11, y + (rowH - 16) / 2);

        String name = FrenchItemNames.toDisplay(e.itemId());
        ctx.drawText(textRenderer, truncate(name, w / 2 - 60), x + 40, y + 6, C_WHITE, false);

        float pct = progressRatio(e);
        String countStr = fmt(e.count()) + " / " + fmt(e.seuil()) + " produits";
        ctx.drawText(textRenderer, countStr, x + 40, y + 20, pct >= 1f ? C_GREEN : C_DIM, false);

        // Barre de progression au centre
        int barX = x + w / 2 + 10, barW = w / 2 - 130;
        if (barW > 20) {
            ctx.fill(barX, y + rowH / 2 - 3, barX + barW, y + rowH / 2 + 2, C_BORDER);
            if (pct > 0) ctx.fill(barX, y + rowH / 2 - 3, barX + (int)(barW * pct), y + rowH / 2 + 2,
                e.enVente() ? C_GREEN : C_GOLD);
            String pctStr = ((int)(pct * 100)) + " %";
            ctx.drawText(textRenderer, pctStr, barX + barW + 6, y + rowH / 2 - 4,
                pct >= 1f ? C_GREEN : C_DIM, false);
        }

        // Statut + prix à droite
        String statut;
        int statutCouleur;
        if (e.desactive())      { statut = "⏸ Retiré";  statutCouleur = C_RED; }
        else if (e.enVente())   { statut = "✔ En vente"; statutCouleur = C_GREEN; }
        else                    { statut = "En cours";   statutCouleur = C_DIM; }
        int sw = textRenderer.getWidth(statut);
        ctx.drawText(textRenderer, statut, x + w - sw - 10, y + 6, statutCouleur, false);
        String prix = e.prix() + " ◆/u  ·  lot " + e.quantite();
        ctx.drawText(textRenderer, prix, x + w - textRenderer.getWidth(prix) - 10, y + 20, C_MID, false);

        // Clic sur la ligne = gérer (op uniquement) — l'index de la liste filtrée
        // est mémorisé ici plutôt que recalculé au clic, qui se désynchroniserait
        // du scroll et du filtre de recherche.
        if (isOp && hover) {
            ctx.fill(x, y, x + w, y + 1, C_GOLD);
            ctx.fill(x, y + rowH - 1, x + w, y + rowH, C_GOLD);
            rowBtnBounds.add(new int[]{x, y, w, rowH, 0, index});
        }
    }

    // ── Modal de gestion (op) ──────────────────────────────────────────────────

    private void renderManageModal(DrawContext ctx, int mx, int my) {
        ProdEntry e = entries.stream().filter(p -> p.itemId().equals(expanded)).findFirst().orElse(null);
        if (e == null) { expanded = null; return; }

        // Le modal capture tous les clics : les bornes des lignes en dessous
        // ne doivent plus répondre.
        rowBtnBounds.clear();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
        ctx.fill(px, py, px + pw, py + ph, 0x99000000);

        int mw = 300, mh = 208;
        int ox = px + (pw - mw) / 2;
        int oy = py + (ph - mh) / 2;
        ctx.fill(ox, oy, ox + mw, oy + mh, C_SURFACE);
        ctx.fill(ox, oy, ox + mw, oy + 2, C_GOLD);

        renderItemIcon(ctx, e.itemId(), ox + 14, oy + 14);
        ctx.drawText(textRenderer, truncate(FrenchItemNames.toDisplay(e.itemId()), mw - 50), ox + 38, oy + 12, C_WHITE, false);
        ctx.drawText(textRenderer, fmt(e.count()) + " / " + fmt(e.seuil()) + " produits", ox + 38, oy + 24, C_DIM, false);

        ctx.drawText(textRenderer, "PRIX UNITAIRE (◆)", ox + 14, oy + 46, C_DIM, false);
        priceInput.render(ctx, textRenderer, ox + 14, oy + 60, mw - 28, mx, my);

        int by = oy + 60 + NumberInput.H + 8;
        drawModalBtn(ctx, ox + 14, by, mw - 28, "Appliquer le prix", C_GOLD, mx, my,
            ProductionNetworking.ACTION_SET_PRICE);
        by += 26;
        drawModalBtn(ctx, ox + 14, by, mw - 28,
            e.desactive() ? "Remettre en vente" : "Retirer de la vente",
            e.desactive() ? C_GREEN : 0xFFB07818, mx, my, ProductionNetworking.ACTION_TOGGLE);
        by += 26;
        boolean armed = System.currentTimeMillis() < deleteConfirmUntil;
        drawModalBtn(ctx, ox + 14, by, mw - 28,
            armed ? "Confirmer la suppression ?" : "Supprimer du catalogue",
            C_RED, mx, my, ProductionNetworking.ACTION_DELETE);

        ctx.drawCenteredTextWithShadow(textRenderer, "Échap pour fermer", ox + mw / 2, oy + mh - 14, C_DIM);
        ctx.getMatrices().pop();
    }

    private void drawModalBtn(DrawContext ctx, int x, int y, int w, String label, int color,
                              int mx, int my, int action) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 22;
        ctx.fill(x, y, x + w, y + 22, hov ? color : C_SURFACE);
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 7, hov ? C_BG : color);
        rowBtnBounds.add(new int[]{x, y, w, 22, action});
    }

    private void renderItemIcon(DrawContext ctx, String itemId, int x, int y) {
        try {
            Item item = Registries.ITEM.get(Identifier.tryParse(itemId));
            ctx.drawItem(new ItemStack(item == Items.AIR ? Items.BARRIER : item), x, y);
        } catch (Exception ignored) {
            ctx.drawItem(new ItemStack(Items.BARRIER), x, y);
        }
    }

    private void renderToast(DrawContext ctx) {
        if (toastMsg == null) return;
        if (System.currentTimeMillis() > toastEnd) { toastMsg = null; return; }
        int tw = textRenderer.getWidth(toastMsg) + 28;
        int th = 26;
        int tx = px + pw - tw - 10;
        int ty = py + ph - th - 10;
        ctx.fill(tx, ty, tx + tw, ty + th, C_SURFACE);
        ctx.fill(tx, ty, tx + 3, ty + th, toastOk ? C_GREEN : C_RED);
        ctx.drawText(textRenderer, toastMsg, tx + 11, ty + (th - textRenderer.fontHeight) / 2, C_WHITE, false);
    }

    // ── Interactions ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;

        // ── Modal de gestion : capture tout ──
        if (expanded != null) {
            if (priceInput.mouseClicked(x, y)) return true;
            for (int[] b : rowBtnBounds) {
                if (x < b[0] || x >= b[0] + b[2] || y < b[1] || y >= b[1] + b[3]) continue;
                if (b[4] == ProductionNetworking.ACTION_DELETE
                        && System.currentTimeMillis() >= deleteConfirmUntil) {
                    deleteConfirmUntil = System.currentTimeMillis() + 3000;
                    return true;
                }
                deleteConfirmUntil = 0;
                sendItemAction(b[4], expanded,
                    b[4] == ProductionNetworking.ACTION_SET_PRICE ? priceInput.getValue() : 0);
                if (b[4] != ProductionNetworking.ACTION_SET_PRICE) expanded = null;
                return true;
            }
            // Ne ferme que sur un clic hors du cadre : un clic dans le vide du
            // modal ne doit pas annuler une saisie de prix en cours.
            int mw = 300, mh = 208;
            int ox = px + (pw - mw) / 2, oy = py + (ph - mh) / 2;
            if (x < ox || x > ox + mw || y < oy || y > oy + mh) expanded = null;
            return true;
        }

        if (x < px || x > px + pw || y < py || y > py + ph) { close(); return true; }

        if (HubBackButton.clicked(px + PAD, py + (TOP_H - HubBackButton.H) / 2, x, y)) return true;

        for (int[] b : adminBtnBounds) {
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                if (b[4] == ProductionNetworking.ACTION_RESET
                        && System.currentTimeMillis() >= resetConfirmUntil) {
                    // Premier clic : arme la confirmation pendant 3 s
                    resetConfirmUntil = System.currentTimeMillis() + 3000;
                    return true;
                }
                resetConfirmUntil = 0;
                sendItemAction(b[4], "", 0);
                return true;
            }
        }

        // ── Ligne cliquée (op) → ouvre le modal de gestion ──
        List<ProdEntry> list = filtered();
        for (int[] b : rowBtnBounds) {
            if (b[4] == 0 && x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]
                    && b[5] < list.size()) {
                ProdEntry cible = list.get(b[5]);
                expanded = cible.itemId();
                priceInput.setValue(cible.prix());
                priceInput.setFocused(false);
                deleteConfirmUntil = 0;
                return true;
            }
        }

        int listY = py + TOP_H + SEARCH_H + 4;
        int visRows = visibleRows();
        if (filtered().size() > visRows && x >= px + pw - 10 && x <= px + pw - 2
                && y >= listY && y <= listY + visRows * ROW_H) {
            draggingScroll = true;
            applyScrollFromMouse(y, listY, visRows);
            return true;
        }
        return super.mouseClicked(mx0, my0, btn);
    }

    private void sendItemAction(int action, String itemId, int valeur) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(action);
        buf.writeString(itemId);
        buf.writeInt(valeur);
        ClientPlayNetworking.send(ProductionNetworking.PROD_ACTION, buf);
    }

    @Override
    public boolean mouseDragged(double mx0, double my0, int btn, double dx, double dy) {
        if (draggingScroll) {
            applyScrollFromMouse((int) my0, py + TOP_H + SEARCH_H + 4, visibleRows());
            return true;
        }
        return super.mouseDragged(mx0, my0, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx0, double my0, int btn) {
        draggingScroll = false;
        return super.mouseReleased(mx0, my0, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (expanded != null) return true;
        int maxScroll = Math.max(0, filtered().size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (expanded != null) {
            if (key == 256) { expanded = null; return true; }
            if (priceInput.keyPressed(key)) return true;
            return true;   // le modal garde le focus clavier
        }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char chr, int mod) {
        if (expanded != null && priceInput.charTyped(chr)) return true;
        return super.charTyped(chr, mod);
    }

    private int visibleRows() {
        return Math.max(1, (ph - TOP_H - SEARCH_H - 8) / ROW_H);
    }

    /** Positionne le scroll d'après la position verticale de la souris sur la piste. */
    private void applyScrollFromMouse(int mouseY, int listY, int visRows) {
        int maxScroll = Math.max(0, filtered().size() - visRows);
        if (maxScroll == 0) { scroll = 0; return; }
        int trackH = visRows * ROW_H - 4;
        float ratio = (float) (mouseY - listY) / Math.max(1, trackH);
        scroll = Math.max(0, Math.min(Math.round(ratio * maxScroll), maxScroll));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String fmt(long n) {
        if (n < 1000) return String.valueOf(n);
        return fmt(n / 1000) + " " + String.format("%03d", n % 1000);
    }

    private String truncate(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
