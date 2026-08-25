package com.nouvelleterrebridge.service;

/**
 * Validation des URL d'image des annonces.
 *
 * <p><b>Aucune restriction</b>, sur décision de l'administrateur du serveur :
 * n'importe quelle URL est acceptée, quel que soit l'hébergeur et le format.
 * Les versions précédentes filtraient les hôtes puis les extensions, et
 * bloquaient trop de liens légitimes.
 *
 * <p>⚠ Contrepartie assumée : une annonce est vue par tous les joueurs, et chaque
 * client télécharge l'URL qu'elle contient. Une annonce pointant vers un serveur
 * maison permet donc de relever l'adresse IP des curieux. C'est un choix
 * d'exploitation — ne pas resserrer sans que ce soit demandé.
 *
 * <p>Les formats que le décodeur du jeu ne sait pas lire (vidéos, WebP) sont
 * acceptés à la publication mais n'afficheront rien : l'écran retombe alors sur
 * sa vignette de repli.
 */
public final class ServiceImages {

    private ServiceImages() {}

    public static String hotesLisibles() {
        return "n'importe quel hébergeur";
    }

    public static String extensionsLisibles() {
        return "lien direct vers une image";
    }

    /**
     * Vérifie une URL d'image.
     *
     * @return null si l'URL convient — seule une adresse qui n'est pas du http(s)
     *         est refusée, faute de pouvoir être téléchargée.
     */
    public static String verifier(String url) {
        if (url == null || url.isBlank()) return null;   // pas d'image = valide
        String bas = url.trim().toLowerCase(java.util.Locale.ROOT);
        if (!bas.startsWith("http://") && !bas.startsWith("https://"))
            return "§cLe lien doit commencer par §fhttps://§c.";
        return null;
    }

    /** Vrai si l'URL est utilisable telle quelle. */
    public static boolean hoteAutorise(String url) {
        return url != null && !url.isBlank() && verifier(url) == null;
    }
}
