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
    public record TransactionData(int type, String label, int amount, long timestamp) {}
    private record SellItem(Item item, String itemId, int qty) {}

    private enum Tab {
        MARKET("🏪  Marché"),
        SELL("💰  Vendre"),
        MY_SHOP("🛒  Mon Shop"),
        SHOPS("👥  Boutiques"),
        PROFILE("★  Profil");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private enum SortMode {
        PRICE_ASC("Prix ↑"), PRICE_DESC("Prix ↓"), NAME("Nom");
        final String label;
        SortMode(String l) { this.label = l; }
        SortMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    // ── Couleurs ──────────────────────────────────────────────────────────────

    private static final int C_BG       = 0xFF14161A;
    private static final int C_PANEL    = 0xFF1B1D22;
    private static final int C_SURFACE  = 0xFF21242C;
    private static final int C_HOVER    = 0xFF282B34;
    private static final int C_STRIP    = 0xFF1E2128;
    private static final int C_BORDER   = 0xFF2A2D38;
    private static final int C_GOLD     = 0xFFE8A838;
    private static final int C_GOLD_DIM = 0x20E8A838;
    private static final int C_RED      = 0xFFBF2040;
    private static final int C_RED_DIM  = 0x15BF2040;
    private static final int C_GREEN    = 0xFF2EAD6B;
    private static final int C_WHITE    = 0xFFFFFFFF;
    private static final int C_MID      = 0xFF9096A3;
    private static final int C_DIM      = 0xFF565C6A;
    private static final int C_DARK     = 0xFF353840;

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int TOP_H    = 44;
    private static final int SIDE_W   = 148;
    private static final int PAD      = 12;
    private static final int COLS     = 5;
    private static final int CARD_H   = 106;
    private static final int GAP      = 8;
    private static final int MODAL_W  = 340;
    private static final int MODAL_H  = 260;
    private static final int SCROLL_W = 4;

    // ── Fenêtre centrée ───────────────────────────────────────────────────────

    private static final int WIN_MAX_W = 920;
    private static final int WIN_MAX_H = 560;
    private int winX, winY, winW, winH;

    // ── Catégories ────────────────────────────────────────────────────────────

    private static final String[][] CATS = {
        {"tous",       "Tout"},
        {"minerais",   "Minerais"},
        {"nourriture", "Nourriture"},
        {"bois",       "Bois & Blocs"},
        {"outils",     "Outils & Armes"},
        {"divers",     "Divers"},
    };

    private static final Map<String, String> CAT_ICONS = new HashMap<>();
    static {
        CAT_ICONS.put("tous",       "minecraft:compass");
        CAT_ICONS.put("minerais",   "minecraft:diamond");
        CAT_ICONS.put("nourriture", "minecraft:bread");
        CAT_ICONS.put("bois",       "minecraft:oak_log");
        CAT_ICONS.put("outils",     "minecraft:diamond_sword");
        CAT_ICONS.put("divers",     "minecraft:ender_eye");
    }

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
    private SortMode sortMode = SortMode.PRICE_ASC;
    private int scrollOffset = 0;

    private TextFieldWidget searchField;
    private ListingData hoveredCard = null;
    private ListingData buyingListing = null;
    private int buyQty = 1;

    private List<SellItem> sellInv = new ArrayList<>();
    private SellItem selectedSellItem = null;
    private int sellQty = 1;
    private int sellPrice = 0;
    private TextFieldWidget sellQtyField;
    private TextFieldWidget sellPriceField;

    private String selectedShop = null;

    private int ticksUntilReward = 36000;
    private int ticksUntilSalary = 72000;
    private List<TransactionData> transactions = new ArrayList<>();
    private long screenOpenTime = System.currentTimeMillis();
    private TextFieldWidget transferToField;
    private TextFieldWidget transferAmountField;
    private String transferTarget = "";
    private int transferAmount = 0;

    private String toastMsg = null;
    private boolean toastOk = true;
    private long toastEnd = 0;

    // ── Constructeur ──────────────────────────────────────────────────────────

    public HdvScreen(int balance, List<ListingData> listings, int ticksUntilReward, int ticksUntilSalary, List<TransactionData> transactions) {
        super(Text.literal("HDV — Nouvelle Terre"));
        this.balance          = balance;
        this.listings         = new ArrayList<>(listings);
        this.ticksUntilReward = ticksUntilReward;
        this.ticksUntilSalary = ticksUntilSalary;
        this.transactions     = new ArrayList<>(transactions);
        this.screenOpenTime   = System.currentTimeMillis();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private void computeWin() {
        winW = Math.min(width - 40, WIN_MAX_W);
        winH = Math.min(height - 40, WIN_MAX_H);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
    }

    @Override
    protected void init() {
        super.init();
        computeWin();

        searchField = new TextFieldWidget(textRenderer, winX + SIDE_W + PAD, winY + TOP_H + PAD, 180, 18, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Rechercher..."));
        searchField.setChangedListener(s -> scrollOffset = 0);
        addSelectableChild(searchField);

        sellQtyField = new TextFieldWidget(textRenderer, winX, winY, 100, 18, Text.literal("1"));
        sellQtyField.setText("1");
        sellQtyField.setChangedListener(s -> {
            try { sellQty = Math.max(1, Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) { sellQty = 1; }
        });
        addSelectableChild(sellQtyField);

        sellPriceField = new TextFieldWidget(textRenderer, winX, winY, 100, 18, Text.literal(""));
        sellPriceField.setPlaceholder(Text.literal("Prix/u..."));
        sellPriceField.setChangedListener(s -> {
            try { sellPrice = Math.max(0, Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) { sellPrice = 0; }
        });
        addSelectableChild(sellPriceField);

        transferToField = new TextFieldWidget(textRenderer, winX, winY, 160, 18, Text.literal(""));
        transferToField.setPlaceholder(Text.literal("Pseudo du joueur..."));
        transferToField.setChangedListener(s -> transferTarget = s.trim());
        addSelectableChild(transferToField);

        transferAmountField = new TextFieldWidget(textRenderer, winX, winY, 100, 18, Text.literal(""));
        transferAmountField.setPlaceholder(Text.literal("Montant..."));
        transferAmountField.setChangedListener(s -> {
            try { transferAmount = Math.max(0, Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) { transferAmount = 0; }
        });
        addSelectableChild(transferAmountField);

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

    public void handleResult(boolean ok, String msg, int newBalance, List<ListingData> newListings, int newTicksReward, int newTicksSalary, List<TransactionData> newTransactions) {
        balance           = newBalance;
        listings          = new ArrayList<>(newListings);
        ticksUntilReward  = newTicksReward;
        ticksUntilSalary  = newTicksSalary;
        transactions      = new ArrayList<>(newTransactions);
        screenOpenTime    = System.currentTimeMillis();
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
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", "");
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 3200;
    }

    // ── Render principal ──────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        computeWin();
        // Window shadow
        ctx.fill(winX + 3, winY + 3, winX + winW + 3, winY + winH + 3, 0x40000000);
        // Window bg — semi-transparent to show game world
        ctx.fill(winX, winY, winX + winW, winY + winH, 0xCC14161A);
        renderTopBar(ctx, mx, my);
        switch (activeTab) {
            case MARKET  -> { renderSidebar(ctx, mx, my); renderMarket(ctx, mx, my); }
            case SELL    -> renderSell(ctx, mx, my);
            case MY_SHOP -> renderMyShop(ctx, mx, my);
            case SHOPS   -> renderShops(ctx, mx, my);
            case PROFILE -> renderProfile(ctx, mx, my);
        }
        if (buyingListing != null) renderBuyModal(ctx, mx, my);
        renderToast(ctx);
        super.render(ctx, mx, my, delta);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        // Panel bg
        ctx.fill(winX, winY, winX + winW, winY + TOP_H, 0xE01B1D22);
        // Bottom border line
        ctx.fill(winX, winY + TOP_H - 1, winX + winW, winY + TOP_H, C_BORDER);

        int ty = winY + (TOP_H - textRenderer.fontHeight) / 2;
        int tx = winX + PAD;

        // Logo
        ctx.drawText(textRenderer, "HDV", tx, ty, C_GOLD, false);
        tx += textRenderer.getWidth("HDV") + 8;
        ctx.fill(tx, winY + (TOP_H - 16) / 2, tx + 1, winY + (TOP_H + 16) / 2, C_BORDER);
        tx += 9;
        ctx.drawText(textRenderer, "Nouvelle Terre", tx, ty, C_MID, false);
        tx += textRenderer.getWidth("Nouvelle Terre") + 20;

        // Tabs — pill style: active = gold bg dark text, hover = subtle, normal = muted
        for (Tab tab : Tab.values()) {
            boolean active = activeTab == tab;
            int tw = textRenderer.getWidth(tab.label) + 18;
            boolean hov = mx >= tx && mx <= tx + tw && my >= winY && my <= winY + TOP_H - 1;
            int tabY = winY + (TOP_H - 22) / 2;

            if (active) {
                ctx.fill(tx, tabY, tx + tw, tabY + 22, C_GOLD);
                ctx.drawText(textRenderer, tab.label, tx + tw / 2 - textRenderer.getWidth(tab.label) / 2, tabY + 7, C_BG, false);
            } else if (hov) {
                ctx.fill(tx, tabY, tx + tw, tabY + 22, C_HOVER);
                ctx.drawCenteredTextWithShadow(textRenderer, tab.label, tx + tw / 2, tabY + 7, C_WHITE);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer, tab.label, tx + tw / 2, tabY + 7, C_DIM);
            }
            tx += tw + 4;
        }

        // Balance — right-aligned chip
        String bal = balance + " ◆";
        int bw = textRenderer.getWidth(bal) + 18;
        int bx = winX + winW - bw - PAD;
        int by = winY + (TOP_H - 20) / 2;
        ctx.fill(bx, by, bx + bw, by + 20, C_STRIP);
        ctx.fill(bx, by, bx + 2, by + 20, C_GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, bal, bx + 9 + bw / 2, by + 6, C_GOLD);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private void renderSidebar(DrawContext ctx, int mx, int my) {
        ctx.fill(winX, winY + TOP_H, winX + SIDE_W, winY + winH, 0xE01B1D22);
        ctx.fill(winX + SIDE_W - 1, winY + TOP_H, winX + SIDE_W, winY + winH, C_BORDER);

        int y = winY + TOP_H + PAD;
        ctx.drawText(textRenderer, "CATEGORIES", winX + PAD, y, C_DIM, false);
        y += textRenderer.fontHeight + 10;

        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        List<ListingData> forCount = listings.stream().filter(l -> !l.seller().equalsIgnoreCase(me)).toList();

        for (String[] cat : CATS) {
            boolean active = activeCategory.equals(cat[0]);
            int rh = 30;
            boolean hov = mx >= winX && mx < winX + SIDE_W && my >= y && my < y + rh;

            if (active) {
                ctx.fill(winX, y, winX + SIDE_W - 1, y + rh, C_HOVER);
                ctx.fill(winX, y, winX + 3, y + rh, C_GOLD);
            } else if (hov) {
                ctx.fill(winX, y, winX + SIDE_W - 1, y + rh, 0x0CFFFFFF);
            }

            String iconId = CAT_ICONS.getOrDefault(cat[0], "minecraft:stone");
            ctx.drawItem(itemStack(iconId), winX + PAD + 2, y + (rh - 16) / 2);

            int textColor = active ? C_GOLD : (hov ? C_WHITE : C_MID);
            ctx.drawText(textRenderer, cat[1], winX + PAD + 24, y + (rh - textRenderer.fontHeight) / 2, textColor, false);

            long count = "tous".equals(cat[0]) ? forCount.size()
                : forCount.stream().filter(l -> matchCat(l.itemId(), cat[0])).count();
            if (count > 0) {
                String badge = String.valueOf(count);
                int badgeW = textRenderer.getWidth(badge) + 6;
                int badgeX = winX + SIDE_W - badgeW - 10;
                int badgeY = y + (rh - 11) / 2;
                ctx.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, active ? C_GOLD_DIM : 0x10FFFFFF);
                ctx.drawText(textRenderer, badge, badgeX + 3, badgeY + 1, active ? C_GOLD : C_DARK, false);
            }
            y += rh + 2;
        }
    }

    // ── Marché ────────────────────────────────────────────────────────────────

    private List<ListingData> filteredListings() {
        String q  = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        String me = client != null && client.player != null ? client.player.getName().getString() : "";

        Comparator<ListingData> comp = switch (sortMode) {
            case PRICE_ASC  -> Comparator.comparingInt(ListingData::pricePerUnit);
            case PRICE_DESC -> Comparator.comparingInt(ListingData::pricePerUnit).reversed();
            case NAME       -> Comparator.comparing(l -> FrenchItemNames.toDisplay(l.itemId()));
        };

        return listings.stream()
            .filter(l -> !l.seller().equalsIgnoreCase(me))
            .filter(l -> matchCat(l.itemId(), activeCategory))
            .filter(l -> q.isEmpty()
                || FrenchItemNames.toDisplay(l.itemId()).toLowerCase().contains(q)
                || l.seller().toLowerCase().contains(q)
                || l.itemId().toLowerCase().contains(q))
            .sorted(comp)
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
        int cx = winX + SIDE_W + PAD;
        int cw = winW - SIDE_W - PAD * 2;

        if (searchField != null) {
            int sfW = Math.min(220, cw - 110);
            searchField.setX(cx);
            searchField.setY(winY + TOP_H + PAD);
            searchField.setWidth(sfW);
            ctx.fill(cx - 1, winY + TOP_H + PAD - 1, cx + sfW + 1, winY + TOP_H + PAD + 19, C_BORDER);
            searchField.render(ctx, mx, my, 0);
        }

        int sfW2 = searchField != null ? Math.min(220, cw - 110) : 0;
        String sortLabel = "⇅ " + sortMode.label;
        // Sort button style
        int sortW = textRenderer.getWidth(sortLabel) + 16;
        int sortX = cx + sfW2 + 8;
        int sortY = winY + TOP_H + PAD;
        boolean sortHov = mx >= sortX && mx < sortX + sortW && my >= sortY && my < sortY + 18;
        ctx.fill(sortX, sortY, sortX + sortW, sortY + 18, sortHov ? C_HOVER : C_STRIP);
        ctx.fill(sortX, sortY, sortX + sortW, sortY + 1, C_BORDER);
        ctx.fill(sortX, sortY + 17, sortX + sortW, sortY + 18, C_BORDER);
        ctx.fill(sortX, sortY, sortX + 1, sortY + 18, C_BORDER);
        ctx.fill(sortX + sortW - 1, sortY, sortX + sortW, sortY + 18, C_BORDER);
        ctx.drawText(textRenderer, sortLabel, sortX + 8, sortY + 5, sortHov ? C_GOLD : C_MID, false);

        int gridY    = winY + TOP_H + PAD + 26;
        int gridH    = winH - (TOP_H + PAD + 26) - 28;
        int scrollBarX = cx + cw - SCROLL_W - 2;
        int gridW    = cw - SCROLL_W - 6;
        int cardW    = (gridW - (COLS - 1) * GAP) / COLS;

        List<ListingData> items = filteredListings();
        int visRows   = Math.max(1, gridH / (CARD_H + GAP));
        int totalRows = (int) Math.ceil((double) items.size() / COLS);
        int maxScroll = Math.max(0, totalRows - visRows);
        scrollOffset  = Math.min(scrollOffset, maxScroll);
        int start = scrollOffset * COLS;

        if (totalRows > visRows) {
            ctx.fill(scrollBarX, gridY, scrollBarX + SCROLL_W, gridY + gridH, C_BORDER);
            int thumbH = Math.max(20, gridH * visRows / totalRows);
            int thumbY = gridY + (maxScroll > 0 ? (gridH - thumbH) * scrollOffset / maxScroll : 0);
            ctx.fill(scrollBarX, thumbY, scrollBarX + SCROLL_W, thumbY + thumbH, 0x60FFFFFF);
        }

        ctx.enableScissor(cx, gridY, cx + gridW, gridY + gridH);
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
            String me = client != null && client.player != null ? client.player.getName().getString() : "";
            boolean hasOwn = listings.stream().anyMatch(l -> l.seller().equalsIgnoreCase(me));
            String emptyMsg = hasOwn
                ? "Vos annonces sont visibles dans l'onglet 'Mon Shop'"
                : "Aucun article disponible";
            ctx.drawCenteredTextWithShadow(textRenderer, emptyMsg, cx + cw / 2, gridY + gridH / 2, C_DIM);
        }

        String pg = items.size() + " article" + (items.size() > 1 ? "s" : "");
        if (maxScroll > 0) pg += "  •  Page " + (scrollOffset + 1) + "/" + (maxScroll + 1);
        ctx.drawCenteredTextWithShadow(textRenderer, pg, cx + cw / 2, winY + winH - 20, C_DIM);
    }

    private void renderCard(DrawContext ctx, int x, int y, int w, int h, ListingData l, boolean hov, boolean isOwn) {
        // Background
        ctx.fill(x, y, x + w, y + h, hov ? C_HOVER : C_PANEL);

        // Border on hover (1px gold all sides)
        if (hov) {
            ctx.fill(x, y, x + w, y + 1, C_GOLD);
            ctx.fill(x, y + h - 1, x + w, y + h, C_GOLD);
            ctx.fill(x, y, x + 1, y + h, C_GOLD);
            ctx.fill(x + w - 1, y, x + w, y + h, C_GOLD);
        } else {
            ctx.fill(x, y, x + w, y + 1, C_BORDER);
            ctx.fill(x, y, x + 1, y + h, C_BORDER);
            ctx.fill(x + w - 1, y, x + w, y + h, C_BORDER);
            ctx.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        }

        // Icon area
        int iconH = 48;
        ctx.fill(x + 1, y + 1, x + w - 1, y + iconH, C_BG);
        drawItemScaled(ctx, itemStack(l.itemId()), x + w / 2, y + iconH / 2, 2.0f);

        // Qty badge — top right
        String qtyStr = "x" + l.quantity();
        int qw = textRenderer.getWidth(qtyStr) + 4;
        ctx.fill(x + w - qw - 3, y + 3, x + w - 3, y + 13, 0xBB000000);
        ctx.drawText(textRenderer, qtyStr, x + w - qw - 1, y + 4, C_MID, false);

        // Info area
        int ny = y + iconH + 5;
        String name = truncate(FrenchItemNames.toDisplay(l.itemId()), w - 8);
        ctx.drawCenteredTextWithShadow(textRenderer, name, x + w / 2, ny, C_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, truncate(l.seller(), w - 8), x + w / 2, ny + 11, C_DIM);

        // Bottom price strip
        int stripY = y + h - 24;
        ctx.fill(x + 1, stripY, x + w - 1, y + h - 1, C_STRIP);
        ctx.fill(x + 1, stripY, x + w - 1, stripY + 1, C_BORDER);
        String price = l.pricePerUnit() + " ◆";
        ctx.drawCenteredTextWithShadow(textRenderer, price, x + w / 2, stripY + 7, C_GOLD);

        // MOI badge — top left
        if (isOwn) {
            int tw = textRenderer.getWidth("MOI") + 6;
            ctx.fill(x + 2, y + 2, x + tw + 2, y + 12, C_GOLD_DIM);
            ctx.drawText(textRenderer, "MOI", x + 5, y + 3, C_GOLD, false);
        }
    }

    // ── Modal achat ───────────────────────────────────────────────────────────

    private void renderBuyModal(DrawContext ctx, int mx, int my) {
        // Overlay
        ctx.fill(winX, winY, winX + winW, winY + winH, 0x88000000);
        ListingData l = buyingListing;
        int x = winX + (winW - MODAL_W) / 2;
        int y = winY + (winH - MODAL_H) / 2;

        // Modal background + border
        ctx.fill(x, y, x + MODAL_W, y + MODAL_H, C_SURFACE);
        ctx.fill(x, y, x + MODAL_W, y + 1, C_BORDER);
        ctx.fill(x, y + MODAL_H - 1, x + MODAL_W, y + MODAL_H, C_BORDER);
        ctx.fill(x, y, x + 1, y + MODAL_H, C_BORDER);
        ctx.fill(x + MODAL_W - 1, y, x + MODAL_W, y + MODAL_H, C_BORDER);
        // Gold accent top
        ctx.fill(x + 1, y + 1, x + MODAL_W - 1, y + 3, C_GOLD);

        // Header — item info
        int fy = y + 14;
        drawItemScaled(ctx, itemStack(l.itemId()), x + 24, fy + 14, 2.0f);
        ctx.drawText(textRenderer, FrenchItemNames.toDisplay(l.itemId()), x + 52, fy + 5, C_WHITE, false);
        ctx.drawText(textRenderer, "Vendu par " + l.seller(), x + 52, fy + 17, C_DIM, false);
        fy += 40;

        // Separator
        ctx.fill(x + 10, fy, x + MODAL_W - 10, fy + 1, C_BORDER);
        fy += 10;

        // Info rows (price + stock) — same layout as before for handleModalClick compatibility
        ctx.fill(x + 10, fy, x + MODAL_W - 10, fy + 58, C_STRIP);
        ctx.drawText(textRenderer, "Prix unitaire", x + 18, fy + 8, C_DIM, false);
        String pu = l.pricePerUnit() + " ◆";
        ctx.drawText(textRenderer, pu, x + MODAL_W - textRenderer.getWidth(pu) - 18, fy + 8, C_GOLD, false);
        ctx.drawText(textRenderer, "Stock disponible", x + 18, fy + 22, C_DIM, false);
        String st = "x" + l.quantity();
        ctx.drawText(textRenderer, st, x + MODAL_W - textRenderer.getWidth(st) - 18, fy + 22, C_MID, false);
        ctx.fill(x + 10, fy + 35, x + MODAL_W - 10, fy + 36, C_BORDER);

        // Qty selector — keep same positions for handleModalClick
        ctx.drawText(textRenderer, "Quantite", x + 18, fy + 42, C_DIM, false);
        int bx  = x + MODAL_W - 102;
        int by2 = fy + 38;
        boolean minHov = mx >= bx && mx < bx + 22 && my >= by2 && my < by2 + 18;
        ctx.fill(bx, by2, bx + 22, by2 + 18, minHov ? C_HOVER : C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "-", bx + 11, by2 + 5, C_WHITE);
        ctx.fill(bx + 24, by2, bx + 54, by2 + 18, C_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, String.valueOf(buyQty), bx + 39, by2 + 5, C_WHITE);
        boolean plusHov = mx >= bx + 56 && mx < bx + 78 && my >= by2 && my < by2 + 18;
        ctx.fill(bx + 56, by2, bx + 78, by2 + 18, plusHov ? C_HOVER : C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "+", bx + 67, by2 + 5, C_WHITE);
        boolean maxHov = mx >= bx + 80 && mx < bx + 100 && my >= by2 && my < by2 + 18;
        ctx.fill(bx + 80, by2, bx + 100, by2 + 18, maxHov ? C_HOVER : C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "MAX", bx + 90, by2 + 5, maxHov ? C_GOLD : C_MID);

        fy += 70;

        // Total
        int total = l.pricePerUnit() * buyQty;
        boolean canAfford = balance >= total;
        ctx.drawText(textRenderer, "Total a payer", x + 18, fy, C_MID, false);
        String tot = total + " ◆";
        ctx.drawText(textRenderer, tot, x + MODAL_W - textRenderer.getWidth(tot) - 18, fy, canAfford ? C_GOLD : C_RED, false);
        fy += 16;

        if (!canAfford) {
            ctx.fill(x + 10, fy, x + MODAL_W - 10, fy + 16, C_RED_DIM);
            ctx.drawText(textRenderer, "Solde insuffisant (" + balance + " ◆)", x + 16, fy + 4, C_RED, false);
        }

        // Buttons — same positions as before for handleModalClick
        int btnY = y + MODAL_H - 38;
        int half = MODAL_W / 2 - 14;
        ctx.fill(x + 10, btnY, x + 10 + half, btnY + 24, C_HOVER);
        ctx.fill(x + 10, btnY, x + 10 + half, btnY + 1, C_BORDER);
        ctx.fill(x + 10, btnY + 23, x + 10 + half, btnY + 24, C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "Annuler", x + 10 + half / 2, btnY + 8, C_MID);
        ctx.fill(x + MODAL_W - 10 - half, btnY, x + MODAL_W - 10, btnY + 24, canAfford ? C_GOLD : C_DARK);
        ctx.drawCenteredTextWithShadow(textRenderer, "Acheter", x + MODAL_W - 10 - half / 2, btnY + 8, canAfford ? C_BG : C_DIM);
    }

    // ── Onglet Vendre ─────────────────────────────────────────────────────────

    private void renderSell(DrawContext ctx, int mx, int my) {
        int formW = 290;
        int formX = winX + winW - formW - PAD;
        int invW  = formX - (winX + PAD * 2);
        int py    = winY + TOP_H + PAD;

        ctx.drawText(textRenderer, "INVENTAIRE — " + sellInv.size() + " items", winX + PAD, py, C_DIM, false);
        py += textRenderer.fontHeight + 8;

        int cellCols = 5;
        int cellW = (invW - (cellCols - 1) * GAP) / cellCols;
        int cellH = 82;

        ctx.enableScissor(winX + PAD, py, winX + PAD + invW, winY + winH - PAD);
        for (int i = 0; i < sellInv.size(); i++) {
            SellItem si = sellInv.get(i);
            int col = i % cellCols;
            int row = i / cellCols;
            int cx  = winX + PAD + col * (cellW + GAP);
            int cy  = py + row * (cellH + GAP);
            boolean sel = selectedSellItem != null && selectedSellItem.itemId().equals(si.itemId());
            boolean hov = mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH;

            ctx.fill(cx, cy, cx + cellW, cy + cellH, sel ? C_HOVER : (hov ? 0xFF1F2128 : C_PANEL));
            if (sel) {
                ctx.fill(cx, cy, cx + cellW, cy + 1, C_GOLD);
                ctx.fill(cx, cy + cellH - 1, cx + cellW, cy + cellH, C_GOLD);
                ctx.fill(cx, cy, cx + 1, cy + cellH, C_GOLD);
                ctx.fill(cx + cellW - 1, cy, cx + cellW, cy + cellH, C_GOLD);
            }

            int iconAreaH = 44;
            ctx.fill(cx + 1, cy + 1, cx + cellW - 1, cy + iconAreaH, C_BG);
            drawItemScaled(ctx, new ItemStack(si.item()), cx + cellW / 2, cy + iconAreaH / 2, 2.0f);

            String badge = "x" + si.qty();
            int bw = textRenderer.getWidth(badge) + 4;
            ctx.fill(cx + cellW - bw - 2, cy + 2, cx + cellW - 2, cy + 12, 0xAA000000);
            ctx.drawText(textRenderer, badge, cx + cellW - bw, cy + 3, C_DIM, false);

            String name = truncate(FrenchItemNames.toDisplay(si.itemId()), cellW - 6);
            ctx.drawCenteredTextWithShadow(textRenderer, name, cx + cellW / 2, cy + iconAreaH + 5, sel ? C_WHITE : C_MID);
        }
        ctx.disableScissor();

        if (sellInv.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Inventaire vide", winX + PAD + invW / 2, winY + TOP_H + (winH - TOP_H) / 2, C_DIM);
        }

        ctx.fill(formX, winY + TOP_H + PAD, formX + formW, winY + winH - PAD, C_SURFACE);
        int fy = winY + TOP_H + PAD + 14;
        ctx.drawText(textRenderer, "Creer une annonce", formX + 12, fy, C_WHITE, false);
        fy += textRenderer.fontHeight + 12;

        if (selectedSellItem != null) {
            ctx.fill(formX + 8, fy, formX + formW - 8, fy + 36, C_STRIP);
            drawItemScaled(ctx, new ItemStack(selectedSellItem.item()), formX + 26, fy + 18, 2.0f);
            ctx.drawText(textRenderer, truncate(FrenchItemNames.toDisplay(selectedSellItem.itemId()), formW - 60), formX + 44, fy + 8, C_WHITE, false);
            ctx.drawText(textRenderer, "En stock : " + selectedSellItem.qty(), formX + 44, fy + 20, C_DIM, false);
        } else {
            ctx.fill(formX + 8, fy, formX + formW - 8, fy + 36, C_STRIP);
            ctx.drawCenteredTextWithShadow(textRenderer, "<- Choisissez un item", formX + formW / 2, fy + 14, C_DARK);
        }
        fy += 46;

        ctx.drawText(textRenderer, "QUANTITE", formX + 12, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        sellQtyField.setX(formX + 12);
        sellQtyField.setY(fy);
        sellQtyField.setWidth(formW - 24);
        sellQtyField.render(ctx, mx, my, 0);
        fy += 26;

        ctx.drawText(textRenderer, "PRIX PAR UNITE (◆)", formX + 12, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        sellPriceField.setX(formX + 12);
        sellPriceField.setY(fy);
        sellPriceField.setWidth(formW - 24);
        sellPriceField.render(ctx, mx, my, 0);
        fy += 26;

        if (selectedSellItem != null && sellPrice > 0 && sellQty > 0) {
            int gross      = sellPrice * sellQty;
            int commission = (int) (gross * 0.05);
            int net        = gross - commission;
            ctx.fill(formX + 8, fy, formX + formW - 8, fy + 52, C_STRIP);
            ctx.drawText(textRenderer, sellQty + "x a " + sellPrice + " ◆", formX + 14, fy + 6, C_DIM, false);
            String gs = gross + " ◆";
            ctx.drawText(textRenderer, gs, formX + formW - textRenderer.getWidth(gs) - 14, fy + 6, C_MID, false);
            ctx.drawText(textRenderer, "Commission 5%", formX + 14, fy + 18, C_DIM, false);
            String cs = "-" + commission + " ◆";
            ctx.drawText(textRenderer, cs, formX + formW - textRenderer.getWidth(cs) - 14, fy + 18, C_RED, false);
            ctx.fill(formX + 8, fy + 31, formX + formW - 8, fy + 32, C_BORDER);
            ctx.drawText(textRenderer, "Net recu", formX + 14, fy + 36, C_DIM, false);
            String ns = net + " ◆";
            ctx.drawText(textRenderer, ns, formX + formW - textRenderer.getWidth(ns) - 14, fy + 36, C_GOLD, false);
        }

        boolean canSell = selectedSellItem != null && sellPrice > 0 && sellQty > 0 && sellQty <= selectedSellItem.qty();
        int btnY = winY + winH - PAD - 28;
        ctx.fill(formX + 8, btnY, formX + formW - 8, btnY + 22, canSell ? C_GOLD : C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "Mettre en vente", formX + formW / 2, btnY + 7, canSell ? C_BG : C_DARK);
    }

    // ── Onglet Mon Shop ───────────────────────────────────────────────────────

    private void renderMyShop(DrawContext ctx, int mx, int my) {
        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        List<ListingData> mine = listings.stream().filter(l -> l.seller().equalsIgnoreCase(me)).toList();

        int py = winY + TOP_H + PAD;
        ctx.drawText(textRenderer, "MES ANNONCES — " + mine.size(), winX + PAD, py, C_DIM, false);
        py += textRenderer.fontHeight + 10;

        if (mine.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Vous n'avez aucune annonce.", winX + winW / 2, py + 50, C_DIM);
            return;
        }

        int cardW = (winW - PAD * 2 - (COLS - 1) * GAP) / COLS;
        for (int i = 0; i < mine.size(); i++) {
            ListingData l = mine.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cx  = winX + PAD + col * (cardW + GAP);
            int cy  = py + row * (CARD_H + GAP);
            boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H;
            renderOwnCard(ctx, cx, cy, cardW, CARD_H, l, hov);
        }
    }

    private void renderOwnCard(DrawContext ctx, int x, int y, int w, int h, ListingData l, boolean hov) {
        ctx.fill(x, y, x + w, y + h, hov ? C_HOVER : C_PANEL);

        if (hov) {
            ctx.fill(x, y, x + w, y + 1, C_GOLD);
            ctx.fill(x, y + h - 1, x + w, y + h, C_GOLD);
            ctx.fill(x, y, x + 1, y + h, C_GOLD);
            ctx.fill(x + w - 1, y, x + w, y + h, C_GOLD);
        } else {
            ctx.fill(x, y, x + w, y + 1, C_BORDER);
            ctx.fill(x, y, x + 1, y + h, C_BORDER);
            ctx.fill(x + w - 1, y, x + w, y + h, C_BORDER);
            ctx.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        }

        int iconH = 48;
        ctx.fill(x + 1, y + 1, x + w - 1, y + iconH, C_BG);
        drawItemScaled(ctx, itemStack(l.itemId()), x + w / 2, y + iconH / 2, 2.0f);

        String qtyStr = "x" + l.quantity();
        int qw = textRenderer.getWidth(qtyStr) + 4;
        ctx.fill(x + w - qw - 3, y + 3, x + w - 3, y + 13, 0xBB000000);
        ctx.drawText(textRenderer, qtyStr, x + w - qw - 1, y + 4, C_MID, false);

        int ny = y + iconH + 5;
        String name = truncate(FrenchItemNames.toDisplay(l.itemId()), w - 8);
        ctx.drawCenteredTextWithShadow(textRenderer, name, x + w / 2, ny, C_WHITE);

        // Price/quantity info line
        String info = l.quantity() + " en stock · " + l.pricePerUnit() + " ◆/u";
        ctx.drawCenteredTextWithShadow(textRenderer, truncate(info, w - 8), x + w / 2, ny + 11, C_DIM);

        // Bottom strip: price normally, red "Retirer" on hover
        int stripY = y + h - 24;
        ctx.fill(x + 1, stripY, x + w - 1, y + h - 1, hov ? C_RED : C_STRIP);
        ctx.fill(x + 1, stripY, x + w - 1, stripY + 1, hov ? C_RED : C_BORDER);
        String stripText = hov ? "Retirer" : (l.pricePerUnit() + " ◆");
        int stripTextColor = hov ? C_WHITE : C_GOLD;
        ctx.drawCenteredTextWithShadow(textRenderer, stripText, x + w / 2, stripY + 7, stripTextColor);

        // MOI badge
        int tw = textRenderer.getWidth("MOI") + 6;
        ctx.fill(x + 2, y + 2, x + tw + 2, y + 12, C_GOLD_DIM);
        ctx.drawText(textRenderer, "MOI", x + 5, y + 3, C_GOLD, false);
    }

    // ── Onglet Boutiques ──────────────────────────────────────────────────────

    private void renderShops(DrawContext ctx, int mx, int my) {
        int py = winY + TOP_H + PAD;

        if (selectedShop != null) {
            ctx.fill(winX + PAD, py, winX + PAD + 72, py + 18, C_STRIP);
            ctx.fill(winX + PAD, py, winX + PAD + 72, py + 1, C_BORDER);
            ctx.fill(winX + PAD, py + 17, winX + PAD + 72, py + 18, C_BORDER);
            ctx.fill(winX + PAD, py, winX + PAD + 1, py + 18, C_BORDER);
            ctx.fill(winX + PAD + 71, py, winX + PAD + 72, py + 18, C_BORDER);
            ctx.drawText(textRenderer, "<- Retour", winX + PAD + 8, py + 5, C_MID, false);
            py += 26;

            ctx.drawText(textRenderer, selectedShop, winX + PAD, py, C_WHITE, false);
            py += textRenderer.fontHeight + 10;

            List<ListingData> items = listings.stream().filter(l -> l.seller().equals(selectedShop)).toList();
            int cardW = (winW - PAD * 2 - (COLS - 1) * GAP) / COLS;
            for (int i = 0; i < items.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int cx  = winX + PAD + col * (cardW + GAP);
                int cy  = py + row * (CARD_H + GAP);
                boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H;
                renderCard(ctx, cx, cy, cardW, CARD_H, items.get(i), hov, false);
            }
        } else {
            ctx.drawText(textRenderer, "BOUTIQUES DES JOUEURS", winX + PAD, py, C_DIM, false);
            py += textRenderer.fontHeight + 10;

            Map<String, Long> sellers = listings.stream()
                .collect(Collectors.groupingBy(ListingData::seller, LinkedHashMap::new, Collectors.counting()));

            int rowH = 48, idx = 0;
            for (Map.Entry<String, Long> e : sellers.entrySet()) {
                int ry = py + idx * (rowH + 6);
                boolean hov = mx >= winX + PAD && mx < winX + winW - PAD && my >= ry && my < ry + rowH;
                ctx.fill(winX + PAD, ry, winX + winW - PAD, ry + rowH, hov ? C_HOVER : C_PANEL);
                if (hov) {
                    ctx.fill(winX + PAD, ry, winX + winW - PAD, ry + 1, C_GOLD);
                    ctx.fill(winX + PAD, ry + rowH - 1, winX + winW - PAD, ry + rowH, C_GOLD);
                    ctx.fill(winX + PAD, ry, winX + PAD + 1, ry + rowH, C_GOLD);
                    ctx.fill(winX + winW - PAD - 1, ry, winX + winW - PAD, ry + rowH, C_GOLD);
                }
                ctx.drawText(textRenderer, e.getKey(), winX + PAD + 12, ry + 10, C_WHITE, false);
                long cnt = e.getValue();
                ctx.drawText(textRenderer, cnt + " article" + (cnt > 1 ? "s" : ""), winX + PAD + 12, ry + 24, C_DIM, false);
                ctx.drawText(textRenderer, "›", winX + winW - PAD - 16, ry + rowH / 2 - textRenderer.fontHeight / 2, C_DARK, false);
                idx++;
            }
        }
    }

    // ── Toast ─────────────────────────────────────────────────────────────────

    private void renderToast(DrawContext ctx) {
        if (toastMsg == null) return;
        if (System.currentTimeMillis() > toastEnd) { toastMsg = null; return; }
        int tw = textRenderer.getWidth(toastMsg) + 28;
        int th = 26;
        int tx = winX + winW - tw - 12;
        int ty = winY + winH - th - 12;
        ctx.fill(tx, ty, tx + tw, ty + th, C_SURFACE);
        ctx.fill(tx, ty, tx + tw, ty + 1, C_BORDER);
        ctx.fill(tx, ty + th - 1, tx + tw, ty + th, C_BORDER);
        ctx.fill(tx + tw - 1, ty, tx + tw, ty + th, C_BORDER);
        ctx.fill(tx, ty, tx + 3, ty + th, toastOk ? C_GREEN : C_RED);
        ctx.drawText(textRenderer, toastMsg, tx + 11, ty + (th - textRenderer.fontHeight) / 2, C_WHITE, false);
    }

    // ── Clics souris ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = (int) mx, y = (int) my;

        // Clic hors fenêtre → fermer
        if (x < winX || x > winX + winW || y < winY || y > winY + winH) {
            close();
            return true;
        }

        if (buyingListing != null) {
            handleModalClick(x, y);
            return true;
        }

        if (y <= winY + TOP_H - 1) {
            handleTabClick(x, y);
            return true;
        }

        if (activeTab == Tab.MARKET && x < winX + SIDE_W) {
            handleCatClick(x, y);
            return true;
        }

        switch (activeTab) {
            case MARKET -> {
                if (checkSortButtonClick(x, y)) return true;
                if (hoveredCard != null) { buyingListing = hoveredCard; buyQty = 1; }
            }
            case SELL    -> handleSellClick(x, y);
            case MY_SHOP -> handleMyShopClick(x, y);
            case SHOPS   -> handleShopsClick(x, y);
            case PROFILE -> handleProfileClick(x, y);
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void handleTabClick(int mx, int my) {
        int tx = winX + PAD + textRenderer.getWidth("HDV") + 13 + textRenderer.getWidth("Nouvelle Terre") + 20;
        for (Tab tab : Tab.values()) {
            int tw = textRenderer.getWidth(tab.label) + 18;
            if (mx >= tx && mx <= tx + tw) {
                activeTab    = tab;
                scrollOffset = 0;
                selectedShop = null;
                if (tab == Tab.SELL) refreshSellInv();
                return;
            }
            tx += tw + 4;
        }
    }

    private void handleCatClick(int mx, int my) {
        int y = winY + TOP_H + PAD + textRenderer.fontHeight + 10;
        for (String[] cat : CATS) {
            int rh = 30;
            if (my >= y && my < y + rh) {
                activeCategory = cat[0];
                scrollOffset   = 0;
                return;
            }
            y += rh + 2;
        }
    }

    private boolean checkSortButtonClick(int mx, int my) {
        int cx   = winX + SIDE_W + PAD;
        int cw   = winW - SIDE_W - PAD * 2;
        int sfW  = Math.min(220, cw - 110);
        String sortLabel = "⇅ " + sortMode.label;
        int sortW = textRenderer.getWidth(sortLabel) + 16;
        int sortX = cx + sfW + 8;
        int sortY = winY + TOP_H + PAD;
        if (mx >= sortX && mx < sortX + sortW && my >= sortY && my < sortY + 18) {
            sortMode = sortMode.next();
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    private void handleModalClick(int mx, int my) {
        ListingData l = buyingListing;
        int ox = winX + (winW - MODAL_W) / 2;
        int oy = winY + (winH - MODAL_H) / 2;

        if (mx < ox || mx > ox + MODAL_W || my < oy || my > oy + MODAL_H) {
            buyingListing = null;
            return;
        }

        int bx  = ox + MODAL_W - 102;
        int by2 = oy + 100;

        if (mx >= bx && mx < bx + 22 && my >= by2 && my < by2 + 18) {
            buyQty = Math.max(1, buyQty - 1);
            return;
        }
        if (mx >= bx + 56 && mx < bx + 78 && my >= by2 && my < by2 + 18) {
            buyQty = Math.min(l.quantity(), buyQty + 1);
            return;
        }
        if (mx >= bx + 80 && mx < bx + 100 && my >= by2 && my < by2 + 18) {
            int maxAffordable = l.pricePerUnit() > 0 ? balance / l.pricePerUnit() : l.quantity();
            buyQty = Math.min(l.quantity(), Math.max(1, maxAffordable));
            return;
        }

        int btnY = oy + MODAL_H - 38;
        int half = MODAL_W / 2 - 14;
        if (mx >= ox + 10 && mx < ox + 10 + half && my >= btnY && my < btnY + 24) {
            buyingListing = null;
            return;
        }
        if (mx >= ox + MODAL_W - 10 - half && mx < ox + MODAL_W - 10 && my >= btnY && my < btnY + 24) {
            if (balance >= l.pricePerUnit() * buyQty) sendBuy(l.itemId(), buyQty);
        }
    }

    private void handleSellClick(int mx, int my) {
        int formW = 290;
        int formX = winX + winW - formW - PAD;
        int invW  = formX - (winX + PAD * 2);
        int py    = winY + TOP_H + PAD + textRenderer.fontHeight + 8;
        int cols  = 5;
        int cellW = (invW - (cols - 1) * GAP) / cols;
        int cellH = 82;

        for (int i = 0; i < sellInv.size(); i++) {
            int col = i % cols, row = i / cols;
            int cx  = winX + PAD + col * (cellW + GAP);
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

        boolean canSell = selectedSellItem != null && sellPrice > 0 && sellQty > 0 && sellQty <= selectedSellItem.qty();
        int btnY = winY + winH - PAD - 28;
        if (canSell && mx >= formX + 8 && mx < formX + formW - 8 && my >= btnY && my < btnY + 22) {
            sendSell(selectedSellItem.itemId(), sellQty, sellPrice);
        }
    }

    private void handleMyShopClick(int mx, int my) {
        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        List<ListingData> mine = listings.stream().filter(l -> l.seller().equalsIgnoreCase(me)).toList();
        int py    = winY + TOP_H + PAD + textRenderer.fontHeight + 10;
        int cardW = (winW - PAD * 2 - (COLS - 1) * GAP) / COLS;
        for (int i = 0; i < mine.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int cx  = winX + PAD + col * (cardW + GAP);
            int cy  = py + row * (CARD_H + GAP);
            if (mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H) {
                if (my >= cy + CARD_H - 24 && my < cy + CARD_H) {
                    sendWithdraw(mine.get(i).id());
                }
                return;
            }
        }
    }

    private void handleShopsClick(int mx, int my) {
        int py = winY + TOP_H + PAD;
        if (selectedShop != null) {
            if (mx >= winX + PAD && mx < winX + PAD + 72 && my >= py && my < py + 18) {
                selectedShop = null;
                return;
            }
            py += 26 + textRenderer.fontHeight + 10;
            List<ListingData> items = listings.stream().filter(l -> l.seller().equals(selectedShop)).toList();
            int cardW = (winW - PAD * 2 - (COLS - 1) * GAP) / COLS;
            for (int i = 0; i < items.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int cx  = winX + PAD + col * (cardW + GAP);
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
                if (mx >= winX + PAD && mx < winX + winW - PAD && my >= ry && my < ry + rowH) {
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
        if (key == 256) {
            if (buyingListing != null) { buyingListing = null; return true; }
            if (selectedShop  != null) { selectedShop  = null; return true; }
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

    // ── Onglet Profil ─────────────────────────────────────────────────────────

    private void renderProfile(DrawContext ctx, int mx, int my) {
        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        int px = winX + PAD;
        int py = winY + TOP_H + PAD;
        int pw = winW - PAD * 2;

        ctx.drawText(textRenderer, "PROFIL — " + me.toUpperCase(), px, py, C_DIM, false);
        py += textRenderer.fontHeight + 12;

        int colW = (pw - 12) / 2;
        long elapsed = System.currentTimeMillis() - screenOpenTime;
        int elapsedTicks = (int) (elapsed / 50);

        // ── Balance card ──────────────────────────────────────────────────────
        int cardX = px, cardY = py, cardH = 80;
        ctx.fill(cardX, cardY, cardX + colW, cardY + cardH, C_PANEL);
        ctx.fill(cardX, cardY, cardX + colW, cardY + 1, C_BORDER);
        ctx.fill(cardX, cardY + cardH - 1, cardX + colW, cardY + cardH, C_BORDER);
        ctx.fill(cardX, cardY, cardX + 1, cardY + cardH, C_BORDER);
        ctx.fill(cardX + colW - 1, cardY, cardX + colW, cardY + cardH, C_BORDER);
        ctx.fill(cardX, cardY + 1, cardX + 3, cardY + cardH - 1, C_GOLD);
        ctx.drawText(textRenderer, "SOLDE", cardX + 12, cardY + 12, C_DIM, false);
        ctx.drawCenteredTextWithShadow(textRenderer, balance + " ◆", cardX + colW / 2, cardY + 34, C_GOLD);
        ctx.drawText(textRenderer, me, cardX + 12, cardY + 56, C_MID, false);

        // ── Salary card ───────────────────────────────────────────────────────
        int salY = py + cardH + 6;
        int salH = 90;
        ctx.fill(cardX, salY, cardX + colW, salY + salH, C_PANEL);
        ctx.fill(cardX, salY, cardX + colW, salY + 1, C_BORDER);
        ctx.fill(cardX, salY + salH - 1, cardX + colW, salY + salH, C_BORDER);
        ctx.fill(cardX, salY, cardX + 1, salY + salH, C_BORDER);
        ctx.fill(cardX + colW - 1, salY, cardX + colW, salY + salH, C_BORDER);
        ctx.drawText(textRenderer, "REVENUS AUTOMATIQUES", cardX + 10, salY + 10, C_DIM, false);
        int rewardTicks = Math.max(0, ticksUntilReward - elapsedTicks);
        int salaryTicks = Math.max(0, ticksUntilSalary - elapsedTicks);
        ctx.drawText(textRenderer, "Récompense  +5 ◆ / 30 min", cardX + 10, salY + 28, C_MID, false);
        ctx.drawText(textRenderer, "Prochain : " + ticksToTime(rewardTicks), cardX + 10, salY + 40, C_GOLD, false);
        ctx.drawText(textRenderer, "Salaire     +8 ◆ / heure", cardX + 10, salY + 58, C_MID, false);
        ctx.drawText(textRenderer, "Prochain : " + ticksToTime(salaryTicks), cardX + 10, salY + 70, C_GOLD, false);

        // ── Transfer form ─────────────────────────────────────────────────────
        int formX = px + colW + 12;
        int formY = py;
        int formW = colW;
        int formH = 176;
        ctx.fill(formX, formY, formX + formW, formY + formH, C_SURFACE);
        ctx.fill(formX, formY, formX + formW, formY + 1, C_BORDER);
        ctx.fill(formX, formY + formH - 1, formX + formW, formY + formH, C_BORDER);
        ctx.fill(formX, formY, formX + 1, formY + formH, C_BORDER);
        ctx.fill(formX + formW - 1, formY, formX + formW, formY + formH, C_BORDER);

        int fy = formY + 12;
        ctx.drawText(textRenderer, "VIREMENT BANCAIRE", formX + 10, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 8;
        ctx.drawText(textRenderer, "DESTINATAIRE", formX + 10, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 3;
        transferToField.setX(formX + 10);
        transferToField.setY(fy);
        transferToField.setWidth(formW - 20);
        transferToField.render(ctx, mx, my, 0);
        fy += 26;
        ctx.drawText(textRenderer, "MONTANT (◆)", formX + 10, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 3;
        transferAmountField.setX(formX + 10);
        transferAmountField.setY(fy);
        transferAmountField.setWidth(formW - 20);
        transferAmountField.render(ctx, mx, my, 0);
        fy += 26 + 4;
        boolean canTransfer = !transferTarget.isEmpty() && transferAmount > 0 && transferAmount <= balance;
        boolean sendHov = mx >= formX + 10 && mx < formX + formW - 10 && my >= fy && my < fy + 24;
        ctx.fill(formX + 10, fy, formX + formW - 10, fy + 24, canTransfer ? (sendHov ? 0xFF1A8050 : C_GREEN) : C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "Envoyer", formX + formW / 2, fy + 8, canTransfer ? C_WHITE : C_DARK);

        // ── Transactions ──────────────────────────────────────────────────────
        int listStartY = py + Math.max(cardH + 6 + salH, formH) + 14;
        ctx.drawText(textRenderer, "TRANSACTIONS RECENTES", px, listStartY - textRenderer.fontHeight - 4, C_DIM, false);

        int rowH = 24;
        ctx.enableScissor(px, listStartY, px + pw, winY + winH - PAD);
        if (transactions.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Aucune transaction récente", px + pw / 2, listStartY + 20, C_DIM);
        }
        for (int i = 0; i < transactions.size(); i++) {
            TransactionData tx = transactions.get(i);
            int ry = listStartY + i * (rowH + 2);
            boolean hov = mx >= px && mx < px + pw && my >= ry && my < ry + rowH;
            ctx.fill(px, ry, px + pw, ry + rowH, hov ? C_HOVER : C_PANEL);
            ctx.fill(px, ry, px + pw, ry + 1, C_BORDER);
            ctx.fill(px, ry + rowH - 1, px + pw, ry + rowH, C_BORDER);

            int accent = switch (tx.type()) {
                case 0, 3 -> C_RED;
                case 1, 2 -> C_GREEN;
                default   -> C_GOLD;
            };
            ctx.fill(px, ry + 1, px + 3, ry + rowH - 1, accent);

            String typeLabel = switch (tx.type()) {
                case 0 -> "Achat";
                case 1 -> "Vente";
                case 2 -> "Reçu";
                case 3 -> "Envoyé";
                default -> "Récompense";
            };
            int midY = ry + (rowH - textRenderer.fontHeight) / 2;
            ctx.drawText(textRenderer, typeLabel, px + 8, midY, C_MID, false);
            ctx.drawText(textRenderer, truncate(tx.label(), pw - 160), px + 72, midY, C_WHITE, false);

            boolean isOut = tx.type() == 0 || tx.type() == 3;
            String amtStr = (isOut ? "-" : "+") + tx.amount() + " ◆";
            int amtColor = isOut ? C_RED : C_GREEN;
            String timeStr = formatAgo(System.currentTimeMillis() - tx.timestamp());
            int timeW = textRenderer.getWidth(timeStr) + 4;
            ctx.drawText(textRenderer, timeStr, px + pw - timeW, midY, C_DIM, false);
            ctx.drawText(textRenderer, amtStr, px + pw - timeW - textRenderer.getWidth(amtStr) - 10, midY, amtColor, false);
        }
        ctx.disableScissor();
    }

    private void handleProfileClick(int mx, int my) {
        int px = winX + PAD;
        int pw = winW - PAD * 2;
        int colW = (pw - 12) / 2;
        int formX = px + colW + 12;
        int formW = colW;
        // fy mirrors the send button position from renderProfile
        int formY = winY + TOP_H + PAD + textRenderer.fontHeight + 12;
        int fy = formY + 12 + textRenderer.fontHeight + 8 + textRenderer.fontHeight + 3 + 26 + textRenderer.fontHeight + 3 + 26 + 4;
        boolean canTransfer = !transferTarget.isEmpty() && transferAmount > 0 && transferAmount <= balance;
        if (canTransfer && mx >= formX + 10 && mx < formX + formW - 10 && my >= fy && my < fy + 24) {
            sendTransfer(transferTarget, transferAmount);
            transferToField.setText("");
            transferAmountField.setText("");
            transferTarget = "";
            transferAmount = 0;
        }
    }

    private void sendTransfer(String target, int amount) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(HdvNetworking.ACTION_TRANSFER);
        buf.writeString(target);
        buf.writeInt(amount);
        ClientPlayNetworking.send(HdvNetworking.HDV_ACTION, buf);
    }

    private String ticksToTime(int ticks) {
        int mins = ticks / 1200;
        int secs = (ticks % 1200) / 20;
        if (mins > 0) return mins + " min " + secs + " s";
        return secs + " s";
    }

    private String formatAgo(long millis) {
        long secs = millis / 1000;
        if (secs < 60)  return secs + "s";
        long mins = secs / 60;
        if (mins < 60)  return mins + "min";
        long hrs = mins / 60;
        if (hrs < 24)   return hrs + "h";
        return (hrs / 24) + "j";
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private void drawItemScaled(DrawContext ctx, ItemStack stack, int centerX, int centerY, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(centerX - 8 * scale, centerY - 8 * scale, 0);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawItem(stack, 0, 0);
        ctx.getMatrices().pop();
    }

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
