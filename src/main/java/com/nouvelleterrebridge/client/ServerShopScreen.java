package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.network.ShopNetworking;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shop Serveur — écran autonome, séparé du HDV entre joueurs.
 * Onglet Acheter : catalogue du serveur (stock illimité, prix dynamique).
 * Onglet Vendre  : revente au serveur au prix de rachat.
 */
@Environment(EnvType.CLIENT)
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
    private static final int COLS      = 4;
    private static final int CARD_H    = 104;
    private static final int GAP       = 8;
    private static final int SCROLL_W  = 4;

    private int winX, winY, winW, winH;

    private int balance;
    private List<ShopEntry> entries;

    private Tab tab = Tab.ACHETER;
    private int scroll = 0;
    private int maxScroll = 0;

    private TextFieldWidget searchField;

    private ShopEntry hovered = null;
    private ShopEntry selected = null;
    private int qty = 1;

    private String toastMsg = null;
    private boolean toastOk = true;
    private long toastEnd = 0;

    /** Inventaire vendable du joueur : itemId → quantité (piles vierges uniquement). */
    private Map<String, Integer> sellable = new LinkedHashMap<>();

    public ServerShopScreen(int balance, List<ShopEntry> entries) {
        super(Text.literal("Shop Serveur"));
        this.balance = balance;
        this.entries = new ArrayList<>(entries);
    }

    @Override
    protected void init() {
        computeWin();
        searchField = new TextFieldWidget(textRenderer, winX + PAD, winY + TOP_H + PAD, 200, 18,
            Text.literal("Rechercher"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Rechercher..."));
        searchField.setChangedListener(s -> scroll = 0);
        addSelectableChild(searchField);
        refreshSellable();
    }

    @Override
    public boolean shouldPause() {
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
        if (client == null || client.player == null) return;
        for (ItemStack s : client.player.getInventory().main) {
            if (s.isEmpty() || s.hasNbt() || s.isDamaged()) continue;
            String id = Registries.ITEM.getId(s.getItem()).toString();
            sellable.merge(id, s.getCount(), Integer::sum);
        }
    }

    public void handleResult(boolean ok, String msg, int newBalance, List<ShopEntry> newEntries) {
        balance  = newBalance;
        entries  = new ArrayList<>(newEntries);
        selected = null;
        qty      = 1;
        refreshSellable();
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", "");
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 3200;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
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

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(winX, winY, winX + winW, winY + TOP_H, 0xE01B1D22);
        ctx.fill(winX, winY + TOP_H - 1, winX + winW, winY + TOP_H, C_BORDER);

        int ty = winY + (TOP_H - textRenderer.fontHeight) / 2;
        int tx = winX + PAD;
        ctx.drawText(textRenderer, "SHOP", tx, ty, C_GOLD, false);
        tx += textRenderer.getWidth("SHOP") + 8;
        ctx.fill(tx, winY + (TOP_H - 16) / 2, tx + 1, winY + (TOP_H + 16) / 2, C_BORDER);
        tx += 9;
        ctx.drawText(textRenderer, "Serveur", tx, ty, C_MID, false);
        tx += textRenderer.getWidth("Serveur") + 20;

        for (Tab t : Tab.values()) {
            String label = t == Tab.ACHETER ? "Acheter" : "Vendre";
            int tw = textRenderer.getWidth(label) + 18;
            boolean active = tab == t;
            boolean hov = mx >= tx && mx <= tx + tw && my >= winY && my <= winY + TOP_H - 1;
            int tabY = winY + (TOP_H - 22) / 2;
            if (active) {
                ctx.fill(tx, tabY, tx + tw, tabY + 22, C_GOLD);
                ctx.drawText(textRenderer, label, tx + tw / 2 - textRenderer.getWidth(label) / 2, tabY + 7, C_BG, false);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer, label, tx + tw / 2, tabY + 7, hov ? C_WHITE : C_DIM);
            }
            tx += tw + 4;
        }

        String bal = balance + " ◆";
        int bw = textRenderer.getWidth(bal) + 18;
        int bx = winX + winW - bw - PAD;
        int by = winY + (TOP_H - 20) / 2;
        ctx.fill(bx, by, bx + bw, by + 20, C_STRIP);
        ctx.fill(bx, by, bx + 2, by + 20, C_GOLD);
        ctx.drawText(textRenderer, bal, bx + 10, by + 6, C_GOLD, false);
    }

    private String query() {
        return searchField != null ? searchField.getText().trim().toLowerCase() : "";
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

    private void renderBuyGrid(DrawContext ctx, int mx, int my) {
        renderSearch(ctx, mx, my);
        List<ShopEntry> list = buyList();
        renderGrid(ctx, mx, my, list, true);
        if (list.isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer, "Aucun article au catalogue.",
                winX + winW / 2, winY + winH / 2, C_DIM);
    }

    private void renderSellGrid(DrawContext ctx, int mx, int my) {
        renderSearch(ctx, mx, my);
        List<ShopEntry> list = sellList();
        renderGrid(ctx, mx, my, list, false);
        if (list.isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer,
                "Rien à vendre — le serveur ne rachète que les objets vierges.",
                winX + winW / 2, winY + winH / 2, C_DIM);
    }

    private void renderSearch(DrawContext ctx, int mx, int my) {
        if (searchField == null) return;
        searchField.setX(winX + PAD);
        searchField.setY(winY + TOP_H + PAD);
        ctx.fill(winX + PAD - 1, winY + TOP_H + PAD - 1,
                 winX + PAD + 201, winY + TOP_H + PAD + 19, C_BORDER);
        searchField.render(ctx, mx, my, 0);
    }

    private void renderGrid(DrawContext ctx, int mx, int my, List<ShopEntry> list, boolean buying) {
        int gx = winX + PAD;
        int gy = winY + TOP_H + PAD + 26;
        int gw = winW - PAD * 2 - SCROLL_W - 4;
        int gh = winY + winH - PAD - gy;

        int cardW   = (gw - (COLS - 1) * GAP) / COLS;
        int visRows = Math.max(1, (gh + GAP) / (CARD_H + GAP));
        int rows    = (int) Math.ceil((double) list.size() / COLS);

        maxScroll = Math.max(0, rows - visRows);
        scroll    = Math.max(0, Math.min(scroll, maxScroll));
        int start = scroll * COLS;

        if (maxScroll > 0) {
            int sbX = gx + gw + 2;
            ctx.fill(sbX, gy, sbX + SCROLL_W, gy + gh, C_BORDER);
            int thumbH = Math.max(20, gh * visRows / rows);
            int thumbY = gy + (gh - thumbH) * scroll / maxScroll;
            ctx.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH, 0x60FFFFFF);
        }

        ctx.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = start; i < list.size(); i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            if (row > visRows) break;
            int cx = gx + col * (cardW + GAP);
            int cy = gy + row * (CARD_H + GAP);
            boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H
                       && my >= gy && my < gy + gh;
            if (hov) hovered = list.get(i);
            renderCard(ctx, cx, cy, cardW, list.get(i), hov, buying);
        }
        ctx.disableScissor();
    }

    private void renderCard(DrawContext ctx, int x, int y, int w, ShopEntry e, boolean hov, boolean buying) {
        ctx.fill(x, y, x + w, y + CARD_H, hov ? C_HOVER : C_PANEL);
        int accent = hov ? (buying ? C_GOLD : C_GREEN) : C_BORDER;
        ctx.fill(x, y, x + w, y + 1, accent);
        ctx.fill(x, y + CARD_H - 1, x + w, y + CARD_H, accent);
        ctx.fill(x, y, x + 1, y + CARD_H, accent);
        ctx.fill(x + w - 1, y, x + w, y + CARD_H, accent);

        int iconH = 46;
        ctx.fill(x + 1, y + 1, x + w - 1, y + iconH, C_BG);
        drawItemScaled(ctx, stackOf(e.itemId()), x + w / 2, y + iconH / 2, 2.0f);

        String name = truncate(FrenchItemNames.toDisplay(e.itemId()), w - 8);
        ctx.drawCenteredTextWithShadow(textRenderer, name, x + w / 2, y + iconH + 5, C_WHITE);

        if (buying) {
            String trend = e.netFlow() >= 64 ? "§c▲" : (e.netFlow() <= -64 ? "§a▼" : "§8=");
            ctx.drawCenteredTextWithShadow(textRenderer, trend, x + w / 2, y + iconH + 17, C_DIM);
        } else {
            int owned = sellable.getOrDefault(e.itemId(), 0);
            ctx.drawCenteredTextWithShadow(textRenderer, "en stock : " + owned,
                x + w / 2, y + iconH + 17, C_DIM);
        }

        int stripY = y + CARD_H - 24;
        ctx.fill(x + 1, stripY, x + w - 1, y + CARD_H - 1, buying ? C_STRIP : 0x152EAD6B);
        ctx.fill(x + 1, stripY, x + w - 1, stripY + 1, C_BORDER);
        int prix = buying ? e.buyPrice() : e.sellPrice();
        ctx.drawCenteredTextWithShadow(textRenderer, prix + " ◆",
            x + w / 2, stripY + 7, buying ? C_GOLD : C_GREEN);
    }

    private void renderTooltip(DrawContext ctx, ShopEntry e, int mx, int my) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("§f" + FrenchItemNames.toDisplay(e.itemId())));
        lines.add(Text.literal("§7Achat : §6" + e.buyPrice() + " ◆"));
        lines.add(Text.literal("§7Rachat : §a" + e.sellPrice() + " ◆"));
        if (e.netFlow() >= 64)       lines.add(Text.literal("§8Très demandé — prix en hausse"));
        else if (e.netFlow() <= -64) lines.add(Text.literal("§8Abondant — prix en baisse"));
        ctx.drawTooltip(textRenderer, lines, mx, my);
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

    private void renderModal(DrawContext ctx, int mx, int my) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);

        int mw = 300, mh = 190;
        int ox = winX + (winW - mw) / 2;
        int oy = winY + (winH - mh) / 2;
        boolean buying = tab == Tab.ACHETER;

        ctx.fill(ox, oy, ox + mw, oy + mh, C_SURFACE);
        int accent = buying ? C_GOLD : C_GREEN;
        ctx.fill(ox, oy, ox + mw, oy + 2, accent);

        String title = buying ? "Acheter" : "Vendre";
        ctx.drawText(textRenderer, title, ox + 14, oy + 12, C_WHITE, false);

        drawItemScaled(ctx, stackOf(selected.itemId()), ox + 30, oy + 52, 2.0f);
        ctx.drawText(textRenderer, truncate(FrenchItemNames.toDisplay(selected.itemId()), mw - 70),
            ox + 52, oy + 40, C_WHITE, false);
        int unit = buying ? selected.buyPrice() : selected.sellPrice();
        ctx.drawText(textRenderer, unit + " ◆ / unité", ox + 52, oy + 54, C_MID, false);

        int max = modalMax();
        qty = Math.max(1, Math.min(qty, max));

        int by = oy + 84;
        drawButton(ctx, ox + 14, by, 24, "-", mx, my);
        String q = String.valueOf(qty);
        ctx.fill(ox + 42, by, ox + 102, by + 20, C_STRIP);
        ctx.drawCenteredTextWithShadow(textRenderer, q, ox + 72, by + 6, C_WHITE);
        drawButton(ctx, ox + 106, by, 24, "+", mx, my);
        drawButton(ctx, ox + 134, by, 40, "max", mx, my);

        ctx.drawText(textRenderer, "Total : §6" + (unit * qty) + " ◆", ox + 14, oy + 116, C_MID, false);
        if (buying)
            ctx.drawText(textRenderer, "§8Solde après : " + (balance - unit * qty) + " ◆", ox + 14, oy + 130, C_DIM, false);
        else
            ctx.drawText(textRenderer, "§8Solde après : " + (balance + unit * qty) + " ◆", ox + 14, oy + 130, C_DIM, false);

        int cbY = oy + mh - 34;
        drawWideButton(ctx, ox + 14, cbY, 120, "Annuler", C_RED, mx, my);
        drawWideButton(ctx, ox + mw - 134, cbY, 120, buying ? "Acheter" : "Vendre", accent, mx, my);

        ctx.getMatrices().pop();
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, String label, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 20;
        ctx.fill(x, y, x + w, y + 20, hov ? C_HOVER : C_STRIP);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 6, hov ? C_WHITE : C_MID);
    }

    private void drawWideButton(DrawContext ctx, int x, int y, int w, String label, int color, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 22;
        ctx.fill(x, y, x + w, y + 22, hov ? color : C_STRIP);
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 7, hov ? C_BG : C_WHITE);
    }

    private void renderToast(DrawContext ctx) {
        if (toastMsg == null) return;
        if (System.currentTimeMillis() > toastEnd) { toastMsg = null; return; }
        int tw = textRenderer.getWidth(toastMsg) + 28;
        int tx = winX + winW - tw - 12;
        int ty = winY + winH - 38;
        ctx.fill(tx, ty, tx + tw, ty + 26, C_SURFACE);
        ctx.fill(tx, ty, tx + 3, ty + 26, toastOk ? C_GREEN : C_RED);
        ctx.drawText(textRenderer, toastMsg, tx + 11, ty + 9, C_WHITE, false);
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;

        if (selected != null) { handleModalClick(x, y); return true; }

        if (x < winX || x > winX + winW || y < winY || y > winY + winH) { close(); return true; }

        if (y <= winY + TOP_H - 1) { handleTabClick(x, y); return true; }

        super.mouseClicked(mx0, my0, btn);

        if (hovered != null) { selected = hovered; qty = 1; return true; }
        return true;
    }

    private void handleTabClick(int mx, int my) {
        int tx = winX + PAD + textRenderer.getWidth("SHOP") + 17
               + textRenderer.getWidth("Serveur") + 20;
        for (Tab t : Tab.values()) {
            String label = t == Tab.ACHETER ? "Acheter" : "Vendre";
            int tw = textRenderer.getWidth(label) + 18;
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
        int mw = 300, mh = 190;
        int ox = winX + (winW - mw) / 2;
        int oy = winY + (winH - mh) / 2;

        if (mx < ox || mx > ox + mw || my < oy || my > oy + mh) { selected = null; return; }

        int max = modalMax();
        int by = oy + 84;
        if (my >= by && my < by + 20) {
            if (mx >= ox + 14  && mx < ox + 38)  { qty = Math.max(1, qty - 1);   return; }
            if (mx >= ox + 106 && mx < ox + 130) { qty = Math.min(max, qty + 1); return; }
            if (mx >= ox + 134 && mx < ox + 174) { qty = max;                    return; }
        }

        int cbY = oy + mh - 34;
        if (my >= cbY && my < cbY + 22) {
            if (mx >= ox + 14 && mx < ox + 134) { selected = null; return; }
            if (mx >= ox + mw - 134 && mx < ox + mw - 14) {
                send(tab == Tab.ACHETER ? ShopNetworking.ACTION_BUY : ShopNetworking.ACTION_SELL,
                     selected.itemId(), qty);
                selected = null;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (selected != null) return true;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256 && selected != null) { selected = null; return true; }
        return super.keyPressed(key, scan, mod);
    }

    private void send(int action, String itemId, int quantity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(action);
        buf.writeString(itemId);
        buf.writeInt(quantity);
        ClientPlayNetworking.send(ShopNetworking.SHOP_ACTION, buf);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private ItemStack stackOf(String itemId) {
        try {
            Item item = Registries.ITEM.get(Identifier.tryParse(itemId));
            return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private void drawItemScaled(DrawContext ctx, ItemStack stack, int cx, int cy, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx - 8 * scale, cy - 8 * scale, 0);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawItem(stack, 0, 0);
        ctx.getMatrices().pop();
    }

    private String truncate(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && textRenderer.getWidth(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
        return sb + "…";
    }
}
