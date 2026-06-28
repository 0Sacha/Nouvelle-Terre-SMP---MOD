package com.nouvelleterrebridge.economy;

import java.util.*;

public class QuestGenerator {

    private static final Random RAND = new Random();
    private static int nextId = (int)(System.currentTimeMillis() % 100_000);

    // ── Templates ─────────────────────────────────────────────────────────────

    private record Template(
        String type, String target, String label,
        int baseQty, float qtyPerLevel,
        String difficulty,
        String rewardType, int baseShards, float shardsPerLevel,
        String rewardItem, int rewardItemQty,
        int rewardXp,
        int costShards,
        int minLevel, int maxPlayers
    ) {}

    private static final List<Template> POOL = new ArrayList<>();

    static {
        // ── KILL — FACILE (lvl 0+) ──────────────────────────────────────────
        k("minecraft:zombie",    "Zombies",              10, 3f, "FACILE",   "SHARDS", 45,  8f, null, 0,  30,  0, 0, 1);
        k("minecraft:skeleton",  "Squelettes",            8, 2f, "FACILE",   "SHARDS", 40,  7f, null, 0,  30,  0, 0, 1);
        k("minecraft:spider",    "Araignées",             8, 2f, "FACILE",   "SHARDS", 40,  7f, null, 0,  30,  0, 0, 1);
        k("minecraft:drowned",   "Noyés",                 6, 2f, "FACILE",   "SHARDS", 38,  6f, null, 0,  30,  0, 0, 1);
        k("minecraft:husk",      "Zombies du Désert",     6, 2f, "FACILE",   "SHARDS", 38,  6f, null, 0,  30,  0, 0, 1);
        k("minecraft:stray",     "Squelettes Givrés",     6, 2f, "FACILE",   "SHARDS", 38,  6f, null, 0,  30,  0, 0, 1);
        // ── KILL — MOYEN (lvl 5+) ───────────────────────────────────────────
        k("minecraft:creeper",   "Creepers",              6, 2f, "MOYEN",    "SHARDS", 65, 12f, null, 0,  70,  0, 5, 1);
        k("minecraft:enderman",  "Endermens",             5, 1f, "MOYEN",    "SHARDS", 75, 14f, null, 0,  80,  0, 5, 1);
        k("minecraft:witch",     "Sorcières",             4, 1f, "MOYEN",    "SHARDS", 75, 12f, null, 0,  80,  0, 5, 1);
        k("minecraft:pillager",  "Pillards",              8, 2f, "MOYEN",    "SHARDS", 70, 12f, null, 0,  70,  0, 5, 1);
        k("minecraft:phantom",   "Phantômes",             6, 2f, "MOYEN",    "ITEM",    0,  0f, "minecraft:phantom_membrane", 5, 80, 0, 5, 1);
        // ── KILL — DIFFICILE (lvl 15+) ──────────────────────────────────────
        k("minecraft:blaze",     "Blazes du Nether",      8, 2f, "DIFFICILE","ITEM",    0,  0f, "minecraft:blaze_rod", 6, 130,  0, 15, 1);
        k("minecraft:wither_skeleton","Squelettes du Nether",5,1f,"DIFFICILE","ITEM",   0,  0f, "minecraft:wither_skeleton_skull", 2, 160, 0, 15, 1);
        k("minecraft:ghast",     "Ghasts",                4, 1f, "DIFFICILE","ITEM",    0,  0f, "minecraft:ghast_tear", 5, 130,  0, 15, 1);
        k("minecraft:vindicator","Vindicateurs",           6, 1f, "DIFFICILE","SHARDS",110, 16f, null, 0, 120,  0, 15, 1);
        // ── KILL — LÉGENDAIRE (lvl 30+) ─────────────────────────────────────
        k("minecraft:elder_guardian","Gardien Ancien",     1, 0f, "LÉGENDAIRE","ITEM",  0,  0f, "minecraft:sponge", 8, 300, 20, 30, 1);
        k("minecraft:evoker",    "Évocateurs",             3, 0f, "LÉGENDAIRE","ITEM",  0,  0f, "minecraft:totem_of_undying", 2, 300, 30, 30, 1);

        // ── KILL — GROUPE (2-3 joueurs) ──────────────────────────────────────
        k("minecraft:zombie",    "Horde de Zombies",      40, 8f, "MOYEN",   "SHARDS",110, 18f, null, 0, 100,  0,  5, 2);
        k("minecraft:creeper",   "Vague de Creepers",     20, 4f, "DIFFICILE","SHARDS",160, 25f, null, 0, 150,  0, 10, 3);
        k("minecraft:wither_skeleton","Raid au Nether",   15, 3f, "LÉGENDAIRE","ITEM",  0,  0f, "minecraft:nether_star", 2, 350, 50, 25, 3);

        // ── HARVEST — FACILE (lvl 0+) ───────────────────────────────────────
        h("minecraft:oak_log",   "Bûcheron Amateur",      32,  8f, "FACILE",  "SHARDS", 28,  4f, null, 0,  25, 0, 0, 1);
        h("minecraft:cobblestone","Carrier",               64, 16f, "FACILE",  "SHARDS", 22,  3f, null, 0,  20, 0, 0, 1);
        h("minecraft:coal",      "Mineur de Charbon",     32,  8f, "FACILE",  "SHARDS", 32,  5f, null, 0,  30, 0, 0, 1);
        h("minecraft:gravel",    "Chercheur de Silex",    48, 12f, "FACILE",  "SHARDS", 20,  3f, null, 0,  20, 0, 0, 1);
        h("minecraft:wheat",     "Agriculteur",           64, 16f, "FACILE",  "SHARDS", 24,  4f, null, 0,  25, 0, 0, 1);
        // ── HARVEST — MOYEN (lvl 5+) ─────────────────────────────────────────
        h("minecraft:raw_iron",  "Mineur de Fer",         16,  4f, "MOYEN",   "SHARDS", 60, 10f, null, 0,  70, 0, 5, 1);
        h("minecraft:raw_gold",  "Chercheur d'Or",         8,  2f, "MOYEN",   "SHARDS", 70, 12f, null, 0,  70, 0, 5, 1);
        h("minecraft:lapis_lazuli","Chercheur de Lapis",  16,  4f, "MOYEN",   "SHARDS", 55,  9f, null, 0,  65, 0, 5, 1);
        h("minecraft:redstone",  "Ingénieur Mineur",      32,  8f, "MOYEN",   "SHARDS", 55,  9f, null, 0,  65, 0, 5, 1);
        h("minecraft:sugar_cane","Sucrier",               64, 16f, "MOYEN",   "SHARDS", 50,  8f, null, 0,  60, 0, 5, 1);
        // ── HARVEST — DIFFICILE (lvl 15+) ───────────────────────────────────
        h("minecraft:diamond",   "Diamanteur",             5,  1f, "DIFFICILE","ITEM",   0,  0f, "minecraft:diamond", 5, 150,   0, 15, 1);
        h("minecraft:emerald",   "Chercheur d'Émeraude",  3,  1f, "DIFFICILE","ITEM",   0,  0f, "minecraft:emerald", 8, 150,   0, 15, 1);
        h("minecraft:obsidian",  "Récolteur d'Obsidienne",32,  4f, "DIFFICILE","SHARDS",110, 14f, null, 0, 120,  0, 15, 1);
        h("minecraft:quartz",    "Chercheur du Nether",   32,  8f, "DIFFICILE","SHARDS", 95, 13f, null, 0, 110,  0, 15, 1);
        // ── HARVEST — LÉGENDAIRE (lvl 30+) ──────────────────────────────────
        h("minecraft:ancient_debris","Chercheur de Débris", 2, 0f,"LÉGENDAIRE","ITEM",  0,  0f, "minecraft:netherite_scrap", 3, 300, 30, 30, 1);
        h("minecraft:amethyst_shard","Cristallographe",    32, 4f,"LÉGENDAIRE","ITEM",  0,  0f, "minecraft:amethyst_block", 3, 200,  0, 25, 1);

        // ── DELIVERY — FACILE (lvl 0+) ──────────────────────────────────────
        d("minecraft:bread",     "Pain pour le Village",  16,  "FACILE",  "SHARDS", 45, null, 0,  35, 0, 0, 1);
        d("minecraft:apple",     "Pommes Fraîches",       32,  "FACILE",  "SHARDS", 38, null, 0,  30, 0, 0, 1);
        // ── DELIVERY — MOYEN (lvl 5+) ────────────────────────────────────────
        d("minecraft:cooked_beef","Boucherie",            16,  "MOYEN",   "SHARDS", 80, null, 0,  90, 0, 5, 1);
        d("minecraft:cooked_chicken","Volailleur",        24,  "MOYEN",   "SHARDS", 72, null, 0,  85, 0, 5, 1);
        d("minecraft:glass",     "Vitrier",               64,  "MOYEN",   "SHARDS", 65, null, 0,  75, 0, 5, 1);
        d("minecraft:bookshelf", "Bibliothécaire",         8,  "MOYEN",   "SHARDS", 90, null, 0,  85, 10, 5, 1);
        // ── DELIVERY — DIFFICILE (lvl 15+) ───────────────────────────────────
        d("minecraft:golden_apple","Pommes en Or",         4,  "DIFFICILE","ITEM",   0, "minecraft:experience_bottle", 24, 150, 20, 15, 1);
        d("minecraft:iron_block","Blocs de Fer",           8,  "DIFFICILE","ITEM",   0, "minecraft:iron_block", 6, 150, 0, 15, 1);
        // ── DELIVERY — GROUPE ─────────────────────────────────────────────────
        d("minecraft:oak_log",   "Collecte de Bois",      64,  "MOYEN",   "SHARDS",110, null, 0, 100, 0, 0, 2);
        d("minecraft:cooked_beef","Banquet Collectif",    32,  "DIFFICILE","ITEM",   0, "minecraft:enchanted_golden_apple", 2, 200, 20, 5, 3);
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private static void k(String target, String label, int bQty, float qpl, String diff,
                           String rt, int bSh, float spl, String ri, int riQty,
                           int xp, int cost, int minLvl, int maxP) {
        POOL.add(new Template("KILL", target, label, bQty, qpl, diff, rt, bSh, spl, ri, riQty, xp, cost, minLvl, maxP));
    }

    private static void h(String target, String label, int bQty, float qpl, String diff,
                           String rt, int bSh, float spl, String ri, int riQty,
                           int xp, int cost, int minLvl, int maxP) {
        POOL.add(new Template("HARVEST", target, label, bQty, qpl, diff, rt, bSh, spl, ri, riQty, xp, cost, minLvl, maxP));
    }

    private static void d(String target, String label, int qty, String diff,
                           String rt, int bSh, String ri, int riQty,
                           int xp, int cost, int minLvl, int maxP) {
        POOL.add(new Template("DELIVERY", target, label, qty, 0f, diff, rt, bSh, 0f, ri, riQty, xp, cost, minLvl, maxP));
    }

    // ── Génération ────────────────────────────────────────────────────────────

    private static final long EXPIRE_MS = 24L * 3600 * 1000;

    /** Génère un pool solo pour un joueur de niveau `playerLevel`. */
    public static List<Quest> generateSolo(int playerLevel, int count) {
        return generate(playerLevel, count, 1);
    }

    /** Génère un pool de quêtes groupe (toutes les maxPlayers > 1). */
    public static List<Quest> generateGroup(int maxLevel, int count) {
        List<Template> eligible = POOL.stream()
            .filter(t -> t.maxPlayers() > 1 && t.minLevel() <= maxLevel)
            .toList();
        return buildFromTemplates(eligible, maxLevel, count);
    }

    private static List<Quest> generate(int playerLevel, int count, int forcedMaxPlayers) {
        List<Template> eligible = POOL.stream()
            .filter(t -> t.maxPlayers() == forcedMaxPlayers && t.minLevel() <= playerLevel)
            .toList();
        return buildFromTemplates(eligible, playerLevel, count);
    }

    private static List<Quest> buildFromTemplates(List<Template> eligible, int refLevel, int count) {
        List<Template> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled, RAND);

        Set<String> used = new HashSet<>();
        List<Quest> result = new ArrayList<>();

        for (Template t : shuffled) {
            if (result.size() >= count) break;
            if (used.contains(t.target())) continue;

            int levelAbove = Math.max(0, refLevel - t.minLevel());
            Quest q = new Quest();
            q.id           = nextId++;
            q.type         = t.type();
            q.target       = t.target();
            q.label        = t.label();
            q.levelRequired = t.minLevel();
            q.maxPlayers   = t.maxPlayers();
            q.isGlobal     = t.maxPlayers() > 1;
            q.quantity     = Math.max(1, (int)(t.baseQty() + t.qtyPerLevel() * levelAbove));
            q.rewardType   = t.rewardType();
            q.rewardXp     = t.rewardXp();
            q.costShards   = t.costShards();
            q.expiresAt    = System.currentTimeMillis() + EXPIRE_MS;

            if ("SHARDS".equals(t.rewardType())) {
                q.rewardShards = Math.max(1, (int)(t.baseShards() + t.shardsPerLevel() * levelAbove));
            } else {
                q.rewardItem    = t.rewardItem();
                q.rewardItemQty = t.rewardItemQty();
            }

            // Tags
            q.tags.add(t.maxPlayers() > 1 ? "GROUPE" : "SOLO");
            q.tags.add(t.type());
            q.tags.add(t.difficulty());
            q.tags.add(t.rewardType());

            result.add(q);
            used.add(t.target());
        }
        return result;
    }
}
