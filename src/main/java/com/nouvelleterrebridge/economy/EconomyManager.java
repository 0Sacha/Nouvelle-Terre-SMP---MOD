package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Gère les interactions économiques avec le bot Discord.
 * Utilise le client HTTP partagé d'EventDispatcher.
 */
public class EconomyManager {

    private static final Gson GSON = new Gson();

    public static void init() {
        // Rien à initialiser, on utilise EventDispatcher.getHttpClient()
    }

    /**
     * Récupère le solde d'un joueur de façon asynchrone.
     * Le callback est exécuté sur le thread serveur.
     */
    public static void getBalance(String pseudo, MinecraftServer server, Consumer<Integer> callback) {
        String url = EventDispatcher.getBotBase() + "/economy/balance/" + pseudo;
        NouvelleTerreBridge.LOGGER.info("[EconomyManager] GET {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("X-Secret", EventDispatcher.getSecret())
                .GET()
                .build();

        EventDispatcher.getHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    NouvelleTerreBridge.LOGGER.info("[EconomyManager] Réponse balance {} : HTTP {}", pseudo, response.statusCode());
                    int solde;
                    try {
                        if (response.statusCode() == 200) {
                            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                            solde = json.get("solde").getAsInt();
                        } else {
                            NouvelleTerreBridge.LOGGER.warn("[EconomyManager] Réponse inattendue : {} — {}", response.statusCode(), response.body());
                            solde = -1;
                        }
                    } catch (Exception e) {
                        NouvelleTerreBridge.LOGGER.error("[EconomyManager] Erreur parsing : {}", e.getMessage());
                        solde = -1;
                    }
                    final int soldeFinal = solde;
                    // Exécute le callback sur le thread serveur
                    server.execute(() -> callback.accept(soldeFinal));
                })
                .exceptionally(e -> {
                    NouvelleTerreBridge.LOGGER.error("[EconomyManager] Erreur HTTP getBalance : {}", e.getMessage());
                    server.execute(() -> callback.accept(-1));
                    return null;
                });
    }

    /**
     * Effectue un virement entre deux joueurs.
     */
    public static void transfer(String de, String vers, int montant, MinecraftServer server, BiConsumer<Boolean, String> callback) {
        String url = EventDispatcher.getBotBase() + "/economy/transfer";
        JsonObject body = new JsonObject();
        body.addProperty("secret", EventDispatcher.getSecret());
        body.addProperty("de", de);
        body.addProperty("vers", vers);
        body.addProperty("montant", montant);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Secret", EventDispatcher.getSecret())
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        EventDispatcher.getHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    try {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        if (response.statusCode() == 200 && json.has("success") && json.get("success").getAsBoolean()) {
                            server.execute(() -> callback.accept(true, "OK"));
                        } else {
                            String raison = json.has("raison") ? json.get("raison").getAsString() : "Erreur inconnue";
                            server.execute(() -> callback.accept(false, raison));
                        }
                    } catch (Exception e) {
                        NouvelleTerreBridge.LOGGER.error("[EconomyManager] Erreur transfer : {}", e.getMessage());
                        server.execute(() -> callback.accept(false, "Erreur de communication."));
                    }
                })
                .exceptionally(e -> {
                    NouvelleTerreBridge.LOGGER.error("[EconomyManager] Erreur HTTP transfer : {}", e.getMessage());
                    server.execute(() -> callback.accept(false, "Bot inaccessible."));
                    return null;
                });
    }

    /**
     * Envoie une récompense via EventDispatcher (déjà fonctionnel).
     */
    public static void reward(String pseudo, int montant, String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", pseudo);
        data.put("amount", montant);
        data.put("description", description);
        EventDispatcher.envoyer("ECONOMY_REWARD", data);
    }
}
