package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.NouvelleTerreBridge;

/**
 * Intégration optionnelle avec Cadmus.
 * Cette classe est chargée de manière réflexive uniquement si Cadmus est présent,
 * pour éviter les erreurs de classe introuvable au démarrage.
 */
public class CadmusIntegration {

    public static void register() {
        // L'implémentation exacte dépend de l'API publique de la version de Cadmus installée.
        // Exemple d'intégration (à adapter selon la javadoc de Cadmus) :
        //
        // CadmusEvents.CLAIM_CHUNK.register((player, chunkPos, level) -> {
        //     String nom = "Territoire de " + player.getName().getString();
        //     int x = chunkPos.getStartX() + 8;
        //     int z = chunkPos.getStartZ() + 8;
        //     TerritoryEvents.surTerritoireRevendique(
        //         player.getName().getString(), nom, x, z
        //     );
        // });

        NouvelleTerreBridge.LOGGER.info("[CadmusIntegration] Prêt. Implémentez le callback selon votre version de Cadmus.");
    }
}
