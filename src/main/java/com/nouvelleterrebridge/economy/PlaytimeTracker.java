package com.nouvelleterrebridge.economy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlaytimeTracker {

    private static final Map<UUID, Integer> ticksDepuisRecompense = new HashMap<>();
    private static final int TICKS_RECOMPENSE  = 36_000; // 30 min
    private static final int SHARDS_RECOMPENSE = 5;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlaytimeTracker::onTick);
    }

    private static void onTick(MinecraftServer server) {
        for (ServerPlayerEntity joueur : server.getPlayerManager().getPlayerList()) {
            UUID uuid = joueur.getUuid();
            int ticks = ticksDepuisRecompense.getOrDefault(uuid, 0) + 1;
            if (ticks >= TICKS_RECOMPENSE) {
                ticks = 0;
                LocalEconomy.getInstance().addShards(joueur.getName().getString(), SHARDS_RECOMPENSE);
                joueur.sendMessage(net.minecraft.text.Text.literal(
                    "§6⏱ §f+" + SHARDS_RECOMPENSE + " ◆§6 pour 30 min de jeu !"));
                com.nouvelleterrebridge.NouvelleTerreBridge.sendBalanceToPlayer(joueur);
            }
            ticksDepuisRecompense.put(uuid, ticks);
        }
    }

    public static void onPlayerLeave(UUID uuid) {
        ticksDepuisRecompense.remove(uuid);
    }

    public static int getTicksUntilReward(UUID uuid) {
        return TICKS_RECOMPENSE - ticksDepuisRecompense.getOrDefault(uuid, 0);
    }
}
