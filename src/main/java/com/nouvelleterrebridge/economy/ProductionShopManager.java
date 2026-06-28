package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.market.MarketManager;

import java.util.Map;

/**
 * Crée/supprime automatiquement les annonces HDV du "Serveur" quand
 * les seuils de production naturelle sont atteints.
 */
public class ProductionShopManager {

    /** Nom vendeur réservé pour les annonces auto — $ interdit dans les pseudos Minecraft. */
    public static final String AUTO_SELLER = "$Serveur";

    public static void checkItem(String itemId, long count) {
        ShopThresholds.Entry entry = ShopThresholds.getOrCreate(itemId);
        if (entry == null) return;
        if (count >= entry.seuil && !MarketManager.getInstance().hasAutoListing(itemId, AUTO_SELLER)) {
            MarketManager.getInstance().addListing(AUTO_SELLER, itemId, entry.quantite, entry.prix);
            NouvelleTerreBridge.LOGGER.info("[ProductionShopManager] Seuil atteint {} ({}) — annonce créée.", itemId, count);
        }
    }

    public static void checkAll() {
        for (Map.Entry<String, ShopThresholds.Entry> e : ShopThresholds.all().entrySet()) {
            checkItem(e.getKey(), ProductionTracker.get(e.getKey()));
        }
    }

    public static void removeAllAutoListings() {
        MarketManager.getInstance().removeAutoListings(AUTO_SELLER);
    }
}
