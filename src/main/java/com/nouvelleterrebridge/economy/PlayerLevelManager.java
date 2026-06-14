package com.nouvelleterrebridge.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PlayerLevelManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("player-levels.json");

    private static class Data {
        int level = 0;
        int xp    = 0;
    }

    private static Map<String, Data> players = new HashMap<>();

    public static synchronized void load() {
        File f = FILE.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type t = new TypeToken<Map<String, Data>>(){}.getType();
            Map<String, Data> loaded = GSON.fromJson(r, t);
            if (loaded != null) players = loaded;
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[PlayerLevelManager] Erreur lecture : {}", e.getMessage());
        }
    }

    private static void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(players, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[PlayerLevelManager] Erreur sauvegarde : {}", e.getMessage());
        }
    }

    private static Data data(String player) {
        return players.computeIfAbsent(player.toLowerCase(), k -> new Data());
    }

    public static synchronized int getLevel(String player) { return data(player).level; }
    public static synchronized int getXp(String player)    { return data(player).xp;    }

    /** XP nécessaire pour passer du niveau `level` au suivant. */
    public static int xpToNextLevel(int level) {
        return 100 + level * 50;
    }

    /** Ajoute de l'XP et fait monter de niveau si besoin. */
    public static synchronized void addXp(String player, int amount, MinecraftServer server) {
        Data d = data(player);
        d.xp += amount;
        boolean leveled = false;
        while (d.xp >= xpToNextLevel(d.level)) {
            d.xp -= xpToNextLevel(d.level);
            d.level++;
            leveled = true;
            final int newLevel = d.level;
            server.execute(() -> {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(player);
                if (sp != null) {
                    sp.sendMessage(Text.literal(
                        "§6[Nouvelle Terre] §e✨ Niveau " + newLevel + " atteint ! De nouvelles quêtes sont disponibles."), false);
                }
            });
        }
        save();
        if (leveled) QuestManager.refreshPlayerPool(player, server);
    }

    public static synchronized void reset() {
        players.clear();
        save();
    }
}
