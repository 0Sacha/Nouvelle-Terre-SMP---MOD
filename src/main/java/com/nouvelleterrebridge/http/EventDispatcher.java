package com.nouvelleterrebridge.http;

import com.google.gson.Gson;
import com.nouvelleterrebridge.ModConfig;
import com.nouvelleterrebridge.NouvelleTerreBridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    }

    public static boolean isBotEnLigne() {
        return botEnLigne;
    }
}
