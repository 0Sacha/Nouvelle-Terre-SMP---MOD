package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Économie locale — balances stockées dans shards.json côté serveur.
 * Toutes les opérations sont instantanées (pas d'HTTP pour le gameplay).
 * Chaque modification envoie un événement async au bot pour garder Discord à jour.
 */
public class LocalEconomy {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LocalEconomy instance;

    private final Path fichier;
    private final Map<String, Integer> soldes = new HashMap<>();

    private LocalEconomy() {
        fichier = FabricLoader.getInstance().getGameDir().resolve("shards.json");
        load();
    }

    public static synchronized LocalEconomy getInstance() {
        if (instance == null) instance = new LocalEconomy();
        return instance;
    }

    public synchronized int getBalance(String pseudo) {
        return soldes.getOrDefault(pseudo.toLowerCase(), 0);
    }

    /**
     * Transfère des shards de `de` vers `vers`.
     * Retourne false si solde insuffisant.
     */
    public synchronized boolean transfer(String de, String vers, int montant) {
        String deKey = de.toLowerCase();
        String versKey = vers.toLowerCase();
        int soldeDe = soldes.getOrDefault(deKey, 0);
        if (soldeDe < montant) return false;
        soldes.put(deKey, soldeDe - montant);
        soldes.merge(versKey, montant, Integer::sum);
        save();
        TransactionLog.log(de,   TransactionLog.TYPE_TRANSFER_OUT, "à " + vers,  montant);
        TransactionLog.log(vers, TransactionLog.TYPE_TRANSFER_IN,  "de " + de,   montant);
        Map<String, Object> data = new HashMap<>();
        data.put("de", de);
        data.put("vers", vers);
        data.put("montant", montant);
        EventDispatcher.envoyer("ECONOMY_TRANSFER", data);
        return true;
    }

    /** Ajoute des shards à un joueur (récompense, salaire, admin give). */
    public synchronized void addShards(String pseudo, int montant) {
        soldes.merge(pseudo.toLowerCase(), montant, Integer::sum);
        save();
        TransactionLog.log(pseudo, TransactionLog.TYPE_REWARD, "Récompense", montant);
        Map<String, Object> data = new HashMap<>();
        data.put("player", pseudo);
        data.put("amount", montant);
        EventDispatcher.envoyer("ECONOMY_REWARD", data);
    }

    /** Retourne true si le joueur a déjà eu un solde enregistré. */
    public synchronized boolean estConnu(String pseudo) {
        return soldes.containsKey(pseudo.toLowerCase());
    }

    /** Retourne les clés (lowercase) de tous les joueurs connus. */
    public synchronized Set<String> getSoldesKeys() {
        return new java.util.HashSet<>(soldes.keySet());
    }

    /** Retire des shards à un joueur (ne passe pas en négatif). */
    public synchronized void removeShards(String pseudo, int montant) {
        String key = pseudo.toLowerCase();
        soldes.put(key, Math.max(0, soldes.getOrDefault(key, 0) - montant));
        save();
        Map<String, Object> data = new HashMap<>();
        data.put("player", pseudo);
        data.put("amount", montant);
        EventDispatcher.envoyer("ECONOMY_DEDUCT", data);
    }

    // ── Persistance ──────────────────────────────────────────────────────────

    private void load() {
        File f = fichier.toFile();
        if (!f.exists()) {
            NouvelleTerreBridge.LOGGER.info("[LocalEconomy] Aucun shards.json, démarrage à zéro.");
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                soldes.putAll(loaded);
                NouvelleTerreBridge.LOGGER.info("[LocalEconomy] {} solde(s) chargé(s).", soldes.size());
            }
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LocalEconomy] Erreur lecture : {}", e.getMessage());
        }
    }

    private void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fichier.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(soldes, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LocalEconomy] Erreur sauvegarde : {}", e.getMessage());
        }
    }
}
