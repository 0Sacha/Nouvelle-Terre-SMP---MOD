package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Suit le temps de jeu des joueurs et distribue des récompenses périodiques.
 *
 * Récompenses :
 *  - 5 Shards toutes les 30 minutes de jeu
 *  - Salaire selon le métier toutes les heures de jeu
 */
public class PlaytimeTracker {

    // Ticks depuis la dernière récompense par joueur
    private static final Map<UUID, Integer> ticksDepuisRecompense = new HashMap<>();
    private static final Map<UUID, Integer> ticksDepuisSalaire = new HashMap<>();

    // 30 min = 30 * 60 * 20 = 36000 ticks
    private static final int TICKS_RECOMPENSE = 36_000;
    // 60 min = 72000 ticks
    private static final int TICKS_SALAIRE = 72_000;

    // Récompenses en Shards
    private static final int SHARDS_RECOMPENSE_TEMPS = 5;

    // Salaires par métier (Shards/heure)
    private static final Map<String, Integer> SALAIRES = new HashMap<>();
    static {
        SALAIRES.put("forgeron", 15);
        SALAIRES.put("fermier", 12);
        SALAIRES.put("charpentier", 12);
        SALAIRES.put("macon", 12);
        SALAIRES.put("herboriste", 10);
        SALAIRES.put("garde", 15);
        SALAIRES.put("marchand", 20);
    }
    private static final int SALAIRE_BASE = 8; // sans métier

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlaytimeTracker::onTick);
    }

    private static void onTick(MinecraftServer server) {
        for (ServerPlayerEntity joueur : server.getPlayerManager().getPlayerList()) {
            UUID uuid = joueur.getUuid();
            String pseudo = joueur.getName().getString();

            // --- Récompense temps de jeu ---
            int ticksR = ticksDepuisRecompense.getOrDefault(uuid, 0) + 1;
            if (ticksR >= TICKS_RECOMPENSE) {
                ticksR = 0;
                EconomyManager.reward(pseudo, SHARDS_RECOMPENSE_TEMPS, "temps de jeu");
                joueur.sendMessage(net.minecraft.text.Text.literal(
                    "§6⏱ +§f" + SHARDS_RECOMPENSE_TEMPS + " 💎§6 pour 30 min de jeu !"
                ));
            }
            ticksDepuisRecompense.put(uuid, ticksR);

            // --- Salaire métier ---
            int ticksS = ticksDepuisSalaire.getOrDefault(uuid, 0) + 1;
            if (ticksS >= TICKS_SALAIRE) {
                ticksS = 0;
                // Récupère le métier depuis la fiche (via événement)
                // On envoie un événement ECONOMY_SALARY au bot
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("player", pseudo);
                // Le bot connaît le métier depuis sa DB, on lui envoie juste l'événement
                // Le bot calcule le salaire et l'applique
                data.put("amount", SALAIRE_BASE); // valeur par défaut, le bot peut surcharger
                data.put("metier", ""); // le bot utilisera son propre métier
                com.nouvelleterrebridge.http.EventDispatcher.envoyer("ECONOMY_SALARY", data);
            }
            ticksDepuisSalaire.put(uuid, ticksS);
        }
    }

    /** Remet les compteurs à zéro quand un joueur se déconnecte. */
    public static void onPlayerLeave(UUID uuid) {
        ticksDepuisRecompense.remove(uuid);
        ticksDepuisSalaire.remove(uuid);
    }
}
