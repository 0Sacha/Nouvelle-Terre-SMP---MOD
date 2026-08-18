package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.market.MarketManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

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

    /**
     * Notifie tout le serveur quand la production fait franchir son seuil à un item.
     *
     * Le franchissement se teste sur l'intervalle {@code ]avant, apres]} et non par
     * égalité stricte avec le seuil : une seule récolte peut apporter plusieurs unités
     * (Fortune, craft d'une pile) et sauter la valeur exacte du seuil.
     */
    public static void notifierSiDebloque(String itemId, long avant, long apres) {
        ShopThresholds.Entry entry = ShopThresholds.get(itemId);
        if (entry == null || entry.desactive) return;
        if (avant >= entry.seuil || apres < entry.seuil) return;

        MinecraftServer server = NouvelleTerreBridge.serveur;
        if (server == null) return;

        String nom  = FrenchItemNames.toDisplay(itemId);
        int    prix = ServerShopPriceManager.getPrice(itemId);

        NouvelleTerreBridge.LOGGER.info("[ProductionShopManager] {} débloqué au Shop Serveur ({} produits).",
            itemId, apres);

        // La notification part sur le thread serveur : notifierSiDebloque est appelé
        // depuis la comptabilisation de production, qui n'y est pas garantie.
        server.execute(() -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                // 0xFF2EAD6B = NotificationHud.COLOR_GREEN (côté client, non accessible ici)
                NouvelleTerreBridge.sendToast(p, 0xFF2EAD6B,
                    "§aNouveauté au Shop",
                    "§f" + nom,
                    "§7Débloqué par la production · §6" + prix + " ◆§7/u");
            }
        });
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
