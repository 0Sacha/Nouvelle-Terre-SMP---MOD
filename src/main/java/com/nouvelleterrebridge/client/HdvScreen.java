package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.HdvNetworking;
import com.nouvelleterrebridge.market.FrenchItemNames;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;

import java.util.*;

@Environment(EnvType.CLIENT)
public class HdvScreen extends Screen {

    // ── Data ──────────────────────────────────────────────────────────────────

    public record ListingData(int id, String seller, String itemId, int quantity, int pricePerUnit, String itemNBT) {
        public ListingData(int id, String seller, String itemId, int quantity, int pricePerUnit) {
            this(id, seller, itemId, quantity, pricePerUnit, "");
        }
        public boolean hasNBT() { return itemNBT != null && !itemNBT.isEmpty(); }
    }
    public record TransactionData(int type, String label, int amount, long timestamp) {}
    public record RecurringData(int id, String to, int amount, int intervalTicks, int ticksUntilNext) {}
    private record SellItem(Item item, String itemId, int qty, String nbt) {
        boolean hasNBT() { return nbt != null && !nbt.isEmpty(); }
    }


    private enum Tab {
        MARKET("🏪  Marché"),
        SELL("💰  Vendre"),
        MY_SHOP("🛒  Mon Shop"),
        SHOPS("👥  Boutiques");

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
    private static final int GAP      = 8;
    private static final int ROW_H    = 46;
    private static final int ROW_GAP  = 4;
    private static final int MODAL_W  = 340;
    private static final int MODAL_H  = 260;
    private static final int SCROLL_W = 6;

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
        {"medic",      "Médical"},
        {"divers",     "Divers"},
    };

    private static final Map<String, String> CAT_ICONS = new HashMap<>();
    static {
        CAT_ICONS.put("tous",       "minecraft:compass");
        CAT_ICONS.put("minerais",   "minecraft:diamond");
        CAT_ICONS.put("nourriture", "minecraft:bread");
        CAT_ICONS.put("bois",       "minecraft:oak_log");
        CAT_ICONS.put("outils",     "minecraft:diamond_sword");
        CAT_ICONS.put("medic",      "cottonmod:bandage");
        CAT_ICONS.put("divers",     "minecraft:ender_eye");
    }

    private static final Map<String, String[]> CAT_KW = new HashMap<>();
    static {
        CAT_KW.put("minerais",   new String[]{"diamond","iron","gold","coal","emerald","lapis","redstone","quartz","amethyst","netherite","obsidian","cobblestone","gravel","stone","granite","diorite","calcite","tuff","deepslate","copper"});
        CAT_KW.put("nourriture", new String[]{"bread","beef","pork","chicken","cod","salmon","apple","carrot","potato","melon","pumpkin","mushroom","wheat","egg","honey","cake","cookie","berry","rabbit","mutton","sugar","milk"});
        CAT_KW.put("bois",       new String[]{"log","wood","plank","stick","fence","door","trapdoor","slab","stair","button","pressure","barrel","bookshelf","sapling","leaves","bamboo","concrete","wool","glass","terracotta","sand","dirt","grass","clay","brick","nether_brick","end_stone","purpur","basalt","blackstone","sandstone","mossy","cobbled","mud","mangrove","cherry","azalea"});
        CAT_KW.put("outils",     new String[]{"pickaxe","axe","shovel","hoe","sword","bow","crossbow","shield","helmet","chestplate","leggings","boots","trident","elytra","armor","shears","flint_and_steel","fishing_rod","spyglass"});
        CAT_KW.put("medic",      new String[]{"cotton","thread","cloth","aloe","chamomile","calendula","bandage","medkit","salve","herbal_medicine","antiseptic","syringe","aloe_gel","parachute"});
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
    private int hoveredCardX = 0;
    private int hoveredCardY = 0;
    private int hoveredCardW = 0;
    private int gridMaxScroll = 0;
    private int tabsStartX = 0;
    private ListingData buyingListing = null;

    // Scrollbar générique (piste + pouce), réarmée par la liste active à chaque frame
    private int scrollTrackX, scrollTrackY, scrollTrackH, scrollThumbH, scrollVisUnits;
    private boolean draggingScroll = false;

    private List<SellItem> sellInv = new ArrayList<>();
    private SellItem selectedSellItem = null;
    private SellItem hoveredSellItem = null;
    private final NumberInput sellQtyInput   = new NumberInput(1, 1, 1);
    private final NumberInput sellPriceInput = new NumberInput(0, 0, 999_999);
    private final NumberInput buyQtyInput    = new NumberInput(1, 1, 1);

    private String selectedShop = null;

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

        sellPriceInput.setPlaceholder("Prix/u...");

        refreshSellInv();
    }

    private void refreshSellInv() {
        if (client == null || client.player == null) return;
        // Clé = id + NBT : les variantes enchantées restent des entrées distinctes,
        // sinon on risquerait de vendre une pile pour une autre.
        Map<String, SellItem> byId = new LinkedHashMap<>();
        for (ItemStack stack : client.player.getInventory().main) {
            if (stack.isEmpty()) continue;
            String id  = Registries.ITEM.getId(stack.getItem()).toString();
            String nbt = stack.hasNbt() ? stack.getNbt().asString() : "";
            byId.merge(id + "|" + nbt, new SellItem(stack.getItem(), id, stack.getCount(), nbt),
                (a, b) -> new SellItem(a.item(), a.itemId(), a.qty() + b.qty(), a.nbt()));
        }
        sellInv = new ArrayList<>(byId.values());
    }

    // ── Résultat réseau ───────────────────────────────────────────────────────

    public void handleResult(boolean ok, String msg, int newBalance, List<ListingData> newListings) {
        balance          = newBalance;
        listings         = new ArrayList<>(newListings);
        buyingListing    = null;
        selectedSellItem = null;
        sellQtyInput.setBounds(1, 1);
        sellQtyInput.setValue(1);
        sellPriceInput.setValue(0);
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
        hoveredCard     = null;   // réarmés par le rendu de l'onglet courant
        hoveredSellItem = null;
        switch (activeTab) {
            case MARKET  -> { renderSidebar(ctx, mx, my); renderMarket(ctx, mx, my); }
            case SELL    -> renderSell(ctx, mx, my);
            case MY_SHOP -> renderMyShop(ctx, mx, my);
            case SHOPS   -> renderShops(ctx, mx, my);
        }
        if (buyingListing != null) renderBuyModal(ctx, mx, my);
        else if (hoveredCard != null) renderListingTooltip(ctx, hoveredCard, mx, my);
        else if (hoveredSellItem != null)
            ctx.drawTooltip(textRenderer, Screen.getTooltipFromItem(client, sellStack(hoveredSellItem)), mx, my);
        renderToast(ctx);
        super.render(ctx, mx, my, delta);
    }

    /**
     * Tooltip vanilla de l'annonce survolée : nom de l'item et, s'il en a,
     * ses enchantements — plus une ligne vendeur/prix.
     */
    private void renderListingTooltip(DrawContext ctx, ListingData l, int mx, int my) {
        ItemStack stack = itemStack(l);
        List<Text> lines = new ArrayList<>(Screen.getTooltipFromItem(client, stack));
        lines.add(Text.literal("§8" + "─".repeat(12)));
        lines.add(Text.literal("§7Vendeur : §f" + l.seller()));
        lines.add(Text.literal("§7Prix : §6" + l.pricePerUnit() + " ◆§7/u  ·  Stock : §f" + l.quantity()));
        ctx.drawTooltip(textRenderer, lines, mx, my);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        // Panel bg
        ctx.fill(winX, winY, winX + winW, winY + TOP_H, 0xE01B1D22);
        // Bottom border line
        ctx.fill(winX, winY + TOP_H - 1, winX + winW, winY + TOP_H, C_BORDER);

        int ty = winY + (TOP_H - textRenderer.fontHeight) / 2;
        int tx = winX + PAD;

        // Retour au Parchemin
        HubBackButton.render(ctx, textRenderer, tx, winY + (TOP_H - HubBackButton.H) / 2, mx, my);
        tx += HubBackButton.W + 8;

        // Logo
        ctx.drawText(textRenderer, "HDV", tx, ty, C_GOLD, false);
        tx += textRenderer.getWidth("HDV") + 8;
        ctx.fill(tx, winY + (TOP_H - 16) / 2, tx + 1, winY + (TOP_H + 16) / 2, C_BORDER);
        tx += 9;
        ctx.drawText(textRenderer, "Nouvelle Terre", tx, ty, C_MID, false);
        tx += textRenderer.getWidth("Nouvelle Terre") + 20;

        // Position réelle des onglets, relue par handleTabClick (évite toute dérive
        // entre le calcul du rendu et celui du clic)
        tabsStartX = tx;

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
        ctx.drawText(textRenderer, bal, bx + 10, by + 6, C_GOLD, false);
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
            .filter(l -> !l.seller().equals("$Serveur"))
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

        int gridY = winY + TOP_H + PAD + 26;
        int gridH = winH - (TOP_H + PAD + 26) - 28;
        int gridW = cw - SCROLL_W - 6;

        List<ListingData> items = filteredListings();
        renderListRows(ctx, mx, my, items, cx, gridY, gridW, gridH, false);

        if (items.isEmpty()) {
            String me = client != null && client.player != null ? client.player.getName().getString() : "";
            boolean hasOwn = listings.stream().anyMatch(l -> l.seller().equalsIgnoreCase(me));
            String emptyMsg = hasOwn
                ? "Vos annonces sont visibles dans l'onglet 'Mon Shop'"
                : "Aucun article disponible";
            ctx.drawCenteredTextWithShadow(textRenderer, emptyMsg, cx + cw / 2, gridY + gridH / 2, C_DIM);
        }

        String pg = items.size() + " article" + (items.size() > 1 ? "s" : "");
        if (gridMaxScroll > 0) pg += "  •  molette ou barre pour faire défiler";
        ctx.drawCenteredTextWithShadow(textRenderer, pg, cx + cw / 2, winY + winH - 20, C_DIM);
    }

    /**
     * Liste de lignes scrollable partagée par tous les onglets (Marché, Mon Shop,
     * détail Boutiques). Clippe le contenu, dessine la scrollbar et mémorise la
     * ligne survolée (`hoveredCard` / `hoveredCardX/Y/W`) pour la détection de clic.
     */
    private void renderListRows(DrawContext ctx, int mx, int my, List<ListingData> items,
                                int gx, int gy, int gw, int gh, boolean ownRows) {
        int visRows = Math.max(1, gh / (ROW_H + ROW_GAP));
        gridMaxScroll = Math.max(0, items.size() - visRows);
        scrollOffset  = Math.max(0, Math.min(scrollOffset, gridMaxScroll));
        int start = scrollOffset;

        renderScrollbar(ctx, gx + gw + 2, gy, gh, visRows, gridMaxScroll);

        hoveredCard = null;
        ctx.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = start; i < items.size(); i++) {
            int y = gy + (i - start) * (ROW_H + ROW_GAP);
            if (y > gy + gh) break;
            boolean hov = mx >= gx && mx < gx + gw && my >= y && my < y + ROW_H
                       && my >= gy && my < gy + gh;
            if (hov) { hoveredCard = items.get(i); hoveredCardX = gx; hoveredCardY = y; hoveredCardW = gw; }
            if (ownRows) renderOwnListRow(ctx, gx, y, gw, items.get(i), hov);
            else         renderListRow(ctx, gx, y, gw, items.get(i), hov);
        }
        ctx.disableScissor();
    }

    /**
     * Scrollbar générique : piste + pouce or opaque, position mémorisée dans
     * scrollTrackX/Y/H + scrollThumbH pour le drag (mouseClicked/mouseDragged).
     */
    private void renderScrollbar(DrawContext ctx, int trackX, int trackY, int trackH, int visUnits, int maxScrollUnits) {
        scrollTrackX = trackX; scrollTrackY = trackY; scrollTrackH = trackH; scrollVisUnits = visUnits;
        if (maxScrollUnits <= 0) { scrollThumbH = 0; return; }
        int totalUnits = visUnits + maxScrollUnits;
        int thumbH = Math.max(18, trackH * visUnits / totalUnits);
        scrollThumbH = thumbH;
        int thumbY = trackY + (trackH - thumbH) * scrollOffset / maxScrollUnits;
        ctx.fill(trackX, trackY, trackX + SCROLL_W, trackY + trackH, C_BORDER);
        ctx.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, C_GOLD);
    }

    /** Repositionne scrollOffset d'après la position verticale de la souris sur la piste. */
    private void applyScrollFromMouse(int mouseY) {
        if (gridMaxScroll <= 0) { scrollOffset = 0; return; }
        int usable = Math.max(1, scrollTrackH - scrollThumbH);
        float ratio = (float) (mouseY - scrollTrackY) / usable;
        scrollOffset = Math.max(0, Math.min(Math.round(ratio * gridMaxScroll), gridMaxScroll));
    }

    /** Ligne d'annonce achetable : icône à gauche, prix + bouton Acheter à droite. */
    private void renderListRow(DrawContext ctx, int x, int y, int w, ListingData l, boolean hov) {
        ctx.fill(x, y, x + w, y + ROW_H, hov ? C_HOVER : C_PANEL);
        if (hov) {
            ctx.fill(x, y, x + w, y + 1, C_GOLD);
            ctx.fill(x, y + ROW_H - 1, x + w, y + ROW_H, C_GOLD);
            ctx.fill(x, y, x + 1, y + ROW_H, C_GOLD);
            ctx.fill(x + w - 1, y, x + w, y + ROW_H, C_GOLD);
        } else {
            ctx.fill(x, y, x + w, y + 1, C_BORDER);
            ctx.fill(x, y + ROW_H - 1, x + w, y + ROW_H, C_BORDER);
        }

        // Icône à gauche
        ctx.fill(x + 1, y + 1, x + 41, y + ROW_H - 1, C_BG);
        drawItemScaled(ctx, itemStack(l), x + 21, y + ROW_H / 2, 2.0f);

        // Nom + vendeur/stock
        int tx = x + 50;
        String name = truncate(FrenchItemNames.toDisplay(l.itemId()), w - 190);
        ctx.drawText(textRenderer, name, tx, y + 9, C_WHITE, false);
        String sub = "Vendu par " + l.seller() + "  ·  x" + l.quantity() + " en stock";
        ctx.drawText(textRenderer, truncate(sub, w - 190), tx, y + 22, C_DIM, false);

        // Prix + bouton Acheter à droite
        int btnW = 82, btnH = 22;
        int btnX = x + w - btnW - 10;
        int btnY = y + (ROW_H - btnH) / 2;
        String price = l.pricePerUnit() + " ◆/u";
        ctx.drawText(textRenderer, price, btnX - textRenderer.getWidth(price) - 14,
            y + (ROW_H - textRenderer.fontHeight) / 2, C_GOLD, false);
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, hov ? C_GOLD : C_STRIP);
        if (!hov) { ctx.fill(btnX, btnY, btnX + btnW, btnY + 1, C_BORDER); ctx.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, C_BORDER); }
        ctx.drawCenteredTextWithShadow(textRenderer, "Acheter", btnX + btnW / 2, btnY + 7, hov ? C_BG : C_MID);
    }

    // ── Modal achat ───────────────────────────────────────────────────────────

    private void renderBuyModal(DrawContext ctx, int mx, int my) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
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
        drawItemScaled(ctx, itemStack(l), x + 24, fy + 14, 2.0f);
        ctx.drawText(textRenderer, FrenchItemNames.toDisplay(l.itemId()), x + 52, fy + 5, C_WHITE, false);
        ctx.drawText(textRenderer, "Vendu par " + l.seller(), x + 52, fy + 17, C_DIM, false);
        fy += 40;

        // Separator
        ctx.fill(x + 10, fy, x + MODAL_W - 10, fy + 1, C_BORDER);
        fy += 10;

        // Prix unitaire + stock
        ctx.fill(x + 10, fy, x + MODAL_W - 10, fy + 36, C_STRIP);
        ctx.drawText(textRenderer, "Prix unitaire", x + 18, fy + 8, C_DIM, false);
        String pu = l.pricePerUnit() + " ◆";
        ctx.drawText(textRenderer, pu, x + MODAL_W - textRenderer.getWidth(pu) - 18, fy + 8, C_GOLD, false);
        ctx.drawText(textRenderer, "Stock disponible", x + 18, fy + 22, C_DIM, false);
        String st = "x" + l.quantity();
        ctx.drawText(textRenderer, st, x + MODAL_W - textRenderer.getWidth(st) - 18, fy + 22, C_MID, false);
        fy += 44;

        ctx.drawText(textRenderer, "QUANTITE", x + 18, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        buyQtyInput.render(ctx, textRenderer, x + 14, fy, MODAL_W - 28, mx, my);
        fy += NumberInput.H + 10;

        // Total
        int buyQty = buyQtyInput.getValue();
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
        ctx.getMatrices().pop();
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
        int gridW = invW - SCROLL_W - 4;
        int cellW = (gridW - (cellCols - 1) * GAP) / cellCols;
        int cellH = 82;
        int gridH = winY + winH - PAD - py;

        int visRows = Math.max(1, (gridH + GAP) / (cellH + GAP));
        int rows    = (int) Math.ceil((double) sellInv.size() / cellCols);
        gridMaxScroll = Math.max(0, rows - visRows);
        scrollOffset  = Math.max(0, Math.min(scrollOffset, gridMaxScroll));
        int start = scrollOffset * cellCols;

        renderScrollbar(ctx, winX + PAD + gridW + 2, py, gridH, visRows, gridMaxScroll);

        ctx.enableScissor(winX + PAD, py, winX + PAD + gridW, py + gridH);
        for (int i = start; i < sellInv.size(); i++) {
            SellItem si = sellInv.get(i);
            int col = (i - start) % cellCols;
            int row = (i - start) / cellCols;
            if (row > visRows) break;
            int cx  = winX + PAD + col * (cellW + GAP);
            int cy  = py + row * (cellH + GAP);
            boolean sel = selectedSellItem != null
                       && selectedSellItem.itemId().equals(si.itemId())
                       && selectedSellItem.nbt().equals(si.nbt());
            boolean hov = mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH
                       && my >= py && my < py + gridH;
            if (hov) hoveredSellItem = si;

            ctx.fill(cx, cy, cx + cellW, cy + cellH, sel ? C_HOVER : (hov ? 0xFF1F2128 : C_PANEL));
            if (sel) {
                ctx.fill(cx, cy, cx + cellW, cy + 1, C_GOLD);
                ctx.fill(cx, cy + cellH - 1, cx + cellW, cy + cellH, C_GOLD);
                ctx.fill(cx, cy, cx + 1, cy + cellH, C_GOLD);
                ctx.fill(cx + cellW - 1, cy, cx + cellW, cy + cellH, C_GOLD);
            }

            int iconAreaH = 44;
            ctx.fill(cx + 1, cy + 1, cx + cellW - 1, cy + iconAreaH, C_BG);
            drawItemScaled(ctx, sellStack(si), cx + cellW / 2, cy + iconAreaH / 2, 2.0f);

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
            drawItemScaled(ctx, sellStack(selectedSellItem), formX + 26, fy + 18, 2.0f);
            ctx.drawText(textRenderer, truncate(FrenchItemNames.toDisplay(selectedSellItem.itemId()), formW - 60), formX + 44, fy + 8, C_WHITE, false);
            String stockLine = "En stock : " + selectedSellItem.qty();
            if (selectedSellItem.hasNBT()) stockLine += "  §b✦ enchanté";
            ctx.drawText(textRenderer, stockLine, formX + 44, fy + 20, C_DIM, false);
        } else {
            ctx.fill(formX + 8, fy, formX + formW - 8, fy + 36, C_STRIP);
            ctx.drawCenteredTextWithShadow(textRenderer, "<- Choisissez un item", formX + formW / 2, fy + 14, C_DARK);
        }
        fy += 46;

        ctx.drawText(textRenderer, "QUANTITE", formX + 12, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        sellQtyInput.render(ctx, textRenderer, formX + 12, fy, formW - 24, mx, my);
        fy += NumberInput.H + 8;

        ctx.drawText(textRenderer, "PRIX PAR UNITE (◆)", formX + 12, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        sellPriceInput.render(ctx, textRenderer, formX + 12, fy, formW - 24, mx, my);
        fy += NumberInput.H + 8;

        int sellQty   = sellQtyInput.getValue();
        int sellPrice = sellPriceInput.getValue();
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

        int gridW = winW - PAD * 2 - SCROLL_W - 4;
        int gridH = winY + winH - PAD - py;
        renderListRows(ctx, mx, my, mine, winX + PAD, py, gridW, gridH, true);
    }

    /** Ligne d'annonce possédée : icône à gauche, infos au centre, bouton Retirer rouge à droite. */
    private void renderOwnListRow(DrawContext ctx, int x, int y, int w, ListingData l, boolean hov) {
        ctx.fill(x, y, x + w, y + ROW_H, hov ? C_HOVER : C_PANEL);
        if (hov) {
            ctx.fill(x, y, x + w, y + 1, C_GOLD);
            ctx.fill(x, y + ROW_H - 1, x + w, y + ROW_H, C_GOLD);
            ctx.fill(x, y, x + 1, y + ROW_H, C_GOLD);
            ctx.fill(x + w - 1, y, x + w, y + ROW_H, C_GOLD);
        } else {
            ctx.fill(x, y, x + w, y + 1, C_BORDER);
            ctx.fill(x, y + ROW_H - 1, x + w, y + ROW_H, C_BORDER);
        }

        ctx.fill(x + 1, y + 1, x + 41, y + ROW_H - 1, C_BG);
        drawItemScaled(ctx, itemStack(l), x + 21, y + ROW_H / 2, 2.0f);

        int tx = x + 50;
        String name = truncate(FrenchItemNames.toDisplay(l.itemId()), w - 180);
        ctx.drawText(textRenderer, name, tx, y + 9, C_WHITE, false);
        String sub = l.quantity() + " en stock  ·  " + l.pricePerUnit() + " ◆/u";
        ctx.drawText(textRenderer, truncate(sub, w - 180), tx, y + 22, C_DIM, false);

        int btnW = 74, btnH = 22;
        int btnX = x + w - btnW - 10;
        int btnY = y + (ROW_H - btnH) / 2;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, hov ? C_RED : C_STRIP);
        if (!hov) { ctx.fill(btnX, btnY, btnX + btnW, btnY + 1, C_BORDER); ctx.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, C_BORDER); }
        ctx.drawCenteredTextWithShadow(textRenderer, "Retirer", btnX + btnW / 2, btnY + 7, hov ? C_WHITE : C_MID);
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
            int gridW = winW - PAD * 2 - SCROLL_W - 4;
            int gridH = winY + winH - PAD - py;
            renderListRows(ctx, mx, my, items, winX + PAD, py, gridW, gridH, false);
        } else {
            List<String> sellers = shopSellers();
            ctx.drawText(textRenderer, "BOUTIQUES DES JOUEURS — " + sellers.size(), winX + PAD, py, C_DIM, false);
            py += textRenderer.fontHeight + 10;

            int rowH    = 48, step = rowH + 6;
            int listH   = winY + winH - PAD - py;
            int visRows = Math.max(1, (listH + 6) / step);
            gridMaxScroll = Math.max(0, sellers.size() - visRows);
            scrollOffset  = Math.max(0, Math.min(scrollOffset, gridMaxScroll));

            int listW = winW - PAD * 2 - SCROLL_W - 4;
            renderScrollbar(ctx, winX + PAD + listW + 2, py, listH, visRows, gridMaxScroll);

            ctx.enableScissor(winX + PAD, py, winX + PAD + listW, py + listH);
            for (int idx = scrollOffset; idx < sellers.size(); idx++) {
                int ry = py + (idx - scrollOffset) * step;
                if (ry > py + listH) break;
                String seller = sellers.get(idx);
                long cnt = listings.stream().filter(l -> l.seller().equals(seller)).count();
                boolean hov = mx >= winX + PAD && mx < winX + PAD + listW
                           && my >= ry && my < ry + rowH && my >= py && my < py + listH;
                ctx.fill(winX + PAD, ry, winX + PAD + listW, ry + rowH, hov ? C_HOVER : C_PANEL);
                if (hov) {
                    ctx.fill(winX + PAD, ry, winX + PAD + listW, ry + 1, C_GOLD);
                    ctx.fill(winX + PAD, ry + rowH - 1, winX + PAD + listW, ry + rowH, C_GOLD);
                    ctx.fill(winX + PAD, ry, winX + PAD + 1, ry + rowH, C_GOLD);
                    ctx.fill(winX + PAD + listW - 1, ry, winX + PAD + listW, ry + rowH, C_GOLD);
                }
                ctx.drawText(textRenderer, seller, winX + PAD + 12, ry + 10, C_WHITE, false);
                ctx.drawText(textRenderer, cnt + " article" + (cnt > 1 ? "s" : ""), winX + PAD + 12, ry + 24, C_DIM, false);
                ctx.drawText(textRenderer, "›", winX + PAD + listW - 16, ry + rowH / 2 - textRenderer.fontHeight / 2, C_DARK, false);
            }
            ctx.disableScissor();
        }
    }

    /** Vendeurs distincts affichables dans Boutiques — exclut le Shop Serveur et soi-même. */
    private List<String> shopSellers() {
        String me = client != null && client.player != null ? client.player.getName().getString() : "";
        return listings.stream()
            .map(ListingData::seller)
            .filter(s -> !s.equals("$Serveur"))
            .filter(s -> !s.equalsIgnoreCase(me))
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
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

        // Drag de la scrollbar — la piste est mémorisée par le dernier rendu
        if (gridMaxScroll > 0 && x >= scrollTrackX - 2 && x <= scrollTrackX + SCROLL_W + 2
                && y >= scrollTrackY && y <= scrollTrackY + scrollTrackH) {
            draggingScroll = true;
            applyScrollFromMouse(y - scrollThumbH / 2);
            return true;
        }

        switch (activeTab) {
            case MARKET -> {
                if (checkSortButtonClick(x, y)) return true;
                if (hoveredCard != null) openBuyModal(hoveredCard);
            }
            case SELL    -> handleSellClick(x, y);
            case MY_SHOP -> handleMyShopClick(x, y);
            case SHOPS   -> handleShopsClick(x, y);
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void handleTabClick(int mx, int my) {
        if (HubBackButton.clicked(winX + PAD, winY + (TOP_H - HubBackButton.H) / 2, mx, my)) return;
        int tx = tabsStartX;
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

    /** Ouvre le modal d'achat : borne la quantité au stock de l'annonce. */
    private void openBuyModal(ListingData l) {
        buyingListing = l;
        buyQtyInput.setBounds(1, Math.max(1, l.quantity()));
        buyQtyInput.setValue(1);
        buyQtyInput.setFocused(false);
    }

    private void handleModalClick(int mx, int my) {
        ListingData l = buyingListing;
        int ox = winX + (winW - MODAL_W) / 2;
        int oy = winY + (winH - MODAL_H) / 2;

        if (mx < ox || mx > ox + MODAL_W || my < oy || my > oy + MODAL_H) {
            buyingListing = null;
            return;
        }

        if (buyQtyInput.mouseClicked(mx, my)) return;

        int btnY = oy + MODAL_H - 38;
        int half = MODAL_W / 2 - 14;
        if (mx >= ox + 10 && mx < ox + 10 + half && my >= btnY && my < btnY + 24) {
            buyingListing = null;
            return;
        }
        if (mx >= ox + MODAL_W - 10 - half && mx < ox + MODAL_W - 10 && my >= btnY && my < btnY + 24) {
            int qty = buyQtyInput.getValue();
            if (balance >= l.pricePerUnit() * qty) sendBuy(l.itemId(), qty, l.itemNBT());
        }
    }

    private void handleSellClick(int mx, int my) {
        int formW = 290;
        int formX = winX + winW - formW - PAD;

        // La case survolée est déterminée au rendu, qui tient compte du scroll —
        // recalculer les positions ici les désynchroniserait dès le premier défilement.
        if (hoveredSellItem != null) {
            selectedSellItem = hoveredSellItem;
            sellQtyInput.setBounds(1, hoveredSellItem.qty());
            sellQtyInput.setValue(1);
            sellPriceInput.setValue(0);
            return;
        }

        if (sellQtyInput.mouseClicked(mx, my))   { sellPriceInput.setFocused(false); return; }
        if (sellPriceInput.mouseClicked(mx, my)) { sellQtyInput.setFocused(false);   return; }

        int sellQty   = sellQtyInput.getValue();
        int sellPrice = sellPriceInput.getValue();
        boolean canSell = selectedSellItem != null && sellPrice > 0 && sellQty > 0 && sellQty <= selectedSellItem.qty();
        int btnY = winY + winH - PAD - 28;
        if (canSell && mx >= formX + 8 && mx < formX + formW - 8 && my >= btnY && my < btnY + 22) {
            sendSell(selectedSellItem.itemId(), sellQty, sellPrice, selectedSellItem.nbt());
        }
    }

    private void handleMyShopClick(int mx, int my) {
        if (hoveredCard != null) sendWithdraw(hoveredCard.id());
    }

    private void handleShopsClick(int mx, int my) {
        int py = winY + TOP_H + PAD;
        if (selectedShop != null) {
            if (mx >= winX + PAD && mx < winX + PAD + 72 && my >= py && my < py + 18) {
                selectedShop = null;
                scrollOffset = 0;
                return;
            }
            if (hoveredCard != null) openBuyModal(hoveredCard);
        } else {
            py += textRenderer.fontHeight + 10;
            List<String> sellers = shopSellers();
            int rowH = 48, step = rowH + 6;
            int listH = winY + winH - PAD - py;
            int listW = winW - PAD * 2 - SCROLL_W - 4;
            for (int idx = scrollOffset; idx < sellers.size(); idx++) {
                int ry = py + (idx - scrollOffset) * step;
                if (ry > py + listH) break;
                if (mx >= winX + PAD && mx < winX + PAD + listW
                    && my >= ry && my < ry + rowH && my < py + listH) {
                    selectedShop = sellers.get(idx);
                    scrollOffset = 0;
                    return;
                }
            }
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (buyingListing != null) return true;
        int next = scrollOffset - (int) Math.signum(delta);
        scrollOffset = Math.max(0, Math.min(next, gridMaxScroll));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingScroll) {
            applyScrollFromMouse((int) my - scrollThumbH / 2);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        draggingScroll = false;
        return super.mouseReleased(mx, my, btn);
    }

    // ── Clavier ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) {
            if (buyingListing != null) { buyingListing = null; return true; }
            if (selectedShop  != null) { selectedShop  = null; return true; }
        }
        if (buyingListing != null) {
            if (buyQtyInput.keyPressed(key)) return true;
        } else if (activeTab == Tab.SELL) {
            if (sellQtyInput.keyPressed(key) || sellPriceInput.keyPressed(key)) return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char chr, int mod) {
        if (buyingListing != null) {
            if (buyQtyInput.charTyped(chr)) return true;
        } else if (activeTab == Tab.SELL) {
            if (sellQtyInput.charTyped(chr) || sellPriceInput.charTyped(chr)) return true;
        }
        return super.charTyped(chr, mod);
    }

    // ── Envoi paquets ─────────────────────────────────────────────────────────

    private void sendBuy(String itemId, int qty, String nbt) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(HdvNetworking.ACTION_BUY);
        buf.writeString(itemId);
        buf.writeInt(qty);
        buf.writeString(nbt != null ? nbt : "");
        ClientPlayNetworking.send(HdvNetworking.HDV_ACTION, buf);
    }

    private void sendSell(String itemId, int qty, int price, String nbt) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(HdvNetworking.ACTION_SELL);
        buf.writeString(itemId);
        buf.writeInt(qty);
        buf.writeInt(price);
        buf.writeString(nbt != null ? nbt : "");
        ClientPlayNetworking.send(HdvNetworking.HDV_ACTION, buf);
    }

    private void sendWithdraw(int listingId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(HdvNetworking.ACTION_WITHDRAW);
        buf.writeInt(listingId);
        ClientPlayNetworking.send(HdvNetworking.HDV_ACTION, buf);
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

    /** ItemStack d'une annonce, NBT appliqué (enchantements, nom custom) si présent. */
    private ItemStack itemStack(ListingData l) {
        ItemStack stack = itemStack(l.itemId());
        if (l.hasNBT()) {
            try {
                stack.setNbt(StringNbtReader.parse(l.itemNBT()));
            } catch (Exception ignored) {}
        }
        return stack;
    }

    /** ItemStack d'une entrée de l'inventaire de vente, NBT appliqué si présent. */
    private ItemStack sellStack(SellItem si) {
        ItemStack stack = new ItemStack(si.item());
        if (si.hasNBT()) {
            try {
                stack.setNbt(StringNbtReader.parse(si.nbt()));
            } catch (Exception ignored) {}
        }
        return stack;
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
