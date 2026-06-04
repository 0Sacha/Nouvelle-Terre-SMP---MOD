package com.nouvelleterrebridge.resourcepack;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.*;

/**
 * Génère le resource pack HDV dark-theme et le sert via HTTP intégré.
 * PNG encodé en pur Java (pas d'AWT) → fonctionne sur serveur headless Linux/Windows.
 */
public class ResourcePackManager {

    private static final String PACK_META =
        "{\"pack\":{\"pack_format\":15,\"description\":\"Nouvelle Terre HDV\"}}";

    private static HttpServer httpServer;
    private static String packHash = "";

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * @param directUrl  URL directe déjà configurée (ex: GitHub Releases), ou "" pour le serveur HTTP intégré
     * @param host       hôte public du serveur (utilisé seulement si directUrl est vide)
     * @param port       port HTTP (utilisé seulement si directUrl est vide)
     */
    public static void init(String directUrl, String host, int port) {
        try {
            if (directUrl != null && !directUrl.isBlank()) {
                // Mode URL directe : télécharger le fichier pour obtenir son vrai hash SHA-1
                NouvelleTerreBridge.LOGGER.info("[ResourcePack] Mode URL directe — {}", directUrl);
                packHash = downloadHash(directUrl);
                if (packHash.isEmpty()) {
                    NouvelleTerreBridge.LOGGER.warn("[ResourcePack] Hash introuvable — le pack ne sera pas envoyé aux joueurs");
                } else {
                    NouvelleTerreBridge.LOGGER.info("[ResourcePack] Hash SHA-1 : {}", packHash);
                }
            } else {
                // Mode serveur HTTP intégré : générer et servir le pack localement
                byte[] zip = buildZip();
                packHash = sha1(zip);
                Path packFile = FabricLoader.getInstance().getGameDir().resolve("nouvelle-terre-hdv.zip");
                Files.write(packFile, zip);
                NouvelleTerreBridge.LOGGER.info("[ResourcePack] Pack sauvegardé : {}", packFile.toAbsolutePath());
                NouvelleTerreBridge.LOGGER.info("[ResourcePack] Hash SHA-1 : {}", packHash);
                startHttp(port, zip);
                NouvelleTerreBridge.LOGGER.info(
                    "[ResourcePack] Serveur HTTP intégré démarré — http://{}:{}/pack.zip", host, port);
            }
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ResourcePack] Erreur init: {}", e.getMessage(), e);
        }
    }

    private static String downloadHash(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent", "NouvelleTerreBridge/1.0");
            try (java.io.InputStream in = conn.getInputStream()) {
                return sha1(in.readAllBytes());
            }
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.warn("[ResourcePack] Impossible de télécharger le pack : {}", e.getMessage());
            return "";
        }
    }

    public static void shutdown() { if (httpServer != null) httpServer.stop(0); }
    public static String getHash()  { return packHash; }

    // ── Construction du ZIP ───────────────────────────────────────────────────

    private static byte[] buildZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            addText(zip, "pack.mcmeta", PACK_META);
            addBytes(zip, "assets/minecraft/textures/gui/container/generic_54.png",
                     genChestPng());
        }
        return baos.toByteArray();
    }

    private static void addText(ZipOutputStream z, String name, String text) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(text.getBytes("UTF-8"));
        z.closeEntry();
    }

    private static void addBytes(ZipOutputStream z, String name, byte[] data) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    // ── Texture fond du coffre 6 rangées (256×256, RGBA) ─────────────────────
    //
    //  GenericContainerScreen dessine en deux passes :
    //   1) UV(0,0)   taille 176×125 → titre + 6 rangées coffre
    //   2) UV(0,126) taille 176×96  → inventaire joueur
    //
    //  On colore uniquement les zones rendues, le reste est du fond inutilisé.

    private static byte[] genChestPng() throws Exception {
        int W = 256, H = 256;
        return encodePng(W, H, (x, y) -> {
            if (x >= 176) return 0x1a1b1e;           // hors zone → fond
            // Zone coffre : y 0–124
            if (y <= 124) {
                if (y < 17)  return 0x23272a;         // barre titre
                if (y == 16) return 0x3a3c40;         // ligne bas titre
                return 0x1a1b1e;                      // fond slots coffre
            }
            if (y == 125) return 0x1a1b1e;            // 1px de transition
            // Zone inventaire joueur : y 126–221
            if (y <= 221) {
                if (y < 143) return 0x23272a;         // barre titre inv
                if (y == 142) return 0x3a3c40;        // ligne bas titre inv
                if (y == 198 && x >= 7 && x < 169) return 0x3a3c40; // séparateur hotbar
                return 0x1a1b1e;
            }
            return 0x1a1b1e;
        });
    }

    // ── Encodeur PNG pur Java (pas d'AWT) ─────────────────────────────────────

    @FunctionalInterface
    interface PixelFn { int rgb(int x, int y); }  // retourne 0xRRGGBB

    private static byte[] encodePng(int w, int h, PixelFn fn) throws Exception {
        // Données brutes : pour chaque ligne, 1 octet filtre (0=None) + w*4 octets RGBA
        byte[] raw = new byte[h * (1 + w * 4)];
        int pos = 0;
        for (int y = 0; y < h; y++) {
            raw[pos++] = 0; // filtre None
            for (int x = 0; x < w; x++) {
                int rgb = fn.rgb(x, y);
                raw[pos++] = (byte) ((rgb >> 16) & 0xFF);
                raw[pos++] = (byte) ((rgb >> 8)  & 0xFF);
                raw[pos++] = (byte) (rgb         & 0xFF);
                raw[pos++] = (byte) 255;             // alpha opaque
            }
        }

        // Compression zlib (format attendu par PNG dans IDAT)
        ByteArrayOutputStream cmpOut = new ByteArrayOutputStream();
        try (DeflaterOutputStream def = new DeflaterOutputStream(cmpOut)) {
            def.write(raw);
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        // Signature PNG
        png.write(new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
        // IHDR : largeur, hauteur, bit depth=8, color type=6 (RGBA), compress, filter, interlace
        writeChunk(png, "IHDR", new byte[]{
            (byte)(w>>24),(byte)(w>>16),(byte)(w>>8),(byte)w,
            (byte)(h>>24),(byte)(h>>16),(byte)(h>>8),(byte)h,
            8, 6, 0, 0, 0
        });
        writeChunk(png, "IDAT", cmpOut.toByteArray());
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String name, byte[] data) throws Exception {
        byte[] nb = name.getBytes("US-ASCII");
        CRC32 crc = new CRC32();
        crc.update(nb);
        crc.update(data);
        int len = data.length;
        out.write(new byte[]{(byte)(len>>24),(byte)(len>>16),(byte)(len>>8),(byte)len});
        out.write(nb);
        out.write(data);
        long cv = crc.getValue();
        out.write(new byte[]{(byte)(cv>>24),(byte)(cv>>16),(byte)(cv>>8),(byte)cv});
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private static void startHttp(int port, byte[] data) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/pack.zip", exchange -> {
            NouvelleTerreBridge.LOGGER.info("[ResourcePack] Téléchargement depuis {}",
                exchange.getRemoteAddress());
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(data); }
        });
        httpServer.setExecutor(null);
        httpServer.start();
    }

    private static String sha1(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
