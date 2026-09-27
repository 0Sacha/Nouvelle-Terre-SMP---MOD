package com.nouvelleterrebridge.market;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.ProductionShopManager;
import com.nouvelleterrebridge.economy.ServerShopPriceManager;
import com.nouvelleterrebridge.economy.TransactionLog;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Logique métier du marché : achat, vente, retrait.
 * Appelé uniquement par le GUI HDV — aucune commande chat.
 */
public final class MarketActions {

    private MarketActions() {}

    // ── Achat ─────────────────────────────────────────────────────────────────

    /**
     * Achète {@code qty} unités de la variante {@code itemNBT} de {@code itemId}
     * ("" = variante sans NBT) au meilleur prix disponible. Seules les annonces
     * de la même variante sont agrégées, pour ne pas livrer un mélange d'items
     * enchantés et vierges.
     * @return message de résultat à afficher au joueur
     */
    public static String buy(ServerPlayer player, String itemId, int qty, String itemNBT) {
        String pseudo  = player.getName().getString();
        String nomItem = FrenchItemNames.toDisplay(itemId);
        LocalEconomy eco = LocalEconomy.getInstance();
        String wanted  = itemNBT == null ? "" : itemNBT;

        List<MarketListing> annonces = MarketManager.getInstance().getAll().stream()
            .filter(l -> l.item.equalsIgnoreCase(itemId) && !l.seller.equalsIgnoreCase(pseudo))
            .filter(l -> (l.itemNBT == null ? "" : l.itemNBT).equals(wanted))
            .sorted(Comparator.comparingInt(l -> l.pricePerUnit))
            .collect(Collectors.toList());

        if (annonces.isEmpty())
            return "§cAucune annonce disponible pour §f" + nomItem + "§c.";

        int stockTotal = annonces.stream().mapToInt(l -> l.quantity).sum();
        if (stockTotal < qty)
            return String.format("§cStock insuffisant — §f%d§c/%d dispo.", stockTotal, qty);

        int coutTotal = 0, restCalc = qty;
        for (MarketListing l : annonces) {
            int pris = Math.min(restCalc, l.quantity);
            coutTotal += pris * l.pricePerUnit;
            restCalc -= pris;
            if (restCalc == 0) break;
        }

        if (eco.getBalance(pseudo) < coutTotal)
            return String.format("§cSolde insuffisant — §f%d💎§c requis, tu as §f%d💎§c.",
                coutTotal, eco.getBalance(pseudo));

        Item itemObj = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        int restant = qty;

        for (MarketListing ann : annonces) {
            if (restant <= 0) break;
            int pris = Math.min(restant, ann.quantity);
            int cout = pris * ann.pricePerUnit;
            boolean isAuto = ann.seller.equals(ProductionShopManager.AUTO_SELLER);

            eco.transfer(pseudo, ann.seller, cout);

            int aDistrib = pris;
            while (aDistrib > 0) {
                int sz = Math.min(aDistrib, new ItemStack(itemObj).getMaxStackSize());
                ItemStack stack = new ItemStack(itemObj, sz);
                // Restaurer les composants (enchantements, etc.) si présents
                if (!ItemComponentCodec.appliquer(stack, ann.itemNBT, player.getServer().registryAccess())) {
                    NouvelleTerreBridge.LOGGER.warn("[MarketActions] Échec restauration composants (annonce #{})", ann.id);
                }
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                aDistrib -= sz;
            }

            if (isAuto) {
                ServerShopPriceManager.recordSale(itemId, pris);
            }

            if (!isAuto) {
                // Annonce joueur : décrémente le stock (l'annonce disparaît à 0)
                int nouvelleQte = ann.quantity - pris;
                MarketManager.getInstance().updateQuantity(ann.id, nouvelleQte);

                // Notif vendeur en ligne
                ServerPlayer vend = player.getServer().getPlayerList().getPlayerByName(ann.seller);
                if (vend != null) vend.sendSystemMessage(Component.literal(String.format(
                    "§a💰 §f%s§a a acheté §f%dx %s§a pour §f%d💎§a !%s Solde : §f%d💎§a.",
                    pseudo, pris, nomItem, cout,
                    nouvelleQte > 0 ? " §7(§f" + nouvelleQte + " restants§7)" : " §7(stock épuisé)",
                    eco.getBalance(ann.seller))));

                TransactionLog.log(ann.seller, TransactionLog.TYPE_SELL, pris + "x " + nomItem + " (à " + pseudo + ")", cout);
                Map<String, Object> data = new HashMap<>();
                data.put("seller", ann.seller); data.put("buyer", pseudo);
                data.put("item", ann.item);     data.put("quantity", pris);
                data.put("total", cout);        data.put("id", ann.id);
                EventDispatcher.envoyer("SALE_COMPLETED", data);
            }
            // Annonce $Serveur : stock illimité, l'annonce reste en place telle quelle
            restant -= pris;
        }

        TransactionLog.log(pseudo, TransactionLog.TYPE_BUY, qty + "x " + nomItem, coutTotal);
        return String.format("§a✅ §f%dx %s §aacheté pour §f%s💎§a au total. Solde : §f%s💎§a.",
            qty, nomItem, EconomieCommand.fmt(coutTotal), EconomieCommand.fmt(eco.getBalance(pseudo)));
    }

    // ── Vente (depuis le GUI client) ──────────────────────────────────────────

    /**
     * Met en vente {@code qty} unités de {@code itemId} depuis l'inventaire du
     * joueur, en ne consommant que les piles dont le NBT correspond à
     * {@code itemNBT} (chaîne vide = uniquement les piles sans NBT). Garantit
     * qu'une pile enchantée n'est jamais consommée à la place d'une pile vierge,
     * et inversement.
     * @return message d'erreur, ou null si l'annonce a été créée
     */
    public static String sellByItemId(ServerPlayer player, String itemId, int qty, int pricePerUnit, String itemNBT) {
        String pseudo  = player.getName().getString();
        String nomItem = FrenchItemNames.toDisplay(itemId);
        String wanted  = itemNBT == null ? "" : itemNBT;

        // Compter la quantité disponible dont le NBT correspond exactement
        HolderLookup.Provider registries = player.getServer().registryAccess();
        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (matchesListing(stack, itemId, wanted, registries)) available += stack.getCount();
        }
        if (available < qty)
            return String.format("§cTu n'as que §f%d§c exemplaire(s) de §f%s§c.", available, nomItem);

        // Retirer les items de l'inventaire (mêmes critères de correspondance)
        int toRemove = qty;
        for (int i = 0; i < player.getInventory().items.size() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (matchesListing(stack, itemId, wanted, registries)) {
                int take = Math.min(toRemove, stack.getCount());
                stack.shrink(take);
                toRemove -= take;
            }
        }

        MarketListing annonce = MarketManager.getInstance()
            .addListing(pseudo, itemId, qty, pricePerUnit, wanted.isEmpty() ? null : wanted);

        player.getServer().getPlayerList().broadcastSystemMessage(Component.literal(String.format(
            "§6[Marché] §e%s §7vend §f%dx %s §7· §f%d💎/u — §f/hdv",
            pseudo, qty, nomItem, pricePerUnit)), false);

        Map<String, Object> data = new HashMap<>();
        data.put("player", pseudo); data.put("item", itemId);
        data.put("quantity", qty);  data.put("price", pricePerUnit); data.put("id", annonce.id);
        EventDispatcher.envoyer("SALE_POSTED", data);

        return null; // succès
    }

    /** Vrai si la pile est du bon item ET porte exactement le NBT attendu ("" = aucun NBT). */
    private static boolean matchesListing(ItemStack stack, String itemId, String wantedNBT,
                                          HolderLookup.Provider registries) {
        if (stack.isEmpty()) return false;
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) return false;
        String actual = ItemComponentCodec.capturer(stack, registries);
        return actual.equals(wantedNBT);
    }

    // ── Retrait ───────────────────────────────────────────────────────────────

    /**
     * Retire l'annonce {@code listingId} et rend les items au joueur.
     * @return message de résultat
     */
    public static String withdraw(ServerPlayer player, int listingId) {
        Optional<MarketListing> opt = MarketManager.getInstance().getListing(listingId);
        if (opt.isEmpty())
            return "§cAnnonce §f#" + listingId + " §cintrouvable.";

        MarketListing ann = opt.get();
        String pseudo = player.getName().getString();
        if (!ann.seller.equalsIgnoreCase(pseudo))
            return "§cCette annonce appartient à §f" + ann.seller + "§c.";

        String nomItem = FrenchItemNames.toDisplay(ann.item);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(ann.item));
        int restant = ann.quantity;
        while (restant > 0) {
            int sz = Math.min(restant, new ItemStack(item).getMaxStackSize());
            ItemStack stack = new ItemStack(item, sz);
            // Restaurer les composants (enchantements, etc.) si présents
            if (!ItemComponentCodec.appliquer(stack, ann.itemNBT, player.getServer().registryAccess())) {
                NouvelleTerreBridge.LOGGER.warn("[MarketActions] Échec restauration composants retrait (annonce #{})", ann.id);
            }
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            restant -= sz;
        }

        MarketManager.getInstance().removeListing(ann.id);

        Map<String, Object> data = new HashMap<>();
        data.put("seller", ann.seller); data.put("item", ann.item);
        data.put("quantity", ann.quantity); data.put("id", ann.id);
        EventDispatcher.envoyer("SALE_CANCELLED", data);

        return String.format("§a✅ Annonce §f#%d §aretirée — §f%dx %s §arecupérés.", ann.id, ann.quantity, nomItem);
    }
}
