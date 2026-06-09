package com.nouvelleterrebridge.shop;

import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.TransactionLog;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
     * Achète {@code qty} unités de {@code itemId} au meilleur prix disponible.
     * @return message de résultat à afficher au joueur
     */
    public static String buy(ServerPlayerEntity player, String itemId, int qty) {
        String pseudo  = player.getName().getString();
        String nomItem = FrenchItemNames.toDisplay(itemId);
        LocalEconomy eco = LocalEconomy.getInstance();

        List<MarketListing> annonces = MarketManager.getInstance().getAll().stream()
            .filter(l -> l.item.equalsIgnoreCase(itemId) && !l.seller.equalsIgnoreCase(pseudo))
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

        Item itemObj = Registries.ITEM.get(Identifier.tryParse(itemId));
        int restant = qty;

        for (MarketListing ann : annonces) {
            if (restant <= 0) break;
            int pris = Math.min(restant, ann.quantity);
            int cout = pris * ann.pricePerUnit;

            eco.transfer(pseudo, ann.seller, cout);

            int aDistrib = pris;
            while (aDistrib > 0) {
                int sz = Math.min(aDistrib, itemObj.getMaxCount());
                ItemStack stack = new ItemStack(itemObj, sz);
                if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
                aDistrib -= sz;
            }

            int nouvelleQte = ann.quantity - pris;
            MarketManager.getInstance().updateQuantity(ann.id, nouvelleQte);

            // Notif vendeur en ligne
            ServerPlayerEntity vend = player.getServer().getPlayerManager().getPlayer(ann.seller);
            if (vend != null) vend.sendMessage(Text.literal(String.format(
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
            restant -= pris;
        }

        TransactionLog.log(pseudo, TransactionLog.TYPE_BUY, qty + "x " + nomItem, coutTotal);
        return String.format("§a✅ §f%dx %s §aacheté pour §f%s💎§a au total. Solde : §f%s💎§a.",
            qty, nomItem, EconomieCommand.fmt(coutTotal), EconomieCommand.fmt(eco.getBalance(pseudo)));
    }

    // ── Vente ─────────────────────────────────────────────────────────────────

    /**
     * Met en vente {@code qty} unités de l'item en main du joueur à {@code pricePerUnit} shards/u.
     * @return message de résultat ou null si succès (la liste a été créée)
     */
    public static String sell(ServerPlayerEntity player, int qty, int pricePerUnit) {
        ItemStack main = player.getMainHandStack();
        if (main.isEmpty())
            return "§cTiens l'item à vendre dans ta main !";
        if (main.getCount() < qty)
            return String.format("§cTu n'as que §f%d§c exemplaire(s) en main.", main.getCount());

        String itemId  = Registries.ITEM.getId(main.getItem()).toString();
        String nomItem = FrenchItemNames.toDisplay(itemId);

        main.decrement(qty);
        MarketListing annonce = MarketManager.getInstance().addListing(
            player.getName().getString(), itemId, qty, pricePerUnit);

        player.getServer().getPlayerManager().broadcast(Text.literal(String.format(
            "§6[Marché] §e%s §7vend §f%dx %s §7· §f%d💎/u — §f/hdv",
            player.getName().getString(), qty, nomItem, pricePerUnit)), false);

        Map<String, Object> data = new HashMap<>();
        data.put("player", player.getName().getString());
        data.put("item", itemId); data.put("quantity", qty);
        data.put("price", pricePerUnit); data.put("id", annonce.id);
        EventDispatcher.envoyer("SALE_POSTED", data);

        return null; // succès
    }

    // ── Vente par ID d'item (depuis le GUI client) ────────────────────────────

    /**
     * Met en vente {@code qty} unités de {@code itemId} depuis l'inventaire du joueur.
     * Contrairement à {@link #sell}, ne requiert pas l'item en main.
     */
    public static String sellByItemId(ServerPlayerEntity player, String itemId, int qty, int pricePerUnit) {
        String pseudo  = player.getName().getString();
        String nomItem = FrenchItemNames.toDisplay(itemId);

        // Compter la quantité disponible dans l'inventaire
        int available = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId))
                available += stack.getCount();
        }
        if (available < qty)
            return String.format("§cTu n'as que §f%d§c exemplaire(s) de §f%s§c.", available, nomItem);

        // Retirer les items de l'inventaire
        int toRemove = qty;
        for (int i = 0; i < player.getInventory().main.size() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                int take = Math.min(toRemove, stack.getCount());
                stack.decrement(take);
                toRemove -= take;
            }
        }

        MarketListing annonce = MarketManager.getInstance().addListing(pseudo, itemId, qty, pricePerUnit);

        player.getServer().getPlayerManager().broadcast(net.minecraft.text.Text.literal(String.format(
            "§6[Marché] §e%s §7vend §f%dx %s §7· §f%d💎/u — §f/hdv",
            pseudo, qty, nomItem, pricePerUnit)), false);

        Map<String, Object> data = new HashMap<>();
        data.put("player", pseudo); data.put("item", itemId);
        data.put("quantity", qty);  data.put("price", pricePerUnit); data.put("id", annonce.id);
        EventDispatcher.envoyer("SALE_POSTED", data);

        return null; // succès
    }

    // ── Retrait ───────────────────────────────────────────────────────────────

    /**
     * Retire l'annonce {@code listingId} et rend les items au joueur.
     * @return message de résultat
     */
    public static String withdraw(ServerPlayerEntity player, int listingId) {
        Optional<MarketListing> opt = MarketManager.getInstance().getListing(listingId);
        if (opt.isEmpty())
            return "§cAnnonce §f#" + listingId + " §cintrouvable.";

        MarketListing ann = opt.get();
        String pseudo = player.getName().getString();
        if (!ann.seller.equalsIgnoreCase(pseudo))
            return "§cCette annonce appartient à §f" + ann.seller + "§c.";

        String nomItem = FrenchItemNames.toDisplay(ann.item);
        Item item = Registries.ITEM.get(Identifier.tryParse(ann.item));
        int restant = ann.quantity;
        while (restant > 0) {
            int sz = Math.min(restant, item.getMaxCount());
            ItemStack stack = new ItemStack(item, sz);
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
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
