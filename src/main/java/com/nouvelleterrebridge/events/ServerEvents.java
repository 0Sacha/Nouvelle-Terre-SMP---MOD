package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Écouteurs pour les événements du cycle de vie du serveur.
 */
public class ServerEvents {

    public static void register() {
        if (!NouvelleTerreBridge.config.isActiverEvenementServeur()) return;

        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            var server = event.getServer();
            NouvelleTerreBridge.LOGGER.info("[ServerEvents] Serveur démarré, envoi de SERVER_START");
            Map<String, Object> data = new HashMap<>();
            data.put("version", "1.20.1");
            data.put("maxPlayers", server.getMaxPlayers());
            EventDispatcher.envoyer("SERVER_START", data);

            // Petite pause pour laisser le bot traiter SERVER_START avant le sync marché
            Executors.newSingleThreadScheduledExecutor().schedule(
                EventDispatcher::envoyerSyncMarche, 3, TimeUnit.SECONDS
            );
        });

        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            NouvelleTerreBridge.LOGGER.info("[ServerEvents] Serveur en arrêt, envoi de SERVER_STOP");
            Map<String, Object> data = new HashMap<>();
            data.put("onlinePlayers", event.getServer().getPlayerCount());
            EventDispatcher.envoyer("SERVER_STOP", data);
        });
    }
}
