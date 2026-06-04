package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.HdvNetworking;
import com.nouvelleterrebridge.shop.FrenchItemNames;
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
import io.netty.buffer.Unpooled;

import java.util.*;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class HdvScreen extends Screen {

    // ── Data ──────────────────────────────────────────────────────────────────

    public record ListingData(int id, String seller, String itemId, int quantity, int pricePerUnit) {}
    private record SellItem(Item item, String itemId, int qty) {}

    private enum Tab {
        MARKET("🏪  Marché"),
        SELL("💰  Vendre"),
        MY_SHOP("🛒  Mon Shop"),
        SHOPS("👥  Boutiques");

        final String label;
        Tab(String label) { this.label = label; }
    }

    // ── Couleurs ──────────────────────────────────────────────────────────────

    private static final int C_BG       = 0xFF1e1f22;
    private static final int C_PANEL    = 0xFF2b2d31;
    private static final int C_HOVER    = 0xFF313338;
    private static final int C_GOLD     = 0xFFf0b232;
    private static final int C_GOLD_DIM = 0x22f0b232;
    private static final int C_SEP      = 0xFF3a3c40;
    private static final int C_RED      = 0xFFd4183d;
    private static final int C_RED_DIM  = 0x18d4183d;
    private static final int C_GREEN    = 0xFF23a55a;
    private static final int C_WHITE    = 0xFFffffff;
    private static final int C_MID      = 0xFFb5bac1;
    private static final int C_DIM      = 0xFF72767d;
    private static final int C_DARK     = 0xFF4f5258;

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int TOP_H    = 50;
    private static final int SIDE_W   = 142;
    private static final int PAD      = 14;
    private static final int COLS     = 5;
    private static final int CARD_H   = 110;
    private static final int GAP      = 8;
    private static final int MODAL_W  = 320;
    private static final int MODAL_H  = 250;

    // ── Catégories ────────────────────────────────────────────────────────────

    private static final String[][] CATS = {
        {"tous",       "Tout"},
        {"minerais",   "Minerais"},
        {"nourriture", "Nourriture"},
        {"bois",       "Bois & Blocs"},
        {"outils",     "Outils & Armes"},
        {"divers",     "Divers"},
    };

    private static final Map<String, String[]> CAT_KW = new HashMap<>();
    static {
        CAT_KW.put("minerais",   new String[]{"diamond","iron","gold","coal","emerald","lapis","redstone","quartz","amethyst","netherite","obsidian","cobblestone","gravel","stone","granite","diorite","calcite","tuff","deepslate","copper"});
        CAT_KW.put("nourriture", new String[]{"bread","beef","pork","chicken","cod","salmon","apple","carrot","potato","melon","pumpkin","mushroom","wheat","egg","honey","cake","cookie","berry","rabbit","mutton","sugar","milk"});
        CAT_KW.put("bois",       new String[]{"log","wood","plank","stick","fence","door","trapdoor","slab","stair","button","pressure","barrel","bookshelf","sapling","leaves","bamboo","concrete","wool","glass","terracotta","sand","dirt","grass","clay","brick","nether_brick","end_stone","purpur","basalt","blackstone","sandstone","mossy","cobbled","mud","mangrove","cherry","azalea"});
        CAT_KW.put("outils",     new String[]{"pickaxe","axe","shovel","hoe","sword","bow","crossbow","shield","helmet","chestplate","leggings","boots","trident","elytra","armor","shears","flint_and_steel","fishing_rod","spyglass"});
        CAT_KW.put("divers",     new String[]{"book","enchanted_book","paper","name_tag","lead","saddle","firework","ender_eye","ender_pearl","nether_star","nautilus","compass","clock","map","blaze_rod","bottle","potion","experience_bottle","shulker_box","dragon_egg","wither","totem","heart","phantom","turtle","scute","ink","dye","feather","string","gunpowder","slime","magma_cream","ghast","bone","rotten_flesh","leather","blaze_powder"});
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private int balance;
    private List<ListingData> listings;

    private Tab activeTab = Tab.MARKET;
    private String activeCategory = "tous";
    private int scrollOffset = 0;

    // Marché
    private TextFieldWidget searchField;
    private ListingData hoveredCard = null;
    private ListingData buyingListing = null;
    private int buyQty = 1;

    // Vente
    private List<SellItem> sellInv = new ArrayList<>();
    private SellItem selectedSellItem = null;
    private int sellQty = 1;
    private int sellPrice = 0;
    private TextFieldWidget sellQtyField;
    private TextFieldWidget sellPriceField;

    // Boutiques
    private String selectedShop = null;

    // Toast
    private String toastMsg = null;
    private boolean toastOk = true;
    private long toastEnd = 0;

    // ── Constructeur ──────────────────────────────────────────────────────────

    public HdvScreen(int balance, List<ListingData> listings) {
        super(Text.literal("HDV — Nouvelle Terre"));
        this.balance  = balance;
        this.listings = new ArrayList<>(listings);
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int cx = SIDE_W + PAD;

        searchField = new TextFieldWidget(textRenderer, cx, TOP_H + PAD, 180, 18, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Rechercher..."));
        searchField.setChangedListener(s -> scrollOffset = 0);
        addSelectableChild(searchField);

        sellQtyField = new TextFieldWidget(textRenderer, 0, 0, 100, 18, Text.literal("1"));
        sellQtyField.setText("1");
        sellQtyField.setChangedListener(s -> {
            try { sellQty = Math.max(1, Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) { sellQty = 1; }
        });
        addSelectableChild(sellQtyField);

        sellPriceField = new TextFieldWidget(textRenderer, 0, 0, 100, 18, Text.literal(""));
        sellPriceField.setPlaceholder(Text.literal("Prix/u..."));
        sellPriceField.setChangedListener(s -> {
            try { sellPrice = Math.max(0, Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) { sellPrice = 0; }
        });
        addSelectableChild(sellPriceField);

        refreshSellInv();
    }

    private void refreshSellInv() {
        if (client == null || client.player == null) return;
        Map<String, SellItem> byId = new LinkedHashMap<>();
        for (ItemStack stack : client.player.getInventory().main) {
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            byId.merge(id, new SellItem(stack.getItem(), id, stack.getCount()),
                (a, b) -> new SellItem(a.item(), a.itemId(), a.qty() + b.qty()));
        }
        sellInv = new ArrayList<>(byId.values());
    }

    // ── Résultat réseau ───────────────────────────────────────────────────────

    public void handleResult(boolean ok, String msg, int newBalance, List<ListingData> newListings) {
        balance  = newBalance;
        listings = new ArrayList<>(newListings);
        buyingListing    = null;
        selectedSellItem = null;
        sellQtyField.setText("1");
        sellPriceField.setText("");
        sellQty   = 1;
        sellPrice = 0;
        refreshSellInv();
        toast(msg, ok);
    }

    private void toast(String msg, boolean ok) {
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", ""); // strip color codes
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 3200;
    }

    // ── Render principal ──────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
        renderTopBar(ctx, mx, my);

        switch (activeTab) {
            case MARKET  -> { renderSidebar(ctx, mx, my); renderMarket(ctx, mx, my); }
            case SELL    -> renderSell(ctx, mx, my);
            case MY_SHOP -> renderMyShop(ctx, mx, my);
            case SHOPS   -> renderShops(ctx, mx, my);
        }

        if (buyingListing != null) renderBuyModal(ctx, mx, my);
        renderToast(ctx);
        super.render(ctx, mx, my, delta);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, TOP_H, C_PANEL);
        ctx.fill(0, TOP_H - 1, width, TOP_H, C_SEP);

        // Titre
        int tx = PAD;
        ctx.drawText(textRenderer, "HDV", tx, (TOP_H - textRenderer.fontHeight) / 2, C_GOLD, false);
        tx += textRenderer.getWidth("HDV") + 4;
        ctx.drawText(textRenderer, "Nouvelle Terre", tx, (TOP_H - textRenderer.fontHeight) / 2, C_WHITE, false);
        tx += textRenderer.getWidth("Nouvelle Terre") + 24;

        // Onglets
        for (Tab tab : Tab.values()) {
            boolean active = activeTab == tab;
            int tw = textRenderer.getWidth(tab.label) + 18;
            int ty = (TOP_H - textRenderer.fontHeight) / 2;
            boolean hov = mx >= tx && mx <= tx + tw && my >= 0 && my <= TOP_H - 1;
            int col = active ? C_GOLD : (hov ? C_MID : C_DIM);
            ctx.drawText(textRenderer, tab.label, tx + 9, ty, col, false);
            if (active) ctx.fill(tx, TOP_H - 2, tx + tw, TOP_H - 1, C_GOLD);
            tx += tw + 4;
        }

        // Solde
        String bal = balance + " 💎";
        int bw = textRenderer.getWidth(bal) + 20;
        int bx = width - bw - PAD;
        ctx.fill(bx - 4, (TOP_H - 20) / 2, bx + bw, (TOP_H + 20) / 2, C_HOVER);
        ctx.drawText(textRenderer, bal, bx + 6, (TOP_H - textRenderer.fontHeight) / 2, C_GOLD, false);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private void renderSidebar(DrawContext ctx, int mx, int my) {
        ctx.fill(0, TOP_H, SIDE_W, height, C_PANEL);
        ctx.fill(SIDE_W - 1, TOP_H, SIDE_W, height, C_SEP);

        int y = TOP_H + PAD;
        ctx.drawText(textRenderer, "CATÉGORIES", PAD, y, C_DIM, false);
        y += textRenderer.fontHeight + 10;

        for (String[] cat : CATS) {
            boolean active = activeCategory.equals(cat[0]);
            int rh = textRenderer.fontHeight + 8;
            boolean hov = mx >= 0 && mx < SIDE_W && my >= y - 2 && my <= y + rh - 2;
            if (active)    ctx.fill(0, y - 2, SIDE_W - 1, y + rh - 2, C_HOVER);
            else if (hov)  ctx.fill(0, y - 2, SIDE_W - 1, y + rh - 2, 0x0AFFFFFF);
            if (active)    ctx.fill(0, y - 2, 3, y + rh - 2, C_GOLD);
            ctx.drawText(textRenderer, cat[1], PAD + 6, y, active ? C_GOLD : (hov ? C_MID : C_DIM), false);
            y += rh;
        }
    }

    // ── Marché ────────────────────────────────────────────────────────────────

    private List<ListingData> filteredListings() {
        String q  = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        return listings.stream()
            .filter(l -> !l.seller().equalsIgnoreCase(me))
            .filter(l -> matchCat(l.itemId(), activeCategory))
            .filter(l -> q.isEmpty()
                || FrenchItemNames.toDisplay(l.itemId()).toLowerCase().contains(q)
                || l.seller().toLowerCase().contains(q)
                || l.itemId().toLowerCase().contains(q))
            .sorted(Comparator.comparingInt(ListingData::pricePerUnit))
            .toList();
    }

    private boolean matchCat(String itemId, String cat) {
        if ("tous".equals(cat)) return true;
        String[] kws = CAT_KW.get(cat);
        if (kws == null) return true;
        String id = itemId.toLowerCase();
        for (String kw : kws) if (id.contains(kw)) return true;
        return false;
    }

    private void renderMarket(DrawContext ctx, int mx, int my) {
        int cx = SIDE_W + PAD;
        int cw = width - cx - PAD;

        // Barre de recherche
        if (searchField != null) {
            searchField.setX(cx);
            searchField.setY(TOP_H + PAD);
            searchField.setWidth(Math.min(200, cw / 2));
            ctx.fill(cx - 1, TOP_H + PAD - 1, cx + searchField.getWidth() + 1, TOP_H + PAD + 19, C_SEP);
            searchField.render(ctx, mx, my, 0);
        }

        int gridY = TOP_H + PAD + 26;
        int gridH = height - gridY - 28;
        int cardW = (cw - (COLS - 1) * GAP) / COLS;

        List<ListingData> items = filteredListings();
        int visRows  = Math.max(1, gridH / (CARD_H + GAP));
        int maxScroll = Math.max(0, (int) Math.ceil((double) items.size() / COLS) - visRows);
        scrollOffset  = Math.min(scrollOffset, maxScroll);
        int start = scrollOffset * COLS;

        ctx.enableScissor(cx, gridY, cx + cw, gridY + gridH);
        hoveredCard = null;
        for (int i = start; i < items.size(); i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            if (row >= visRows + 1) break;
            int x = cx + col * (cardW + GAP);
            int y = gridY + row * (CARD_H + GAP);
            boolean hov = mx >= x && mx < x + cardW && my >= y && my < y + CARD_H;
            if (hov) hoveredCard = items.get(i);
            renderCard(ctx, x, y, cardW, CARD_H, items.get(i), hov, false);
        }
        ctx.disableScissor();

        if (items.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Aucun article disponible", cx + cw / 2, gridY + gridH / 2, C_DIM);
        }

        // Pagination
        String pg = items.size() + " article" + (items.size() > 1 ? "s" : "");
        ctx.drawCenteredTextWithShadow(textRenderer, pg, cx + cw / 2, height - 20, C_DIM);
    }

    private void renderCard(DrawContext ctx, int x, int y, int w, int h, ListingData l, boolean hov, boolean isOwn) {
        ctx.fill(x, y, x + w, y + h, hov ? C_HOVER : C_PANEL);
        if (hov) {
            ctx.fill(x, y,         x + w,     y + 1,     C_GOLD);
            ctx.fill(x, y + h - 1, x + w,     y + h,     C_GOLD);
            ctx.fill(x, y,         x + 1,     y + h,     C_GOLD);
            ctx.fill(x + w - 1, y, x + w,     y + h,     C_GOLD);
        }

        // Item icon (centré, 16×16)
        ItemStack stack = itemStack(l.itemId());
        int ix = x + w / 2 - 8;
        int iy = y + 10;
        ctx.drawItem(stack, ix, iy);

        // Nom
        String name = truncate(FrenchItemNames.toDisplay(l.itemId()), w - 8);
        ctx.drawCenteredTextWithShadow(textRenderer, name, x + w / 2, iy + 20, C_WHITE);

        // Vendeur
        ctx.drawCenteredTextWithShadow(textRenderer, l.seller(), x + w / 2, iy + 31, C_DIM);

        // Stock + prix
        int by = y + h - 18;
        ctx.drawText(textRenderer, "x" + l.quantity(), x + 5, by, C_DARK, false);
        String price = l.pricePerUnit() + " 💎";
        ctx.drawText(textRenderer, price, x + w - textRenderer.getWidth(price) - 5, by, C_GOLD, false);

        // Tag "MOI" si propriétaire
        if (isOwn) {
            int tw = textRenderer.getWidth("MOI") + 6;
            ctx.fill(x + w - tw - 3, y + 3, x + w - 3, y + 13, C_GOLD_DIM);
            ctx.drawText(textRenderer, "MOI", x + w - tw, y + 4, C_GOLD, false);
        }
    }

    // ── Modal achat ───────────────────────────────────────────────────────────

    private void renderBuyModal(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0x88000000);
        ListingData l = buyingListing;
        int x = (width - MODAL_W) / 2;
        int y = (height - MODAL_H) / 2;

        ctx.fill(x, y, x + MODAL_W, y + MODAL_H, C_PANEL);
        ctx.fill(x, y, x + MODAL_W, y + 1, C_SEP);
        ctx.fill(x, y + MODAL_H - 1, x + MODAL_W, y + MODAL_H, C_SEP);
        ctx.fill(x, y, x + 1, y + MODAL_H, C_SEP);
        ctx.fill(x + MODAL_W - 1, y, x + MODAL_W, y + MODAL_H, C_SEP);

        int fy = y + 16;

        // Icône + nom + vendeur
        ctx.drawItem(itemStack(l.itemId()), x + 14, fy + 2);
        ctx.drawText(textRenderer, FrenchItemNames.toDisplay(l.itemId()), x + 36, fy, C_WHITE, false);
        ctx.drawText(textRenderer, "Vendeur: " + l.seller(), x + 36, fy + 12, C_DIM, false);
        fy += 34;

        // Infos
        ctx.fill(x + 10, fy, x + MODAL_W - 10, fy + 70, C_BG);
        ctx.drawText(textRenderer, "Prix unitaire", x + 18, fy + 8, C_DIM, false);
        String pu = l.pricePerUnit() + " 💎";
        ctx.drawText(textRenderer, pu, x + MODAL_W - textRenderer.getWidth(pu) - 18, fy + 8, C_GOLD, false);
        ctx.drawText(textRenderer, "Stock disponible", x + 18, fy + 20, C_DIM, false);
        String st = "x" + l.quantity();
        ctx.drawText(textRenderer, st, x + MODAL_W - textRenderer.getWidth(st) - 18, fy + 20, C_MID, false);

        ctx.fill(x + 10, fy + 33, x + MODAL_W - 10, fy + 34, C_SEP);

        // Sélecteur quantité
        ctx.drawText(textRenderer, "Quantité", x + 18, fy + 40, C_DIM, false);
        int bx = x + MODAL_W - 90;
        int by2 = fy + 36;
        // bouton −
        ctx.fill(bx, by2, bx + 22, by2 + 18, C_SEP);
        ctx.drawCenteredTextWithShadow(textRenderer, "−", bx + 11, by2 + 5, C_WHITE);
        // quantité
        ctx.drawCenteredTextWithShadow(textRenderer, String.valueOf(buyQty), bx + 34, by2 + 5, C_WHITE);
        // bouton +
        ctx.fill(bx + 46, by2, bx + 68, by2 + 18, C_SEP);
        ctx.drawCenteredTextWithShadow(textRenderer, "+", bx + 57, by2 + 5, C_WHITE);
        fy += 80;

        // Total
        int total = l.pricePerUnit() * buyQty;
        boolean canAfford = balance >= total;
        ctx.drawText(textRenderer, "Total", x + 18, fy, C_MID, false);
        String tot = total + " 💎";
        ctx.drawText(textRenderer, tot, x + MODAL_W - textRenderer.getWidth(tot) - 18, fy, canAfford ? C_GOLD : C_RED, false);
        fy += 16;

        if (!canAfford) {
            ctx.fill(x + 10, fy, x + MODAL_W - 10, fy + 16, C_RED_DIM);
            ctx.drawText(textRenderer, "Solde insuffisant (" + balance + " 💎)", x + 16, fy + 4, C_RED, false);
            fy += 22;
        }

        // Boutons
        int btnY = y + MODAL_H - 36;
        int half = MODAL_W / 2 - 12;
        ctx.fill(x + 8, btnY, x + 8 + half, btnY + 24, C_SEP);
        ctx.drawCenteredTextWithShadow(textRenderer, "Annuler", x + 8 + half / 2, btnY + 8, C_MID);
        ctx.fill(x + MODAL_W - 8 - half, btnY, x + MODAL_W - 8, btnY + 24, canAfford ? C_GOLD : C_SEP);
        ctx.drawCenteredTextWithShadow(textRenderer, "Confirmer", x + MODAL_W - 8 - half / 2, btnY + 8, canAfford ? C_BG : C_DARK);
    }

    // ── Onglet Vendre ─────────────────────────────────────────────────────────

    private void renderSell(DrawContext ctx, int mx, int my) {
        int formW = 290;
        int formX = width - formW - PAD;
        int invW  = formX - PAD * 2;
        int py    = TOP_H + PAD;

        // Label inventaire
        ctx.drawText(textRenderer, "INVENTAIRE — " + sellInv.size() + " items", PAD, py, C_DIM, false);
        py += textRenderer.fontHeight + 8;

        // Grille inventaire
        int cellCols = 5;
        int cellW = (invW - (cellCols - 1) * GAP) / cellCols;
        int cellH = 80;

        ctx.enableScissor(PAD, py, PAD + invW, height - PAD);
        for (int i = 0; i < sellInv.size(); i++) {
            SellItem si = sellInv.get(i);
            int col = i % cellCols;
            int row = i / cellCols;
            int cx  = PAD + col * (cellW + GAP);
            int cy  = py + row * (cellH + GAP);
            boolean sel = selectedSellItem != null && selectedSellItem.itemId().equals(si.itemId());
            boolean hov = mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH;
            ctx.fill(cx, cy, cx + cellW, cy + cellH, sel ? C_HOVER : (hov ? 0xFF25262a : C_PANEL));
            if (sel) {
                ctx.fill(cx, cy, cx + cellW, cy + 1, C_GOLD);
                ctx.fill(cx, cy + cellH - 1, cx + cellW, cy + cellH, C_GOLD);
                ctx.fill(cx, cy, cx + 1, cy + cellH, C_GOLD);
                ctx.fill(cx + cellW - 1, cy, cx + cellW, cy + cellH, C_GOLD);
            }
            ctx.drawItem(new ItemStack(si.item()), cx + cellW / 2 - 8, cy + 8);
            String name = truncate(FrenchItemNames.toDisplay(si.itemId()), cellW - 4);
            ctx.drawCenteredTextWithShadow(textRenderer, name, cx + cellW / 2, cy + 28, sel ? C_WHITE : C_MID);
            String badge = "x" + si.qty();
            int bw = textRenderer.getWidth(badge) + 6;
            ctx.fill(cx + cellW - bw - 2, cy + 2, cx + cellW - 2, cy + 12, C_BG);
            ctx.drawText(textRenderer, badge, cx + cellW - bw + 1, cy + 3, C_MID, false);
        }
        ctx.disableScissor();

        if (sellInv.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Inventaire vide", PAD + invW / 2, TOP_H + (height - TOP_H) / 2, C_DIM);
        }

        // Panneau formulaire
        ctx.fill(formX, TOP_H + PAD, formX + formW, height - PAD, C_PANEL);
        int fy = TOP_H + PAD + 14;
        ctx.drawText(textRenderer, "Créer une annonce", formX + 12, fy, C_WHITE, false);
        fy += textRenderer.fontHeight + 12;

        // Preview item sélectionné
        if (selectedSellItem != null) {
            ctx.fill(formX + 8, fy, formX + formW - 8, fy + 34, C_BG);
            ctx.drawItem(new ItemStack(selectedSellItem.item()), formX + 16, fy + 9);
            ctx.drawText(textRenderer, truncate(FrenchItemNames.toDisplay(selectedSellItem.itemId()), formW - 50), formX + 38, fy + 7, C_WHITE, false);
            ctx.drawText(textRenderer, "En stock: " + selectedSellItem.qty(), formX + 38, fy + 19, C_DIM, false);
        } else {
            ctx.fill(formX + 8, fy, formX + formW - 8, fy + 34, C_BG);
            ctx.drawCenteredTextWithShadow(textRenderer, "← Choisissez un item", formX + formW / 2, fy + 12, C_DARK);
        }
        fy += 44;

        // Quantité
        ctx.drawText(textRenderer, "QUANTITÉ", formX + 12, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        sellQtyField.setX(formX + 12);
        sellQtyField.setY(fy);
        sellQtyField.setWidth(formW - 24);
        sellQtyField.render(ctx, mx, my, 0);
        fy += 26;

        // Prix
        ctx.drawText(textRenderer, "PRIX PAR UNITÉ (💎)", formX + 12, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        sellPriceField.setX(formX + 12);
        sellPriceField.setY(fy);
        sellPriceField.setWidth(formW - 24);
        sellPriceField.render(ctx, mx, my, 0);
        fy += 26;

        // Récapitulatif
        if (selectedSellItem != null && sellPrice > 0 && sellQty > 0) {
            int gross      = sellPrice * sellQty;
            int commission = (int) (gross * 0.05);
            int net        = gross - commission;
            ctx.fill(formX + 8, fy, formX + formW - 8, fy + 52, C_BG);
            ctx.drawText(textRenderer, sellQty + "x à " + sellPrice + " 💎", formX + 14, fy + 6, C_DIM, false);
            String gs = gross + " 💎";
            ctx.drawText(textRenderer, gs, formX + formW - textRenderer.getWidth(gs) - 14, fy + 6, C_MID, false);
            ctx.drawText(textRenderer, "Commission 5%", formX + 14, fy + 18, C_DIM, false);
            String cs = "-" + commission + " 💎";
            ctx.drawText(textRenderer, cs, formX + formW - textRenderer.getWidth(cs) - 14, fy + 18, C_RED, false);
            ctx.fill(formX + 8, fy + 31, formX + formW - 8, fy + 32, C_SEP);
            ctx.drawText(textRenderer, "Net reçu", formX + 14, fy + 36, C_DIM, false);
            String ns = net + " 💎";
            ctx.drawText(textRenderer, ns, formX + formW - textRenderer.getWidth(ns) - 14, fy + 36, C_GOLD, false);
        }

        // Bouton vendre
        boolean canSell = selectedSellItem != null && sellPrice > 0 && sellQty > 0 && sellQty <= selectedSellItem.qty();
        int btnY = height - PAD - 28;
        ctx.fill(formX + 8, btnY, formX + formW - 8, btnY + 22, canSell ? C_GOLD : C_SEP);
        ctx.drawCenteredTextWithShadow(textRenderer, "Mettre en vente", formX + formW / 2, btnY + 7, canSell ? C_BG : C_DARK);
    }

    // ── Onglet Mon Shop ───────────────────────────────────────────────────────

    private void renderMyShop(DrawContext ctx, int mx, int my) {
        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        List<ListingData> mine = listings.stream().filter(l -> l.seller().equalsIgnoreCase(me)).toList();

        int py = TOP_H + PAD;
        ctx.drawText(textRenderer, "MES ANNONCES — " + mine.size(), PAD, py, C_DIM, false);
        py += textRenderer.fontHeight + 10;

        if (mine.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Vous n'avez aucune annonce.", width / 2, py + 50, C_DIM);
            return;
        }

        int cardW = (width - PAD * 2 - (COLS - 1) * GAP) / COLS;
        for (int i = 0; i < mine.size(); i++) {
            ListingData l = mine.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cx  = PAD + col * (cardW + GAP);
            int cy  = py + row * (CARD_H + GAP);
            boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H;
            renderCard(ctx, cx, cy, cardW, CARD_H, l, hov, true);
            if (hov) {
                int bw2 = 54;
                int bx2 = cx + cardW / 2 - bw2 / 2;
                int by2 = cy + CARD_H - 18;
                ctx.fill(bx2, by2, bx2 + bw2, by2 + 14, C_RED);
                ctx.drawCenteredTextWithShadow(textRenderer, "Retirer", bx2 + bw2 / 2, by2 + 3, C_WHITE);
            }
        }
    }

    // ── Onglet Boutiques ──────────────────────────────────────────────────────

    private void renderShops(DrawContext ctx, int mx, int my) {
        int py = TOP_H + PAD;

        if (selectedShop != null) {
            // Bouton retour
            ctx.fill(PAD, py, PAD + 72, py + 18, C_PANEL);
            ctx.drawText(textRenderer, "← Retour", PAD + 8, py + 5, C_MID, false);
            py += 26;

            ctx.drawText(textRenderer, selectedShop, PAD, py, C_WHITE, false);
            py += textRenderer.fontHeight + 10;

            List<ListingData> items = listings.stream().filter(l -> l.seller().equals(selectedShop)).toList();
            int cardW = (width - PAD * 2 - (COLS - 1) * GAP) / COLS;
            for (int i = 0; i < items.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int cx  = PAD + col * (cardW + GAP);
                int cy  = py + row * (CARD_H + GAP);
                boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H;
                renderCard(ctx, cx, cy, cardW, CARD_H, items.get(i), hov, false);
            }
        } else {
            ctx.drawText(textRenderer, "BOUTIQUES DES JOUEURS", PAD, py, C_DIM, false);
            py += textRenderer.fontHeight + 10;

            Map<String, Long> sellers = listings.stream()
                .collect(Collectors.groupingBy(ListingData::seller, LinkedHashMap::new, Collectors.counting()));

            int rowH = 48, idx = 0;
            for (Map.Entry<String, Long> e : sellers.entrySet()) {
                int ry = py + idx * (rowH + 6);
                boolean hov = mx >= PAD && mx < width - PAD && my >= ry && my < ry + rowH;
                ctx.fill(PAD, ry, width - PAD, ry + rowH, hov ? C_HOVER : C_PANEL);
                if (hov) {
                    ctx.fill(PAD, ry, width - PAD, ry + 1, C_GOLD);
                    ctx.fill(PAD, ry + rowH - 1, width - PAD, ry + rowH, C_GOLD);
                }
                ctx.drawText(textRenderer, e.getKey(), PAD + 12, ry + 10, C_WHITE, false);
                long cnt = e.getValue();
                ctx.drawText(textRenderer, cnt + " article" + (cnt > 1 ? "s" : ""), PAD + 12, ry + 24, C_DIM, false);
                ctx.drawText(textRenderer, "›", width - PAD - 16, ry + rowH / 2 - textRenderer.fontHeight / 2, C_DARK, false);
                idx++;
            }
        }
    }

    // ── Toast ─────────────────────────────────────────────────────────────────

    private void renderToast(DrawContext ctx) {
        if (toastMsg == null) return;
        if (System.currentTimeMillis() > toastEnd) { toastMsg = null; return; }
        int bg = toastOk ? C_GREEN : C_RED;
        int tw = textRenderer.getWidth(toastMsg) + 24;
        int tx = (width - tw) / 2;
        ctx.fill(tx, 8, tx + tw, 8 + 20, bg);
        ctx.drawCenteredTextWithShadow(textRenderer, toastMsg, width / 2, 14, C_WHITE);
    }

    // ── Clics souris ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = (int) mx, y = (int) my;

        // Modal achat en priorité
        if (buyingListing != null) {
            handleModalClick(x, y);
            return true;
        }

        // Top bar (onglets)
        if (y <= TOP_H - 1) {
            handleTabClick(x, y);
            return true;
        }

        // Sidebar catégories (onglet Marché seulement)
        if (activeTab == Tab.MARKET && x < SIDE_W) {
            handleCatClick(x, y);
            return true;
        }

        switch (activeTab) {
            case MARKET  -> { if (hoveredCard != null) { buyingListing = hoveredCard; buyQty = 1; } }
            case SELL    -> handleSellClick(x, y);
            case MY_SHOP -> handleMyShopClick(x, y);
            case SHOPS   -> handleShopsClick(x, y);
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void handleTabClick(int mx, int my) {
        int tx = PAD + textRenderer.getWidth("HDV") + 4 + textRenderer.getWidth("Nouvelle Terre") + 24;
        for (Tab tab : Tab.values()) {
            int tw = textRenderer.getWidth(tab.label) + 18;
            if (mx >= tx && mx <= tx + tw) {
                activeTab     = tab;
                scrollOffset  = 0;
                selectedShop  = null;
                if (tab == Tab.SELL) refreshSellInv();
                return;
            }
            tx += tw + 4;
        }
    }

    private void handleCatClick(int mx, int my) {
        int y = TOP_H + PAD + textRenderer.fontHeight + 10;
        for (String[] cat : CATS) {
            int rh = textRenderer.fontHeight + 8;
            if (my >= y - 2 && my < y + rh - 2) {
                activeCategory = cat[0];
                scrollOffset   = 0;
                return;
            }
            y += rh;
        }
    }

    private void handleModalClick(int mx, int my) {
        ListingData l = buyingListing;
        int ox = (width - MODAL_W) / 2;
        int oy = (height - MODAL_H) / 2;

        // Clic hors modal → fermer
        if (mx < ox || mx > ox + MODAL_W || my < oy || my > oy + MODAL_H) {
            buyingListing = null;
            return;
        }

        // Boutons − et + du sélecteur
        int bx = ox + MODAL_W - 90;
        int by2 = oy + 16 + 34 + 36;
        if (mx >= bx && mx < bx + 22 && my >= by2 && my < by2 + 18) {
            buyQty = Math.max(1, buyQty - 1);
            return;
        }
        if (mx >= bx + 46 && mx < bx + 68 && my >= by2 && my < by2 + 18) {
            buyQty = Math.min(l.quantity(), buyQty + 1);
            return;
        }

        // Boutons Annuler / Confirmer
        int btnY = oy + MODAL_H - 36;
        int half = MODAL_W / 2 - 12;
        if (mx >= ox + 8 && mx < ox + 8 + half && my >= btnY && my < btnY + 24) {
            buyingListing = null;
            return;
        }
        if (mx >= ox + MODAL_W - 8 - half && mx < ox + MODAL_W - 8 && my >= btnY && my < btnY + 24) {
            if (balance >= l.pricePerUnit() * buyQty) sendBuy(l.itemId(), buyQty);
        }
    }

    private void handleSellClick(int mx, int my) {
        int py  = TOP_H + PAD + textRenderer.fontHeight + 8;
        int formW = 290;
        int formX = width - formW - PAD;
        int invW  = formX - PAD * 2;
        int cols  = 5;
        int cellW = (invW - (cols - 1) * GAP) / cols;
        int cellH = 80;

        // Clic sur grille inventaire
        for (int i = 0; i < sellInv.size(); i++) {
            int col = i % cols, row = i / cols;
            int cx  = PAD + col * (cellW + GAP);
            int cy  = py + row * (cellH + GAP);
            if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH) {
                selectedSellItem = sellInv.get(i);
                sellQty = 1;
                sellQtyField.setText("1");
                sellPriceField.setText("");
                sellPrice = 0;
                return;
            }
        }

        // Bouton vendre
        boolean canSell = selectedSellItem != null && sellPrice > 0 && sellQty > 0 && sellQty <= selectedSellItem.qty();
        int btnY = height - PAD - 28;
        if (canSell && mx >= formX + 8 && mx < formX + formW - 8 && my >= btnY && my < btnY + 22) {
            sendSell(selectedSellItem.itemId(), sellQty, sellPrice);
        }
    }

    private void handleMyShopClick(int mx, int my) {
        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        List<ListingData> mine = listings.stream().filter(l -> l.seller().equalsIgnoreCase(me)).toList();
        int py    = TOP_H + PAD + textRenderer.fontHeight + 10;
        int cardW = (width - PAD * 2 - (COLS - 1) * GAP) / COLS;
        for (int i = 0; i < mine.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int cx  = PAD + col * (cardW + GAP);
            int cy  = py + row * (CARD_H + GAP);
            if (mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H) {
                int bw2 = 54, bx2 = cx + cardW / 2 - bw2 / 2, by2 = cy + CARD_H - 18;
                if (mx >= bx2 && mx < bx2 + bw2 && my >= by2 && my < by2 + 14) {
                    sendWithdraw(mine.get(i).id());
                }
                return;
            }
        }
    }

    private void handleShopsClick(int mx, int my) {
        int py = TOP_H + PAD;
        if (selectedShop != null) {
            // Bouton retour
            if (mx >= PAD && mx < PAD + 72 && my >= py && my < py + 18) {
                selectedShop = null;
                return;
            }
            py += 26 + textRenderer.fontHeight + 10;
            List<ListingData> items = listings.stream().filter(l -> l.seller().equals(selectedShop)).toList();
            int cardW = (width - PAD * 2 - (COLS - 1) * GAP) / COLS;
            for (int i = 0; i < items.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int cx  = PAD + col * (cardW + GAP);
                int cy  = py + row * (CARD_H + GAP);
                if (mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H) {
                    buyingListing = items.get(i);
                    buyQty = 1;
                    return;
                }
            }
        } else {
            py += textRenderer.fontHeight + 10;
            Map<String, Long> sellers = listings.stream()
                .collect(Collectors.groupingBy(ListingData::seller, LinkedHashMap::new, Collectors.counting()));
            int rowH = 48, idx = 0;
            for (String seller : sellers.keySet()) {
                int ry = py + idx * (rowH + 6);
                if (mx >= PAD && mx < width - PAD && my >= ry && my < ry + rowH) {
                    selectedShop = seller;
                    return;
                }
                idx++;
            }
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (activeTab == Tab.MARKET && buyingListing == null) {
            scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(delta));
        }
        return true;
    }

    // ── Clavier ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) { // ESC
            if (buyingListing != null) { buyingListing = null; return true; }
            if (selectedShop  != null) { selectedShop  = null;  return true; }
        }
        return super.keyPressed(key, scan, mod);
    }

    // ── Envoi paquets ─────────────────────────────────────────────────────────

    private void sendBuy(String itemId, int qty) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(HdvNetworking.ACTION_BUY);
        buf.writeString(itemId);
        buf.writeInt(qty);
        ClientPlayNetworking.send(HdvNetworking.HDV_ACTION, buf);
    }

    private void sendSell(String itemId, int qty, int price) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(HdvNetworking.ACTION_SELL);
        buf.writeString(itemId);
        buf.writeInt(qty);
        buf.writeInt(price);
        ClientPlayNetworking.send(HdvNetworking.HDV_ACTION, buf);
    }

    private void sendWithdraw(int listingId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(HdvNetworking.ACTION_WITHDRAW);
        buf.writeInt(listingId);
        ClientPlayNetworking.send(HdvNetworking.HDV_ACTION, buf);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private ItemStack itemStack(String itemId) {
        try {
            Item item = Registries.ITEM.get(Identifier.tryParse(itemId));
            return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private String truncate(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override public boolean shouldPause()       { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
