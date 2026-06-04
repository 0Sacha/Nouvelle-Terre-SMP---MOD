package com.nouvelleterrebridge.shop;

import com.nouvelleterrebridge.commands.EconomieCommand;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Collectors;

public final class HdvGui {

    private static final Map<UUID, HdvState> STATES = new HashMap<>();

    private HdvGui() {}

    // ── Ouverture ─────────────────────────────────────────────────────────────

    /** Ouvre le HDV sur la page vendeurs (accueil). */
    public static void openHdv(ServerPlayerEntity player) {
        open(player, new HdvState(new SimpleInventory(54)));
    }

    /** Ouvre le HDV directement sur Ma Boutique. */
    public static void openHdvMyShop(ServerPlayerEntity player) {
        HdvState state = new HdvState(new SimpleInventory(54));
        state.mode = HdvState.Mode.MY_SHOP;
        open(player, state);
    }

    /** Ouvre le HDV sur la liste des items avec un filtre texte actif. */
    public static void openHdvWithSearch(ServerPlayerEntity player, String query) {
        HdvState state = new HdvState(new SimpleInventory(54));
        state.mode        = HdvState.Mode.ITEMS;
        state.searchQuery = query.isEmpty() ? null : query;
        open(player, state);
    }

    private static void open(ServerPlayerEntity player, HdvState state) {
        STATES.put(player.getUuid(), state);
        buildPage(player, state);
        UUID uuid = player.getUuid();
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new HdvScreenHandler(syncId, playerInv, state.inv, uuid),
            Text.literal("§6§l🏪 HDV — Nouvelle Terre")));
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public static boolean hasState(UUID uuid) { return STATES.containsKey(uuid); }
    public static void removeState(UUID uuid) { STATES.remove(uuid); }

    public static void handleClick(ServerPlayerEntity player, int slot, int button, SlotActionType action) {
        HdvState state = STATES.get(player.getUuid());
        if (state == null) return;
        if (action == SlotActionType.QUICK_MOVE) {
            state.shiftClick.getOrDefault(slot, () -> {}).run();
        } else if (action == SlotActionType.PICKUP) {
            if (button == 0) state.leftClick.getOrDefault(slot,  () -> {}).run();
            else if (button == 1) state.rightClick.getOrDefault(slot, () -> {}).run();
        }
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private static void buildPage(ServerPlayerEntity player, HdvState state) {
        state.clearHandlers();
        for (int i = 0; i < 54; i++) state.inv.setStack(i, ItemStack.EMPTY);
        switch (state.mode) {
            case VENDORS  -> buildVendors(player, state);
            case ITEMS    -> buildItems(player, state);
            case MY_SHOP  -> buildMyShop(player, state);
            case SELL     -> buildSell(player, state);
        }
    }

    // ── Page VENDORS (accueil) ────────────────────────────────────────────────

    private static void buildVendors(ServerPlayerEntity player, HdvState state) {
        // Header
        for (int i = 0; i < 9; i++) state.inv.setStack(i, fill());

        state.inv.setStack(4, guiItem(Items.GOLD_INGOT,
            "§6§lHDV — Nouvelle Terre", "§7Boutiques des joueurs actifs"));

        // Bouton recherche globale
        boolean hasSearch = state.searchQuery != null && !state.searchQuery.isEmpty();
        if (hasSearch) {
            state.inv.setStack(6, guiItem(Items.SPYGLASS,
                "§b🔍 §f\"" + state.searchQuery + "\"",
                "§7Recherche active",
                "§aG §8» §cEffacer",
                "§bD §8» §bModifier"));
            state.leftClick.put(6, () -> { state.searchQuery = null; buildPage(player, state); });
            state.rightClick.put(6, () -> openSearch(player));
        } else {
            state.inv.setStack(6, guiItem(Items.SPYGLASS, "§b🔍 Rechercher",
                "§7Chercher un item sur tout le HDV"));
            state.leftClick.put(6, () -> openSearch(player));
        }

        // Ma Boutique
        state.inv.setStack(8, guiItem(Items.EMERALD, "§6§lMa Boutique",
            "§7Gérer mes annonces"));
        state.leftClick.put(8, () -> {
            state.mode = HdvState.Mode.MY_SHOP;
            state.page = 0;
            buildPage(player, state);
        });

        // Fond
        for (int i = 9; i < 54; i++) state.inv.setStack(i, fill());

        // Liste des vendeurs
        List<MarketListing> all = MarketManager.getInstance().getAll();
        Map<String, List<MarketListing>> parVendeur = all.stream()
            .collect(Collectors.groupingBy(l -> l.seller));

        // Filtrage par recherche si actif
        String sq = state.searchQuery != null ? state.searchQuery.toLowerCase() : null;
        List<String> vendeurs = parVendeur.keySet().stream()
            .filter(v -> sq == null || v.toLowerCase().contains(sq)
                || parVendeur.get(v).stream().anyMatch(l ->
                    FrenchItemNames.toDisplay(l.item).toLowerCase().contains(sq)
                    || l.item.toLowerCase().contains(sq)))
            .sorted()
            .collect(Collectors.toList());

        int perPage    = 36;
        int totalPages = Math.max(1, (vendeurs.size() + perPage - 1) / perPage);
        state.page = Math.max(0, Math.min(state.page, totalPages - 1));

        int start = state.page * perPage;
        int end   = Math.min(start + perPage, vendeurs.size());

        if (vendeurs.isEmpty()) {
            state.inv.setStack(22, guiItem(Items.BARRIER,
                hasSearch ? "§cAucun vendeur pour \"" + state.searchQuery + "\"" : "§cAucun vendeur actif",
                "§7Utilise §f/hdv §7pour vendre un item"));
        } else {
            for (int i = start; i < end; i++) {
                String vendeur = vendeurs.get(i);
                int slot = 9 + (i - start);
                List<MarketListing> annonces = parVendeur.get(vendeur);
                int nbItems = annonces.stream().mapToInt(l -> l.quantity).sum();

                state.inv.setStack(slot, headItem(vendeur,
                    "§f§l" + vendeur,
                    "§7Annonces : §f" + annonces.size(),
                    "§7Items en vente : §f" + nbItems,
                    "§aG §8» §fVoir la boutique"));

                final String fv = vendeur;
                state.leftClick.put(slot, () -> {
                    state.mode          = HdvState.Mode.ITEMS;
                    state.currentSeller = fv;
                    state.page          = 0;
                    buildPage(player, state);
                });
            }
        }

        buildPagination(player, state, totalPages, vendeurs.size(), "vendeur");
    }

    // ── Page ITEMS ────────────────────────────────────────────────────────────

    private static void buildItems(ServerPlayerEntity player, HdvState state) {
        for (int i = 0; i < 9; i++) state.inv.setStack(i, fill());

        // Retour
        state.inv.setStack(0, guiItem(Items.ARROW, "§c◀ Vendeurs"));
        state.leftClick.put(0, () -> {
            state.mode          = HdvState.Mode.VENDORS;
            state.currentSeller = null;
            state.page          = 0;
            buildPage(player, state);
        });

        // Titre
        if (state.currentSeller != null) {
            state.inv.setStack(4, headItem(state.currentSeller,
                "§f§lBoutique de §6" + state.currentSeller));
        } else {
            state.inv.setStack(4, guiItem(Items.CHEST, "§f§lTous les items"));
        }

        // Recherche
        boolean hasSearch = state.searchQuery != null && !state.searchQuery.isEmpty();
        if (hasSearch) {
            state.inv.setStack(6, guiItem(Items.SPYGLASS,
                "§b🔍 §f\"" + state.searchQuery + "\"",
                "§7Filtre actif",
                "§aG §8» §cEffacer",
                "§bD §8» §bModifier"));
            state.leftClick.put(6, () -> { state.searchQuery = null; state.page = 0; buildPage(player, state); });
            state.rightClick.put(6, () -> openSearch(player));
        } else {
            state.inv.setStack(6, guiItem(Items.SPYGLASS, "§b🔍 Rechercher",
                "§7Chercher un item ou un vendeur"));
            state.leftClick.put(6, () -> openSearch(player));
        }

        // Ma Boutique
        state.inv.setStack(8, guiItem(Items.EMERALD, "§6Ma Boutique"));
        state.leftClick.put(8, () -> { state.mode = HdvState.Mode.MY_SHOP; state.page = 0; buildPage(player, state); });

        for (int i = 9; i < 54; i++) state.inv.setStack(i, fill());

        String me = player.getName().getString();
        String sq = state.searchQuery != null ? state.searchQuery.toLowerCase() : null;

        List<MarketListing> listings = MarketManager.getInstance().getAll().stream()
            .filter(l -> state.currentSeller == null || l.seller.equalsIgnoreCase(state.currentSeller))
            .filter(l -> sq == null
                || FrenchItemNames.toDisplay(l.item).toLowerCase().contains(sq)
                || l.item.toLowerCase().contains(sq)
                || l.seller.toLowerCase().contains(sq))
            .sorted(Comparator.comparingInt(l -> l.pricePerUnit))
            .collect(Collectors.toList());

        int perPage    = 36;
        int totalPages = Math.max(1, (listings.size() + perPage - 1) / perPage);
        state.page = Math.max(0, Math.min(state.page, totalPages - 1));
        int start = state.page * perPage;
        int end   = Math.min(start + perPage, listings.size());

        if (listings.isEmpty()) {
            String msg = hasSearch ? "§cAucun résultat pour \"" + state.searchQuery + "\"" : "§cAucune annonce ici";
            state.inv.setStack(22, guiItem(Items.BARRIER, msg, "§7Sois le premier à vendre !"));
        } else {
            for (int i = start; i < end; i++) {
                MarketListing l = listings.get(i);
                int slot = 9 + (i - start);
                boolean isOwn = l.seller.equalsIgnoreCase(me);
                Item mc  = itemOf(l.item);
                String nom = FrenchItemNames.toDisplay(l.item);

                if (isOwn) {
                    state.inv.setStack(slot, guiItem(mc, "§7§l" + nom,
                        "§8#" + l.id + " §7[Votre annonce]",
                        "§7Prix : §f" + EconomieCommand.fmt(l.pricePerUnit) + " 💎§7/u",
                        "§7Stock : §f" + l.quantity,
                        "§8Gérer depuis §7Ma Boutique"));
                } else {
                    state.inv.setStack(slot, guiItem(mc, "§f§l" + nom,
                        "§7Vendeur : §f" + l.seller,
                        "§7Prix    : §f" + EconomieCommand.fmt(l.pricePerUnit) + " 💎§7/u",
                        "§7Stock   : §f" + l.quantity,
                        "§8——————————————————",
                        "§aG §8» §fAcheter 1",
                        "§bD §8» §fAcheter 1 pile (max 64)",
                        "§eShift §8» §fAcheter tout le stock"));
                    final MarketListing fl = l;
                    state.leftClick.put (slot, () -> doAchat(player, fl.item, 1, state));
                    state.rightClick.put(slot, () -> doAchat(player, fl.item, Math.min(64, fl.quantity), state));
                    state.shiftClick.put(slot, () -> doAchat(player, fl.item, fl.quantity, state));
                }
            }
        }

        buildPagination(player, state, totalPages, listings.size(), "annonce");
    }

    // ── Page MY_SHOP ──────────────────────────────────────────────────────────

    private static void buildMyShop(ServerPlayerEntity player, HdvState state) {
        for (int i = 0; i < 9; i++) state.inv.setStack(i, fill());

        state.inv.setStack(0, guiItem(Items.ARROW, "§c◀ Vendeurs"));
        state.leftClick.put(0, () -> { state.mode = HdvState.Mode.VENDORS; state.page = 0; buildPage(player, state); });

        state.inv.setStack(4, guiItem(Items.EMERALD, "§6§lMa Boutique",
            "§7Clic droit §cpour retirer une annonce"));

        state.inv.setStack(8, guiItem(Items.WRITABLE_BOOK, "§a§l+ Vendre",
            "§7Tiens l'item dans ta main et clique"));
        state.leftClick.put(8, () -> { state.mode = HdvState.Mode.SELL; state.selectedSellQty = 1; buildPage(player, state); });

        for (int i = 9; i < 54; i++) state.inv.setStack(i, fill());

        String me = player.getName().getString();
        List<MarketListing> mine = MarketManager.getInstance().getBySeller(me);

        int perPage    = 36;
        int totalPages = Math.max(1, (mine.size() + perPage - 1) / perPage);
        state.page = Math.max(0, Math.min(state.page, totalPages - 1));
        int start = state.page * perPage;
        int end   = Math.min(start + perPage, mine.size());

        if (mine.isEmpty()) {
            state.inv.setStack(22, guiItem(Items.BARRIER, "§cAucune annonce active",
                "§7Clique sur §a+ Vendre §7avec ton item en main"));
        } else {
            for (int i = start; i < end; i++) {
                MarketListing l = mine.get(i);
                int slot = 9 + (i - start);
                state.inv.setStack(slot, guiItem(itemOf(l.item), "§f§l" + FrenchItemNames.toDisplay(l.item),
                    "§8#" + l.id + " · §fEn vente",
                    "§7Prix  : §f" + EconomieCommand.fmt(l.pricePerUnit) + " 💎§7/u",
                    "§7Stock : §f" + l.quantity,
                    "§7Total : §f" + EconomieCommand.fmt(l.getTotal()) + " 💎",
                    "§8——————————————————",
                    "§cD §8» §fRetirer l'annonce"));
                final int lid = l.id;
                state.rightClick.put(slot, () -> {
                    String msg = MarketActions.withdraw(player, lid);
                    player.sendMessage(Text.literal(msg));
                    buildPage(player, state);
                });
            }
        }

        buildPagination(player, state, totalPages, mine.size(), "annonce");
    }

    // ── Page SELL ─────────────────────────────────────────────────────────────

    private static void buildSell(ServerPlayerEntity player, HdvState state) {
        ItemStack main = player.getMainHandStack();

        if (main.isEmpty()) {
            player.sendMessage(Text.literal("§c❌ Tiens l'item à vendre dans ta main principale !"));
            state.mode = HdvState.Mode.MY_SHOP;
            buildPage(player, state);
            return;
        }

        String itemId  = Registries.ITEM.getId(main.getItem()).toString();
        String nomItem = FrenchItemNames.toDisplay(itemId);
        int maxQty     = main.getCount();
        state.selectedSellQty = Math.max(1, Math.min(state.selectedSellQty, maxQty));

        for (int i = 0; i < 9; i++) state.inv.setStack(i, fill());
        state.inv.setStack(0, guiItem(Items.ARROW, "§c◀ Ma Boutique"));
        state.leftClick.put(0, () -> { state.mode = HdvState.Mode.MY_SHOP; state.page = 0; buildPage(player, state); });
        state.inv.setStack(4, guiItem(Items.GOLD_INGOT, "§6§l🏷 Vendre un item",
            "§7Choisis la quantité puis confirme"));

        for (int i = 9; i < 54; i++) state.inv.setStack(i, fill());

        // Aperçu item
        state.inv.setStack(13, guiItem(itemOf(itemId), "§f§l" + nomItem,
            "§7En main  : §f" + maxQty,
            "§7À vendre : §e" + state.selectedSellQty));

        // Sélecteurs quantité
        int[] presets  = {1, 4, 8, 16, 32, 64};
        int[] slots    = {19, 20, 21, 22, 23, 24};
        for (int j = 0; j < presets.length; j++) {
            int qty = presets[j]; int slot = slots[j];
            if (qty > maxQty) {
                state.inv.setStack(slot, guiItem(Items.RED_STAINED_GLASS_PANE, "§8" + qty + "x"));
            } else {
                boolean sel = qty == state.selectedSellQty;
                state.inv.setStack(slot, guiItem(
                    sel ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                    (sel ? "§a§l✔ " : "§f") + qty + "x",
                    sel ? "§aSélectionné" : "§7Cliquer pour sélectionner"));
                final int fq = qty;
                state.leftClick.put(slot, () -> { state.selectedSellQty = fq; buildPage(player, state); });
            }
        }
        // Bouton "Tout" si pas déjà dans les presets
        boolean dejaDans = Arrays.stream(presets).anyMatch(p -> p == maxQty);
        if (!dejaDans) {
            boolean sel = maxQty == state.selectedSellQty;
            state.inv.setStack(25, guiItem(
                sel ? Items.LIME_STAINED_GLASS_PANE : Items.YELLOW_STAINED_GLASS_PANE,
                (sel ? "§a§l✔ " : "§e") + "Tout (" + maxQty + "x)",
                sel ? "§aSélectionné" : "§7Vendre toute la pile"));
            state.leftClick.put(25, () -> { state.selectedSellQty = maxQty; buildPage(player, state); });
        }

        // Bouton confirmer → ouvre l'enclume pour le prix
        final int qtyFinal = state.selectedSellQty;
        state.inv.setStack(49, guiItem(Items.EMERALD,
            "§a§l✅ Confirmer — §f" + qtyFinal + "x §a" + nomItem,
            "§7Tape le prix par unité dans l'enclume"));
        state.leftClick.put(49, () -> {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new HdvSellPriceHandler(syncId, playerInv, (ServerPlayerEntity) p, qtyFinal),
                Text.literal("§a§l💎 Prix par unité ?")));
        });
    }

    // ── Recherche ─────────────────────────────────────────────────────────────

    private static void openSearch(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new HdvSearchHandler(syncId, playerInv, (ServerPlayerEntity) p),
            Text.literal("§b§l🔍 Rechercher dans le HDV")));
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    private static void buildPagination(ServerPlayerEntity player, HdvState state,
                                        int totalPages, int total, String unit) {
        if (state.page > 0) {
            state.inv.setStack(45, guiItem(Items.ARROW, "§f◀ Page précédente",
                "§7Page " + state.page + " / " + totalPages));
            state.leftClick.put(45, () -> { state.page--; buildPage(player, state); });
        }
        state.inv.setStack(49, guiItem(Items.PAPER,
            "§7Page §f" + (state.page + 1) + " §7/ §f" + totalPages,
            "§7" + total + " " + unit + (total > 1 ? "s" : "")));
        if (state.page < totalPages - 1) {
            state.inv.setStack(53, guiItem(Items.ARROW, "§fPage suivante ▶",
                "§7Page " + (state.page + 2) + " / " + totalPages));
            state.leftClick.put(53, () -> { state.page++; buildPage(player, state); });
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private static void doAchat(ServerPlayerEntity player, String itemId, int qty, HdvState state) {
        String msg = MarketActions.buy(player, itemId, qty);
        player.sendMessage(Text.literal(msg));
        buildPage(player, state);
    }

    // ── Helpers visuels ───────────────────────────────────────────────────────

    private static ItemStack guiItem(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name).styled(s -> s.withItalic(false)));
        if (lore.length > 0) {
            NbtList nb = new NbtList();
            for (String line : lore)
                nb.add(NbtString.of(Text.Serializer.toJson(
                    Text.literal(line).styled(s -> s.withItalic(false)))));
            stack.getOrCreateSubNbt("display").put("Lore", nb);
        }
        return stack;
    }

    private static ItemStack headItem(String playerName, String displayName, String... lore) {
        ItemStack stack = guiItem(Items.PLAYER_HEAD, displayName, lore);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("SkullOwner", playerName);
        return stack;
    }

    private static ItemStack fill() {
        return guiItem(Items.BLACK_STAINED_GLASS_PANE, "§r");
    }

    private static Item itemOf(String mcId) {
        Item item = Registries.ITEM.get(Identifier.tryParse(mcId));
        return item == Items.AIR ? Items.BARRIER : item;
    }
}
