package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.client.NotificationHud;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.stream.Collectors;

public class PayCommand {

    private static final SuggestionProvider<ServerCommandSource> JOUEURS_CONNUS =
        (ctx, builder) -> {
            Set<String> online = ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .stream().map(p -> p.getName().getString())
                .collect(Collectors.toSet());
            online.forEach(builder::suggest);
            LocalEconomy.getInstance().getSoldesKeys().stream()
                .filter(k -> online.stream().noneMatch(p -> p.equalsIgnoreCase(k)))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("pay")
                .then(CommandManager.argument("joueur", StringArgumentType.word())
                    .suggests(JOUEURS_CONNUS)
                    .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                        .executes(ctx -> executer(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "joueur"),
                            IntegerArgumentType.getInteger(ctx, "montant")))))
        );
    }

    private static int executer(ServerCommandSource source, String cible, int montant) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs."));
            return 0;
        }

        String sender = joueur.getName().getString();

        if (sender.equalsIgnoreCase(cible)) {
            NouvelleTerreBridge.sendToast(joueur, NotificationHud.COLOR_RED,
                "✗  Virement refusé",
                "Vous ne pouvez pas vous payer vous-même.");
            return 0;
        }

        if (!LocalEconomy.getInstance().estConnu(cible)) {
            NouvelleTerreBridge.sendToast(joueur, NotificationHud.COLOR_RED,
                "✗  Joueur inconnu",
                cible + " n'a jamais joué ici.");
            return 0;
        }

        boolean ok = LocalEconomy.getInstance().transfer(sender, cible, montant);

        if (!ok) {
            int solde = LocalEconomy.getInstance().getBalance(sender);
            NouvelleTerreBridge.sendToast(joueur, NotificationHud.COLOR_RED,
                "✗  Solde insuffisant",
                "Requis : " + EconomieCommand.fmt(montant) + " ◆",
                "Solde  : " + EconomieCommand.fmt(solde) + " ◆");
            return 0;
        }

        // Toast expéditeur
        int nouveauSolde = LocalEconomy.getInstance().getBalance(sender);
        NouvelleTerreBridge.sendToast(joueur, NotificationHud.COLOR_GREEN,
            "✦  Virement envoyé",
            "→ " + cible + "  -" + EconomieCommand.fmt(montant) + " ◆",
            "Solde : " + EconomieCommand.fmt(nouveauSolde) + " ◆");
        NouvelleTerreBridge.sendBalanceToPlayer(joueur);

        // Toast destinataire (si connecté)
        ServerPlayerEntity dest = source.getServer().getPlayerManager().getPlayer(cible);
        if (dest != null) {
            int soldeDest = LocalEconomy.getInstance().getBalance(cible);
            NouvelleTerreBridge.sendToast(dest, NotificationHud.COLOR_GOLD,
                "✦  Virement reçu !",
                "← " + sender + "  +" + EconomieCommand.fmt(montant) + " ◆",
                "Solde : " + EconomieCommand.fmt(soldeDest) + " ◆");
            NouvelleTerreBridge.sendBalanceToPlayer(dest);
        }

        return 1;
    }
}
