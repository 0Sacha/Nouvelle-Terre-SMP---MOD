package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.fabricmc.loader.api.FabricLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Intégration avec Cadmus pour les événements de revendication de territoire.
 * Utilise une intégration optionnelle — si Cadmus n'est pas présent, rien ne se passe.
 */
public class TerritoryEvents {

    public static void register() {
        if (!NouvelleTerreBridge.config.isActiverEvenementTerritoire()) return;

        // Vérifie si Cadmus est chargé avant de tenter l'intégration
        if (!FabricLoader.getInstance().isModLoaded("cadmus")) {
            NouvelleTerreBridge.LOGGER.info("[TerritoryEvents] Cadmus non détecté, intégration territoire désactivée.");
            return;
        }

        // Intégration Cadmus via l'API de callback si disponible
        // L'implémentation exacte dépend de la version de l'API Cadmus
        enregistrerCallbackCadmus();
        NouvelleTerreBridge.LOGGER.info("[TerritoryEvents] Intégration Cadmus activée.");
    }

    private static void enregistrerCallbackCadmus() {
        // Cadmus détecté mais intégration non implémentée pour l'instant
        NouvelleTerreBridge.LOGGER.info("[TerritoryEvents] Cadmus détecté — intégration à implémenter.");
    }

    /**
     * Méthode publique pour déclencher l'événement de revendication.
     * Peut être appelée depuis CadmusIntegration ou les commandes.
     */
    public static void surTerritoireRevendique(String joueur, String nom, int x, int z) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", joueur);
        data.put("name", nom);
        data.put("x", x);
        data.put("z", z);
        EventDispatcher.envoyer("LAND_CLAIMED", data);
    }
}
