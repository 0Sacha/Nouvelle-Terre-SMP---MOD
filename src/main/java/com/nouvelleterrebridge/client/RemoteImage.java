package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.service.ServiceImages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Charge et met en cache les images d'annonces du LeBonCube.
 *
 * Le téléchargement se fait sur un fil séparé — un appel réseau dans la boucle de
 * rendu figerait le jeu — puis l'enregistrement de la texture repasse par le fil
 * client, seul autorisé à toucher au gestionnaire de textures.
 *
 * L'URL est revalidée ici avant tout appel réseau : le serveur filtre déjà à la
 * publication, mais le client ne doit pas dépendre de cette seule vérification
 * pour décider quoi télécharger.
 */
@Environment(EnvType.CLIENT)
public final class RemoteImage {

    /** Plafond de téléchargement — une photo de téléphone dépasse souvent 2 Mo. */
    private static final int TAILLE_MAX = 8 * 1024 * 1024;
    /** Côté maximal après réduction : au-delà, la texture pèse pour rien. */
    private static final int LARGEUR_MAX = 512;

    private enum Etat { EN_COURS, PRETE, ECHEC }

    private record Entree(Etat etat, Identifier texture, int largeur, int hauteur) {}

    private static final Map<String, Entree> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "LeBonCube-images");
        t.setDaemon(true);   // ne doit jamais retenir la fermeture du jeu
        return t;
    });

    private RemoteImage() {}

    /** Identifiant de texture prêt à dessiner, ou null (chargement ou échec). */
    public static Identifier texture(String url) {
        Entree e = demander(url);
        return e != null && e.etat() == Etat.PRETE ? e.texture() : null;
    }

    public static int largeur(String url) {
        Entree e = CACHE.get(url);
        return e != null && e.etat() == Etat.PRETE ? e.largeur() : 0;
    }

    public static int hauteur(String url) {
        Entree e = CACHE.get(url);
        return e != null && e.etat() == Etat.PRETE ? e.hauteur() : 0;
    }

    public static boolean enCours(String url) {
        Entree e = CACHE.get(url);
        return e != null && e.etat() == Etat.EN_COURS;
    }

    public static boolean echec(String url) {
        Entree e = CACHE.get(url);
        return e == null || e.etat() == Etat.ECHEC;
    }

    private static Entree demander(String url) {
        if (url == null || url.isBlank()) return null;
        Entree existante = CACHE.get(url);
        if (existante != null) return existante;

        if (!ServiceImages.hoteAutorise(url)) {
            CACHE.put(url, new Entree(Etat.ECHEC, null, 0, 0));
            return CACHE.get(url);
        }

        CACHE.put(url, new Entree(Etat.EN_COURS, null, 0, 0));
        POOL.submit(() -> telecharger(url));
        return CACHE.get(url);
    }

    private static void telecharger(String url) {
        try {
            HttpURLConnection co = (HttpURLConnection) new URL(url).openConnection();
            co.setConnectTimeout(6000);
            co.setReadTimeout(8000);
            co.setInstanceFollowRedirects(true);
            // User-Agent de navigateur : le CDN de Discord — et plusieurs autres
            // hébergeurs — répondent 403 à un agent inconnu. C'est ce qui faisait
            // échouer silencieusement toutes les images, URL valide comprise.
            co.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36");
            co.setRequestProperty("Accept", "image/*,*/*;q=0.8");

            int code = co.getResponseCode();
            if (code != 200) {
                NouvelleTerreBridge.LOGGER.warn("[LeBonCube] Image refusée par l'hôte (HTTP {}) : {}", code, url);
                echouer(url);
                return;
            }
            if (co.getContentLength() > TAILLE_MAX) { echouer(url); return; }

            byte[] donnees;
            try (InputStream in = co.getInputStream()) {
                donnees = in.readNBytes(TAILLE_MAX + 1);
            }
            if (donnees.length > TAILLE_MAX) { echouer(url); return; }

            // Une photo de téléphone fait couramment 2160×2880 : la rejeter revenait
            // à refuser le cas d'usage normal. On la réduit plutôt, ce qui garde la
            // mémoire vidéo raisonnable — 2160×2880 en RGBA pèse ~25 Mo par annonce.
            NativeImage image = reduire(NativeImage.read(new java.io.ByteArrayInputStream(donnees)));

            MinecraftClient.getInstance().execute(() -> {
                try {
                    Identifier id = Identifier.of("nouvelle-terre-bridge", "leboncube/" + Integer.toHexString(url.hashCode()));
                    MinecraftClient.getInstance().getTextureManager()
                        .registerTexture(id, new NativeImageBackedTexture(image));
                    CACHE.put(url, new Entree(Etat.PRETE, id, image.getWidth(), image.getHeight()));
                } catch (Exception e) {
                    image.close();
                    echouer(url);
                }
            });
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.warn("[LeBonCube] Image non chargée ({}) : {}", url, e.getMessage());
            echouer(url);
        }
    }

    /**
     * Ramène l'image dans {@link #LARGEUR_MAX} sur son plus grand côté, en gardant
     * ses proportions. Une image déjà assez petite est renvoyée telle quelle.
     */
    private static NativeImage reduire(NativeImage source) {
        int l = source.getWidth(), h = source.getHeight();
        if (l <= LARGEUR_MAX && h <= LARGEUR_MAX) return source;

        float ratio = Math.min(LARGEUR_MAX / (float) l, LARGEUR_MAX / (float) h);
        int nl = Math.max(1, Math.round(l * ratio));
        int nh = Math.max(1, Math.round(h * ratio));

        NativeImage reduite = new NativeImage(nl, nh, false);
        source.resizeSubRectTo(0, 0, l, h, reduite);
        source.close();
        return reduite;
    }

    private static void echouer(String url) {
        CACHE.put(url, new Entree(Etat.ECHEC, null, 0, 0));
    }
}
