package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.util.FichierSur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ClientConfig {

    private static final Gson   GSON     = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "nouvelle-terre-client.json";
    private static ClientConfig INSTANCE;

    // ── Général ───────────────────────────────────────────────────────────────
    public boolean discordRPCEnabled = true;
    public String  discordRPCServer  = "91.197.6.86";

    // ── HUD ───────────────────────────────────────────────────────────────────

    /** Réglages d'un widget du HUD, tous widgets confondus. */
    public static class WidgetState {
        public boolean enabled;
        public float   x, y;
        public float   taille = 1.0f;
        public boolean fond   = true;
        public boolean ombre  = true;
        /** Réglages oui/non propres au widget (clé déclarée par le widget). */
        public Map<String, Boolean> options = new HashMap<>();
    }

    /** Identifiant du widget → ses réglages. Seule source lue une fois remplie. */
    public Map<String, WidgetState> widgets = new HashMap<>();

    /**
     * Dispositions enregistrées : nom du profil → réglages de tous les widgets.
     * {@link #widgets} reste la disposition courante ; elle est recopiée dans le
     * profil actif à chaque fermeture du menu.
     */
    public Map<String, Map<String, WidgetState>> profils = new java.util.LinkedHashMap<>();
    public String profilActif = "Principal";

    // ── Anciens réglages du HUD (avant la refonte de l'éditeur) ───────────────
    //
    // Chaque widget avait ses propres champs. Ils ne sont plus écrits, mais ils
    // restent lisibles : au premier lancement après la mise à jour, ancienEtat()
    // les reprend pour que personne ne perde la disposition de son HUD.
    // ⚠ Ne pas les supprimer tant que des joueurs peuvent encore venir d'une
    // version antérieure à la 2.2.0.
    public boolean hudEnabled = true;   // widget « balance »
    public float   balanceX   = 0.99f;
    public float   balanceY   = 0.01f;
    public boolean coordsEnabled      = false;
    public float   coordsX            = 0.01f;
    public float   coordsY            = 0.06f;
    public boolean coordsShowDecimals = false;
    public boolean timeEnabled  = false;
    public float   timeX        = 0.01f;
    public float   timeY        = 0.01f;
    public boolean timeShowIcon = true;
    public boolean fpsEnabled  = false;
    public float   fpsX        = 0.01f;
    public float   fpsY        = 0.14f;
    public boolean fpsShowPing = true;
    public boolean biomeEnabled = false;
    public float   biomeX       = 0.01f;
    public float   biomeY       = 0.17f;
    public boolean dimensionEnabled = false;
    public float   dimensionX       = 0.01f;
    public float   dimensionY       = 0.26f;
    public boolean effetsEnabled = false;
    public float   effetsX       = 0.01f;
    public float   effetsY       = 0.29f;
    public boolean notifEnabled = true;
    public float   notifX       = 0.99f;
    public float   notifY       = 0.85f;
    public boolean questEnabled = false;
    public float   questX       = 0.35f;
    public float   questY       = 0.02f;

    /** Réglages d'un widget tels qu'ils étaient avant la refonte, ou null s'il est nouveau. */
    public WidgetState ancienEtat(String id) {
        WidgetState s = new WidgetState();
        switch (id) {
            case "balance"   -> { s.enabled = hudEnabled;       s.x = balanceX;   s.y = balanceY; }
            case "coords"    -> { s.enabled = coordsEnabled;    s.x = coordsX;    s.y = coordsY;
                                  s.options.put("decimales", coordsShowDecimals); }
            case "time"      -> { s.enabled = timeEnabled;      s.x = timeX;      s.y = timeY;
                                  s.options.put("icone", timeShowIcon); }
            case "fps"       -> { s.enabled = fpsEnabled;       s.x = fpsX;       s.y = fpsY;
                                  s.options.put("ping", fpsShowPing); }
            case "biome"     -> { s.enabled = biomeEnabled;     s.x = biomeX;     s.y = biomeY; }
            case "dimension" -> { s.enabled = dimensionEnabled; s.x = dimensionX; s.y = dimensionY; }
            case "effets"    -> { s.enabled = effetsEnabled;    s.x = effetsX;    s.y = effetsY; }
            case "notif"     -> { s.enabled = notifEnabled;     s.x = notifX;     s.y = notifY; }
            case "quest"     -> { s.enabled = questEnabled;     s.x = questX;     s.y = questY; }
            default          -> { return null; }
        }
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static ClientConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILENAME);
        File file = path.toFile();
        if (file.exists()) {
            try (Reader r = new FileReader(file)) {
                ClientConfig loaded = GSON.fromJson(r, ClientConfig.class);
                INSTANCE = loaded != null ? loaded : new ClientConfig();
                if (INSTANCE.widgets == null) INSTANCE.widgets = new HashMap<>();
                if (INSTANCE.profils == null) INSTANCE.profils = new java.util.LinkedHashMap<>();
                return;
            } catch (Exception ignored) {
                // Fichier illisible : on repart des réglages par défaut plutôt que de planter
            }
        }
        INSTANCE = new ClientConfig();
        INSTANCE.save();
    }

    public void save() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILENAME);
        try {
            FichierSur.ecrire(path, GSON.toJson(this));
        } catch (IOException ignored) {}
    }
}
