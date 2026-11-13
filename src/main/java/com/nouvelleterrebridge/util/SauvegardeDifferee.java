package com.nouvelleterrebridge.util;

import com.nouvelleterrebridge.NouvelleTerreBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regroupe les écritures des fichiers modifiés très souvent.
 *
 * Les compteurs de production et la progression des quêtes changent à
 * <b>chaque bloc cassé</b> et chaque mob tué. Chacun réécrivait son fichier en
 * entier, sur le thread du serveur, à chaque fois : un joueur qui mine
 * déclenchait des dizaines d'écritures disque par seconde, et le tick attendait
 * le disque.
 *
 * Ici l'appelant <i>demande</i> une écriture ; elle n'a lieu qu'au prochain
 * {@link #vider()} (toutes les 5 secondes, et à l'arrêt du serveur). Mille
 * modifications entre deux passages ne coûtent qu'une écriture.
 *
 * ⚠ À réserver aux données qu'on peut se permettre de perdre sur quelques
 * secondes en cas de crash (compteurs, progression). <b>L'argent reste écrit
 * immédiatement</b> : {@code shards.json} ne passe pas par ici.
 */
public final class SauvegardeDifferee {

    private static final Map<String, Runnable> EN_ATTENTE = new LinkedHashMap<>();

    private SauvegardeDifferee() {}

    /** Plusieurs demandes sous la même clé ne font qu'une écriture. */
    public static void demander(String cle, Runnable ecriture) {
        synchronized (EN_ATTENTE) {
            EN_ATTENTE.put(cle, ecriture);
        }
    }

    public static void vider() {
        List<Runnable> aFaire;
        synchronized (EN_ATTENTE) {
            if (EN_ATTENTE.isEmpty()) return;
            aFaire = new ArrayList<>(EN_ATTENTE.values());
            EN_ATTENTE.clear();
        }
        // Hors du verrou : chaque écriture prend le verrou de son gestionnaire
        for (Runnable r : aFaire) {
            try {
                r.run();
            } catch (Exception e) {
                NouvelleTerreBridge.LOGGER.error("[SauvegardeDifferee] Écriture échouée : {}", e.getMessage());
            }
        }
    }
}
