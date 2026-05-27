package com.nouvelleterrebridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

/**
 * Configuration du mod, lue depuis config/nouvelle-terre-bridge.json.
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NOM_FICHIER = "nouvelle-terre-bridge.json";

    // URL du bot Discord (endpoint HTTP)
    private String botUrl = "http://localhost:3001";

    // Secret partagé avec le bot (doit être identique des deux côtés)
    private String sharedSecret = "changez_ce_secret";

    // Activation individuelle des fonctionnalités
    private boolean activerEvenementServeur = true;
    private boolean activerEvenementJoueur = true;
    private boolean activerEvenementEconomie = true;
    private boolean activerEvenementTerritoire = true;

    // Délai en secondes avant de vider la file d'attente (si le bot revient en ligne)
    private int delaiVideFileAttente = 30;

    public static ModConfig charger() {
        Path cheminConfig = FabricLoader.getInstance().getConfigDir().resolve(NOM_FICHIER);
        File fichier = cheminConfig.toFile();

        if (!fichier.exists()) {
            // Crée le fichier avec les valeurs par défaut
            ModConfig defaut = new ModConfig();
            defaut.sauvegarder();
            NouvelleTerreBridge.LOGGER.info("[Config] Fichier de configuration créé : {}", cheminConfig);
            return defaut;
        }

        try (Reader reader = new FileReader(fichier)) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            NouvelleTerreBridge.LOGGER.info("[Config] Configuration chargée depuis {}", cheminConfig);
            return config;
        } catch (IOException e) {
            NouvelleTerreBridge.LOGGER.error("[Config] Impossible de lire la configuration, utilisation des valeurs par défaut", e);
            return new ModConfig();
        }
    }

    public void sauvegarder() {
        Path cheminConfig = FabricLoader.getInstance().getConfigDir().resolve(NOM_FICHIER);
        try (Writer writer = new FileWriter(cheminConfig.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            NouvelleTerreBridge.LOGGER.error("[Config] Impossible de sauvegarder la configuration", e);
        }
    }

    // --- Getters ---

    public String getBotUrl() { return botUrl; }
    public String getSharedSecret() { return sharedSecret; }
    public boolean isActiverEvenementServeur() { return activerEvenementServeur; }
    public boolean isActiverEvenementJoueur() { return activerEvenementJoueur; }
    public boolean isActiverEvenementEconomie() { return activerEvenementEconomie; }
    public boolean isActiverEvenementTerritoire() { return activerEvenementTerritoire; }
    public int getDelaiVideFileAttente() { return delaiVideFileAttente; }
}
