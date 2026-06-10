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

            // Salaire : le timer monte jusqu'à TICKS_SALAIRE puis s'arrête.
            // Le joueur réclame manuellement via le GUI HDV (ACTION_CLAIM_SALARY).
            int ticksS = ticksDepuisSalaire.getOrDefault(uuid, 0);
            if (ticksS < TICKS_SALAIRE) {
                ticksDepuisSalaire.put(uuid, ticksS + 1);
            }
        }
    }

    public static void onPlayerLeave(UUID uuid) {
        ticksDepuisRecompense.remove(uuid);
        ticksDepuisSalaire.remove(uuid);
    }

    public static int getTicksUntilReward(UUID uuid) {
        return TICKS_RECOMPENSE - ticksDepuisRecompense.getOrDefault(uuid, 0);
    }

    public static int getTicksUntilSalary(UUID uuid) {
        return TICKS_SALAIRE - ticksDepuisSalaire.getOrDefault(uuid, 0);
    }

    public static int getSalaireBase() { return SALAIRE_BASE; }

    /**
     * Si le timer de salaire est écoulé, remet le compteur à 0 et retourne le montant à payer.
     * Sinon retourne 0 (pas encore disponible).
     */
    public static synchronized int tryClaimSalary(UUID uuid) {
        int ticks = ticksDepuisSalaire.getOrDefault(uuid, 0);
        if (ticks < TICKS_SALAIRE) return 0;
        ticksDepuisSalaire.put(uuid, 0);
        return SALAIRE_BASE;
    }
}
