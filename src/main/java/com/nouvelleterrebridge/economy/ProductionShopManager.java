package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.market.MarketManager;

import java.util.Map;

/**
 * Crée/supprime automatiquement les annonces HDV du "Serveur" quand
 * les seuils de production naturelle sont atteints.
 */
public class ProductionShopManager {

    /** Nom du compte système du serveur — le $ est interdit dans les pseudos Minecraft. */
    public static final String AUTO_SELLER = "$Serveur";

    /**
     * Suit le déblocage des items par la production naturelle.
     *
     * Depuis que le Shop Serveur est un écran autonome, il lit directement
     * {@link ShopThresholds} et {@link ProductionTracker} : plus besoin de
     * matérialiser des annonces {@code $Serveur} dans le HDV des joueurs.
     */
    public static void checkItem(String itemId, long count) {
        ShopThresholds.Entry entry = ShopThresholds.getOrCreate(itemId);
        if (entry == null) return;
        if (count == entry.seuil) {
            NouvelleTerreBridge.LOGGER.info("[ProductionShopManager] Seuil atteint {} ({}) — disponible au Shop Serveur.",
                itemId, count);
        }
    }

    public static void checkAll() {
        for (Map.Entry<String, ShopThresholds.Entry> e : ShopThresholds.all().entrySet()) {
            checkItem(e.getKey(), ProductionTracker.get(e.getKey()));
        }
        // Purge les anciennes annonces auto : le shop ne passe plus par le HDV
        removeAllAutoListings();
    }

    public static void removeAllAutoListings() {
        MarketManager.getInstance().removeAutoListings(AUTO_SELLER);
    }
}
