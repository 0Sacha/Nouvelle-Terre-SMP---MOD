package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.stream.Collectors;

public class PayCommand {

    private static final String SHARD = "§b◆§r";

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
            joueur.sendMessage(Text.literal(EconomieCommand.SEP_RED));
            joueur.sendMessage(Text.literal("  §c§l✗ §f§lVirement refusé"));
            joueur.sendMessage(Text.literal("  §7Vous ne pouvez pas vous payer vous-même."));
            joueur.sendMessage(Text.literal(EconomieCommand.SEP_RED));
            return 0;
        }

        if (!LocalEconomy.getInstance().estConnu(cible)) {
            joueur.sendMessage(Text.literal(EconomieCommand.SEP_RED));
            joueur.sendMessage(Text.literal("  §c§l✗ §f§lJoueur inconnu"));
            joueur.sendMessage(Text.literal("  §7§o" + cible + " §7n'a jamais joué sur ce serveur."));
            joueur.sendMessage(Text.literal(EconomieCommand.SEP_RED));
            return 0;
        }

        boolean ok = LocalEconomy.getInstance().transfer(sender, cible, montant);

        if (!ok) {
            int solde = LocalEconomy.getInstance().getBalance(sender);
            joueur.sendMessage(Text.literal(EconomieCommand.SEP_RED));
            joueur.sendMessage(Text.literal("  §c§l✗ §f§lSolde insuffisant"));
            joueur.sendMessage(Text.literal("  §7Requis §8» §c§l" + EconomieCommand.fmt(montant) + " " + SHARD));
            joueur.sendMessage(Text.literal("  §7Solde  §8» §f§l" + EconomieCommand.fmt(solde) + " " + SHARD));
            joueur.sendMessage(Text.literal(EconomieCommand.SEP_RED));
            return 0;
        }

        // Feedback expéditeur
        int nouveauSolde = LocalEconomy.getInstance().getBalance(sender);
        joueur.sendMessage(Text.literal(EconomieCommand.SEP_GREEN));
        joueur.sendMessage(Text.literal("    §a§l✦ §f§lVirement envoyé"));
        joueur.sendMessage(Text.literal("  §7Pour    §8» §f§l" + cible));
        joueur.sendMessage(Text.literal("  §7Montant §8» §c§l-" + EconomieCommand.fmt(montant) + " " + SHARD));
        joueur.sendMessage(Text.literal("  §7Solde   §8» §f§l" + EconomieCommand.fmt(nouveauSolde) + " " + SHARD));
        joueur.sendMessage(Text.literal(EconomieCommand.SEP_GREEN));
        NouvelleTerreBridge.sendBalanceToPlayer(joueur);

        // Notification destinataire (si connecté)
        ServerPlayerEntity dest = source.getServer().getPlayerManager().getPlayer(cible);
        if (dest != null) {
            int soldeDest = LocalEconomy.getInstance().getBalance(cible);
            dest.sendMessage(Text.literal(EconomieCommand.SEP_GOLD));
            dest.sendMessage(Text.literal("    §6§l✦ §f§lVirement reçu !"));
            dest.sendMessage(Text.literal("  §7De      §8» §f§l" + sender));
            dest.sendMessage(Text.literal("  §7Montant §8» §a§l+" + EconomieCommand.fmt(montant) + " " + SHARD));
            dest.sendMessage(Text.literal("  §7Solde   §8» §f§l" + EconomieCommand.fmt(soldeDest) + " " + SHARD));
            dest.sendMessage(Text.literal(EconomieCommand.SEP_GOLD));
            NouvelleTerreBridge.sendBalanceToPlayer(dest);
        }

        return 1;
    }
}
