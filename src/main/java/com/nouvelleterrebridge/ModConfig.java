package com.nouvelleterrebridge;

import com.nouvelleterrebridge.util.FichierSur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NOM_FICHIER = "nouvelle-terre-bridge.json";

    // URL du bot Discord (endpoint HTTP)
    private String botUrl = "http://localhost:3001";

    // Secret partagé avec le bot (doit être identique des deux côtés)
    private String sharedSecret = "changez_ce_secret";

    private boolean activerEvenementServeur = true;
    private boolean activerEvenementJoueur = true;

    // Délai en secondes avant de vider la file d'attente (si le bot revient en ligne)
    private int delaiVideFileAttente = 30;


    public static ModConfig charger() {
        Path cheminConfig = FMLPaths.CONFIGDIR.get().resolve(NOM_FICHIER);
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
            if (config != null) config.avertirSiDefauts();
            return config;
        } catch (IOException e) {
            NouvelleTerreBridge.LOGGER.error("[Config] Impossible de lire la configuration, utilisation des valeurs par défaut", e);
            return new ModConfig();
        }
    }

    public void sauvegarder() {
        Path cheminConfig = FMLPaths.CONFIGDIR.get().resolve(NOM_FICHIER);
        try {
            FichierSur.ecrire(cheminConfig, GSON.toJson(this));
        } catch (IOException e) {
            NouvelleTerreBridge.LOGGER.error("[Config] Impossible de sauvegarder la configuration", e);
        }
    }

    /** Le secret d'exemple est public (il est dans le code) : le garder revient à ne pas en avoir. */
    private void avertirSiDefauts() {
        if ("changez_ce_secret".equals(sharedSecret))
            NouvelleTerreBridge.LOGGER.warn("[Config] ⚠ sharedSecret est resté à sa valeur d'exemple — "
                + "n'importe qui peut se faire passer pour ce serveur auprès du bot. Changez-le des deux côtés.");
        if (botUrl != null && botUrl.startsWith("http://") && !botUrl.contains("localhost") && !botUrl.contains("127.0.0.1"))
            NouvelleTerreBridge.LOGGER.warn("[Config] ⚠ botUrl est en http:// — le secret circule en clair. Utilisez https://.");
    }

    // --- Getters ---

    public String getBotUrl() { return botUrl; }
    public String getSharedSecret() { return sharedSecret; }
    public boolean isActiverEvenementServeur() { return activerEvenementServeur; }
    public boolean isActiverEvenementJoueur() { return activerEvenementJoueur; }
    public int getDelaiVideFileAttente() { return delaiVideFileAttente; }


}
