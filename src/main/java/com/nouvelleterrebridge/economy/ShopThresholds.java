package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Seuils de déblocage du shop auto, persistés dans <gameDir>/seuils-shop.json.
 * Les entrées sont créées dynamiquement au premier contact avec un item (bloc cassé,
 * drop mob, craft). Le seuil est calculé automatiquement d'après la rareté vanilla.
 * Les admins peuvent éditer le JSON pour surcharger n'importe quelle entrée.
 */
public class ShopThresholds {

    public static class Entry {
        public long seuil    = 512;
        public int  prix     = 1;
        public int  quantite = 64;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("seuils-shop.json");
    private static Map<String, Entry> thresholds = new HashMap<>();

    public static synchronized void load() {
        File f = FILE.toFile();
        if (!f.exists()) {
            thresholds = new HashMap<>();
            save();
            NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Fichier seuils-shop.json créé (auto-rempli au jeu).");
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

    /**
     * Retourne l'entrée pour cet item, en la créant automatiquement si elle n'existe pas encore.
     * Retourne null pour les items invalides (air, identifiant inconnu).
     */
    public static synchronized Entry getOrCreate(String itemId) {
        Entry existing = thresholds.get(itemId);
        if (existing != null) return existing;

        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) return null;

        Rarity rarity = new ItemStack(item).getRarity();
        Entry e = fromRarity(rarity);
        thresholds.put(itemId, e);
        save();
        NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Nouveau seuil auto — {} (rareté {}) : seuil={} prix={}◆",
            itemId, rarity, e.seuil, e.prix);
        return e;
    }

    private static Entry fromRarity(Rarity rarity) {
        Entry e = new Entry();
        switch (rarity) {
            case UNCOMMON -> { e.seuil = 32;  e.prix = 5;  e.quantite = 16; }
            case RARE     -> { e.seuil = 8;   e.prix = 15; e.quantite = 8;  }
            case EPIC     -> { e.seuil = 2;   e.prix = 40; e.quantite = 2;  }
            default       -> { e.seuil = 512; e.prix = 1;  e.quantite = 64; } // COMMON
        }
        return e;
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

    /** Lit sans créer. */
    public static synchronized Entry get(String itemId) {
        return thresholds.get(itemId);
    }

    /** Vide tous les seuils (ils se recréent dynamiquement au premier contact). */
    public static synchronized void resetAll() {
        thresholds.clear();
        save();
        NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Seuils remis à zéro.");
    }
}
