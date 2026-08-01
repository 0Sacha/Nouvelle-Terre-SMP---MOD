package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.market.FrenchItemNames;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Achat et revente auprès du Shop Serveur ($Serveur).
 *
 * Le shop a un stock illimité à l'achat ; à la revente il absorbe tout, mais
 * au prix de rachat (marge), et chaque transaction fait bouger le prix.
 */
public final class ServerShopActions {

    private ServerShopActions() {}

    /** Compte système du serveur — le préfixe $ l'exclut des classements et statistiques. */
    public static final String COMPTE_SERVEUR = ProductionShopManager.AUTO_SELLER;

    // ── Achat (joueur → serveur) ──────────────────────────────────────────────

    public static String buy(ServerPlayerEntity player, String itemId, int qty) {
        if (qty <= 0) return "§cQuantité invalide.";

        Item item = resolve(itemId);
        if (item == null) return "§cItem inconnu.";
        if (!estDebloque(itemId)) return "§cCet article n'est pas au catalogue du serveur.";

        String pseudo  = player.getName().getString();
        String nomItem = FrenchItemNames.toDisplay(itemId);
        LocalEconomy eco = LocalEconomy.getInstance();

        int prixUnite = ServerShopPriceManager.getPrice(itemId);
        int total     = prixUnite * qty;

        if (eco.getBalance(pseudo) < total)
            return String.format("§cSolde insuffisant — §f%s ◆§c requis, tu as §f%s ◆§c.",
                EconomieCommand.fmt(total), EconomieCommand.fmt(eco.getBalance(pseudo)));

        eco.removeShards(pseudo, total);
        eco.addShards(COMPTE_SERVEUR, total);

        int restant = qty;
        while (restant > 0) {
            int sz = Math.min(restant, item.getMaxCount());
            ItemStack stack = new ItemStack(item, sz);
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
            restant -= sz;
        }

        ServerShopPriceManager.recordSale(itemId, qty);
        TransactionLog.log(pseudo, TransactionLog.TYPE_BUY, qty + "x " + nomItem + " (Shop Serveur)", total);

        return String.format("§a✅ §f%dx %s §aacheté pour §f%s ◆§a. Solde : §f%s ◆§a.",
            qty, nomItem, EconomieCommand.fmt(total), EconomieCommand.fmt(eco.getBalance(pseudo)));
    }

    // ── Revente (joueur → serveur) ────────────────────────────────────────────

    public static String sell(ServerPlayerEntity player, String itemId, int qty) {
        if (qty <= 0) return "§cQuantité invalide.";

        Item item = resolve(itemId);
        if (item == null) return "§cItem inconnu.";
        if (!estDebloque(itemId)) return "§cCet article n'est pas au catalogue du serveur.";

        String pseudo  = player.getName().getString();
        String nomItem = FrenchItemNames.toDisplay(itemId);
        LocalEconomy eco = LocalEconomy.getInstance();

        // Seules les piles vierges sont rachetées : impossible d'évaluer
        // équitablement un objet enchanté, renommé ou abîmé.
        int disponible = 0;
        for (ItemStack s : player.getInventory().main)
            if (estRachetable(s, itemId)) disponible += s.getCount();

        if (disponible < qty)
            return String.format("§cTu n'as que §f%d§c exemplaire(s) de §f%s§c en état d'être vendu(s). "
                + "§7(objets enchantés, renommés ou abîmés non rachetés)", disponible, nomItem);

        int prixUnite = ServerShopPriceManager.getBuybackPrice(itemId);
        int total     = prixUnite * qty;

        int aRetirer = qty;
        for (int i = 0; i < player.getInventory().main.size() && aRetirer > 0; i++) {
            ItemStack s = player.getInventory().main.get(i);
            if (estRachetable(s, itemId)) {
                int pris = Math.min(aRetirer, s.getCount());
                s.decrement(pris);
                aRetirer -= pris;
            }
        }

        // Le compte serveur peut passer négatif : c'est un puits comptable,
        // exclu des totaux, pas une trésorerie à équilibrer.
        eco.forceDeduct(COMPTE_SERVEUR, total);
        eco.addShards(pseudo, total);

        ServerShopPriceManager.recordPurchase(itemId, qty);
        TransactionLog.log(pseudo, TransactionLog.TYPE_SELL, qty + "x " + nomItem + " (Shop Serveur)", total);

        return String.format("§a✅ §f%dx %s §avendu pour §f%s ◆§a. Solde : §f%s ◆§a.",
            qty, nomItem, EconomieCommand.fmt(total), EconomieCommand.fmt(eco.getBalance(pseudo)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Un item n'entre au catalogue que lorsque la production naturelle du serveur
     * a franchi son seuil. Revalidé côté serveur : le client ne fait pas autorité.
     */
    public static boolean estDebloque(String itemId) {
        ShopThresholds.Entry seuil = ShopThresholds.get(itemId);
        if (seuil == null) return false;
        return ProductionTracker.get(itemId) >= seuil.seuil;
    }

    private static boolean estRachetable(ItemStack s, String itemId) {
        if (s.isEmpty() || s.hasNbt()) return false;
        if (s.isDamaged()) return false;
        return Registries.ITEM.getId(s.getItem()).toString().equals(itemId);
    }

    private static Item resolve(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        Item item = Registries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }
}
