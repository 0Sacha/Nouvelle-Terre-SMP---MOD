package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.network.ProductionNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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

    public record ProdEntry(String itemId, long count, long seuil, int prix, int quantite, boolean enVente) {}

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

    private static final int MAX_PW = 520;
    private static final int MAX_PH = 420;
    private static final int TOP_H  = 46;
    private static final int PAD    = 12;
    private static final int ROW_H  = 30;

    private int pw, ph, px, py;

    // ── State ──────────────────────────────────────────────────────────────────

    private boolean isOp;
    private List<ProdEntry> entries;
    private int scroll = 0;

    private String  toastMsg;
    private boolean toastOk;
    private long    toastEnd;

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
        ctx.drawText(textRenderer, "⛏  Production naturelle", px + PAD, py + 9, C_GOLD, false);
        long enVente = entries.stream().filter(ProdEntry::enVente).count();
        ctx.drawText(textRenderer, "Seuil atteint → l'item apparaît au shop $Serveur  ·  §a"
            + enVente + " en vente§7 / " + entries.size(),
            px + PAD, py + 24, C_DIM, false);

        adminBtnBounds.clear();
        if (isOp) renderAdminButtons(ctx, mx, my);

        // Liste
        int listY = py + TOP_H + 1 + 4;
        int listH = ph - TOP_H - 1 - 8;
        int visRows = Math.max(1, listH / ROW_H);
        int maxScroll = Math.max(0, entries.size() - visRows);
        scroll = Math.min(scroll, maxScroll);

        for (int i = scroll; i < Math.min(scroll + visRows, entries.size()); i++) {
            renderRow(ctx, entries.get(i), px + 6, listY + (i - scroll) * ROW_H, pw - 18, mx, my);
        }

        // Scrollbar
        if (entries.size() > visRows) {
            int trackX = px + pw - 8;
            int trackH = visRows * ROW_H - 4;
            ctx.fill(trackX, listY, trackX + 4, listY + trackH, C_BORDER);
            float ratio  = (float) visRows / entries.size();
            int   thumbH = Math.max(16, (int)(trackH * ratio));
            int   thumbY = maxScroll > 0 ? listY + (int)((trackH - thumbH) * ((float) scroll / maxScroll)) : listY;
            ctx.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, C_GOLD);
        }

        renderToast(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void renderAdminButtons(DrawContext ctx, int mx, int my) {
        String[] labels  = {"Recheck", "Recharger", "Reset"};
        int[]    actions = {ProductionNetworking.ACTION_RECHECK, ProductionNetworking.ACTION_RELOAD, ProductionNetworking.ACTION_RESET};
        int bx = px + pw - PAD;
        for (int i = labels.length - 1; i >= 0; i--) {
            int bw = textRenderer.getWidth(labels[i]) + 14;
            bx -= bw;
            int by = py + 9;
            boolean danger = actions[i] == ProductionNetworking.ACTION_RESET;
            boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + 18;
            int base  = danger ? 0xFF3D0A16 : C_SURFACE;
            int hover = danger ? C_RED : C_HOVER;
            ctx.fill(bx, by, bx + bw, by + 18, hov ? hover : base);
            ctx.fill(bx, by, bx + bw, by + 1, danger ? C_RED : C_BORDER);
            ctx.drawText(textRenderer, labels[i], bx + 7, by + 5, danger ? C_WHITE : C_MID, false);
            adminBtnBounds.add(new int[]{bx, by, bw, 18, actions[i]});
            bx -= 6;
        }
    }

    private void renderRow(DrawContext ctx, ProdEntry e, int x, int y, int w, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
        ctx.fill(x, y, x + w, y + ROW_H - 2, hover ? C_HOVER : C_PANEL);
        ctx.fill(x, y, x + 2, y + ROW_H - 2, e.enVente() ? C_GREEN : C_BORDER);

        renderItemIcon(ctx, e.itemId(), x + 6, y + 6);
        String name = FrenchItemNames.toDisplay(e.itemId());
        ctx.drawText(textRenderer, truncate(name, w / 2 - 40), x + 28, y + 5, C_WHITE, false);

        // Barre de progression sous le nom
        int barX = x + 28, barW = w / 2 - 44;
        float pct = progressRatio(e);
        ctx.fill(barX, y + 17, barX + barW, y + 21, C_BORDER);
        if (pct > 0) ctx.fill(barX, y + 17, barX + (int)(barW * pct), y + 21,
            e.enVente() ? C_GREEN : C_GOLD);

        // Compteur au centre-droit
        String countStr = fmt(e.count()) + " / " + fmt(e.seuil());
        ctx.drawText(textRenderer, countStr, x + w / 2 + 10, y + 5,
            pct >= 1f ? C_GREEN : C_MID, false);

        // Statut à droite
        String status = e.enVente()
            ? "✔ En vente — " + e.prix() + " ◆/u"
            : ((int)(pct * 100)) + " %";
        int sw = textRenderer.getWidth(status);
        ctx.drawText(textRenderer, status, x + w - sw - 8, y + 5,
            e.enVente() ? C_GREEN : C_DIM, false);

        if (e.enVente()) {
            String lot = "lot de " + e.quantite() + " au /hdv";
            int lw = textRenderer.getWidth(lot);
            ctx.drawText(textRenderer, lot, x + w - lw - 8, y + 16, C_DIM, false);
        }
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
        if (x < px || x > px + pw || y < py || y > py + ph) { close(); return true; }

        for (int[] b : adminBtnBounds) {
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeInt(b[4]);
                ClientPlayNetworking.send(ProductionNetworking.PROD_ACTION, buf);
                return true;
            }
        }
        return super.mouseClicked(mx0, my0, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int listH = ph - TOP_H - 1 - 8;
        int visRows = Math.max(1, listH / ROW_H);
        int maxScroll = Math.max(0, entries.size() - visRows);
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll));
        return true;
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
