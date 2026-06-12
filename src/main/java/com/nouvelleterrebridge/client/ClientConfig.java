package com.nouvelleterrebridge.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class ClientConfig {

    private static final Gson   GSON     = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "nouvelle-terre-client.json";
    private static ClientConfig INSTANCE;

    // ── Général ───────────────────────────────────────────────────────────────
    public boolean discordRPCEnabled = true;
    public String  discordRPCServer  = "91.197.6.86";

    // ── Widget Solde ──────────────────────────────────────────────────────────
    public boolean hudEnabled = true;   // id "balance" — gardé pour rétrocompat JSON
    public float   balanceX   = 0.99f;
    public float   balanceY   = 0.01f;

    // ── Widget Coordonnées ────────────────────────────────────────────────────
    public boolean coordsEnabled      = false;
    public float   coordsX            = 0.01f;
    public float   coordsY            = 0.06f;
    public boolean coordsShowDecimals = false;

    // ── Widget Boussole ───────────────────────────────────────────────────────
    public boolean compassEnabled     = false;
    public float   compassX           = 0.50f;
    public float   compassY           = 0.02f;
    public boolean compassShowDegrees = false;

    // ── Widget Heure ──────────────────────────────────────────────────────────
    public boolean timeEnabled  = false;
    public float   timeX        = 0.01f;
    public float   timeY        = 0.01f;
    public boolean timeShowIcon = true;

    // ─────────────────────────────────────────────────────────────────────────

    public static ClientConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
        File file = path.toFile();
        if (file.exists()) {
            try (Reader r = new FileReader(file)) {
                ClientConfig loaded = GSON.fromJson(r, ClientConfig.class);
                INSTANCE = loaded != null ? loaded : new ClientConfig();
                return;
            } catch (IOException ignored) {}
        }
        INSTANCE = new ClientConfig();
        INSTANCE.save();
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
        try (Writer w = new FileWriter(path.toFile())) {
            GSON.toJson(this, w);
        } catch (IOException ignored) {}
    }
}
