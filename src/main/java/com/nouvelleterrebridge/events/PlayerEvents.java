package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.FirstJoinTracker;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.network.HdvNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

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

            boolean premiereFois = !FirstJoinTracker.getInstance().hasReceived(pseudo);

            Map<String, Object> data = new HashMap<>();
            data.put("player", pseudo);
            data.put("uuid", uuid);

            if (premiereFois) {
                NouvelleTerreBridge.LOGGER.info("[PlayerEvents] Première connexion de {}", pseudo);
                LocalEconomy.getInstance().addShards(pseudo, 500);
                FirstJoinTracker.getInstance().markReceived(pseudo);
                joueur.sendMessage(net.minecraft.text.Text.literal(
                    "§6[Nouvelle Terre] §f✨ Bienvenue ! Tu reçois §e§l500 ◆ §fde départ. Bonne aventure !"));
                EventDispatcher.envoyer("PLAYER_FIRST_JOIN", data);
            } else {
                EventDispatcher.envoyer("PLAYER_JOIN", data);
            }

            // Envoie la version serveur au client pour vérification
            String version = FabricLoader.getInstance()
                .getModContainer(NouvelleTerreBridge.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
            PacketByteBuf versionBuf = PacketByteBufs.create();
            versionBuf.writeString(version);
            ServerPlayNetworking.send(joueur, HdvNetworking.NT_VERSION, versionBuf);
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
