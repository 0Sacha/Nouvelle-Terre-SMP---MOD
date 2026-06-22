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
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Écouteurs pour les événements liés aux joueurs (connexion, déconnexion).
 * La mort est gérée par LivingEntityMixin.
 *
 * Règles :
 * – Toujours envoyer PLAYER_JOIN (pas de PLAYER_FIRST_JOIN). Le bot fait un UPDATE,
 *   jamais un INSERT : l'entrée joueurs existe déjà depuis la confirmation du personnage.
 * – premiere_mc=true uniquement si c'est la première vraie connexion MC (FirstJoinTracker).
 * – La whitelist est gérée exclusivement par le bot via RCON ; le mod ne l'effleure pas.
 */
public class PlayerEvents {

    // Cache uuid → nom RP pour l'afficher au départ sans refaire une requête HTTP
    private static final Map<String, String> nomsRP = new ConcurrentHashMap<>();

    public static void register() {
        if (!NouvelleTerreBridge.config.isActiverEvenementJoueur()) return;

        // ── Connexion ────────────────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joueur = handler.getPlayer();
            String pseudo = joueur.getName().getString();
            String uuid   = joueur.getUuidAsString();

            // 500 ◆ de départ uniquement à la première vraie connexion MC
            boolean premiereFois = !FirstJoinTracker.getInstance().hasReceived(pseudo);
            if (premiereFois) {
                NouvelleTerreBridge.LOGGER.info("[PlayerEvents] Première connexion MC de {}", pseudo);
                LocalEconomy.getInstance().addShards(pseudo, 500);
                FirstJoinTracker.getInstance().markReceived(pseudo);
                joueur.sendMessage(Text.literal(
                    "§6[Nouvelle Terre] §f✨ Bienvenue ! Tu reçois §e§l500 ◆ §fde départ. Bonne aventure !"));
            }

            // Envoi de l'événement — le bot fait UPDATE joueurs SET en_ligne=true,
            // derniere_connexion=NOW(), shards=? [, premiere_connexion=NOW() si encore nulle]
            Map<String, Object> data = new HashMap<>();
            data.put("player",       pseudo);
            data.put("uuid",         uuid);
            data.put("premiere_mc",  premiereFois);
            data.put("balance",      LocalEconomy.getInstance().getBalance(pseudo));
            EventDispatcher.envoyer("PLAYER_JOIN", data);

            // Envoi de la version mod au client
            String version = FabricLoader.getInstance()
                .getModContainer(NouvelleTerreBridge.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
            PacketByteBuf versionBuf = PacketByteBufs.create();
            versionBuf.writeString(version);
            ServerPlayNetworking.send(joueur, HdvNetworking.NT_VERSION, versionBuf);

            // Récupération du nom RP → broadcast dans le chat serveur
            EventDispatcher.fetchNomRP(uuid, server, nomRP -> {
                nomsRP.put(uuid, nomRP);
                server.getPlayerManager().broadcast(
                    Text.literal("§8[RP] §f" + nomRP + " §8(§7" + pseudo + "§8) §7est arrivé sur le serveur."),
                    false);
            });
        });

        // ── Déconnexion ──────────────────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity joueur = handler.getPlayer();
            String pseudo = joueur.getName().getString();
            String uuid   = joueur.getUuidAsString();

            PlaytimeTracker.onPlayerLeave(joueur.getUuid());

            // Affiche le nom RP dans le chat si disponible, puis libère le cache
            String nomRP = nomsRP.remove(uuid);
            if (nomRP != null) {
                server.getPlayerManager().broadcast(
                    Text.literal("§8[RP] §f" + nomRP + " §8(§7" + pseudo + "§8) §7a quitté le serveur."),
                    false);
            }

            // Événement pour le bot : UPDATE joueurs SET en_ligne=false,
            // derniere_connexion=NOW() WHERE uuid=?
            Map<String, Object> data = new HashMap<>();
            data.put("player", pseudo);
            data.put("uuid",   uuid);
            if (nomRP != null) data.put("nom_rp", nomRP);
            EventDispatcher.envoyer("PLAYER_LEAVE", data);
        });
    }
}
