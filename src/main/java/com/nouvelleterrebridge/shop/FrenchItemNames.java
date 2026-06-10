package com.nouvelleterrebridge.shop;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Dictionnaire bidirectionnel FR ↔ ID Minecraft pour les items courants.
 */
public class FrenchItemNames {

    private static final Map<String, String> FR_TO_MC = new HashMap<>();
    private static final Map<String, String> MC_TO_FR = new HashMap<>();

    static {
        // Blocs communs
        add("terre",         "minecraft:dirt");
        add("herbe",         "minecraft:grass_block");
        add("pierre",        "minecraft:stone");
        add("pierre taillee","minecraft:cobblestone");
        add("sable",         "minecraft:sand");
        add("gravier",       "minecraft:gravel");
        // Bois — troncs et planches toutes essences
        add("bois chene",        "minecraft:oak_log");
        add("planche chene",     "minecraft:oak_planks");
        add("bois bouleau",      "minecraft:birch_log");
        add("planche bouleau",   "minecraft:birch_planks");
        add("bois epinette",     "minecraft:spruce_log");
        add("planche epinette",  "minecraft:spruce_planks");
        add("bois jungle",       "minecraft:jungle_log");
        add("planche jungle",    "minecraft:jungle_planks");
        add("bois acacia",       "minecraft:acacia_log");
        add("planche acacia",    "minecraft:acacia_planks");
        add("bois noyer",        "minecraft:dark_oak_log");
        add("planche noyer",     "minecraft:dark_oak_planks");
        add("bois mangrove",     "minecraft:mangrove_log");
        add("planche mangrove",  "minecraft:mangrove_planks");
        add("bois cerisier",     "minecraft:cherry_log");
        add("planche cerisier",  "minecraft:cherry_planks");
        add("verre",         "minecraft:glass");
        add("obsidienne",    "minecraft:obsidian");
        add("argile",        "minecraft:clay");
        add("bedrock",       "minecraft:bedrock");
        add("neige",         "minecraft:snow_block");
        add("glace",         "minecraft:ice");
        add("feuille",       "minecraft:oak_leaves");
        add("champignon",    "minecraft:red_mushroom");

        // Minerais & lingots
        add("charbon",       "minecraft:coal");
        add("fer brut",      "minecraft:raw_iron");
        add("lingot fer",    "minecraft:iron_ingot");
        add("or brut",       "minecraft:raw_gold");
        add("lingot or",     "minecraft:gold_ingot");
        add("diamant",       "minecraft:diamond");
        add("emeraude",      "minecraft:emerald");
        add("lapis",         "minecraft:lapis_lazuli");
        add("redstone",      "minecraft:redstone");
        add("quartz",        "minecraft:quartz");
        add("cuivre",        "minecraft:copper_ingot");
        add("netherite",     "minecraft:netherite_ingot");
        add("amethyste",     "minecraft:amethyst_shard");

        // Nourriture
        add("pain",          "minecraft:bread");
        add("pomme",         "minecraft:apple");
        add("boeuf",         "minecraft:cooked_beef");
        add("poulet",        "minecraft:cooked_chicken");
        add("porc",          "minecraft:cooked_porkchop");
        add("mouton",        "minecraft:cooked_mutton");
        add("lapin",         "minecraft:cooked_rabbit");
        add("saumon",        "minecraft:cooked_salmon");
        add("ble",           "minecraft:wheat");
        add("carotte",       "minecraft:carrot");
        add("pomme de terre","minecraft:potato");
        add("citrouille",    "minecraft:pumpkin");
        add("sucre",         "minecraft:sugar");
        add("oeuf",          "minecraft:egg");
        add("lait",          "minecraft:milk_bucket");
        add("melon",         "minecraft:melon_slice");
        add("betterave",     "minecraft:beetroot");

        // Ressources
        add("baton",         "minecraft:stick");
        add("corde",         "minecraft:string");
        add("plume",         "minecraft:feather");
        add("os",            "minecraft:bone");
        add("poudre os",     "minecraft:bone_meal");
        add("poudre",        "minecraft:gunpowder");
        add("fleche",        "minecraft:arrow");
        add("cuir",          "minecraft:leather");
        // Laine — toutes les couleurs
        add("laine blanche",       "minecraft:white_wool");
        add("laine orange",        "minecraft:orange_wool");
        add("laine magenta",       "minecraft:magenta_wool");
        add("laine bleue claire",  "minecraft:light_blue_wool");
        add("laine jaune",         "minecraft:yellow_wool");
        add("laine verte claire",  "minecraft:lime_wool");
        add("laine rose",          "minecraft:pink_wool");
        add("laine grise",         "minecraft:gray_wool");
        add("laine gris clair",    "minecraft:light_gray_wool");
        add("laine cyan",          "minecraft:cyan_wool");
        add("laine violette",      "minecraft:purple_wool");
        add("laine bleue",         "minecraft:blue_wool");
        add("laine marron",        "minecraft:brown_wool");
        add("laine verte",         "minecraft:green_wool");
        add("laine rouge",         "minecraft:red_wool");
        add("laine noire",         "minecraft:black_wool");
        add("perle",         "minecraft:ender_pearl");
        add("tige blaze",    "minecraft:blaze_rod");
        add("poudre blaze",  "minecraft:blaze_powder");
        add("papier",        "minecraft:paper");
        add("livre",         "minecraft:book");
        add("torche",        "minecraft:torch");
        add("coffre",        "minecraft:chest");
        add("echelle",       "minecraft:ladder");
        add("seaux",         "minecraft:bucket");
        add("seau eau",      "minecraft:water_bucket");
        add("seau lave",     "minecraft:lava_bucket");
        add("silex",         "minecraft:flint");
        add("glowstone",     "minecraft:glowstone_dust");
        add("fiole",         "minecraft:glass_bottle");
        add("tocsin",        "minecraft:bell");
        add("lanterne",      "minecraft:lantern");

        // Construire le dictionnaire inverse
        FR_TO_MC.forEach((fr, mc) -> MC_TO_FR.putIfAbsent(mc, fr));
    }

    private static void add(String fr, String mc) {
        FR_TO_MC.put(normaliser(fr), mc);
    }

    private static String normaliser(String s) {
        return s.toLowerCase()
                .replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
                .replace("à", "a").replace("â", "a")
                .replace("î", "i").replace("ï", "i")
                .replace("ô", "o").replace("ö", "o")
                .replace("û", "u").replace("ü", "u")
                .replace("ç", "c")
                .trim();
    }

    /**
     * Convertit un nom français (ex: "diamant") ou un ID MC (ex: "minecraft:diamond")
     * en ID Minecraft complet.
     * Retourne null si introuvable.
     */
    public static String toMinecraftId(String input) {
        if (input == null || input.isBlank()) return null;

        // Déjà un ID Minecraft valide avec namespace
        if (input.contains(":")) {
            return Registries.ITEM.containsId(Identifier.tryParse(input)) ? input : null;
        }

        String norm = normaliser(input);

        // Recherche exacte dans le dico français
        String exact = FR_TO_MC.get(norm);
        if (exact != null) return exact;

        // Essaie comme ID minecraft:xxx directement
        String withPrefix = "minecraft:" + norm.replace(" ", "_");
        if (Registries.ITEM.containsId(Identifier.tryParse(withPrefix))) return withPrefix;

        // Recherche par préfixe
        for (Map.Entry<String, String> entry : FR_TO_MC.entrySet()) {
            if (entry.getKey().startsWith(norm)) return entry.getValue();
        }

        return null;
    }

    /**
     * Convertit un ID Minecraft en nom court (français si dispo, sinon formaté).
     */
    public static String toDisplay(String mcId) {
        if (mcId == null) return "?";
        String fr = MC_TO_FR.get(mcId);
        if (fr != null) return Character.toUpperCase(fr.charAt(0)) + fr.substring(1);
        // Strip n'importe quel namespace (minecraft:, mcwbridges:, etc.) puis formate
        String raw = mcId.replaceAll("^[^:]+:", "").replace("_", " ");
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
