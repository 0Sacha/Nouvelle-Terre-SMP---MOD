package com.nouvelleterrebridge.service;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.ServerShopActions;
import com.nouvelleterrebridge.market.MarketManager;
import com.nouvelleterrebridge.network.ServiceNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Réception des actions LeBonCube et construction des paquets d'état.
 *
 * Toutes les actions sont exécutées dans {@code server.execute} : elles touchent
 * l'économie et le fichier d'annonces, qui ne sont pas faits pour être manipulés
 * depuis le fil réseau.
 */
public final class ServiceNetworkHandler {

    /** Niveau op requis pour /server-admin — le monitoring expose toute l'économie. */
    public static final int NIVEAU_ADMIN = 4;

    private ServiceNetworkHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ServiceNetworking.MARCHE_ACTION,
            (server, player, handler, buf, responseSender) -> {
                // Lecture obligatoirement ici : le buffer est libéré au retour.
                int action = buf.readInt();
                String s1 = buf.readString();
                String s2 = buf.readString();
                String s3 = buf.readString();
                String s4 = buf.readString();
                String s5 = buf.readString();
                int i1 = buf.readInt();
                int i2 = buf.readInt();

                server.execute(() -> {
                    String pseudo = player.getName().getString();
                    ServiceManager m = ServiceManager.getInstance();
                    String err = switch (action) {
                        case ServiceNetworking.ACTION_PUBLIER ->
                            m.publier(pseudo, s1, s2, s3, i1, s4, s5);
                        case ServiceNetworking.ACTION_RETIRER ->
                            m.retirer(pseudo, i1, player.hasPermissionLevel(NIVEAU_ADMIN));
                        case ServiceNetworking.ACTION_COMMANDER -> {
                            String e = m.commander(pseudo, i1);
                            if (e == null) prevenirPrestataire(server, m, i1, pseudo);
                            yield e;
                        }
                        case ServiceNetworking.ACTION_LIVREE -> {
                            String e = m.marquerLivree(pseudo, i1);
                            if (e == null) prevenirSuivi(server, m, i1, pseudo, action);
                            yield e;
                        }
                        case ServiceNetworking.ACTION_VALIDER -> {
                            String e = m.validerClient(pseudo, i1, i2, s1);
                            if (e == null) prevenirSuivi(server, m, i1, pseudo, action);
                            yield e;
                        }
                        case ServiceNetworking.ACTION_ANNULER -> {
                            String e = m.demanderAnnulation(pseudo, i1);
                            if (e == null) prevenirSuivi(server, m, i1, pseudo, action);
                            yield e;
                        }
                        case ServiceNetworking.ACTION_MESSAGE  -> {
                            String e = m.envoyerMessage(pseudo, i1, s1);
                            if (e == null) prevenirMessage(server, m, i1, pseudo);
                            yield e;
                        }
                        default -> "§cAction inconnue.";
                    };

                    String msg = err != null ? err : messageSucces(action);
                    envoyerResultat(player, err == null, msg);
                    NouvelleTerreBridge.sendBalanceToPlayer(player);
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(ServiceNetworking.ADMIN_ACTION,
            (server, player, handler, buf, responseSender) -> {
                int action = buf.readInt();
                int id     = buf.readInt();
                server.execute(() -> {
                    if (!player.hasPermissionLevel(NIVEAU_ADMIN)) {
                        player.sendMessage(Text.literal("§cRéservé aux administrateurs."));
                        return;
                    }
                    ServiceManager m = ServiceManager.getInstance();
                    String err = switch (action) {
                        case ServiceNetworking.ADMIN_REMBOURSER      -> m.arbitrerRembourser(id);
                        case ServiceNetworking.ADMIN_PAYER           -> m.arbitrerPayer(id);
                        case ServiceNetworking.ADMIN_RETIRER_ANNONCE -> m.retirer(player.getName().getString(), id, true);
                        default -> "§cAction inconnue.";
                    };
                    player.sendMessage(Text.literal(err != null ? err : "§a✅ Fait."));
                    ouvrirAdmin(player, server);
                });
            });
    }

    private static String messageSucces(int action) {
        return switch (action) {
            case ServiceNetworking.ACTION_PUBLIER   -> "§a✅ Annonce publiée !";
            case ServiceNetworking.ACTION_RETIRER   -> "§a✅ Annonce retirée.";
            case ServiceNetworking.ACTION_COMMANDER -> "§a✅ Commande passée — acompte versé au prestataire.";
            case ServiceNetworking.ACTION_LIVREE    -> "§a✅ Prestation déclarée terminée, en attente du client.";
            case ServiceNetworking.ACTION_VALIDER   -> "§a✅ Prestation validée — le solde a été versé.";
            case ServiceNetworking.ACTION_ANNULER   -> "§e⚠ Annulation demandée.";
            case ServiceNetworking.ACTION_MESSAGE   -> "§a✅ Message envoyé.";
            default -> "§a✅ Fait.";
        };
    }

    private static void prevenirPrestataire(MinecraftServer server, ServiceManager m,
                                            int annonceId, String client) {
        m.annonce(annonceId).ifPresent(a -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(a.auteur);
            if (p == null) return;
            NouvelleTerreBridge.sendToast(p, NouvelleTerreBridge.TOAST_OR,
                "✦  Nouvelle commande !", client + " a commandé", a.titre);
            p.sendMessage(Text.literal("§6[LeBonCube] §f" + client
                + " §avous a commandé §f" + a.titre + " §a— voir §e/leboncube"));
            rafraichir(p);
        });
    }

    /**
     * Prévient l'autre partie d'une commande.
     *
     * Toast <b>et</b> message chat : un toast dure quelques secondes et sa zone
     * peut être déplacée ou masquée dans l'éditeur HUD, donc il ne suffit pas à
     * lui seul à garantir qu'un message reçu soit vu.
     *
     * Le rafraîchissement est indispensable : sans lui, un joueur qui a /leboncube
     * ouvert ne verrait rien arriver tant qu'il ne cliquerait pas quelque part.
     */
    private static void prevenirAutrePartie(MinecraftServer server, ServiceCommande c,
                                            String auteur, int couleur,
                                            String titreToast, String ligneToast,
                                            String messageChat) {
        String destinataire = c.client.equalsIgnoreCase(auteur) ? c.prestataire : c.client;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(destinataire);
        if (p == null) return;   // hors ligne : il retrouvera tout dans /leboncube
        NouvelleTerreBridge.sendToast(p, couleur, titreToast, ligneToast, "Voir /leboncube");
        p.sendMessage(Text.literal(messageChat));
        rafraichir(p);
    }

    private static void prevenirMessage(MinecraftServer server, ServiceManager m,
                                        int commandeId, String auteur) {
        m.commande(commandeId).ifPresent(c -> prevenirAutrePartie(server, c, auteur,
            NouvelleTerreBridge.TOAST_VERT,
            "✉  Nouveau message", auteur + " · " + c.titre,
            "§6[LeBonCube] §b✉ §f" + auteur + " §avous a écrit à propos de §f"
                + c.titre + " §a— §e/leboncube"));
    }

    /** Suivi d'une commande : livraison, validation, demande d'annulation. */
    private static void prevenirSuivi(MinecraftServer server, ServiceManager m,
                                      int commandeId, String auteur, int action) {
        m.commande(commandeId).ifPresent(c -> {
            switch (action) {
                case ServiceNetworking.ACTION_LIVREE -> prevenirAutrePartie(server, c, auteur,
                    NouvelleTerreBridge.TOAST_OR,
                    "📦  Prestation terminée", c.titre,
                    "§6[LeBonCube] §f" + auteur + " §aa terminé §f" + c.titre
                        + " §a— validez pour libérer le solde : §e/leboncube");
                case ServiceNetworking.ACTION_VALIDER -> prevenirAutrePartie(server, c, auteur,
                    NouvelleTerreBridge.TOAST_VERT,
                    "✅  Prestation validée", c.titre,
                    "§6[LeBonCube] §f" + auteur + " §aa validé §f" + c.titre
                        + " §a— le solde vous a été versé.");
                // Le second à demander l'annulation la déclenche : le message doit
                // dire ce qui s'est réellement passé, pas relancer une demande.
                case ServiceNetworking.ACTION_ANNULER -> {
                    if (ServiceCommande.ANNULEE.equals(c.statut))
                        prevenirAutrePartie(server, c, auteur, NouvelleTerreBridge.TOAST_ROUGE,
                            "✖  Commande annulée", c.titre,
                            "§6[LeBonCube] §f" + auteur + " §ca accepté l'annulation de §f"
                                + c.titre + " §c— le client a été intégralement remboursé.");
                    else
                        prevenirAutrePartie(server, c, auteur, NouvelleTerreBridge.TOAST_ROUGE,
                            "⚠  Annulation demandée", c.titre,
                            "§6[LeBonCube] §f" + auteur + " §cdemande l'annulation de §f"
                                + c.titre + " §c— §e/leboncube");
                }
                default -> { }
            }
        });
    }

    // ── Paquets d'état ────────────────────────────────────────────────────────

    /** Ouvre LeBonCube chez le joueur (/leboncube, hub du Parchemin). */
    public static void ouvrir(ServerPlayerEntity player) {
        envoyerOuverture(player, true);
    }

    /**
     * Met à jour l'écran d'un joueur <b>sans le lui ouvrir</b>.
     *
     * Sert à prévenir l'autre partie d'une commande en direct. Sans ce drapeau,
     * recevoir un message ferait surgir LeBonCube par-dessus le jeu — exactement
     * le défaut qu'avaient les quêtes avant la 1.4.2.
     */
    public static void rafraichir(ServerPlayerEntity player) {
        envoyerOuverture(player, false);
    }

    /**
     * Envoie l'état sur MARCHE_OPEN, précédé du drapeau d'ouverture.
     *
     * ⚠ Le drapeau est écrit <b>systématiquement</b>, quelle que soit sa valeur :
     * le client en lit toujours un. L'avoir rendu conditionnel décalait tout le
     * paquet d'un octet à chaque rafraîchissement, ce qui déconnectait les deux
     * joueurs à la moindre notification.
     */
    private static void envoyerOuverture(ServerPlayerEntity player, boolean ouvrir) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(ouvrir);
        ecrireEtat(buf, player);
        ServerPlayNetworking.send(player, ServiceNetworking.MARCHE_OPEN, buf);
    }

    private static void envoyerResultat(ServerPlayerEntity player, boolean ok, String msg) {
        // MARCHE_RESULT a son propre en-tête (ok + message) et ne porte pas de
        // drapeau d'ouverture : l'écran est forcément déjà ouvert.
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(ok);
        buf.writeString(msg);
        ecrireEtat(buf, player);
        ServerPlayNetworking.send(player, ServiceNetworking.MARCHE_RESULT, buf);
    }

    /** Corps commun aux deux canaux : solde, catégories, annonces, commandes. */
    private static void ecrireEtat(PacketByteBuf buf, ServerPlayerEntity player) {
        String pseudo = player.getName().getString();
        ServiceManager m = ServiceManager.getInstance();

        buf.writeInt(LocalEconomy.getInstance().getBalance(pseudo));

        // Catégories existantes : ce sont les joueurs qui les créent, le client a
        // besoin de la liste pour proposer celles déjà utilisées.
        List<String> cats = m.categories();
        buf.writeInt(cats.size());
        for (String c : cats) buf.writeString(c);

        List<ServiceAnnonce> annonces = m.annoncesActives();
        buf.writeInt(annonces.size());
        for (ServiceAnnonce a : annonces) {
            buf.writeInt(a.id);
            buf.writeString(a.auteur);
            buf.writeString(a.titre);
            buf.writeString(a.description);
            buf.writeString(a.imageUrl == null ? "" : a.imageUrl);
            buf.writeInt(a.prix);
            buf.writeString(a.contact);
            buf.writeString(a.categorie);
            buf.writeLong(a.creeLe);
            buf.writeFloat(m.noteMoyenne(a.auteur));
            buf.writeInt(m.notesDe(a.auteur).size());
        }

        ecrireCommandes(buf, m.prestationsDe(pseudo));
        ecrireCommandes(buf, m.commandesDe(pseudo));
        ecrireCommandes(buf, m.archivesDe(pseudo));
    }

    private static void ecrireCommandes(PacketByteBuf buf, List<ServiceCommande> list) {
        buf.writeInt(list.size());
        for (ServiceCommande c : list) {
            buf.writeInt(c.id);
            buf.writeString(c.titre);
            buf.writeString(c.client);
            buf.writeString(c.prestataire);
            buf.writeInt(c.prix);
            buf.writeInt(c.acompte);
            buf.writeInt(c.sequestre);
            buf.writeString(c.statut);
            buf.writeBoolean(c.valideParPrestataire);
            buf.writeBoolean(c.valideParClient);
            buf.writeString(c.annulationDemandeePar == null ? "" : c.annulationDemandeePar);
            buf.writeLong(c.creeLe);
            buf.writeInt(c.note);
            buf.writeString(c.avis == null ? "" : c.avis);
            buf.writeInt(c.messages.size());
            for (ServiceCommande.Message msg : c.messages) {
                buf.writeString(msg.auteur);
                buf.writeString(msg.texte);
                buf.writeLong(msg.envoyeLe);
            }
        }
    }

    // ── /server-admin ─────────────────────────────────────────────────────────

    public static void ouvrirAdmin(ServerPlayerEntity player, MinecraftServer server) {
        LocalEconomy eco = LocalEconomy.getInstance();
        ServiceManager m = ServiceManager.getInstance();
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(eco.getBalance(ServerShopActions.COMPTE_SERVEUR));
        buf.writeInt(eco.getBalance(ServiceManager.COMPTE_SEQUESTRE));
        buf.writeLong(eco.masseMonetaire());
        buf.writeInt(eco.nombreJoueursConnus());
        buf.writeInt(eco.soldeMedian());
        buf.writeInt(server.getPlayerManager().getCurrentPlayerCount());
        buf.writeInt(MarketManager.getInstance().getAll().size());
        buf.writeInt(m.annoncesActives().size());
        buf.writeDouble(com.nouvelleterrebridge.economy.ServerShopPriceManager.multiplicateurInflation());

        // Litiges : commandes en cours dont une partie a demandé l'annulation
        List<ServiceCommande> litiges = m.toutesCommandes().stream()
            .filter(c -> !c.estArchivee() && c.annulationDemandeePar != null)
            .toList();
        buf.writeInt(litiges.size());
        for (ServiceCommande c : litiges) {
            buf.writeInt(c.id);
            buf.writeString(c.titre);
            buf.writeString(c.client);
            buf.writeString(c.prestataire);
            buf.writeInt(c.prix);
            buf.writeInt(c.sequestre);
            buf.writeString(c.annulationDemandeePar);
        }

        ServerPlayNetworking.send(player, ServiceNetworking.ADMIN_OPEN, buf);
    }
}
