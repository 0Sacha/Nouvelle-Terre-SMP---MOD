package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.resourcepack.ResourcePackManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

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

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Démarrage du serveur HTTP pour le resource pack
            ResourcePackManager.init(
                NouvelleTerreBridge.config.hasDirectUrl() ? NouvelleTerreBridge.config.getResourcePackUrl() : "",
                NouvelleTerreBridge.config.getResourcePackHost(),
                NouvelleTerreBridge.config.getResourcePackPort());

            NouvelleTerreBridge.LOGGER.info("[ServerEvents] Serveur démarré, envoi de SERVER_START");
            Map<String, Object> data = new HashMap<>();
            data.put("version", "1.20.1");
            data.put("maxPlayers", server.getMaxPlayerCount());
            EventDispatcher.envoyer("SERVER_START", data);

            // Petite pause pour laisser le bot traiter SERVER_START avant le sync marché
            Executors.newSingleThreadScheduledExecutor().schedule(
                EventDispatcher::envoyerSyncMarche, 3, TimeUnit.SECONDS
            );
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            NouvelleTerreBridge.LOGGER.info("[ServerEvents] Serveur en arrêt, envoi de SERVER_STOP");
            ResourcePackManager.shutdown();
            Map<String, Object> data = new HashMap<>();
            data.put("onlinePlayers", server.getCurrentPlayerCount());
            EventDispatcher.envoyer("SERVER_STOP", data);
        });
    }
}
