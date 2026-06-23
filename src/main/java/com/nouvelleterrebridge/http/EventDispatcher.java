package com.nouvelleterrebridge.http;

import com.google.gson.Gson;
import com.nouvelleterrebridge.ModConfig;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.market.MarketListing;
import com.nouvelleterrebridge.market.MarketManager;

import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Envoie les événements du mod vers le bot Discord via HTTP.
 * Utilise java.net.http.HttpClient (Java 17 intégré) — aucune dépendance externe.
 */
public class EventDispatcher {

    private static final Gson GSON = new Gson();
    private static HttpClient httpClient;
    private static ModConfig config;
    private static boolean botEnLigne = false;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void init(ModConfig cfg) {
        config = cfg;
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Vérifie périodiquement si le bot est revenu en ligne pour vider la file
        scheduler.scheduleAtFixedRate(
                EventDispatcher::tenterVideFileAttente,
                config.getDelaiVideFileAttente(),
                config.getDelaiVideFileAttente(),
                TimeUnit.SECONDS
        );

        NouvelleTerreBridge.LOGGER.info("[EventDispatcher] Initialisé, cible : {}", config.getBotUrl());
    }

    /**
     * Envoie un événement au bot Discord.
     * Si le bot est offline, l'événement est mis en file d'attente.
     */
    public static void envoyer(String type, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("data", data);
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("secret", config.getSharedSecret());

        String json = GSON.toJson(payload);
        envoyerJson(type, json);
    }

    private static void envoyerJson(String type, String json) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBotUrl() + "/event"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // Requête asynchrone pour ne pas bloquer le thread du serveur
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        if (!botEnLigne) {
                            NouvelleTerreBridge.LOGGER.info("[EventDispatcher] Bot de nouveau en ligne !");
                            botEnLigne = true;
                        }
                    } else {
                        NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] Réponse HTTP {} pour {}", response.statusCode(), type);
                        EventQueue.getInstance().ajouter(type, json);
                    }
                })
                .exceptionally(e -> {
                    NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] Bot hors ligne, mise en file : {} ({})", type, e.getMessage());
                    botEnLigne = false;
                    EventQueue.getInstance().ajouter(type, json);
                    return null;
                });
    }

    /**
     * Tente de vider la file d'attente si le bot est accessible.
     */
    private static void tenterVideFileAttente() {
        EventQueue queue = EventQueue.getInstance();
        if (queue.estVide()) return;

        HttpRequest healthCheck = HttpRequest.newBuilder()
                .uri(URI.create(config.getBotUrl() + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(healthCheck, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return;
            botEnLigne = true;
        } catch (Exception e) {
            botEnLigne = false;
            return;
        }

        NouvelleTerreBridge.LOGGER.info("[EventDispatcher] Vidage de la file d'attente ({} événements)...", queue.taille());
        for (EventQueue.EvenementEnAttente evt : queue.vider()) {
            envoyerJson(evt.type, evt.json);
        }
        // Resync le marché après reconnexion au bot
        envoyerSyncMarche();
    }

    /** Envoie l'état complet du marché au bot pour resynchronisation. */
    public static void envoyerSyncMarche() {
        List<MarketListing> listings = MarketManager.getInstance().getAll();
        List<Map<String, Object>> liste = new ArrayList<>();
        for (MarketListing l : listings) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", l.id);
            item.put("seller", l.seller);
            item.put("item", l.item);
            item.put("quantity", l.quantity);
            item.put("price", l.pricePerUnit);
            liste.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("listings", liste);
        envoyer("MARKET_SYNC", data);
        NouvelleTerreBridge.LOGGER.info("[EventDispatcher] MARKET_SYNC envoyé ({} annonce(s)).", listings.size());
    }

    public static boolean isBotEnLigne() {
        return botEnLigne;
    }

    /** Expose le client HTTP partagé pour les autres modules. */
    public static HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Interroge le bot pour récupérer le nom RP d'un joueur (prenom + nom de personnage).
     * Endpoint attendu : GET {botBase}/joueur/{uuid}  →  { "nom_rp": "Jean Dupont" }
     * Si le joueur n'a pas encore de personnage confirmé, le bot répond 404 ou sans champ nom_rp.
     * Le callback est toujours exécuté sur le thread principal du serveur Minecraft.
     */
    public static void fetchNomRP(String uuid, MinecraftServer server, Consumer<String> onSuccess) {
        String url = getBotBase() + "/joueur/" + uuid + "?secret=" + config.getSharedSecret();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("X-Secret", config.getSharedSecret())
                .GET()
                .build();
        httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] /joueur/{} → HTTP {} : {}",
                            uuid, resp.statusCode(), resp.body().length() > 300 ? resp.body().substring(0, 300) : resp.body());
                        return;
                    }
                    try {
                        var obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (!obj.has("nom_rp") || obj.get("nom_rp").isJsonNull()) return;
                        String nomRP = obj.get("nom_rp").getAsString().trim();
                        if (nomRP.isEmpty()) return;
                        server.execute(() -> onSuccess.accept(nomRP));
                    } catch (Exception e) {
                        NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] Erreur parsing nom_rp pour {} : {}", uuid, e.getMessage());
                    }
                })
                .exceptionally(e -> {
                    NouvelleTerreBridge.LOGGER.debug("[EventDispatcher] Nom RP indisponible pour {} : {}", uuid, e.getMessage());
                    return null;
                });
    }

    /**
     * Récupère la liste de tous les personnages confirmés depuis le bot.
     * Endpoint attendu : GET {botBase}/personnages
     * → [ { "nom_rp": "Jean Dupont", "pseudo_mc": "Steve", "en_ligne": true }, ... ]
     */
    public static void fetchPersonnages(MinecraftServer server, Consumer<List<Map<String, Object>>> onSuccess) {
        String url = getBotBase() + "/personnages?secret=" + config.getSharedSecret();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("X-Secret", config.getSharedSecret())
                .GET()
                .build();
        httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] /personnages → HTTP {} : {}",
                            resp.statusCode(), resp.body().length() > 300 ? resp.body().substring(0, 300) : resp.body());
                        server.execute(() -> onSuccess.accept(new ArrayList<>()));
                        return;
                    }
                    NouvelleTerreBridge.LOGGER.info("[EventDispatcher] /personnages → {} octets reçus", resp.body().length());
                    try {
                        var arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                        List<Map<String, Object>> list = new ArrayList<>();
                        for (var el : arr) {
                            var obj = el.getAsJsonObject();
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("nom_rp",    obj.has("nom_rp")    && !obj.get("nom_rp").isJsonNull()    ? obj.get("nom_rp").getAsString()    : "Inconnu");
                            entry.put("pseudo_mc", obj.has("pseudo_mc") && !obj.get("pseudo_mc").isJsonNull() ? obj.get("pseudo_mc").getAsString() : "");
                            boolean enLigne = false;
                            if (obj.has("en_ligne") && !obj.get("en_ligne").isJsonNull()) {
                                var el2 = obj.get("en_ligne");
                                enLigne = el2.isJsonPrimitive() && (
                                    (el2.getAsJsonPrimitive().isBoolean() && el2.getAsBoolean()) ||
                                    (el2.getAsJsonPrimitive().isNumber()  && el2.getAsInt() != 0) ||
                                    (el2.getAsJsonPrimitive().isString()  && el2.getAsString().equalsIgnoreCase("true")));
                            }
                            entry.put("en_ligne", enLigne);
                            list.add(entry);
                        }
                        server.execute(() -> onSuccess.accept(list));
                    } catch (Exception e) {
                        NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] Erreur parsing /personnages : {}", e.getMessage());
                        server.execute(() -> onSuccess.accept(new ArrayList<>()));
                    }
                })
                .exceptionally(e -> {
                    NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] Registre indisponible : {}", e.getMessage());
                    server.execute(() -> onSuccess.accept(new ArrayList<>()));
                    return null;
                });
    }

    /** Retourne la base de l'URL du bot (sans /event). */
    public static String getBotBase() {
        String url = config.getBotUrl();
        if (url.endsWith("/event")) url = url.substring(0, url.length() - 6);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    public static String getSecret() {
        return config.getSharedSecret();
    }
}
