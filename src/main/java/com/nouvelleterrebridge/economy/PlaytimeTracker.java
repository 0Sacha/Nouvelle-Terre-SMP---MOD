package com.nouvelleterrebridge.economy;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlaytimeTracker {

    private static final Map<UUID, Integer> ticksDepuisRecompense = new HashMap<>();
    private static final Map<UUID, Integer> ticksDepuisSalaire   = new HashMap<>();

    // 30 min = 36 000 ticks, 60 min = 72 000 ticks
    private static final int TICKS_RECOMPENSE = 36_000;
    private static final int TICKS_SALAIRE    = 72_000;

    private static final int SHARDS_RECOMPENSE = 5;

    private static final Map<String, Integer> SALAIRES = new HashMap<>();
    static {
        SALAIRES.put("forgeron",    15);
        SALAIRES.put("fermier",     12);
        SALAIRES.put("charpentier", 12);
        SALAIRES.put("macon",       12);
        SALAIRES.put("herboriste",  10);
        SALAIRES.put("garde",       15);
        SALAIRES.put("marchand",    20);
    }
    private static final int SALAIRE_BASE = 8;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlaytimeTracker::onTick);
    }

    private static void onTick(MinecraftServer server) {
        for (ServerPlayerEntity joueur : server.getPlayerManager().getPlayerList()) {
            UUID uuid   = joueur.getUuid();
            String pseudo = joueur.getName().getString();

            // Récompense temps de jeu (toutes les 30 min)
            int ticksR = ticksDepuisRecompense.getOrDefault(uuid, 0) + 1;
            if (ticksR >= TICKS_RECOMPENSE) {
                ticksR = 0;
                LocalEconomy.getInstance().addShards(pseudo, SHARDS_RECOMPENSE);
                joueur.sendMessage(net.minecraft.text.Text.literal(
                    "§6⏱ §f+" + SHARDS_RECOMPENSE + "💎§6 pour 30 min de jeu !"
                ));
            }
            ticksDepuisRecompense.put(uuid, ticksR);

            // Salaire métier (toutes les heures)
            int ticksS = ticksDepuisSalaire.getOrDefault(uuid, 0) + 1;
            if (ticksS >= TICKS_SALAIRE) {
                ticksS = 0;
                // Le salaire précis dépend du métier stocké dans la DB du bot.
                // On envoie ECONOMY_SALARY ; le bot applique le bon montant côté Discord.
                // Localement on applique le salaire de base en attendant la synchro des métiers.
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("player", pseudo);
                data.put("amount", SALAIRE_BASE);
                data.put("metier", "");
                com.nouvelleterrebridge.http.EventDispatcher.envoyer("ECONOMY_SALARY", data);
                LocalEconomy.getInstance().addShards(pseudo, SALAIRE_BASE);
                NouvelleTerreBridge.LOGGER.info("[PlaytimeTracker] Salaire versé à {} ({} shards).", pseudo, SALAIRE_BASE);
            }
            ticksDepuisSalaire.put(uuid, ticksS);
        }
    }

    public static void onPlayerLeave(UUID uuid) {
        ticksDepuisRecompense.remove(uuid);
        ticksDepuisSalaire.remove(uuid);
    }
}
