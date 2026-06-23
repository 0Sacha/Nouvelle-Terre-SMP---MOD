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
import java.util.LinkedHashMap;
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
        boolean changed = false;
        for (Map.Entry<String, Entry> def : getDefaults().entrySet()) {
            if (!thresholds.containsKey(def.getKey())) {
                thresholds.put(def.getKey(), def.getValue());
                changed = true;
            }
        }
        if (changed) {
            save();
            NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Nouvelles entrées par défaut fusionnées dans seuils-shop.json.");
        }
    }

    private static void createExample() {
        thresholds = new LinkedHashMap<>(getDefaults());
        save();
        NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Fichier exemple créé : seuils-shop.json");
    }

    private static Map<String, Entry> getDefaults() {
        Map<String, Entry> d = new LinkedHashMap<>();
        put(d, "minecraft:oak_log",          576,  2, 64);
        put(d, "cottonmod:cotton",            256,  3, 32);
        put(d, "cottonmod:aloe_leaf",          64,  5, 16);
        put(d, "cottonmod:chamomile_flower",   64,  5, 16);
        put(d, "cottonmod:calendula_flower",   64,  5, 16);
        put(d, "cottonmod:thread",            128,  5, 16);
        put(d, "cottonmod:cloth",              64,  8,  8);
        put(d, "cottonmod:bandage",            32, 12,  8);
        put(d, "cottonmod:medkit",             16, 25,  4);
        put(d, "cottonmod:antiseptic",         32, 10,  8);
        put(d, "cottonmod:aloe_gel",           32,  8,  8);
        put(d, "cottonmod:salve",              16, 15,  4);
        put(d, "cottonmod:herbal_medicine",    16, 20,  4);
        return d;
    }

    private static void put(Map<String, Entry> map, String id, long seuil, int prix, int quantite) {
        Entry e = new Entry(); e.seuil = seuil; e.prix = prix; e.quantite = quantite;
        map.put(id, e);
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
