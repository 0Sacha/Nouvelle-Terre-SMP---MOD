package com.nouvelleterrebridge.economy;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mémorise les blocs posés par les joueurs, pour que les recasser ne compte pas
 * comme de la production naturelle.
 *
 * Sans ça, poser puis casser le même bloc de diamant 512 fois débloquait le bloc
 * au Shop Serveur aussi sûrement que d'en avoir réellement fabriqué 512 : le
 * compteur ne voyait qu'une suite de blocs cassés.
 *
 * <p><b>Bornes assumées.</b> Rien n'est persisté : la marque est posée au moment
 * où le joueur pose le bloc, donc elle est toujours plus récente que la casse
 * qu'elle doit annuler. Un redémarrage entre la pose et la casse fait repasser le
 * bloc pour naturel — cas marginal, et non exploitable sans pouvoir redémarrer le
 * serveur à volonté.
 *
 * <p>La table est bornée à {@link #MAX_MARQUES} entrées en éviction LRU : un
 * serveur qui tourne des mois accumulerait sinon toutes les poses jamais faites.
 * Déborder ne fait que rendre au compteur des blocs très anciens, jamais l'inverse.
 */
public final class PlacedBlockTracker {

    /** ~200 000 positions : de quoi couvrir de gros chantiers sans peser en mémoire. */
    private static final int MAX_MARQUES = 200_000;

    private static final Map<String, Boolean> marques =
        new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MAX_MARQUES;
            }
        };

    private PlacedBlockTracker() {}

    /** Clé dimension + position : deux dimensions partagent les mêmes coordonnées. */
    private static String cle(World world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "@" + pos.asLong();
    }

    /** Marque un bloc comme posé par un joueur. */
    public static synchronized void marquer(World world, BlockPos pos) {
        marques.put(cle(world, pos), Boolean.TRUE);
    }

    /**
     * Consomme la marque d'une position.
     * @return true si le bloc avait été posé par un joueur (donc à ne pas compter).
     */
    public static synchronized boolean estPoseParJoueur(World world, BlockPos pos) {
        return marques.remove(cle(world, pos)) != null;
    }

    public static synchronized int taille() {
        return marques.size();
    }

    public static synchronized void reset() {
        marques.clear();
    }
}
