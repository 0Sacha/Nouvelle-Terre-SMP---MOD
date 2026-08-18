package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.market.MarketManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Suit le déblocage des items du Shop Serveur par la production naturelle.
 *
 * Depuis la 1.3.0 le Shop est un écran autonome qui lit directement
 * {@link ShopThresholds} et {@link ProductionTracker} : ce gestionnaire ne
 * matérialise plus d'annonces {@code $Serveur} dans le HDV, il crée les entrées
 * de seuil au premier contact et annonce les franchissements.
 */
public final class ProductionShopManager {

    /** Nom du compte système du serveur — le $ est interdit dans les pseudos Minecraft. */
    public static final String AUTO_SELLER = "$Serveur";

    private ProductionShopManager() {}

    /**
     * Comptabilise une production : crée l'entrée de seuil au premier contact
     * avec l'item, et notifie tout le serveur si ce gain lui fait franchir son
     * seuil de déblocage.
     *
     * Le franchissement se teste sur l'intervalle {@code ]avant, apres]} et non
     * par égalité stricte avec le seuil : une seule récolte peut apporter
     * plusieurs unités (Fortune, craft d'une pile) et sauter la valeur exacte.
     */
    public static void onProduction(String itemId, long avant, long apres) {
        ShopThresholds.Entry entry = ShopThresholds.getOrCreate(itemId);
        if (entry == null || entry.desactive) return;
        if (avant >= entry.seuil || apres < entry.seuil) return;

        MinecraftServer server = NouvelleTerreBridge.serveur;
        if (server == null) return;

        String nom  = FrenchItemNames.toDisplay(itemId);
        int    prix = ServerShopPriceManager.getPrice(itemId);

        NouvelleTerreBridge.LOGGER.info("[ProductionShopManager] {} débloqué au Shop Serveur ({} produits).",
            itemId, apres);

        // La notification part sur le thread serveur : onProduction est appelé
        // depuis la comptabilisation de production, qui n'y est pas garantie.
        server.execute(() -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                NouvelleTerreBridge.sendToast(p, NouvelleTerreBridge.TOAST_VERT,
                    "§aNouveauté au Shop",
                    "§f" + nom,
                    "§7Débloqué par la production · §6" + prix + " ◆§7/u");
            }
        });
    }

    /**
     * Purge les annonces {@code $Serveur} héritées d'avant la 1.3.0, quand le
     * shop passait encore par des annonces HDV. Sans effet sur un marche.json
     * récent — gardé pour les serveurs qui migrent depuis une vieille version.
     */
    public static void purgerAnnoncesLegacy() {
        MarketManager.getInstance().removeAutoListings(AUTO_SELLER);
    }
}
