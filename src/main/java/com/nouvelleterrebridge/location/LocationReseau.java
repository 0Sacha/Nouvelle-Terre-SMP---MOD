package com.nouvelleterrebridge.location;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.network.LocationNetworking;
import com.nouvelleterrebridge.network.NtNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Côté serveur de l'écran Location : ouverture, actions, commande {@code /location}. */
public final class LocationReseau {

    private LocationReseau() {}

    public static void register() {
        LocationManager.get().charger();
        LocationVerrou.register();

        NtNet.surServeur(LocationNetworking.LOC_ACTION, (server, player, buf) -> {
            int action  = buf.readInt();
            int id      = buf.readInt();
            int prix    = buf.readInt();
            int caution = buf.readInt();
            // Thread serveur : toutes les actions touchent l'inventaire du joueur
            server.execute(() -> {
                LocationManager m = LocationManager.get();
                String err;
                String succes;
                switch (action) {
                    case LocationNetworking.ACTION_PROPOSER -> {
                        err = m.proposer(player, id, prix, caution);
                        succes = "§a✅ Objet proposé à la location.";
                    }
                    case LocationNetworking.ACTION_RETIRER -> {
                        err = m.retirerOffre(player, id);
                        succes = "§a✅ Offre retirée — objet récupéré.";
                    }
                    case LocationNetworking.ACTION_LOUER -> {
                        err = m.louer(player, id);
                        succes = "§a✅ Objet loué — le loyer sera prélevé chaque jour.";
                    }
                    case LocationNetworking.ACTION_RENDRE -> {
                        err = m.rendre(player, id);
                        succes = "§a✅ Objet rendu.";
                    }
                    case LocationNetworking.ACTION_PERDU -> {
                        err = m.declarerPerdu(player, id);
                        succes = "§eObjet déclaré perdu — la caution revient au propriétaire.";
                    }
                    case LocationNetworking.ACTION_RESILIER -> {
                        err = m.resilier(player, id);
                        succes = "§a✅ Contrat résilié — l'objet te reviendra à la fin du jour payé.";
                    }
                    case LocationNetworking.ACTION_RECUPERER -> {
                        err = m.recuperer(player, id);
                        succes = "§a✅ Objet récupéré.";
                    }
                    default -> { err = "§cAction inconnue."; succes = ""; }
                }
                FriendlyByteBuf resp = NtNet.buffer();
                resp.writeBoolean(err == null);
                resp.writeUtf(err == null ? succes : err);
                ecrireEtat(resp, player);
                NtNet.versClient(player, LocationNetworking.LOC_RESULT, resp);
                NouvelleTerreBridge.sendBalanceToPlayer(player);
            });
        });
    }

    public static void commande(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("location").executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer joueur)) {
                ctx.getSource().sendFailure(Component.literal("Commande réservée aux joueurs."));
                return 0;
            }
            ouvrir(joueur);
            return 1;
        }));
    }

    public static void ouvrir(ServerPlayer joueur) {
        FriendlyByteBuf buf = NtNet.buffer();
        ecrireEtat(buf, joueur);
        NtNet.versClient(joueur, LocationNetworking.LOC_OPEN, buf);
    }

    private static void ecrireEtat(FriendlyByteBuf buf, ServerPlayer joueur) {
        String pseudo = joueur.getName().getString();
        LocationManager m = LocationManager.get();
        long maintenant = System.currentTimeMillis();

        buf.writeInt(LocalEconomy.getInstance().getBalance(pseudo));

        List<LocationManager.Offre> offres = m.offres();
        buf.writeInt(offres.size());
        for (LocationManager.Offre o : offres) {
            buf.writeInt(o.id);
            buf.writeUtf(o.proprietaire);
            buf.writeUtf(o.item);
            buf.writeUtf(o.nbt == null ? "" : o.nbt);
            buf.writeInt(o.prixJour);
            buf.writeInt(o.caution);
            buf.writeBoolean(o.proprietaire.equalsIgnoreCase(pseudo));
        }

        List<LocationManager.Contrat> contrats = m.contratsDe(pseudo);
        buf.writeInt(contrats.size());
        for (LocationManager.Contrat c : contrats) {
            long reste = c.bloqueDepuis > 0
                ? c.bloqueDepuis + LocationManager.GRACE_MS - maintenant
                : c.echeance - maintenant;
            buf.writeInt(c.id);
            buf.writeUtf(c.proprietaire);
            buf.writeUtf(c.locataire);
            buf.writeUtf(c.item);
            buf.writeInt(c.prixJour);
            buf.writeInt(c.caution);
            buf.writeLong(Math.max(0, reste));
            buf.writeInt(c.etat());
            buf.writeBoolean(c.resilie);
            buf.writeBoolean(c.locataire.equalsIgnoreCase(pseudo));
        }

        List<LocationManager.Retour> retours = m.retoursDe(pseudo);
        buf.writeInt(retours.size());
        for (LocationManager.Retour r : retours) {
            buf.writeInt(r.id);
            buf.writeUtf(r.item);
            buf.writeUtf(r.nbt == null ? "" : r.nbt);
            buf.writeUtf(r.origine == null ? "" : r.origine);
        }
    }
}
