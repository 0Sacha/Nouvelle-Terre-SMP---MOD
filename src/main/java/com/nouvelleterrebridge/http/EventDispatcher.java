package com.nouvelleterrebridge.http;

import com.google.gson.Gson;
import com.nouvelleterrebridge.ModConfig;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Envoie les événements du mod vers le bot Discord via HTTP.
 * Gère la reconnexion automatique et la file d'attente offline.
 */
public class EventDispatcher {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    private static OkHttpClient httpClient;
    private static ModConfig config;
    private static boolean botEnLigne = false;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void init(ModConfig cfg) {
        config = cfg;
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
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
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(config.getBotUrl() + "/event")
                .post(body)
                .build();

        // Requête asynchrone pour ne pas bloquer le thread du serveur
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] Bot hors ligne, mise en file : {} ({})", type, e.getMessage());
                botEnLigne = false;
                EventQueue.getInstance().ajouter(type, json);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.isSuccessful()) {
                        if (!botEnLigne) {
                            NouvelleTerreBridge.LOGGER.info("[EventDispatcher] Bot de nouveau en ligne !");
                            botEnLigne = true;
                        }
                    } else {
                        NouvelleTerreBridge.LOGGER.warn("[EventDispatcher] Réponse HTTP {} pour {}", response.code(), type);
                        EventQueue.getInstance().ajouter(type, json);
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Tente de vider la file d'attente si le bot est accessible.
     */
    private static void tenterVideFileAttente() {
        EventQueue queue = EventQueue.getInstance();
        if (queue.estVide()) return;

        // Vérifie d'abord que le bot répond
        Request healthCheck = new Request.Builder()
                .url(config.getBotUrl() + "/health")
                .get()
                .build();

        try (Response response = httpClient.newCall(healthCheck).execute()) {
            if (!response.isSuccessful()) return;
            botEnLigne = true;
        } catch (IOException e) {
            botEnLigne = false;
            return;
        }

        // Vide la file dans l'ordre
        NouvelleTerreBridge.LOGGER.info("[EventDispatcher] Vidage de la file d'attente ({} événements)...", queue.taille());
        for (EventQueue.EvenementEnAttente evt : queue.vider()) {
            envoyerJson(evt.type, evt.json);
        }
    }

    public static boolean isBotEnLigne() {
        return botEnLigne;
    }
}
