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

    /**
     * Part du prix de vente que le serveur consent à payer quand c'est lui qui
     * achète. La marge est indispensable : sans elle, revendre immédiatement ce
     * qu'on vient d'acheter serait neutre, et la moindre variation de prix
     * transformerait le shop en machine à shards.
     */
    private static final float RATIO_RACHAT = 0.55f;

    public static class PriceEntry {
        public int  basePrice    = 1;
        public long unitsSold    = 0;   // vendues par le serveur aux joueurs
        public long unitsBought  = 0;   // rachetées par le serveur aux joueurs
        public int  dynamicPrice = 1;
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
        resyncBasePrices();
    }

    /**
     * Réaligne le prix de base de chaque entrée sur {@link ShopThresholds}.
     *
     * {@code basePrice} est une copie figée au moment de la création : sans cette
     * resynchronisation, une révision des prix de référence resterait sans effet
     * sur tout item déjà échangé au shop. Le flux net (vendu/racheté), lui, est
     * l'état réel du marché et n'est jamais réinitialisé.
     */
    private static void resyncBasePrices() {
        int corriges = 0;
        for (Map.Entry<String, PriceEntry> e : prices.entrySet()) {
            ShopThresholds.Entry seuil = ShopThresholds.get(e.getKey());
            if (seuil == null || seuil.prix == e.getValue().basePrice) continue;
            e.getValue().basePrice = seuil.prix;
            e.getValue().dynamicPrice = calculatePrice(e.getValue());
            corriges++;
        }
        if (corriges > 0) {
            save();
            NouvelleTerreBridge.LOGGER.info("[ServerShopPriceManager] {} prix de base resynchronisé(s).", corriges);
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

    /** Le serveur a vendu des unités au joueur : l'item se raréfie, le prix monte. */
    public static synchronized void recordSale(String itemId, int quantity) {
        PriceEntry e = getOrCreate(itemId);
        e.unitsSold += quantity;
        e.dynamicPrice = calculatePrice(e);
        save();
    }

    /** Le serveur a racheté des unités au joueur : l'item devient abondant, le prix baisse. */
    public static synchronized void recordPurchase(String itemId, int quantity) {
        PriceEntry e = getOrCreate(itemId);
        e.unitsBought += quantity;
        e.dynamicPrice = calculatePrice(e);
        save();
    }

    /**
     * Prix d'achat (ce que paie le joueur), calculé sur le flux net.
     * Plus le serveur a vendu, plus c'est cher ; plus il a racheté, moins ça l'est.
     */
    private static int calculatePrice(PriceEntry entry) {
        int  base = entry.basePrice;
        long net  = entry.unitsSold - entry.unitsBought;

        float mult;
        if      (net >= 2048) mult = 2.00f;
        else if (net >= 1024) mult = 1.75f;
        else if (net >=  512) mult = 1.50f;
        else if (net >=  256) mult = 1.25f;
        else if (net >=   64) mult = 1.10f;
        else if (net >   -64) mult = 1.00f;
        else if (net >  -256) mult = 0.90f;
        else if (net >  -512) mult = 0.80f;
        else if (net > -1024) mult = 0.70f;
        else                  mult = 0.60f;

        return Math.max(1, Math.round(base * mult));
    }

    /** Prix auquel le joueur achète l'item au serveur. */
    public static synchronized int getPrice(String itemId) {
        return getOrCreate(itemId).dynamicPrice;
    }

    /** Prix auquel le serveur rachète l'item au joueur (prix d'achat diminué de la marge). */
    public static synchronized int getBuybackPrice(String itemId) {
        return Math.max(1, Math.round(getOrCreate(itemId).dynamicPrice * RATIO_RACHAT));
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
