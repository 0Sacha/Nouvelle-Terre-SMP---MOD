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

    // ── Widget Santé ──────────────────────────────────────────────────────────
    public boolean santeEnabled = false;
    public float   santeX       = 0.01f;
    public float   santeY       = 0.08f;

    // ── Widget Nourriture ─────────────────────────────────────────────────────
    public boolean nourritureEnabled = false;
    public float   nourritureX       = 0.01f;
    public float   nourritureY       = 0.11f;

    // ── Widget FPS ────────────────────────────────────────────────────────────
    public boolean fpsEnabled  = false;
    public float   fpsX        = 0.01f;
    public float   fpsY        = 0.14f;
    public boolean fpsShowPing = true;

    // ── Widget Biome ──────────────────────────────────────────────────────────
    public boolean biomeEnabled = false;
    public float   biomeX       = 0.01f;
    public float   biomeY       = 0.17f;

    // ── Widget Armure ─────────────────────────────────────────────────────────
    public boolean armureEnabled = false;
    public float   armureX       = 0.01f;
    public float   armureY       = 0.20f;

    // ── Widget XP ─────────────────────────────────────────────────────────────
    public boolean xpEnabled = false;
    public float   xpX       = 0.01f;
    public float   xpY       = 0.23f;

    // ── Widget Dimension ──────────────────────────────────────────────────────
    public boolean dimensionEnabled = false;
    public float   dimensionX       = 0.01f;
    public float   dimensionY       = 0.26f;

    // ── Widget Effets actifs ──────────────────────────────────────────────────
    public boolean effetsEnabled = false;
    public float   effetsX       = 0.01f;
    public float   effetsY       = 0.29f;

    // ── Notifications (position) ──────────────────────────────────────────────
    public boolean notifEnabled = true;
    public float   notifX       = 0.99f;
    public float   notifY       = 0.85f;

    // ── Widget Quête active ───────────────────────────────────────────────────
    public boolean questEnabled = false;
    public float   questX       = 0.35f;
    public float   questY       = 0.02f;

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
