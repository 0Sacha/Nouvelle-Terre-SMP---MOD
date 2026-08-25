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

        // Surtaxe de fortune : les plus riches paient davantage, ce qui freine la
        // concentration des shards sans pénaliser les joueurs au niveau médian.
        int    prixBase = ServerShopPriceManager.getPrice(itemId);
        double taxe     = ServerShopPriceManager.taxeRichesse(pseudo);
        int    prixUnite = Math.max(1, (int) Math.round(prixBase * (1.0 + taxe)));
        int    total     = prixUnite * qty;

        if (eco.getBalance(pseudo) < total)
            return String.format("§cSolde insuffisant — §f%s ◆§c requis, tu as §f%s ◆§c.",
                EconomieCommand.fmt(total), EconomieCommand.fmt(eco.getBalance(pseudo)));

        eco.removeShards(pseudo, total);
        eco.addShards(COMPTE_SERVEUR, total, "Vente au joueur");

        int restant = qty;
        while (restant > 0) {
            int sz = Math.min(restant, item.getMaxCount());
            ItemStack stack = new ItemStack(item, sz);
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
            restant -= sz;
        }

        ServerShopPriceManager.recordSale(itemId, qty);
        TransactionLog.log(pseudo, TransactionLog.TYPE_BUY, qty + "x " + nomItem + " (Shop Serveur)", total);

        String mentionTaxe = taxe > 0
            ? String.format(" §7(dont %d%% de taxe de fortune)", Math.round(taxe * 100))
            : "";
        return String.format("§a✅ §f%dx %s §aacheté pour §f%s ◆§a%s. Solde : §f%s ◆§a.",
            qty, nomItem, EconomieCommand.fmt(total), mentionTaxe,
            EconomieCommand.fmt(eco.getBalance(pseudo)));
    }

    // ── Revente (joueur → serveur) ────────────────────────────────────────────

    public static String sell(ServerPlayerEntity player, String itemId, int qty) {
        if (qty <= 0) return "§cQuantité invalide.";
        // Le Parchemin est un outil d'interface distribué gratuitement : le revendre
        // reviendrait à imprimer des shards à volonté.
        if (itemId.equals("nouvelle-terre-bridge:parchemin"))
            return "§cLe Parchemin ne peut pas être vendu.";

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

        // ⚠ Enregistrer le rachat AVANT de calculer le prix.
        //
        // L'ordre inverse rendait l'aller-retour achat/revente rentable : acheter
        // faisait monter le prix, et la revente était payée à ce prix gonflé par
        // l'achat du joueur lui-même. Au-delà de +82 % (les facteurs peuvent
        // doubler), la marge de 55 % ne suffisait plus et la boucle imprimait des
        // shards — 50 000 devenaient des millions en quelques minutes.
        //
        // En enregistrant d'abord, le vendeur encaisse son propre impact sur le
        // marché : un aller-retour revient toujours à payer P puis récupérer
        // 0,55 × P, soit une perte garantie, quels que soient les multiplicateurs.
        ServerShopPriceManager.recordPurchase(itemId, qty);

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
        eco.addShards(pseudo, total, "Revente au Shop Serveur");

        TransactionLog.log(pseudo, TransactionLog.TYPE_SELL, qty + "x " + nomItem + " (Shop Serveur)", total);

        return String.format("§a✅ §f%dx %s §avendu pour §f%s ◆§a. Solde : §f%s ◆§a.",
            qty, nomItem, EconomieCommand.fmt(total), EconomieCommand.fmt(eco.getBalance(pseudo)));
    }

    /** Remet un Parchemin au joueur s'il n'en a plus. Toujours gratuit. */
    public static String claimParchemin(ServerPlayerEntity player) {
        for (ItemStack s : player.getInventory().main)
            if (s.isOf(com.nouvelleterrebridge.NouvelleTerreBridge.PARCHEMIN))
                return "§eTu as déjà ton Parchemin.";

        ItemStack stack = new ItemStack(com.nouvelleterrebridge.NouvelleTerreBridge.PARCHEMIN);
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        return "§a✅ Parchemin récupéré — clic droit pour ouvrir le menu.";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Un item n'entre au catalogue que lorsque la production naturelle du serveur
     * a franchi son seuil. Revalidé côté serveur : le client ne fait pas autorité.
     */
    public static boolean estDebloque(String itemId) {
        ShopThresholds.Entry seuil = ShopThresholds.get(itemId);
        if (seuil == null || seuil.desactive) return false;
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
