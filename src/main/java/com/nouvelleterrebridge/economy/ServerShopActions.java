package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.market.FrenchItemNames;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Plafond par transaction : un inventaire plein (36 emplacements de 64).
     * Sans lui, une quantité géante faisait déborder {@code prix × quantité} en
     * négatif — l'achat créditait alors le joueur — et la boucle de livraison
     * créait des millions de piles, gelant le serveur en un seul paquet.
     */
    public static final int QTE_MAX = 36 * 64;

    public static String buy(ServerPlayer player, String itemId, int qty) {
        if (qty <= 0 || qty > QTE_MAX) return "§cQuantité invalide (" + QTE_MAX + " maximum par achat).";

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
        long   totalLong = (long) prixUnite * qty;
        if (totalLong > Integer.MAX_VALUE) return "§cMontant trop élevé — réduis la quantité.";
        int    total     = (int) totalLong;

        if (eco.getBalance(pseudo) < total)
            return String.format("§cSolde insuffisant — §f%s ◆§c requis, tu as §f%s ◆§c.",
                EconomieCommand.fmt(total), EconomieCommand.fmt(eco.getBalance(pseudo)));

        eco.removeShards(pseudo, total);
        eco.addShards(COMPTE_SERVEUR, total, "Vente au joueur");

        int restant = qty;
        while (restant > 0) {
            int sz = Math.min(restant, new ItemStack(item).getMaxStackSize());
            ItemStack stack = new ItemStack(item, sz);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
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

    public static String sell(ServerPlayer player, String itemId, int qty) {
        if (qty <= 0 || qty > QTE_MAX) return "§cQuantité invalide (" + QTE_MAX + " maximum par vente).";
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
        for (ItemStack s : player.getInventory().items)
            if (estRachetable(s, itemId)) disponible += s.getCount();

        if (disponible < qty)
            return String.format("§cTu n'as que §f%d§c exemplaire(s) de §f%s§c en état d'être vendu(s). "
                + "§7(objets enchantés, renommés ou abîmés non rachetés)", disponible, nomItem);

        int aRetirer = qty;
        for (int i = 0; i < player.getInventory().items.size() && aRetirer > 0; i++) {
            ItemStack s = player.getInventory().items.get(i);
            if (estRachetable(s, itemId)) {
                int pris = Math.min(aRetirer, s.getCount());
                s.shrink(pris);
                aRetirer -= pris;
            }
        }

        int total = encaisser(pseudo, itemId, qty, 1.0, "Revente au Shop Serveur");
        TransactionLog.log(pseudo, TransactionLog.TYPE_SELL, qty + "x " + nomItem + " (Shop Serveur)", total);

        return String.format("§a✅ §f%dx %s §avendu pour §f%s ◆§a. Solde : §f%s ◆§a.",
            qty, nomItem, EconomieCommand.fmt(total), EconomieCommand.fmt(eco.getBalance(pseudo)));
    }

    /**
     * Cœur de tout rachat : fait bouger le prix, puis paie. Renvoie le montant versé.
     *
     * ⚠ Enregistrer le rachat AVANT de calculer le prix.
     *
     * L'ordre inverse rendait l'aller-retour achat/revente rentable : acheter
     * faisait monter le prix, et la revente était payée à ce prix gonflé par
     * l'achat du joueur lui-même. Au-delà de +82 % (les facteurs peuvent
     * doubler), la marge de 55 % ne suffisait plus et la boucle imprimait des
     * shards — 50 000 devenaient des millions en quelques minutes.
     *
     * En enregistrant d'abord, le vendeur encaisse son propre impact sur le
     * marché : un aller-retour revient toujours à payer P puis récupérer
     * 0,55 × P, soit une perte garantie, quels que soient les multiplicateurs.
     *
     * @param part fraction du prix de rachat réellement versée (1.0 = tout)
     */
    private static int encaisser(String pseudo, String itemId, int qty, double part, String raison) {
        ServerShopPriceManager.recordPurchase(itemId, qty);

        int prixUnite = ServerShopPriceManager.getBuybackPrice(itemId);
        // Calcul en long : ni une grosse quantité ni un prix admin aberrant ne déborde.
        int total = (int) Math.min(Integer.MAX_VALUE, Math.round((long) prixUnite * qty * part));
        if (total <= 0) return 0;

        // Le compte serveur peut passer négatif : c'est un puits comptable,
        // exclu des totaux, pas une trésorerie à équilibrer.
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.forceDeduct(COMPTE_SERVEUR, total);
        eco.addShards(pseudo, total, raison);
        return total;
    }

    // ── Vente groupée ─────────────────────────────────────────────────────────

    /**
     * Identifiant de l'article si cette pile peut être rachetée telle quelle
     * (vierge et au catalogue), sinon {@code null}.
     */
    public static String idVendable(ItemStack s) {
        if (s.isEmpty() || !s.getComponentsPatch().isEmpty() || s.isDamaged()) return null;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        if (id.equals("nouvelle-terre-bridge:parchemin") || !estDebloque(id)) return null;
        return id;
    }

    /** Vend d'un coup tout ce que l'inventaire contient de rachetable. */
    public static String vendreTout(ServerPlayer player) {
        Map<String, Integer> lot = new LinkedHashMap<>();
        for (ItemStack s : player.getInventory().items) {
            String id = idVendable(s);
            if (id == null) continue;
            lot.merge(id, s.getCount(), Integer::sum);
            s.setCount(0);
        }
        if (lot.isEmpty()) return "§cRien à vendre — le serveur ne rachète que les objets vierges du catalogue.";

        String pseudo = player.getName().getString();
        int total = 0, unites = 0;
        for (Map.Entry<String, Integer> e : lot.entrySet()) {
            total  += encaisser(pseudo, e.getKey(), e.getValue(), 1.0, "Revente au Shop Serveur");
            unites += e.getValue();
        }
        // Une seule ligne d'historique : une par article noierait le reste de /bank.
        TransactionLog.log(pseudo, TransactionLog.TYPE_SELL,
            "Vente groupée : " + unites + " objets (Shop Serveur)", total);

        return String.format("§a✅ §f%d objets §a(%d articles) vendus pour §f%s ◆§a. Solde : §f%s ◆§a.",
            unites, lot.size(), EconomieCommand.fmt(total),
            EconomieCommand.fmt(LocalEconomy.getInstance().getBalance(pseudo)));
    }

    /**
     * Part du prix de rachat retenue par le module de vente d'un sac à dos.
     * Vendre sans passer par le Shop est un confort : il se paie, et la retenue
     * freine aussi le revenu d'une ferme laissée tourner toute seule.
     */
    public static final double COMMISSION_SAC = 0.10;

    /**
     * Paie ce qu'un sac à dos équipé du module de vente vient d'écouler. Les objets
     * ont déjà été retirés du sac par l'appelant ; {@code lot} = article → quantité,
     * uniquement des articles validés par {@link #idVendable}.
     */
    public static void venteAutomatique(ServerPlayer player, Map<String, Integer> lot) {
        String pseudo = player.getName().getString();
        int total = 0;
        for (Map.Entry<String, Integer> e : lot.entrySet())
            total += encaisser(pseudo, e.getKey(), e.getValue(), 1.0 - COMMISSION_SAC, "Vente automatique (sac à dos)");
        if (total <= 0) return;

        // Cumulé sur la dernière ligne : le sac vend toutes les quelques secondes.
        TransactionLog.cumuler(pseudo, TransactionLog.TYPE_SELL, "Vente automatique (sac à dos)", total);
        player.displayClientMessage(
            Component.literal("§a+" + EconomieCommand.fmt(total) + " ◆ §7— vente automatique du sac"), true);
        com.nouvelleterrebridge.NouvelleTerreBridge.sendBalanceToPlayer(player);
    }

    /** Remet un Parchemin au joueur s'il n'en a plus. Toujours gratuit. */
    public static String claimParchemin(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items)
            if (s.is(com.nouvelleterrebridge.NouvelleTerreBridge.PARCHEMIN.get()))
                return "§eTu as déjà ton Parchemin.";

        ItemStack stack = new ItemStack(com.nouvelleterrebridge.NouvelleTerreBridge.PARCHEMIN.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
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
        if (s.isEmpty() || !s.getComponentsPatch().isEmpty()) return false;
        if (s.isDamaged()) return false;
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(itemId);
    }

    private static Item resolve(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }
}
