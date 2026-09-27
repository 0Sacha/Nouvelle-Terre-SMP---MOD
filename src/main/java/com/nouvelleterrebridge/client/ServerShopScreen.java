package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.network.ShopNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shop Serveur — écran autonome, séparé du HDV entre joueurs.
 * Onglet Acheter : catalogue du serveur (stock illimité, prix dynamique).
 * Onglet Vendre  : revente au serveur au prix de rachat.
 */
public class ServerShopScreen extends Screen {

    /** Une ligne du catalogue serveur. */
    public record ShopEntry(String itemId, int buyPrice, int sellPrice, long netFlow) {}

    private enum Tab { ACHETER, VENDRE }

    // ── Couleurs (alignées sur HdvScreen) ─────────────────────────────────────
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
    private static final int WIN_MAX_W = 780;
    private static final int WIN_MAX_H = 520;
    private static final int TOP_H     = 44;
    private static final int PAD       = 12;
    private static final int ROW_H     = 46;
    private static final int ROW_GAP   = 4;
    private static final int GAP       = 8;
    private static final int SCROLL_W  = 6;
    private static final int BANNER_H  = 46;
    private static final int MODAL_W   = 300;
    private static final int MODAL_H   = 200;

    private int winX, winY, winW, winH;

    private int balance;
    private List<ShopEntry> entries;

    private Tab tab = Tab.ACHETER;
    private int scroll = 0;
    private int maxScroll = 0;
    private int tabsStartX = 0;

    // Piste de scrollbar mémorisée au rendu, relue par le drag
    private int scrollTrackY, scrollTrackH, scrollTrackX, scrollThumbH;
    private boolean draggingScroll = false;

    // Position du bouton du bandeau, calculée au rendu et relue par mouseClicked
    private int parcheminBtnX = 0, parcheminBtnY = 0, parcheminBtnW = 0;

    private EditBox searchField;

    private ShopEntry hovered = null;
    private ShopEntry selected = null;
    private final NumberInput qtyInput = new NumberInput(1, 1, 1);

    private String toastMsg = null;
    private boolean toastOk = true;
    private long toastEnd = 0;

    /** Inventaire vendable du joueur : itemId → quantité (piles vierges uniquement). */
    private Map<String, Integer> sellable = new LinkedHashMap<>();

    public ServerShopScreen(int balance, List<ShopEntry> entries) {
        super(Component.literal("Shop Serveur"));
        this.balance = balance;
        this.entries = new ArrayList<>(entries);
    }

    @Override
    protected void init() {
        computeWin();
        searchField = new EditBox(font, winX + PAD, winY + TOP_H + PAD, 200, 18,
            Component.literal("Rechercher"));
        searchField.setBordered(false);
        searchField.setHint(Component.literal("Rechercher..."));
        qtyInput.setPlaceholder("Quantité...");
        searchField.setResponder(s -> scroll = 0);
        addRenderableWidget(searchField);
        refreshSellable();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void computeWin() {
        winW = Math.min(WIN_MAX_W, width  - 40);
        winH = Math.min(WIN_MAX_H, height - 40);
        winX = (width  - winW) / 2;
        winY = (height - winH) / 2;
    }

    /** Recense les piles vierges de l'inventaire : le serveur ne rachète rien d'autre. */
    private void refreshSellable() {
        sellable = new LinkedHashMap<>();
        if (this.minecraft == null || this.minecraft.player == null) return;
        for (ItemStack s : this.minecraft.player.getInventory().items) {
            if (s.isEmpty() || !s.getComponentsPatch().isEmpty() || s.isDamaged()) continue;
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            sellable.merge(id, s.getCount(), Integer::sum);
        }
    }

    public void handleResult(boolean ok, String msg, int newBalance, List<ShopEntry> newEntries) {
        balance  = newBalance;
        entries  = new ArrayList<>(newEntries);
        selected = null;
        qtyInput.setBounds(1, 1);
        qtyInput.clear();
        refreshSellable();
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", "");
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 3200;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        // No-op — cet écran dessine son propre fond ; super.render() (appelé en dernier
        // pour les widgets vanilla) réappliquerait sinon flou + texture menu par-dessus.
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        computeWin();
        ctx.fill(winX + 3, winY + 3, winX + winW + 3, winY + winH + 3, 0x40000000);
        ctx.fill(winX, winY, winX + winW, winY + winH, 0xCC14161A);

        renderTopBar(ctx, mx, my);
        hovered = null;
        if (tab == Tab.ACHETER) renderBuyGrid(ctx, mx, my);
        else                    renderSellGrid(ctx, mx, my);

        if (selected != null) renderModal(ctx, mx, my);
        else if (hovered != null) renderTooltip(ctx, hovered, mx, my);

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

        ctx.drawString(font, "SHOP", tx, ty, C_GOLD, false);
        tx += font.width("SHOP") + 8;
        ctx.fill(tx, winY + (TOP_H - 16) / 2, tx + 1, winY + (TOP_H + 16) / 2, C_BORDER);
        tx += 9;
        ctx.drawString(font, "Serveur", tx, ty, C_MID, false);
        tx += font.width("Serveur") + 20;

        tabsStartX = tx;

        for (Tab t : Tab.values()) {
            String label = t == Tab.ACHETER ? "Acheter" : "Vendre";
            int tw = font.width(label) + 18;
            boolean active = tab == t;
            boolean hov = mx >= tx && mx <= tx + tw && my >= winY && my <= winY + TOP_H - 1;
            int tabY = winY + (TOP_H - 22) / 2;
            if (active) {
                ctx.fill(tx, tabY, tx + tw, tabY + 22, C_GOLD);
                ctx.drawString(font, label, tx + tw / 2 - font.width(label) / 2, tabY + 7, C_BG, false);
            } else {
                ctx.drawCenteredString(font, label, tx + tw / 2, tabY + 7, hov ? C_WHITE : C_DIM);
            }
            tx += tw + 4;
        }

        String bal = balance + " ◆";
        int bw = font.width(bal) + 18;
        int bx = winX + winW - bw - PAD;
        int by = winY + (TOP_H - 20) / 2;
        ctx.fill(bx, by, bx + bw, by + 20, C_STRIP);
        ctx.fill(bx, by, bx + 2, by + 20, C_GOLD);
        ctx.drawString(font, bal, bx + 10, by + 6, C_GOLD, false);
    }

    private String query() {
        return searchField != null ? searchField.getValue().trim().toLowerCase() : "";
    }

    private List<ShopEntry> buyList() {
        String q = query();
        return entries.stream()
            .filter(e -> q.isEmpty()
                || FrenchItemNames.toDisplay(e.itemId()).toLowerCase().contains(q)
                || e.itemId().toLowerCase().contains(q))
            .sorted(Comparator.comparing(e -> FrenchItemNames.toDisplay(e.itemId())))
            .toList();
    }

    /** Entrées vendables : intersection du catalogue et de l'inventaire du joueur. */
    private List<ShopEntry> sellList() {
        String q = query();
        Map<String, ShopEntry> byId = new LinkedHashMap<>();
        for (ShopEntry e : entries) byId.put(e.itemId(), e);

        List<ShopEntry> out = new ArrayList<>();
        for (String id : sellable.keySet()) {
            ShopEntry e = byId.get(id);
            if (e == null) continue;
            if (!q.isEmpty()
                && !FrenchItemNames.toDisplay(id).toLowerCase().contains(q)
                && !id.toLowerCase().contains(q)) continue;
            out.add(e);
        }
        out.sort(Comparator.comparing(e -> FrenchItemNames.toDisplay(e.itemId())));
        return out;
    }

    private void renderBuyGrid(GuiGraphics ctx, int mx, int my) {
        renderSearch(ctx, mx, my);
        renderParcheminBanner(ctx, mx, my);
        List<ShopEntry> list = buyList();
        renderGrid(ctx, mx, my, list, true);
        if (list.isEmpty())
            ctx.drawCenteredString(font, "Aucun article au catalogue.",
                winX + winW / 2, winY + winH / 2 + BANNER_H / 2, C_DIM);
    }

    /** Bandeau épinglé : le Parchemin, offert. Toujours visible, jamais vendable. */
    private void renderParcheminBanner(GuiGraphics ctx, int mx, int my) {
        int bx = winX + PAD;
        int by = winY + TOP_H + PAD + 26;
        int bw = winW - PAD * 2;

        ctx.fill(bx, by, bx + bw, by + BANNER_H, 0xFF1A2A22);
        ctx.fill(bx, by, bx + 3, by + BANNER_H, C_GREEN);
        ctx.fill(bx, by, bx + bw, by + 1, C_BORDER);
        ctx.fill(bx, by + BANNER_H - 1, bx + bw, by + BANNER_H, C_BORDER);

        drawItemScaled(ctx, parcheminStack(), bx + 30, by + BANNER_H / 2, 2.0f);

        ctx.drawString(font, "§fParchemin §7— votre terminal portatif",
            bx + 52, by + 10, C_WHITE, false);
        ctx.drawString(font, "§8Accès au marché, à la banque, aux quêtes… sans commande",
            bx + 52, by + 24, C_DIM, false);

        String label = "Obtenir — gratuit";
        int btnW = font.width(label) + 20;
        parcheminBtnX = bx + bw - btnW - 10;
        parcheminBtnY = by + (BANNER_H - 20) / 2;
        boolean hov = mx >= parcheminBtnX && mx < parcheminBtnX + btnW
                   && my >= parcheminBtnY && my < parcheminBtnY + 20;
        parcheminBtnW = btnW;

        ctx.fill(parcheminBtnX, parcheminBtnY, parcheminBtnX + btnW, parcheminBtnY + 20,
                 hov ? C_GREEN : C_STRIP);
        ctx.fill(parcheminBtnX, parcheminBtnY, parcheminBtnX + btnW, parcheminBtnY + 1, C_GREEN);
        ctx.drawCenteredString(font, label,
            parcheminBtnX + btnW / 2, parcheminBtnY + 6, hov ? C_BG : C_GREEN);
    }

    private ItemStack parcheminStack() {
        return stackOf("nouvelle-terre-bridge:parchemin");
    }

    private void renderSellGrid(GuiGraphics ctx, int mx, int my) {
        renderSearch(ctx, mx, my);
        List<ShopEntry> list = sellList();
        renderGrid(ctx, mx, my, list, false);
        if (list.isEmpty())
            ctx.drawCenteredString(font,
                "Rien à vendre — le serveur ne rachète que les objets vierges.",
                winX + winW / 2, winY + winH / 2, C_DIM);
    }

    private void renderSearch(GuiGraphics ctx, int mx, int my) {
        if (searchField == null) return;
        searchField.setX(winX + PAD);
        searchField.setY(winY + TOP_H + PAD);
        ctx.fill(winX + PAD - 1, winY + TOP_H + PAD - 1,
                 winX + PAD + 201, winY + TOP_H + PAD + 19, C_BORDER);
        searchField.render(ctx, mx, my, 0);
    }

    private void renderGrid(GuiGraphics ctx, int mx, int my, List<ShopEntry> list, boolean buying) {
        int gx = winX + PAD;
        // L'onglet Acheter réserve la place du bandeau Parchemin épinglé en haut
        int gy = winY + TOP_H + PAD + 26 + (buying ? BANNER_H + GAP : 0);
        int gw = winW - PAD * 2 - SCROLL_W - 4;
        int gh = winY + winH - PAD - gy;

        int visRows = Math.max(1, gh / (ROW_H + ROW_GAP));
        maxScroll = Math.max(0, list.size() - visRows);
        scroll    = Math.max(0, Math.min(scroll, maxScroll));

        renderScrollbar(ctx, gx + gw + 2, gy, gh, visRows);

        ctx.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = scroll; i < list.size(); i++) {
            int ry = gy + (i - scroll) * (ROW_H + ROW_GAP);
            if (ry > gy + gh) break;
            boolean hov = mx >= gx && mx < gx + gw && my >= ry && my < ry + ROW_H
                       && my >= gy && my < gy + gh;
            if (hov) hovered = list.get(i);
            renderRow(ctx, gx, ry, gw, list.get(i), hov, buying);
        }
        ctx.disableScissor();
    }

    /** Scrollbar : piste + pouce or opaque, position mémorisée pour le drag. */
    private void renderScrollbar(GuiGraphics ctx, int trackX, int trackY, int trackH, int visUnits) {
        scrollTrackX = trackX; scrollTrackY = trackY; scrollTrackH = trackH;
        if (maxScroll <= 0) { scrollThumbH = 0; return; }
        int thumbH = Math.max(18, trackH * visUnits / (visUnits + maxScroll));
        scrollThumbH = thumbH;
        int thumbY = trackY + (trackH - thumbH) * scroll / maxScroll;
        ctx.fill(trackX, trackY, trackX + SCROLL_W, trackY + trackH, C_BORDER);
        ctx.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, C_GOLD);
    }

    private void applyScrollFromMouse(int mouseY) {
        if (maxScroll <= 0) { scroll = 0; return; }
        int usable = Math.max(1, scrollTrackH - scrollThumbH);
        float ratio = (float) (mouseY - scrollTrackY) / usable;
        scroll = Math.max(0, Math.min(Math.round(ratio * maxScroll), maxScroll));
    }

    /** Ligne du catalogue : icône à gauche, prix et bouton d'action à droite. */
    private void renderRow(GuiGraphics ctx, int x, int y, int w, ShopEntry e, boolean hov, boolean buying) {
        int accent = buying ? C_GOLD : C_GREEN;
        ctx.fill(x, y, x + w, y + ROW_H, hov ? C_HOVER : C_PANEL);
        if (hov) {
            ctx.fill(x, y, x + w, y + 1, accent);
            ctx.fill(x, y + ROW_H - 1, x + w, y + ROW_H, accent);
            ctx.fill(x, y, x + 1, y + ROW_H, accent);
            ctx.fill(x + w - 1, y, x + w, y + ROW_H, accent);
        } else {
            ctx.fill(x, y, x + w, y + 1, C_BORDER);
            ctx.fill(x, y + ROW_H - 1, x + w, y + ROW_H, C_BORDER);
        }

        ctx.fill(x + 1, y + 1, x + 41, y + ROW_H - 1, C_BG);
        drawItemScaled(ctx, stackOf(e.itemId()), x + 21, y + ROW_H / 2, 2.0f);

        int tx = x + 50;
        ctx.drawString(font, truncate(FrenchItemNames.toDisplay(e.itemId()), w - 200), tx, y + 9, C_WHITE, false);
        String sub = buying
            ? (e.netFlow() >= 64 ? "§cPrix en hausse — très demandé"
                                 : (e.netFlow() <= -64 ? "§aPrix en baisse — abondant" : "§8Prix stable"))
            : "§8En stock : " + sellable.getOrDefault(e.itemId(), 0);
        ctx.drawString(font, sub, tx, y + 22, C_DIM, false);

        int btnW = 82, btnH = 22;
        int btnX = x + w - btnW - 10;
        int btnY = y + (ROW_H - btnH) / 2;
        int prix = buying ? e.buyPrice() : e.sellPrice();
        String price = prix + " ◆";
        ctx.drawString(font, price, btnX - font.width(price) - 14,
            y + (ROW_H - font.lineHeight) / 2, accent, false);
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, hov ? accent : C_STRIP);
        if (!hov) { ctx.fill(btnX, btnY, btnX + btnW, btnY + 1, C_BORDER); ctx.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, C_BORDER); }
        ctx.drawCenteredString(font, buying ? "Acheter" : "Vendre",
            btnX + btnW / 2, btnY + 7, hov ? C_BG : C_MID);
    }

    private void renderTooltip(GuiGraphics ctx, ShopEntry e, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§f" + FrenchItemNames.toDisplay(e.itemId())));
        lines.add(Component.literal("§7Achat : §6" + e.buyPrice() + " ◆"));
        lines.add(Component.literal("§7Rachat : §a" + e.sellPrice() + " ◆"));
        if (e.netFlow() >= 64)       lines.add(Component.literal("§8Très demandé — prix en hausse"));
        else if (e.netFlow() <= -64) lines.add(Component.literal("§8Abondant — prix en baisse"));
        ctx.renderTooltip(font, lines, java.util.Optional.empty(), mx, my);
    }

    // ── Modal quantité ────────────────────────────────────────────────────────

    private int modalMax() {
        if (selected == null) return 1;
        if (tab == Tab.ACHETER) {
            int p = selected.buyPrice();
            return Math.max(1, Math.min(2304, p > 0 ? balance / p : 1));
        }
        return Math.max(1, sellable.getOrDefault(selected.itemId(), 1));
    }

    private void renderModal(GuiGraphics ctx, int mx, int my) {
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 300);

        int mw = MODAL_W, mh = MODAL_H;
        int ox = winX + (winW - mw) / 2;
        int oy = winY + (winH - mh) / 2;
        boolean buying = tab == Tab.ACHETER;

        ctx.fill(ox, oy, ox + mw, oy + mh, C_SURFACE);
        int accent = buying ? C_GOLD : C_GREEN;
        ctx.fill(ox, oy, ox + mw, oy + 2, accent);

        String title = buying ? "Acheter" : "Vendre";
        ctx.drawString(font, title, ox + 14, oy + 12, C_WHITE, false);

        drawItemScaled(ctx, stackOf(selected.itemId()), ox + 30, oy + 52, 2.0f);
        ctx.drawString(font, truncate(FrenchItemNames.toDisplay(selected.itemId()), mw - 70),
            ox + 52, oy + 40, C_WHITE, false);
        int unit = buying ? selected.buyPrice() : selected.sellPrice();
        ctx.drawString(font, unit + " ◆ / unité", ox + 52, oy + 54, C_MID, false);

        qtyInput.setBounds(1, modalMax());
        qtyInput.render(ctx, font, ox + 14, oy + 76, mw - 28, mx, my);
        int qty = qtyInput.getValue();

        ctx.drawString(font, "Total : §6" + (unit * qty) + " ◆", ox + 14, oy + 128, C_MID, false);
        int after = buying ? balance - unit * qty : balance + unit * qty;
        ctx.drawString(font, "§8Solde après : " + after + " ◆", ox + 14, oy + 142, C_DIM, false);

        int cbY = oy + mh - 34;
        drawWideButton(ctx, ox + 14, cbY, 120, "Annuler", C_RED, mx, my);
        drawWideButton(ctx, ox + mw - 134, cbY, 120, buying ? "Acheter" : "Vendre", accent, mx, my);

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
        int tw = font.width(toastMsg) + 28;
        int tx = winX + winW - tw - 12;
        int ty = winY + winH - 38;
        ctx.fill(tx, ty, tx + tw, ty + 26, C_SURFACE);
        ctx.fill(tx, ty, tx + 3, ty + 26, toastOk ? C_GREEN : C_RED);
        ctx.drawString(font, toastMsg, tx + 11, ty + 9, C_WHITE, false);
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;

        if (selected != null) { handleModalClick(x, y); return true; }

        if (x < winX || x > winX + winW || y < winY || y > winY + winH) { onClose(); return true; }

        if (y <= winY + TOP_H - 1) { handleTabClick(x, y); return true; }

        if (tab == Tab.ACHETER
            && x >= parcheminBtnX && x < parcheminBtnX + parcheminBtnW
            && y >= parcheminBtnY && y < parcheminBtnY + 20) {
            send(ShopNetworking.ACTION_CLAIM_PARCHEMIN, "nouvelle-terre-bridge:parchemin", 1);
            return true;
        }

        // Drag de la scrollbar — piste mémorisée au dernier rendu
        if (maxScroll > 0 && x >= scrollTrackX - 2 && x <= scrollTrackX + SCROLL_W + 2
                && y >= scrollTrackY && y <= scrollTrackY + scrollTrackH) {
            draggingScroll = true;
            applyScrollFromMouse(y - scrollThumbH / 2);
            return true;
        }

        super.mouseClicked(mx0, my0, btn);

        if (hovered != null) {
            selected = hovered;
            qtyInput.setBounds(1, modalMax());
            qtyInput.clear();
            qtyInput.setFocused(false);
        }
        return true;
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

    private void handleTabClick(int mx, int my) {
        if (HubBackButton.clicked(winX + PAD, winY + (TOP_H - HubBackButton.H) / 2, mx, my)) return;
        int tx = tabsStartX;
        for (Tab t : Tab.values()) {
            String label = t == Tab.ACHETER ? "Acheter" : "Vendre";
            int tw = font.width(label) + 18;
            if (mx >= tx && mx <= tx + tw) {
                tab    = t;
                scroll = 0;
                if (t == Tab.VENDRE) refreshSellable();
                return;
            }
            tx += tw + 4;
        }
    }

    private void handleModalClick(int mx, int my) {
        int mw = MODAL_W, mh = MODAL_H;
        int ox = winX + (winW - mw) / 2;
        int oy = winY + (winH - mh) / 2;

        if (mx < ox || mx > ox + mw || my < oy || my > oy + mh) { selected = null; return; }

        if (qtyInput.mouseClicked(mx, my)) return;

        int cbY = oy + mh - 34;
        if (my >= cbY && my < cbY + 22) {
            if (mx >= ox + 14 && mx < ox + 134) { selected = null; return; }
            if (mx >= ox + mw - 134 && mx < ox + mw - 14) {
                send(tab == Tab.ACHETER ? ShopNetworking.ACTION_BUY : ShopNetworking.ACTION_SELL,
                     selected.itemId(), qtyInput.getValue());
                selected = null;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double amount) {
        if (selected != null) return true;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256 && selected != null) { selected = null; return true; }
        if (selected != null && qtyInput.keyPressed(key)) return true;
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char chr, int mod) {
        if (selected != null && qtyInput.charTyped(chr)) return true;
        return super.charTyped(chr, mod);
    }

    private void send(int action, String itemId, int quantity) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(action);
        buf.writeUtf(itemId);
        buf.writeInt(quantity);
        NtNet.versServeur(ShopNetworking.SHOP_ACTION, buf);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private ItemStack stackOf(String itemId) {
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
            return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
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
        if (font.width(s) <= maxW) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && font.width(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
        return sb + "…";
    }
}
