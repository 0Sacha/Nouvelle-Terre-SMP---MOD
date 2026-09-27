package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.stream.Collectors;

public class PayCommand {

    private static final SuggestionProvider<CommandSourceStack> JOUEURS_CONNUS =
        (ctx, builder) -> {
            Set<String> online = ctx.getSource().getServer().getPlayerList().getPlayers()
                .stream().map(p -> p.getName().getString())
                .collect(Collectors.toSet());
            online.forEach(builder::suggest);
            LocalEconomy.getInstance().getSoldesKeys().stream()
                // Les comptes système ($Serveur, $Sequestre) ne sont pas des joueurs :
                // les proposer laissait croire qu'on pouvait leur virer des shards.
                .filter(k -> !k.startsWith("$"))
                .filter(k -> online.stream().noneMatch(p -> p.equalsIgnoreCase(k)))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pay")
                .then(Commands.argument("joueur", StringArgumentType.word())
                    .suggests(JOUEURS_CONNUS)
                    .then(Commands.argument("montant", IntegerArgumentType.integer(1))
                        .executes(ctx -> executer(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "joueur"),
                            IntegerArgumentType.getInteger(ctx, "montant")))))
        );
    }

    private static int executer(CommandSourceStack source, String cible, int montant) {
        if (!(source.getEntity() instanceof ServerPlayer joueur)) {
            source.sendFailure(Component.literal("Commande réservée aux joueurs."));
            return 0;
        }

        String sender = joueur.getName().getString();

        if (sender.equalsIgnoreCase(cible)) {
            NouvelleTerreBridge.sendToast(joueur, NouvelleTerreBridge.TOAST_ROUGE,
                "✗  Virement refusé",
                "Vous ne pouvez pas vous payer vous-même.");
            return 0;
        }

        if (cible.startsWith("$")) {
            NouvelleTerreBridge.sendToast(joueur, NouvelleTerreBridge.TOAST_ROUGE,
                "✗  Destinataire invalide",
                cible + " est un compte système.");
            return 0;
        }

        if (!LocalEconomy.getInstance().estConnu(cible)) {
            NouvelleTerreBridge.sendToast(joueur, NouvelleTerreBridge.TOAST_ROUGE,
                "✗  Joueur inconnu",
                cible + " n'a jamais joué ici.");
            return 0;
        }

        boolean ok = LocalEconomy.getInstance().transfer(sender, cible, montant);

        if (!ok) {
            int solde = LocalEconomy.getInstance().getBalance(sender);
            NouvelleTerreBridge.sendToast(joueur, NouvelleTerreBridge.TOAST_ROUGE,
                "✗  Solde insuffisant",
                "Requis : " + EconomieCommand.fmt(montant) + " ◆",
                "Solde  : " + EconomieCommand.fmt(solde) + " ◆");
            return 0;
        }

        // Toast expéditeur
        int nouveauSolde = LocalEconomy.getInstance().getBalance(sender);
        NouvelleTerreBridge.sendToast(joueur, NouvelleTerreBridge.TOAST_VERT,
            "✦  Virement envoyé",
            "→ " + cible + "  -" + EconomieCommand.fmt(montant) + " ◆",
            "Solde : " + EconomieCommand.fmt(nouveauSolde) + " ◆");
        NouvelleTerreBridge.sendBalanceToPlayer(joueur);

        // Notification destinataire (si connecté) — toast ET message chat.
        // Le toast seul passait inaperçu : il s'efface au bout de quelques secondes
        // et le joueur peut avoir masqué la zone de notification dans l'éditeur HUD.
        ServerPlayer dest = source.getServer().getPlayerList().getPlayerByName(cible);
        if (dest != null) {
            int soldeDest = LocalEconomy.getInstance().getBalance(cible);
            NouvelleTerreBridge.sendToast(dest, NouvelleTerreBridge.TOAST_OR,
                "✦  Virement reçu !",
                "← " + sender + "  +" + EconomieCommand.fmt(montant) + " ◆",
                "Solde : " + EconomieCommand.fmt(soldeDest) + " ◆");
            dest.sendSystemMessage(Component.literal(
                "§6[Nouvelle Terre] §f" + sender + " §avous a envoyé §e"
                + EconomieCommand.fmt(montant) + " ◆§a — solde : §e"
                + EconomieCommand.fmt(soldeDest) + " ◆"));
            NouvelleTerreBridge.sendBalanceToPlayer(dest);
        }

        return 1;
    }
}
