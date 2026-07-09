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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Bonus quotidien de connexion : +BONUS ◆ à la première connexion de chaque jour réel.
 * L'argent est créé (injecté dans l'économie), pas prélevé d'un compte.
 * Persistance : <gameDir>/nouvelle-terre-bonus.json (pseudo lowercase → date "yyyy-MM-dd").
 */
public class DailyBonusTracker {

    public static final int BONUS = 25;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("nouvelle-terre-bonus.json");
    private static Map<String, String> lastClaim = new HashMap<>();

    public static synchronized void load() {
        File f = FILE.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = GSON.fromJson(r, type);
            if (loaded != null) lastClaim = new HashMap<>(loaded);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[DailyBonusTracker] Erreur lecture : {}", e.getMessage());
        }
    }

    /** Retourne true si le bonus du jour vient d'être accordé (première connexion du jour). */
    public static synchronized boolean claimToday(String pseudo) {
        String today = LocalDate.now().toString();
        String key = pseudo.toLowerCase();
        if (today.equals(lastClaim.get(key))) return false;
        lastClaim.put(key, today);
        save();
        return true;
    }

    private static void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(lastClaim, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[DailyBonusTracker] Erreur sauvegarde : {}", e.getMessage());
        }
    }
}
