package com.nouvelleterrebridge.service;

import java.util.List;
import java.util.Locale;

/**
 * Contrôle des URL d'image acceptées dans les annonces.
 *
 * <p><b>Politique voulue par l'administrateur du serveur</b> : n'importe quel
 * hébergeur est accepté, seules les vidéos et les GIF sont refusés. La liste
 * d'hôtes fermée qui existait auparavant bloquait trop de liens légitimes.
 *
 * <p>⚠ Contrepartie assumée : une annonce est vue par tous les joueurs, et chaque
 * client télécharge l'URL qu'elle contient. Une annonce pointant vers un serveur
 * maison permet donc d'enregistrer l'adresse IP de ceux qui la consultent. C'est
 * un choix d'exploitation, pas un oubli — ne pas « resserrer » sans le demander.
 *
 * <p>Partagé client/serveur : le serveur refuse à la publication, le client
 * revérifie avant de télécharger — ni l'un ni l'autre ne fait autorité seul.
 */
public final class ServiceImages {

    /**
     * Formats refusés : animations et vidéos.
     *
     * Le GIF est écarté sur demande ; les formats vidéo n'ont de toute façon aucune
     * chance d'être décodés par le lecteur d'images du jeu.
     */
    private static final List<String> EXTENSIONS_REFUSEES = List.of(
        ".gif", ".gifv", ".apng",
        ".mp4", ".webm", ".mov", ".avi", ".mkv", ".flv", ".wmv", ".m4v", ".mpg", ".mpeg"
    );

    private ServiceImages() {}

    public static String hotesLisibles() {
        return "n'importe quel hébergeur";
    }

    public static String extensionsLisibles() {
        return "images fixes (pas de GIF ni de vidéo)";
    }

    /**
     * Vérifie une URL d'image.
     *
     * @return null si l'URL convient, sinon le motif du refus, à afficher au joueur.
     */
    public static String verifier(String url) {
        if (url == null || url.isBlank()) return null;   // pas d'image = valide
        String u = url.trim();
        String bas = u.toLowerCase(Locale.ROOT);

        if (!bas.startsWith("http://") && !bas.startsWith("https://"))
            return "§cLe lien doit commencer par §fhttps://§c.";

        // Découpage manuel plutôt que java.net.URI : celui-ci lève une exception au
        // moindre caractère non encodé, et un lien vers « Capture d'écran.png » en
        // contient (apostrophe, accents, espaces). Le refus était incompréhensible.
        String chemin = cheminDe(bas);
        for (String ext : EXTENSIONS_REFUSEES) {
            if (chemin.endsWith(ext))
                return "§cLes GIF et les vidéos ne sont pas acceptés §7(" + ext + ")§c.";
        }
        return null;
    }

    /** Partie chemin d'une URL, sans le domaine ni les paramètres. */
    private static String cheminDe(String urlMinuscule) {
        int debut = urlMinuscule.indexOf("://");
        String reste = debut < 0 ? urlMinuscule : urlMinuscule.substring(debut + 3);
        int slash = reste.indexOf('/');
        if (slash < 0) return "";
        String chemin = reste.substring(slash);
        int fin = chemin.indexOf('?');
        if (fin >= 0) chemin = chemin.substring(0, fin);
        fin = chemin.indexOf('#');
        if (fin >= 0) chemin = chemin.substring(0, fin);
        return chemin;
    }

    /** Vrai si l'URL est utilisable telle quelle. */
    public static boolean hoteAutorise(String url) {
        return url != null && !url.isBlank() && verifier(url) == null;
    }
}
