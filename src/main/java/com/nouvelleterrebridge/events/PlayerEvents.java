package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;

import java.util.HashMap;
import java.util.Map;

/**
 * Écouteurs pour les événements liés aux joueurs (connexion, déconnexion).
 * La mort est gérée par LivingEntityMixin.
 */
public class PlayerEvents {

    public static void register() {
        if (!NouvelleTerreBridge.config.isActiverEvenementJoueur()) return;

        // Connexion d'un joueur
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joueur = handler.getPlayer();
            String pseudo = joueur.getName().getString();
            String uuid = joueur.getUuidAsString();

            // Détecte si c'est la première connexion via la stat "leave_game"
            // Un nouveau joueur n'aura jamais quitté le jeu
            int nbDeparts = joueur.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.LEAVE_GAME));
            boolean premiereFois = nbDeparts == 0;

            Map<String, Object> data = new HashMap<>();
            data.put("player", pseudo);
            data.put("uuid", uuid);

            if (premiereFois) {
                NouvelleTerreBridge.LOGGER.info("[PlayerEvents] Première connexion de {}", pseudo);
                EventDispatcher.envoyer("PLAYER_FIRST_JOIN", data);
            } else {
                EventDispatcher.envoyer("PLAYER_JOIN", data);
            }
        });

        // Déconnexion d'un joueur
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity joueur = handler.getPlayer();
            PlaytimeTracker.onPlayerLeave(joueur.getUuid());
            Map<String, Object> data = new HashMap<>();
            data.put("player", joueur.getName().getString());
            data.put("uuid", joueur.getUuidAsString());
            EventDispatcher.envoyer("PLAYER_LEAVE", data);
        });
    }
}
