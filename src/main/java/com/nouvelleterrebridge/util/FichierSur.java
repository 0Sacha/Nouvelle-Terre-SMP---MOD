package com.nouvelleterrebridge.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Écriture atomique des fichiers de données.
 *
 * Tous les gestionnaires du mod réécrivent leur JSON en entier à chaque
 * opération. Écrire directement dans le fichier vivant le tronque d'abord :
 * un crash ou un arrêt forcé pendant l'écriture laissait un fichier vide ou
 * coupé en deux, et le chargement suivant repartait d'un état vierge — pour
 * {@code shards.json}, tous les soldes du serveur.
 *
 * Ici le contenu part dans un fichier temporaire voisin, qui remplace l'ancien
 * d'un seul geste : à tout instant le fichier vivant est soit l'ancienne
 * version complète, soit la nouvelle.
 */
public final class FichierSur {

    private FichierSur() {}

    /**
     * Le contenu doit être entièrement sérialisé <b>avant</b> l'appel (une
     * chaîne, pas un flux) : une exception en cours de sérialisation ne doit
     * rien écrire du tout.
     */
    public static void ecrire(Path fichier, String contenu) throws IOException {
        Path parent = fichier.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
        Files.writeString(tmp, contenu, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, fichier, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Système de fichiers sans renommage atomique : on garde au moins
            // l'écriture en deux temps, le fichier vivant n'est jamais tronqué.
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
