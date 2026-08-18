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

    /**
     * Révision de la table {@link #PRIX_REFERENCE}. L'incrémenter réapplique les
     * prix de référence aux entrées déjà présentes dans seuils-shop.json, au
     * prochain démarrage et **sans toucher aux compteurs de production**.
     */
    private static final int VERSION_PRIX = 1;

    public static class Entry {
        public long seuil    = 512;
        public int  prix     = 1;
        public int  quantite = 64;
        /** Révision des prix appliquée à cette entrée ; 0 = fichier antérieur au versionnage. */
        public int  versionPrix = 0;
        /**
         * Retiré du catalogue par un admin. Distinct d'une suppression : le compteur
         * de production et le prix sont conservés, l'item peut être remis en vente
         * sans que les joueurs perdent leur progression.
         */
        public boolean desactive = false;
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
        migrerPrix();
    }

    /**
     * Réapplique les prix de référence aux entrées créées sous une révision antérieure.
     *
     * Sans cela, {@link #getOrCreate} conserverait indéfiniment les prix issus de la
     * rareté vanilla (diamant et netherite y sont COMMON, donc quasi gratuits), et la
     * seule façon de corriger serait de supprimer le fichier — ce qui viderait le
     * catalogue jusqu'à ce que chaque item soit reproduit.
     *
     * Les compteurs de production ({@code production.json}) ne sont pas touchés.
     * Une entrée déjà à jour est laissée telle quelle : les surcharges manuelles
     * d'un admin survivent tant que {@link #VERSION_PRIX} n'est pas incrémenté.
     */
    private static void migrerPrix() {
        int migres = 0;
        for (Map.Entry<String, Entry> e : thresholds.entrySet()) {
            Entry entry = e.getValue();
            if (entry.versionPrix >= VERSION_PRIX) continue;

            Integer reference = PRIX_REFERENCE.get(e.getKey());
            if (reference != null) {
                Entry neuf = fromPrix(reference);
                NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Prix mis à jour — {} : {}◆ → {}◆ (seuil {} → {})",
                    e.getKey(), entry.prix, neuf.prix, entry.seuil, neuf.seuil);
                entry.prix     = neuf.prix;
                entry.seuil    = neuf.seuil;
                entry.quantite = neuf.quantite;
                migres++;
            }
            entry.versionPrix = VERSION_PRIX;
        }
        if (migres > 0) {
            save();
            NouvelleTerreBridge.LOGGER.info("[ShopThresholds] {} prix réalignés sur la table de référence.", migres);
        }
    }

    /**
     * Retourne l'entrée pour cet item, en la créant automatiquement si elle n'existe pas encore.
     * Retourne null pour les items invalides (air, identifiant inconnu).
     */
    /**
     * Prix de référence (◆ par unité) des items à valeur significative.
     *
     * Indispensable : la rareté vanilla ne reflète pas la valeur de jeu — le
     * diamant, le lingot de netherite et le bloc de terre sont tous
     * {@code Rarity.COMMON}. S'appuyer sur elle seule donnait un diamant à
     * quelques shards. Cette table fait autorité ; la rareté ne sert plus que
     * de repli pour tout le reste.
     */
    private static final Map<String, Integer> PRIX_REFERENCE = new HashMap<>();
    static {
        // ── Minerais et lingots ──────────────────────────────────────────────
        PRIX_REFERENCE.put("minecraft:netherite_ingot",    900);
        PRIX_REFERENCE.put("minecraft:netherite_scrap",    220);
        PRIX_REFERENCE.put("minecraft:ancient_debris",     250);
        PRIX_REFERENCE.put("minecraft:diamond",            120);
        PRIX_REFERENCE.put("minecraft:emerald",             45);
        PRIX_REFERENCE.put("minecraft:gold_ingot",          25);
        PRIX_REFERENCE.put("minecraft:raw_gold",            20);
        PRIX_REFERENCE.put("minecraft:iron_ingot",          12);
        PRIX_REFERENCE.put("minecraft:raw_iron",            10);
        PRIX_REFERENCE.put("minecraft:copper_ingot",         5);
        PRIX_REFERENCE.put("minecraft:raw_copper",           4);
        PRIX_REFERENCE.put("minecraft:amethyst_shard",       8);
        PRIX_REFERENCE.put("minecraft:lapis_lazuli",         6);
        PRIX_REFERENCE.put("minecraft:quartz",               5);
        PRIX_REFERENCE.put("minecraft:redstone",             4);
        PRIX_REFERENCE.put("minecraft:coal",                 3);
        PRIX_REFERENCE.put("minecraft:charcoal",             2);

        // ── Blocs compressés (9 unités) ──────────────────────────────────────
        PRIX_REFERENCE.put("minecraft:netherite_block",   8100);
        PRIX_REFERENCE.put("minecraft:diamond_block",     1080);
        PRIX_REFERENCE.put("minecraft:emerald_block",      405);
        PRIX_REFERENCE.put("minecraft:gold_block",         225);
        PRIX_REFERENCE.put("minecraft:iron_block",         108);
        PRIX_REFERENCE.put("minecraft:coal_block",          27);

        // ── Objets rares / prestige ──────────────────────────────────────────
        PRIX_REFERENCE.put("minecraft:dragon_egg",        2000);
        PRIX_REFERENCE.put("minecraft:nether_star",       1500);
        PRIX_REFERENCE.put("minecraft:elytra",            1200);
        PRIX_REFERENCE.put("minecraft:totem_of_undying",   800);
        PRIX_REFERENCE.put("minecraft:enchanted_golden_apple", 600);
        PRIX_REFERENCE.put("minecraft:trident",            500);
        PRIX_REFERENCE.put("minecraft:heart_of_the_sea",   400);
        PRIX_REFERENCE.put("minecraft:wither_skeleton_skull", 200);
        PRIX_REFERENCE.put("minecraft:shulker_shell",      150);
        PRIX_REFERENCE.put("minecraft:sponge",              80);
        PRIX_REFERENCE.put("minecraft:golden_apple",        60);
        PRIX_REFERENCE.put("minecraft:nautilus_shell",      60);
        PRIX_REFERENCE.put("minecraft:crying_obsidian",     40);
        PRIX_REFERENCE.put("minecraft:name_tag",            40);
        PRIX_REFERENCE.put("minecraft:saddle",              50);

        // ── Butin de mobs / consommables ─────────────────────────────────────
        PRIX_REFERENCE.put("minecraft:ghast_tear",          40);
        PRIX_REFERENCE.put("minecraft:phantom_membrane",    25);
        PRIX_REFERENCE.put("minecraft:blaze_rod",           20);
        PRIX_REFERENCE.put("minecraft:ender_pearl",         15);
        PRIX_REFERENCE.put("minecraft:experience_bottle",   12);
        PRIX_REFERENCE.put("minecraft:obsidian",            10);
        PRIX_REFERENCE.put("minecraft:lead",                 8);
        PRIX_REFERENCE.put("minecraft:cooked_beef",          8);
        PRIX_REFERENCE.put("minecraft:honeycomb",            6);
        PRIX_REFERENCE.put("minecraft:gunpowder",            6);
        PRIX_REFERENCE.put("minecraft:leather",              6);
        PRIX_REFERENCE.put("minecraft:glowstone_dust",       5);
        PRIX_REFERENCE.put("minecraft:slime_ball",           5);
        PRIX_REFERENCE.put("minecraft:book",                 5);
        PRIX_REFERENCE.put("minecraft:bread",                4);
        PRIX_REFERENCE.put("minecraft:nether_wart",          4);
        PRIX_REFERENCE.put("minecraft:oak_log",              3);
        PRIX_REFERENCE.put("minecraft:feather",              2);
        PRIX_REFERENCE.put("minecraft:string",               2);
        PRIX_REFERENCE.put("minecraft:wheat",                2);
        PRIX_REFERENCE.put("minecraft:sugar_cane",           2);
        PRIX_REFERENCE.put("minecraft:glass",                2);

        // ── Mod médical (cottonmod) ──────────────────────────────────────────
        PRIX_REFERENCE.put("cottonmod:medkit",              45);
        PRIX_REFERENCE.put("cottonmod:bandage",             15);
        PRIX_REFERENCE.put("cottonmod:cotton",               6);
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

        Entry e;
        Integer reference = PRIX_REFERENCE.get(itemId);
        if (reference != null) {
            e = fromPrix(reference);
            NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Nouveau seuil — {} (prix de référence) : seuil={} prix={}◆",
                itemId, e.seuil, e.prix);
        } else {
            Rarity rarity = new ItemStack(item).getRarity();
            e = fromRarity(rarity);
            NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Nouveau seuil auto — {} (rareté {}) : seuil={} prix={}◆",
                itemId, rarity, e.seuil, e.prix);
        }
        e.versionPrix = VERSION_PRIX;   // créée avec la table courante : pas à migrer
        thresholds.put(itemId, e);
        save();
        return e;
    }

    /** Seuil et taille de lot déduits du prix : plus un item vaut cher, plus il se vend par petits lots. */
    private static Entry fromPrix(int prix) {
        Entry e = new Entry();
        e.prix = prix;
        if (prix >= 500)      { e.seuil = 1;   e.quantite = 1;  }
        else if (prix >= 100) { e.seuil = 4;   e.quantite = 2;  }
        else if (prix >= 30)  { e.seuil = 16;  e.quantite = 8;  }
        else if (prix >= 10)  { e.seuil = 64;  e.quantite = 16; }
        else                  { e.seuil = 256; e.quantite = 64; }
        return e;
    }

    private static Entry fromRarity(Rarity rarity) {
        Entry e = new Entry();
        switch (rarity) {
            case UNCOMMON -> { e.seuil = 32;  e.prix = 12; e.quantite = 16; }
            case RARE     -> { e.seuil = 8;   e.prix = 35; e.quantite = 8;  }
            case EPIC     -> { e.seuil = 2;   e.prix = 100; e.quantite = 2;  }
            default       -> { e.seuil = 512; e.prix = 2;  e.quantite = 64; } // COMMON
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

    /** Change le prix d'un item du catalogue. @return false si l'entrée n'existe pas. */
    public static synchronized boolean setPrix(String itemId, int prix) {
        Entry e = thresholds.get(itemId);
        if (e == null) return false;
        e.prix = Math.max(1, prix);
        // Marquée à la révision courante : sinon migrerPrix() écraserait la
        // correction de l'admin au prochain démarrage.
        e.versionPrix = VERSION_PRIX;
        save();
        return true;
    }

    /** Active/désactive la vente d'un item. @return le nouvel état, ou null si absent. */
    public static synchronized Boolean toggleDesactive(String itemId) {
        Entry e = thresholds.get(itemId);
        if (e == null) return null;
        e.desactive = !e.desactive;
        save();
        return e.desactive;
    }

    /** Supprime définitivement une entrée du catalogue (elle se recréera au prochain contact). */
    public static synchronized boolean supprimer(String itemId) {
        if (thresholds.remove(itemId) == null) return false;
        save();
        return true;
    }

    /** Vide tous les seuils (ils se recréent dynamiquement au premier contact). */
    public static synchronized void resetAll() {
        thresholds.clear();
        save();
        NouvelleTerreBridge.LOGGER.info("[ShopThresholds] Seuils remis à zéro.");
    }
}
