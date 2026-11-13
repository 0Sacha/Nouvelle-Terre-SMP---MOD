package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.neoforged.fml.loading.FMLPaths;

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

    // ── Sauvegarde quotidienne automatique ────────────────────────────────────

    /**
     * Fichiers couverts par la copie quotidienne : tout ce qui porte un état que
     * les joueurs ne peuvent pas reconstituer (soldes, annonces, contrats,
     * crédits, progression).
     */
    private static final String[] FICHIERS_SUIVIS = {
        "shards.json", "marche.json", "leboncube.json",
        "nouvelle-terre-credits.json", "nouvelle-terre-virements.json",
        "quetes.json", "player-levels.json", "production.json",
        "seuils-shop.json", "server-shop-prices.json"
    };

    private static final String MOTIF_AUTO = "auto";
    /** Deux semaines de recul : assez pour remarquer un problème, sans grossir indéfiniment. */
    private static final int COPIES_AUTO_GARDEES = 14;
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * À appeler <b>avant tout chargement</b>, au tout début de l'initialisation.
     *
     * Un fichier illisible (vide, tronqué) est mis de côté puis remplacé par sa
     * dernière copie quotidienne. Sans ça, le gestionnaire concerné démarrait
     * sur un état vierge et l'écrivait par-dessus à la première opération.
     */
    public static void verifierAuDemarrage() {
        for (String nom : FICHIERS_SUIVIS) {
            Path fichier = FMLPaths.GAMEDIR.get().resolve(nom);
            if (!Files.exists(fichier) || estLisible(fichier)) continue;

            NouvelleTerreBridge.LOGGER.error(
                "[Sauvegarde] ⚠ {} est illisible (fichier vide ou tronqué).", nom);
            sauver(nom, "corrompu");

            String base = nom.replace(".json", "");
            String derniere = lister().stream()
                .filter(n -> n.startsWith(base + "." + MOTIF_AUTO + "-"))
                .findFirst().orElse(null);
            try {
                if (derniere != null) {
                    Files.copy(dossier().resolve(derniere), fichier, StandardCopyOption.REPLACE_EXISTING);
                    NouvelleTerreBridge.LOGGER.error(
                        "[Sauvegarde] {} remplacé par sa dernière copie quotidienne : {}", nom, derniere);
                } else {
                    NouvelleTerreBridge.LOGGER.error(
                        "[Sauvegarde] Aucune copie quotidienne de {} — il repart de zéro. "
                        + "L'original est dans backups-economie/.", nom);
                }
            } catch (Exception e) {
                NouvelleTerreBridge.LOGGER.error("[Sauvegarde] Échec de la remise en place de {} : {}",
                    nom, e.getMessage());
            }
        }
        sauvegardeQuotidienne();
    }

    private static boolean estLisible(Path fichier) {
        try {
            String contenu = Files.readString(fichier, java.nio.charset.StandardCharsets.UTF_8);
            if (contenu.isBlank()) return false;
            com.google.gson.JsonParser.parseString(contenu);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Copie chaque fichier suivi une fois par jour, et ne garde que les
     * {@link #COPIES_AUTO_GARDEES} dernières. Sans effet si la copie du jour
     * existe déjà : peut être appelée aussi souvent qu'on veut.
     */
    public static synchronized void sauvegardeQuotidienne() {
        String aujourdHui = LocalDateTime.now().format(JOUR);
        java.util.List<String> existantes = lister();

        for (String nom : FICHIERS_SUIVIS) {
            String prefixe = nom.replace(".json", "") + "." + MOTIF_AUTO + "-";
            boolean faite = existantes.stream().anyMatch(n -> n.startsWith(prefixe + aujourdHui));
            if (!faite) sauver(nom, MOTIF_AUTO);
        }

        // Purge des copies automatiques trop anciennes. Les copies manuelles
        // (migration, purge, restauration, fichier corrompu) ne sont jamais
        // supprimées : elles sont rares et répondent chacune à un événement.
        java.util.List<String> apres = lister();
        for (String nom : FICHIERS_SUIVIS) {
            String prefixe = nom.replace(".json", "") + "." + MOTIF_AUTO + "-";
            apres.stream().filter(n -> n.startsWith(prefixe)).skip(COPIES_AUTO_GARDEES).forEach(n -> {
                try { Files.deleteIfExists(dossier().resolve(n)); } catch (Exception ignored) {}
            });
        }
    }

    /** Dossier des copies, à la racine du serveur. */
    public static Path dossier() {
        return FMLPaths.GAMEDIR.get().resolve("backups-economie");
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

            Path destination = FMLPaths.GAMEDIR.get().resolve(cible);
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
        Path source = FMLPaths.GAMEDIR.get().resolve(nomFichier);
        if (!Files.exists(source)) return false;

        try {
            Path dossier = FMLPaths.GAMEDIR.get().resolve("backups-economie");
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
