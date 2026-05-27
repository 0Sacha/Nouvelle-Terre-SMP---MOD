package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.http.EventDispatcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Méthodes utilitaires pour déclencher les événements économiques.
 * Appelées depuis les commandes /vente et /achat.
 */
public class EconomyEvents {

    /**
     * Déclenche l'événement d'une nouvelle vente postée sur le marché.
     */
    public static void surVentePostee(String vendeur, String item, int quantite, int prix) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", vendeur);
        data.put("item", item);
        data.put("quantity", quantite);
        data.put("price", prix);
        data.put("currency", "shard");
        EventDispatcher.envoyer("SALE_POSTED", data);
    }

    /**
     * Déclenche l'événement d'une vente conclue.
     */
    public static void surVenteConclue(String vendeur, String acheteur, String item, int quantite, int total) {
        Map<String, Object> data = new HashMap<>();
        data.put("seller", vendeur);
        data.put("buyer", acheteur);
        data.put("item", item);
        data.put("quantity", quantite);
        data.put("total", total);
        data.put("currency", "shard");
        EventDispatcher.envoyer("SALE_COMPLETED", data);
    }
}
