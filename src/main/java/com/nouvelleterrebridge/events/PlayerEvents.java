package com.nouvelleterrebridge.events;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.FirstJoinTracker;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.network.HdvNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    public static void register() {
        if (!NouvelleTerreBridge.config.isActiverEvenementJoueur()) return;

        // Chat RP : remplace <YelloX605> par <Colt Trekker> si le nom RP est connu
        NeoForge.EVENT_BUS.addListener((ServerChatEvent event) -> {
            String nomRP = NouvelleTerreBridge.nomsRP.get(event.getPlayer().getStringUUID());
            if (nomRP == null) return;
            event.setMessage(Component.literal("§8<§f" + nomRP + "§8> §f" + event.getRawText()));
        });

        // ── Connexion ────────────────────────────────────────────────────────────
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
            var server = joueur.getServer();
            String pseudo = joueur.getName().getString();
            String uuid   = joueur.getStringUUID();

            // 500 ◆ de départ uniquement à la première vraie connexion MC
            boolean premiereFois = !FirstJoinTracker.getInstance().hasReceived(pseudo);
            if (premiereFois) {
                NouvelleTerreBridge.LOGGER.info("[PlayerEvents] Première connexion MC de {}", pseudo);
                LocalEconomy.getInstance().addShards(pseudo, 500, "Pécule de départ");
                FirstJoinTracker.getInstance().markReceived(pseudo);
                joueur.sendSystemMessage(Component.literal(
                    "§6[Nouvelle Terre] §f✨ Bienvenue ! Tu reçois §e§l500 ◆ §fde départ. Bonne aventure !"));
            }

            // Bonus quotidien : +25 ◆ créés à la première connexion de chaque jour réel
            if (!premiereFois && com.nouvelleterrebridge.economy.DailyBonusTracker.claimToday(pseudo)) {
                LocalEconomy.getInstance().addShards(pseudo, com.nouvelleterrebridge.economy.DailyBonusTracker.BONUS,
                    "Bonus quotidien de connexion");
                joueur.sendSystemMessage(Component.literal(
                    "§6[Banque] §fBonus quotidien de connexion : §a+"
                    + com.nouvelleterrebridge.economy.DailyBonusTracker.BONUS + " ◆"));
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
            String version = ModList.get()
                .getModContainerById(NouvelleTerreBridge.NEOFORGE_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
            FriendlyByteBuf versionBuf = com.nouvelleterrebridge.network.NtNet.buffer();
            versionBuf.writeUtf(version);
            NtNet.versClient(joueur, HdvNetworking.NT_VERSION, versionBuf);

            // Envoyer les noms RP des joueurs déjà en ligne au client qui rejoint
            for (Map.Entry<String, String> e : NouvelleTerreBridge.nomsRP.entrySet()) {
                try {
                    FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
                    buf.writeUUID(UUID.fromString(e.getKey()));
                    buf.writeUtf(e.getValue());
                    NtNet.versClient(joueur, HdvNetworking.NT_NOM_RP, buf);
                } catch (IllegalArgumentException ignored) {}
            }

            // Récupération du nom RP → cache + scoreboard team + broadcast clients
            EventDispatcher.fetchNomRP(uuid, server, nomRP -> {
                NouvelleTerreBridge.nomsRP.put(uuid, nomRP);

                // Paquet dédié → cache client → mixin Entity.getDisplayName (nameplate)
                UUID uuidObj = UUID.fromString(uuid);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
                    buf.writeUUID(uuidObj);
                    buf.writeUtf(nomRP);
                    NtNet.versClient(p, HdvNetworking.NT_NOM_RP, buf);
                }

                // Scoreboard team → tab list : "§fNomRP §8(§7pseudo§8)"
                // PlayerListEntry.displayName doit rester null pour que Minecraft utilise le team prefix/suffix
                var scoreboard = server.getScoreboard();
                var teamName   = "nt_" + uuid.replace("-", "").substring(0, 8);
                var oldTeam    = scoreboard.getPlayerTeam(teamName);
                if (oldTeam != null) scoreboard.removePlayerTeam(oldTeam);
                var team = scoreboard.addPlayerTeam(teamName);
                team.setPlayerPrefix(Component.literal("§f" + nomRP + " §8(§7"));
                team.setPlayerSuffix(Component.literal("§8)"));
                scoreboard.addPlayerToTeam(pseudo, team);

                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§8[RP] §f" + nomRP + " §8(§7" + pseudo + "§8) §7est arrivé sur le serveur."),
                    false);
            });
        });

        // ── Déconnexion ──────────────────────────────────────────────────────────
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
            var server = joueur.getServer();
            String pseudo = joueur.getName().getString();
            String uuid   = joueur.getStringUUID();

            PlaytimeTracker.onPlayerLeave(joueur.getUUID());

            // Nettoyage scoreboard team
            var scoreboard = server.getScoreboard();
            var teamName   = "nt_" + uuid.replace("-", "").substring(0, 8);
            var team       = scoreboard.getPlayerTeam(teamName);
            if (team != null) scoreboard.removePlayerTeam(team);

            String nomRP = NouvelleTerreBridge.nomsRP.remove(uuid);
            if (nomRP != null) {
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§8[RP] §f" + nomRP + " §8(§7" + pseudo + "§8) §7a quitté le serveur."),
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
