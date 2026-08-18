package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Cumule la production naturelle d'items (blocs cassés, drops mob) par type d'item.
 * Persiste dans <gameDir>/production.json. Ne compte pas les échanges entre joueurs.
 */
public class ProductionTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("production.json");
    private static Map<String, Long> counts = new HashMap<>();

    public static synchronized void load() {
        File f = FILE.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> loaded = GSON.fromJson(r, type);
            if (loaded != null) counts = new HashMap<>(loaded);
            NouvelleTerreBridge.LOGGER.info("[ProductionTracker] {} compteur(s) chargé(s).", counts.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ProductionTracker] Erreur lecture : {}", e.getMessage());
        }
    }

    public static synchronized void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(counts, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ProductionTracker] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    public static synchronized void add(String itemId, long amount) {
        if (amount <= 0) return;
        long avant  = counts.getOrDefault(itemId, 0L);
        long newVal = counts.merge(itemId, amount, Long::sum);
        save();
        ProductionShopManager.onProduction(itemId, avant, newVal);
    }

    /**
     * Retire des unités du compteur, sans jamais passer sous zéro.
     *
     * Sert à annuler une production qui n'en était pas une : décompacter un bloc
     * rend les ingrédients d'origine, la matière n'a fait que changer de forme.
     */
    public static synchronized void remove(String itemId, long amount) {
        if (amount <= 0) return;
        Long actuel = counts.get(itemId);
        if (actuel == null) return;
        long nouveau = Math.max(0L, actuel - amount);
        if (nouveau == 0L) counts.remove(itemId);
        else               counts.put(itemId, nouveau);
        save();
    }

    public static synchronized long get(String itemId) {
        return counts.getOrDefault(itemId, 0L);
    }

    public static synchronized Map<String, Long> all() {
        return new HashMap<>(counts);
    }

    public static synchronized void reset() {
        counts.clear();
        save();
        ProductionShopManager.purgerAnnoncesLegacy();
        NouvelleTerreBridge.LOGGER.info("[ProductionTracker] Compteurs réinitialisés.");
    }
}
