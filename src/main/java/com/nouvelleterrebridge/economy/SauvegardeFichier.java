package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copie de sûreté d'un fichier de données avant une opération irréversible
 * (migration des prix, purge de l'état du marché).
 *
 * Ces opérations réécrivent des fichiers qui portent l'état réel du serveur.
 * En cas de mauvais réglage, la seule façon de revenir en arrière est d'avoir
 * gardé la version d'avant — d'où une copie systématique, jamais écrasée
 * puisque l'horodatage fait partie du nom.
 */
public final class SauvegardeFichier {

    private static final DateTimeFormatter HORODATAGE =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SauvegardeFichier() {}

    /** Dossier des copies, à la racine du serveur. */
    public static Path dossier() {
        return FabricLoader.getInstance().getGameDir().resolve("backups-economie");
    }

    /**
     * Sauvegardes disponibles, de la plus récente à la plus ancienne.
     * L'horodatage étant en tête décroissante dans le nom, un tri inverse suffit.
     */
    public static java.util.List<String> lister() {
        try (var flux = Files.list(dossier())) {
            return flux.map(p -> p.getFileName().toString())
                       .filter(n -> n.endsWith(".json"))
                       .sorted(java.util.Comparator.reverseOrder())
                       .toList();
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /**
     * Restaure une sauvegarde par-dessus le fichier vivant.
     *
     * Le fichier courant est lui-même sauvegardé au préalable : se tromper de
     * restauration ne doit pas être un aller simple.
     *
     * @return le nom du fichier restauré, ou null en cas d'échec
     */
    public static String restaurer(String nomSauvegarde) {
        try {
            Path source = dossier().resolve(nomSauvegarde);
            // Un nom fabriqué ne doit pas permettre d'écrire ailleurs qu'ici
            if (!source.normalize().startsWith(dossier().normalize()) || !Files.exists(source))
                return null;

            // Le nom d'une copie est « <cible>.<motif>-<date>.json » : la cible est
            // ce qui précède le premier point.
            String cible = nomSauvegarde.substring(0, nomSauvegarde.indexOf('.')) + ".json";
            sauver(cible, "avant-restauration");

            Path destination = FabricLoader.getInstance().getGameDir().resolve(cible);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            NouvelleTerreBridge.LOGGER.info("[Sauvegarde] Restauré : {} → {}", nomSauvegarde, cible);
            return cible;
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[Sauvegarde] Échec de restauration : {}", e.getMessage());
            return null;
        }
    }

    /**
     * Sauvegarde {@code nomFichier} dans le dossier {@code backups-economie/}.
     *
     * @param motif court libellé intégré au nom, pour retrouver la raison de la copie
     * @return true si une copie a été écrite (false si le fichier n'existe pas encore)
     */
    public static boolean sauver(String nomFichier, String motif) {
        Path source = FabricLoader.getInstance().getGameDir().resolve(nomFichier);
        if (!Files.exists(source)) return false;

        try {
            Path dossier = FabricLoader.getInstance().getGameDir().resolve("backups-economie");
            Files.createDirectories(dossier);

            String base = nomFichier.replace(".json", "");
            String nom  = base + "." + motif + "-"
                        + LocalDateTime.now().format(HORODATAGE) + ".json";

            Files.copy(source, dossier.resolve(nom), StandardCopyOption.REPLACE_EXISTING);
            NouvelleTerreBridge.LOGGER.info("[Sauvegarde] {} → backups-economie/{}", nomFichier, nom);
            return true;
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[Sauvegarde] Échec pour {} : {}", nomFichier, e.getMessage());
            return false;
        }
    }
}
