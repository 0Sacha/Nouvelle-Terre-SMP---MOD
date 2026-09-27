package com.nouvelleterrebridge.service;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.ServerShopActions;
import com.nouvelleterrebridge.market.MarketManager;
import com.nouvelleterrebridge.network.ServiceNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

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
        NtNet.surServeur(ServiceNetworking.MARCHE_ACTION, (server, player, buf) -> {
                // Lecture obligatoirement ici : le buffer est libéré au retour.
                int action = buf.readInt();
                String s1 = buf.readUtf();
                String s2 = buf.readUtf();
                String s3 = buf.readUtf();
                String s4 = buf.readUtf();
                String s5 = buf.readUtf();
                int i1 = buf.readInt();
                int i2 = buf.readInt();

                server.execute(() -> {
                    String pseudo = player.getName().getString();
                    ServiceManager m = ServiceManager.getInstance();
                    String err = switch (action) {
                        case ServiceNetworking.ACTION_PUBLIER ->
                            m.publier(pseudo, s1, s2, s3, i1, s4, s5);
                        case ServiceNetworking.ACTION_RETIRER ->
                            m.retirer(pseudo, i1, player.hasPermissions(NIVEAU_ADMIN));
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

        NtNet.surServeur(ServiceNetworking.ADMIN_ACTION, (server, player, buf) -> {
                int action = buf.readInt();
                int id     = buf.readInt();
                server.execute(() -> {
                    if (!player.hasPermissions(NIVEAU_ADMIN)) {
                        player.sendSystemMessage(Component.literal("§cRéservé aux administrateurs."));
                        return;
                    }
                    ServiceManager m = ServiceManager.getInstance();
                    String err = switch (action) {
                        case ServiceNetworking.ADMIN_REMBOURSER      -> m.arbitrerRembourser(id);
                        case ServiceNetworking.ADMIN_PAYER           -> m.arbitrerPayer(id);
                        case ServiceNetworking.ADMIN_RETIRER_ANNONCE -> m.retirer(player.getName().getString(), id, true);
                        default -> "§cAction inconnue.";
                    };
                    player.sendSystemMessage(Component.literal(err != null ? err : "§a✅ Fait."));
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
            ServerPlayer p = server.getPlayerList().getPlayerByName(a.auteur);
            if (p == null) return;
            NouvelleTerreBridge.sendToast(p, NouvelleTerreBridge.TOAST_OR,
                "✦  Nouvelle commande !", client + " a commandé", a.titre);
            p.sendSystemMessage(Component.literal("§6[LeBonCube] §f" + client
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
        ServerPlayer p = server.getPlayerList().getPlayerByName(destinataire);
        if (p == null) return;   // hors ligne : il retrouvera tout dans /leboncube
        NouvelleTerreBridge.sendToast(p, couleur, titreToast, ligneToast, "Voir /leboncube");
        p.sendSystemMessage(Component.literal(messageChat));
        rafraichir(p);
    }

    private static void prevenirMessage(MinecraftServer server, ServiceManager m,
                                        int commandeId, String auteur) {
        m.commande(commandeId).ifPresent(c -> {
            // Aperçu du message : savoir qu'on a reçu quelque chose sans savoir
            // quoi oblige à ouvrir l'écran pour rien.
            String dernier = c.messages.isEmpty() ? ""
                : c.messages.get(c.messages.size() - 1).texte;
            prevenirAutrePartie(server, c, auteur,
                NouvelleTerreBridge.TOAST_VERT,
                "✉  " + auteur, apercu(dernier, 38),
                "§6[LeBonCube] §b✉ §f" + auteur + " §7(" + c.titre + ") §f: "
                    + apercu(dernier, 120) + " §8— §e/leboncube");
        });
    }

    /** Tronque un message pour l'afficher en aperçu. */
    private static String apercu(String texte, int max) {
        if (texte == null || texte.isBlank()) return "…";
        String t = texte.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
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
    public static void ouvrir(ServerPlayer player) {
        envoyerOuverture(player, true);
    }

    /**
     * Met à jour l'écran d'un joueur <b>sans le lui ouvrir</b>.
     *
     * Sert à prévenir l'autre partie d'une commande en direct. Sans ce drapeau,
     * recevoir un message ferait surgir LeBonCube par-dessus le jeu — exactement
     * le défaut qu'avaient les quêtes avant la 1.4.2.
     */
    public static void rafraichir(ServerPlayer player) {
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
    private static void envoyerOuverture(ServerPlayer player, boolean ouvrir) {
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeBoolean(ouvrir);
        ecrireEtat(buf, player);
        NtNet.versClient(player, ServiceNetworking.MARCHE_OPEN, buf);
    }

    private static void envoyerResultat(ServerPlayer player, boolean ok, String msg) {
        // MARCHE_RESULT a son propre en-tête (ok + message) et ne porte pas de
        // drapeau d'ouverture : l'écran est forcément déjà ouvert.
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeBoolean(ok);
        buf.writeUtf(msg);
        ecrireEtat(buf, player);
        NtNet.versClient(player, ServiceNetworking.MARCHE_RESULT, buf);
    }

    /** Corps commun aux deux canaux : solde, catégories, annonces, commandes. */
    private static void ecrireEtat(FriendlyByteBuf buf, ServerPlayer player) {
        String pseudo = player.getName().getString();
        ServiceManager m = ServiceManager.getInstance();

        buf.writeInt(LocalEconomy.getInstance().getBalance(pseudo));

        // Catégories existantes : ce sont les joueurs qui les créent, le client a
        // besoin de la liste pour proposer celles déjà utilisées.
        List<String> cats = m.categories();
        buf.writeInt(cats.size());
        for (String c : cats) buf.writeUtf(c);

        List<ServiceAnnonce> annonces = m.annoncesActives();
        buf.writeInt(annonces.size());
        for (ServiceAnnonce a : annonces) {
            buf.writeInt(a.id);
            buf.writeUtf(a.auteur);
            buf.writeUtf(a.titre);
            buf.writeUtf(a.description);
            buf.writeUtf(a.imageUrl == null ? "" : a.imageUrl);
            buf.writeInt(a.prix);
            buf.writeUtf(a.contact);
            buf.writeUtf(a.categorie);
            buf.writeLong(a.creeLe);
            buf.writeFloat(m.noteMoyenne(a.auteur));
            buf.writeInt(m.notesDe(a.auteur).size());
        }

        ecrireCommandes(buf, m.prestationsDe(pseudo));
        ecrireCommandes(buf, m.commandesDe(pseudo));
        ecrireCommandes(buf, m.archivesDe(pseudo));
    }

    private static void ecrireCommandes(FriendlyByteBuf buf, List<ServiceCommande> list) {
        buf.writeInt(list.size());
        for (ServiceCommande c : list) {
            buf.writeInt(c.id);
            buf.writeUtf(c.titre);
            buf.writeUtf(c.client);
            buf.writeUtf(c.prestataire);
            buf.writeInt(c.prix);
            buf.writeInt(c.acompte);
            buf.writeInt(c.sequestre);
            buf.writeUtf(c.statut);
            buf.writeBoolean(c.valideParPrestataire);
            buf.writeBoolean(c.valideParClient);
            buf.writeUtf(c.annulationDemandeePar == null ? "" : c.annulationDemandeePar);
            buf.writeLong(c.creeLe);
            buf.writeLong(c.termineeLe);
            buf.writeInt(c.note);
            buf.writeUtf(c.avis == null ? "" : c.avis);
            buf.writeInt(c.messages.size());
            for (ServiceCommande.Message msg : c.messages) {
                buf.writeUtf(msg.auteur);
                buf.writeUtf(msg.texte);
                buf.writeLong(msg.envoyeLe);
            }
        }
    }

    // ── /server-admin ─────────────────────────────────────────────────────────

    public static void ouvrirAdmin(ServerPlayer player, MinecraftServer server) {
        LocalEconomy eco = LocalEconomy.getInstance();
        ServiceManager m = ServiceManager.getInstance();
        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();

        buf.writeInt(eco.getBalance(ServerShopActions.COMPTE_SERVEUR));
        buf.writeInt(eco.getBalance(ServiceManager.COMPTE_SEQUESTRE));
        buf.writeLong(eco.masseMonetaire());
        buf.writeInt(eco.nombreJoueursConnus());
        buf.writeInt(eco.soldeMedian());
        buf.writeInt(server.getPlayerList().getPlayerCount());
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
            buf.writeUtf(c.titre);
            buf.writeUtf(c.client);
            buf.writeUtf(c.prestataire);
            buf.writeInt(c.prix);
            buf.writeInt(c.sequestre);
            buf.writeUtf(c.annulationDemandeePar);
        }

        NtNet.versClient(player, ServiceNetworking.ADMIN_OPEN, buf);
    }
}
