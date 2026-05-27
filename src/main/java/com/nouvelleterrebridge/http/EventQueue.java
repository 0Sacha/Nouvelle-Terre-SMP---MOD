package com.nouvelleterrebridge.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * File d'attente persistante pour les événements quand le bot est hors ligne.
 * Les événements sont sauvegardés dans un fichier JSON pour survivre aux redémarrages.
 */
public class EventQueue {

    private static final Gson GSON = new Gson();
    private static final String NOM_FICHIER = "nouvelle-terre-queue.json";
    private static final EventQueue INSTANCE = new EventQueue();

    private final List<EvenementEnAttente> file = new ArrayList<>();

    private EventQueue() {}

    public static EventQueue getInstance() {
        return INSTANCE;
    }

    public static class EvenementEnAttente {
        public final String type;
        public final String json;
        public final long enfileA;

        public EvenementEnAttente(String type, String json, long enfileA) {
            this.type = type;
            this.json = json;
            this.enfileA = enfileA;
        }
    }

    /**
     * Ajoute un événement à la file et persiste immédiatement.
     */
    public synchronized void ajouter(String type, String json) {
        file.add(new EvenementEnAttente(type, json, System.currentTimeMillis()));
        sauvegarder();
    }

    /**
     * Retourne tous les événements et vide la file.
     */
    public synchronized List<EvenementEnAttente> vider() {
        List<EvenementEnAttente> copie = new ArrayList<>(file);
        file.clear();
        sauvegarder();
        return Collections.unmodifiableList(copie);
    }

    public synchronized boolean estVide() {
        return file.isEmpty();
    }

    public synchronized int taille() {
        return file.size();
    }

    /**
     * Charge la file depuis le fichier JSON (au démarrage du serveur).
     */
    public synchronized void charger() {
        Path chemin = obtenirChemin();
        if (!chemin.toFile().exists()) return;

        try (Reader reader = new FileReader(chemin.toFile())) {
            Type type = new TypeToken<List<EvenementEnAttente>>(){}.getType();
            List<EvenementEnAttente> charge = GSON.fromJson(reader, type);
            if (charge != null) {
                file.addAll(charge);
                NouvelleTerreBridge.LOGGER.info("[EventQueue] {} événement(s) en attente récupéré(s) depuis le disque.", file.size());
            }
        } catch (IOException e) {
            NouvelleTerreBridge.LOGGER.error("[EventQueue] Impossible de lire la file d'attente", e);
        }
    }

    private void sauvegarder() {
        Path chemin = obtenirChemin();
        try (Writer writer = new FileWriter(chemin.toFile())) {
            GSON.toJson(file, writer);
        } catch (IOException e) {
            NouvelleTerreBridge.LOGGER.error("[EventQueue] Impossible de sauvegarder la file d'attente", e);
        }
    }

    private Path obtenirChemin() {
        return FabricLoader.getInstance().getConfigDir().resolve(NOM_FICHIER);
    }
}
