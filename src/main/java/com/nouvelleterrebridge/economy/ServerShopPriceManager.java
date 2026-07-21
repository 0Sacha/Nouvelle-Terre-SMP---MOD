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
 * Gère les prix dynamiques du shop serveur.
 * Le prix augmente avec le nombre de ventes (supply/demand).
 * Persiste dans <gameDir>/server-shop-prices.json.
 */
public class ServerShopPriceManager {

    public static class PriceEntry {
        public int basePrice = 1;
        public long unitsSold = 0;
        public int dynamicPrice = 1;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("server-shop-prices.json");
    private static Map<String, PriceEntry> prices = new HashMap<>();

    public static synchronized void load() {
        File f = FILE.toFile();
        if (!f.exists()) {
            prices = new HashMap<>();
            save();
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, PriceEntry>>(){}.getType();
            Map<String, PriceEntry> loaded = GSON.fromJson(r, type);
            if (loaded != null) prices = new HashMap<>(loaded);
            NouvelleTerreBridge.LOGGER.info("[ServerShopPriceManager] {} prix chargé(s).", prices.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ServerShopPriceManager] Erreur lecture : {}", e.getMessage());
        }
    }

    public static synchronized void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(prices, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[ServerShopPriceManager] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    /** Récupère ou crée l'entrée de prix pour un item. */
    public static synchronized PriceEntry getOrCreate(String itemId) {
        return prices.computeIfAbsent(itemId, k -> {
            ShopThresholds.Entry threshold = ShopThresholds.get(itemId);
            if (threshold == null) threshold = ShopThresholds.getOrCreate(itemId);
            PriceEntry e = new PriceEntry();
            e.basePrice = threshold != null ? threshold.prix : 1;
            e.dynamicPrice = e.basePrice;
            return e;
        });
    }

    /** Enregistre une vente et met à jour le prix dynamique. */
    public static synchronized void recordSale(String itemId, int quantity) {
        PriceEntry e = getOrCreate(itemId);
        e.unitsSold += quantity;
        e.dynamicPrice = calculatePrice(e);
        save();
    }

    /** Calcule le prix dynamique basé sur le volume de ventes. */
    private static int calculatePrice(PriceEntry entry) {
        int base = entry.basePrice;
        long sold = entry.unitsSold;

        if (sold < 64)    return base;
        if (sold < 256)   return (int)(base * 1.1f);
        if (sold < 512)   return (int)(base * 1.25f);
        if (sold < 1024)  return (int)(base * 1.5f);
        if (sold < 2048)  return (int)(base * 1.75f);
        return (int)(base * 2.0f);
    }

    public static synchronized int getPrice(String itemId) {
        return getOrCreate(itemId).dynamicPrice;
    }

    public static synchronized Map<String, PriceEntry> all() {
        return new HashMap<>(prices);
    }

    public static synchronized void reset() {
        prices.clear();
        save();
        NouvelleTerreBridge.LOGGER.info("[ServerShopPriceManager] Prix réinitialisés.");
    }
}
