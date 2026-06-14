package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class QuestManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path TEMPLATES = FabricLoader.getInstance().getGameDir().resolve("quetes-templates.json");
    private static final Path PROGRESS  = FabricLoader.getInstance().getGameDir().resolve("quetes-progress.json");

    private static List<Quest> quests = new ArrayList<>();

    private static class PlayerData {
        List<Integer>        accepted  = new ArrayList<>();
        Map<String, Integer> progress  = new HashMap<>(); // String key pour Gson
        List<Integer>        completed = new ArrayList<>();
    }
    private static Map<String, PlayerData> players = new HashMap<>();

    // ── Chargement ────────────────────────────────────────────────────────────

    public static synchronized void load() {
        loadTemplates();
        loadProgress();
    }

    private static void loadTemplates() {
        File f = TEMPLATES.toFile();
        if (!f.exists()) { createExamples(); return; }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type t = new TypeToken<List<Quest>>(){}.getType();
            List<Quest> loaded = GSON.fromJson(r, t);
            if (loaded != null) quests = loaded;
            NouvelleTerreBridge.LOGGER.info("[QuestManager] {} quête(s) chargée(s).", quests.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[QuestManager] Erreur lecture templates : {}", e.getMessage());
        }
    }

    private static void createExamples() {
        quests = new ArrayList<>();
        add(1, "KILL",    "minecraft:zombie",   20, 15, "Exterminateur de zombies");
        add(2, "KILL",    "minecraft:skeleton", 15, 12, "Chasseur de squelettes");
        add(3, "KILL",    "minecraft:creeper",  10, 20, "Démolisseur de creepers");
        add(4, "HARVEST", "minecraft:oak_log",  64,  8, "Bûcheron amateur");
        add(5, "HARVEST", "minecraft:coal",     32, 10, "Mineur de charbon");
        add(6, "HARVEST", "minecraft:wheat",    64,  6, "Agriculteur");
        saveTemplates();
        NouvelleTerreBridge.LOGGER.info("[QuestManager] quetes-templates.json créé.");
    }

    private static void add(int id, String type, String target, int qty, int reward, String label) {
        Quest q = new Quest();
        q.id = id; q.type = type; q.target = target;
        q.quantity = qty; q.reward = reward; q.label = label;
        quests.add(q);
    }

    private static void saveTemplates() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(TEMPLATES.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(quests, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[QuestManager] Erreur sauvegarde templates : {}", e.getMessage());
        }
    }

    private static void loadProgress() {
        File f = PROGRESS.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type t = new TypeToken<Map<String, PlayerData>>(){}.getType();
            Map<String, PlayerData> loaded = GSON.fromJson(r, t);
            if (loaded != null) players = loaded;
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[QuestManager] Erreur lecture progression : {}", e.getMessage());
        }
    }

    private static void saveProgress() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(PROGRESS.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(players, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[QuestManager] Erreur sauvegarde progression : {}", e.getMessage());
        }
    }

    private static PlayerData data(String player) {
        return players.computeIfAbsent(player.toLowerCase(), k -> new PlayerData());
    }

    // ── API publique ──────────────────────────────────────────────────────────

    public static synchronized List<Quest> getQuests() {
        return Collections.unmodifiableList(quests);
    }

    public static synchronized Map<Integer, Integer> getPlayerProgress(String player) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Map.Entry<String, Integer> e : data(player).progress.entrySet())
            map.put(Integer.parseInt(e.getKey()), e.getValue());
        return map;
    }

    public static synchronized Set<Integer> getPlayerCompleted(String player) {
        return new HashSet<>(data(player).completed);
    }

    /** @return null = succès, sinon message d'erreur */
    public static synchronized String accept(String player, int questId) {
        Quest q = find(questId);
        if (q == null) return "Quête introuvable.";
        PlayerData d = data(player);
        if (d.completed.contains(questId)) return "Quête déjà terminée.";
        if (d.accepted.contains(questId))  return "Quête déjà acceptée.";
        d.accepted.add(questId);
        d.progress.put(String.valueOf(questId), 0);
        saveProgress();
        return null;
    }

    /** @return null = succès, sinon message d'erreur */
    public static synchronized String claim(String player, int questId, MinecraftServer server) {
        Quest q = find(questId);
        if (q == null) return "Quête introuvable.";
        PlayerData d = data(player);
        if (!d.accepted.contains(questId))  return "Quête non acceptée.";
        if (d.completed.contains(questId))  return "Récompense déjà réclamée.";
        int prog = d.progress.getOrDefault(String.valueOf(questId), 0);
        if (prog < q.quantity) return "Objectif non atteint (" + prog + "/" + q.quantity + ").";
        LocalEconomy.getInstance().addShards(player, q.reward);
        TransactionLog.log(player, TransactionLog.TYPE_REWARD, "Quête : " + q.label, q.reward);
        d.completed.add(questId);
        d.accepted.remove(Integer.valueOf(questId));
        d.progress.remove(String.valueOf(questId));
        saveProgress();
        server.execute(() -> {
            var sp = server.getPlayerManager().getPlayer(player);
            if (sp != null) NouvelleTerreBridge.sendBalanceToPlayer(sp);
        });
        return null;
    }

    public static synchronized void onMobKilled(String player, String entityTypeId) {
        PlayerData d = data(player);
        boolean changed = false;
        for (Quest q : quests) {
            if (!"KILL".equals(q.type))      continue;
            if (!q.target.equals(entityTypeId)) continue;
            if (!d.accepted.contains(q.id))  continue;
            if (d.completed.contains(q.id))  continue;
            int cur = d.progress.getOrDefault(String.valueOf(q.id), 0);
            d.progress.put(String.valueOf(q.id), Math.min(cur + 1, q.quantity));
            changed = true;
        }
        if (changed) saveProgress();
    }

    public static synchronized void onItemHarvested(String player, String itemId, int count) {
        PlayerData d = data(player);
        boolean changed = false;
        for (Quest q : quests) {
            if (!"HARVEST".equals(q.type))   continue;
            if (!q.target.equals(itemId))    continue;
            if (!d.accepted.contains(q.id))  continue;
            if (d.completed.contains(q.id))  continue;
            int cur = d.progress.getOrDefault(String.valueOf(q.id), 0);
            d.progress.put(String.valueOf(q.id), Math.min(cur + count, q.quantity));
            changed = true;
        }
        if (changed) saveProgress();
    }

    public static synchronized void reload() {
        loadTemplates();
    }

    public static synchronized void reset() {
        players.clear();
        saveProgress();
        NouvelleTerreBridge.LOGGER.info("[QuestManager] Progression réinitialisée.");
    }

    private static Quest find(int id) {
        return quests.stream().filter(q -> q.id == id).findFirst().orElse(null);
    }
}
