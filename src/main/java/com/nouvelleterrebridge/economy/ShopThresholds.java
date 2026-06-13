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
 * Charge les seuils de déblocage du shop depuis <gameDir>/seuils-shop.json.
 * Format : { "minecraft:oak_log": { "seuil": 576, "prix": 2, "quantite": 64 } }
 */
public class ShopThresholds {

    public static class Entry {
        public long seuil    = 64;
        public int  prix     = 1;
        public int  quantite = 64;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("seuils-shop.json");
    private static Map<String, Entry> thresholds = new HashMap<>();

    public static synchronized void load() {
        File f = FILE.toFile();
        if (!f.exists()) {
            createExample();
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Entry>>(){}.getType();
            Map<String, Entry> loaded = GSON.fromJson(r, type);
            if (loaded != null) thresholds = new HashMap<>(loaded);
            NouvelleTerreBridge.LOGGER.info("[ShopThresholds] {} seuil(s) chargé(s).", thresholds.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ShopThresholds] Erreur lecture : {}", e.getMessage());
        }
    }

    private static void createExample() {
        thresholds = new HashMap<>();
        Entry ex = new Entry();
        ex.seuil    = 576;
        ex.prix     = 2;
        ex.quantite = 64;
        thresholds.put("minecraft:oak_log", ex);
        save();
        NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Fichier exemple créé : seuils-shop.json");
    }

    public static synchronized void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(thresholds, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ShopThresholds] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    public static synchronized Map<String, Entry> all() {
        return new HashMap<>(thresholds);
    }

    public static synchronized Entry get(String itemId) {
        return thresholds.get(itemId);
    }
}
