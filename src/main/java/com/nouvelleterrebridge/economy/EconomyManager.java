package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.http.EventDispatcher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Gère les interactions économiques avec le bot Discord.
 * Interroge les endpoints /economy/* du bot via HTTP.
 */
public class EconomyManager {

    private static final Gson GSON = new Gson();
    private static HttpClient httpClient;

    public static void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static String getBotBase() {
        // Retire /event de l'URL complète pour obtenir la base
        String url = NouvelleTerreBridge.config.getBotUrl();
        if (url.endsWith("/event")) url = url.substring(0, url.length() - 6);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static String getSecret() {
        return NouvelleTerreBridge.config.getSharedSecret();
    }

    /**
     * Récupère le solde d'un joueur. Callback reçoit -1 en cas d'erreur.
     */
    public static void getBalance(String pseudo, Consumer<Integer> callback) {
        String url = getBotBase() + "/economy/balance/" + pseudo + "?secret=" + getSecret();
        // Pour un GET, on passe le secret en query param
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                            callback.accept(json.get("solde").getAsInt());
                        } else {
                            callback.accept(-1);
                        }
                    } catch (Exception e) {
                        callback.accept(-1);
                    }
                })
                .exceptionally(e -> {
                    callback.accept(-1);
                    return null;
                });
    }

    /**
     * Effectue un virement entre deux joueurs.
     * Callback reçoit (success, message).
     */
    public static void transfer(String de, String vers, int montant, BiConsumer<Boolean, String> callback) {
        String url = getBotBase() + "/economy/transfer";
        JsonObject body = new JsonObject();
        body.addProperty("secret", getSecret());
        body.addProperty("de", de);
        body.addProperty("vers", vers);
        body.addProperty("montant", montant);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    try {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        if (response.statusCode() == 200 && json.has("success") && json.get("success").getAsBoolean()) {
                            callback.accept(true, "OK");
                        } else {
                            String raison = json.has("raison") ? json.get("raison").getAsString() : "Erreur inconnue";
                            callback.accept(false, raison);
                        }
                    } catch (Exception e) {
                        callback.accept(false, "Erreur de communication avec le bot.");
                    }
                })
                .exceptionally(e -> {
                    callback.accept(false, "Bot inaccessible.");
                    return null;
                });
    }

    /**
     * Envoie une récompense de Shards à un joueur.
     */
    public static void reward(String pseudo, int montant, String description) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("player", pseudo);
        data.put("amount", montant);
        data.put("description", description);
        EventDispatcher.envoyer("ECONOMY_REWARD", data);
    }
}
