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
import java.util.HashSet;
import java.util.Set;

/**
 * Mémorise les joueurs qui ont reçu leurs 500 ◆ de départ.
 * Indépendant de la stat Leave_Game pour permettre le reset admin.
 */
public class FirstJoinTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FirstJoinTracker instance;

    private final Path fichier;
    private final Set<String> received = new HashSet<>();

    private FirstJoinTracker() {
        fichier = FabricLoader.getInstance().getGameDir().resolve("economy-starters.json");
        load();
    }

    public static synchronized FirstJoinTracker getInstance() {
        if (instance == null) instance = new FirstJoinTracker();
        return instance;
    }

    public synchronized boolean hasReceived(String pseudo) {
        return received.contains(pseudo.toLowerCase());
    }

    public synchronized void markReceived(String pseudo) {
        received.add(pseudo.toLowerCase());
        save();
    }

    public synchronized void resetAll() {
        received.clear();
        save();
    }

    public void load() {
        File f = fichier.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(r, type);
            if (loaded != null) received.addAll(loaded);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[FirstJoinTracker] Erreur lecture : {}", e.getMessage());
        }
    }

    private void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fichier.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(received, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[FirstJoinTracker] Erreur sauvegarde : {}", e.getMessage());
        }
    }
}
