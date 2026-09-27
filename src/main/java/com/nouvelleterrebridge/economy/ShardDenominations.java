package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Coupures de la monnaie physique : 1, 5, 10, 20, 50 et 100 ◆.
 *
 * Purement une question de rangement : retirer 5 000 ◆ en pièces de 1 remplissait
 * 78 piles d'inventaire. La valeur totale est la seule chose qui compte — une
 * coupure de 100 vaut exactement cent pièces de 1, rien de plus.
 *
 * La coupure de 1 garde l'identifiant d'origine {@code nouvelle-terre-bridge:shard} :
 * changer son id aurait fait disparaître tous les Shards déjà en circulation.
 */
public final class ShardDenominations {

    /** Valeurs des coupures, de la plus grande à la plus petite (ordre du rendu de monnaie). */
    public static final int[] VALEURS = {100, 50, 20, 10, 5, 1};

    private ShardDenominations() {}

    /** L'item correspondant à une coupure. */
    public static Item item(int valeur) {
        return switch (valeur) {
            case 100 -> NouvelleTerreBridge.SHARD_100.get();
            case 50  -> NouvelleTerreBridge.SHARD_50.get();
            case 20  -> NouvelleTerreBridge.SHARD_20.get();
            case 10  -> NouvelleTerreBridge.SHARD_10.get();
            case 5   -> NouvelleTerreBridge.SHARD_5.get();
            default  -> NouvelleTerreBridge.SHARD.get();
        };
    }

    /** Valeur d'un item de monnaie, 0 s'il n'en est pas un. */
    public static int valeur(Item item) {
        if (item == NouvelleTerreBridge.SHARD_100.get()) return 100;
        if (item == NouvelleTerreBridge.SHARD_50.get())  return 50;
        if (item == NouvelleTerreBridge.SHARD_20.get())  return 20;
        if (item == NouvelleTerreBridge.SHARD_10.get())  return 10;
        if (item == NouvelleTerreBridge.SHARD_5.get())   return 5;
        if (item == NouvelleTerreBridge.SHARD.get())     return 1;
        return 0;
    }

    public static boolean estMonnaie(ItemStack stack) {
        return !stack.isEmpty() && valeur(stack.getItem()) > 0;
    }

    /** Total en ◆ de tout l'argent physique porté par le joueur. */
    public static int totalEnPoche(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            total += valeur(s.getItem()) * s.getCount();
        }
        return total;
    }

    /**
     * Décompose un montant en piles de coupures, des plus grosses aux plus petites.
     * Les coupures sont limitées à 64 par pile, comme n'importe quel item.
     */
    public static List<ItemStack> decomposer(int montant) {
        List<ItemStack> piles = new ArrayList<>();
        int restant = Math.max(0, montant);
        for (int valeur : VALEURS) {
            int nombre = restant / valeur;
            restant -= nombre * valeur;
            while (nombre > 0) {
                int taille = Math.min(64, nombre);
                piles.add(new ItemStack(item(valeur), taille));
                nombre -= taille;
            }
        }
        return piles;
    }

    /** Donne le montant au joueur en coupures ; ce qui ne rentre pas est lâché au sol. */
    public static void donner(ServerPlayer player, int montant) {
        for (ItemStack pile : decomposer(montant)) {
            if (!player.getInventory().add(pile)) player.drop(pile, false);
        }
    }

    /**
     * Retire jusqu'à {@code montant} ◆ de monnaie physique de l'inventaire.
     *
     * Les petites coupures partent en premier : sans ça, retirer 7 ◆ à un joueur qui
     * a un billet de 100 et sept pièces de 1 aurait cassé le billet pour rien.
     * Une coupure ne peut pas être coupée en deux, donc le total prélevé peut
     * dépasser le montant demandé — l'excédent est retourné par
     * {@link #retirer(ServerPlayer, int)} sous forme d'appoint.
     *
     * @return le total réellement prélevé (≥ montant si les fonds suffisaient)
     */
    public static int retirer(ServerPlayer player, int montant) {
        if (montant <= 0) return 0;
        int prelevé = 0;

        // Plus petites coupures d'abord : on ne casse un gros billet qu'en dernier recours
        for (int i = VALEURS.length - 1; i >= 0 && prelevé < montant; i--) {
            int valeur = VALEURS[i];
            for (int slot = 0; slot < player.getInventory().getContainerSize() && prelevé < montant; slot++) {
                ItemStack s = player.getInventory().getItem(slot);
                if (s.isEmpty() || s.getItem() != item(valeur)) continue;

                int besoin = (montant - prelevé + valeur - 1) / valeur;   // arrondi au-dessus
                int pris   = Math.min(besoin, s.getCount());
                s.shrink(pris);
                if (s.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
                prelevé += pris * valeur;
            }
        }
        return prelevé;
    }
}
