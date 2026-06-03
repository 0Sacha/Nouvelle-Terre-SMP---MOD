package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public class PayerCommand {

    /** Suggère les joueurs en ligne sauf l'expéditeur. */
    private static final SuggestionProvider<ServerCommandSource> JOUEURS_EN_LIGNE =
        (ctx, builder) -> {
            String moi = ctx.getSource().getName();
            ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .stream()
                .map(p -> p.getName().getString())
                .filter(name -> !name.equalsIgnoreCase(moi))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("virer")
                .then(CommandManager.argument("joueur", StringArgumentType.word())
                    .suggests(JOUEURS_EN_LIGNE)
                    .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                        .executes(ctx -> executerPayer(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "joueur"),
                            IntegerArgumentType.getInteger(ctx, "montant")
                        ))))
        );
    }

    private static int executerPayer(ServerCommandSource source, String destinataire, int montant) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Cette commande est réservée aux joueurs."));
            return 0;
        }

        String expediteur = joueur.getName().getString();

        if (expediteur.equalsIgnoreCase(destinataire)) {
            joueur.sendMessage(Text.literal(SoldeCommand.SEP_RED));
            joueur.sendMessage(Text.literal("    §c§l✗ §f§lErreur de virement"));
            joueur.sendMessage(Text.literal("  §7Tu ne peux pas te payer toi-même."));
            joueur.sendMessage(Text.literal(SoldeCommand.SEP_RED));
            return 0;
        }

        LocalEconomy eco = LocalEconomy.getInstance();

        if (!eco.estConnu(destinataire)) {
            joueur.sendMessage(Text.literal(SoldeCommand.SEP_YELLOW));
            joueur.sendMessage(Text.literal("    §e§l⚠ §f§lJoueur introuvable"));
            joueur.sendMessage(Text.literal("  §7Pseudo  §8» §f§l" + destinataire));
            joueur.sendMessage(Text.literal("  §7Ce joueur n'a jamais joué sur le serveur."));
            joueur.sendMessage(Text.literal(SoldeCommand.SEP_YELLOW));
            return 0;
        }

        int solde = eco.getBalance(expediteur);
        if (solde < montant) {
            int manque = montant - solde;
            joueur.sendMessage(Text.literal(SoldeCommand.SEP_RED));
            joueur.sendMessage(Text.literal("    §c§l✗ §f§lSolde insuffisant !"));
            joueur.sendMessage(Text.literal("  §7Tu as    §8» §f§l" + SoldeCommand.fmt(solde)  + " §6💎"));
            joueur.sendMessage(Text.literal("  §7Requis   §8» §f§l" + SoldeCommand.fmt(montant) + " §6💎"));
            joueur.sendMessage(Text.literal("  §7Manque   §8» §c§l-" + SoldeCommand.fmt(manque)  + " §6💎"));
            joueur.sendMessage(Text.literal(SoldeCommand.SEP_RED));
            return 0;
        }

        boolean ok = eco.transfer(expediteur, destinataire, montant);
        if (!ok) {
            joueur.sendMessage(Text.literal("§c❌ Virement impossible (erreur interne)."));
            return 0;
        }

        int soldeFinal = eco.getBalance(expediteur);
        int soldeDest  = eco.getBalance(destinataire);

        // Nom du destinataire cliquable (hover : voir son solde, click : ouvre /payer vers lui)
        MutableText nomDest = Text.literal(destinataire)
            .styled(s -> s
                .withColor(Formatting.WHITE)
                .withBold(true)
                .withHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Text.literal("§7Solde de §f" + destinataire + " : §a§l" + SoldeCommand.fmt(soldeDest) + " §6💎")))
                .withClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND, "/payer " + destinataire + " ")));

        // Reçu expéditeur
        joueur.sendMessage(Text.literal(SoldeCommand.SEP_GREEN));
        joueur.sendMessage(Text.literal("    §a§l✓ §f§lVirement effectué !"));
        joueur.sendMessage(Text.literal("  §7À        §8» ").append(nomDest));
        joueur.sendMessage(Text.literal("  §7Envoyé   §8» §c§l-" + SoldeCommand.fmt(montant)    + " §6💎"));
        joueur.sendMessage(Text.literal("  §7Restant  §8» §f§l"  + SoldeCommand.fmt(soldeFinal) + " §6💎"));
        joueur.sendMessage(Text.literal(SoldeCommand.SEP_GREEN));

        // Notification au destinataire s'il est connecté
        ServerPlayerEntity dest = source.getServer().getPlayerManager().getPlayer(destinataire);
        if (dest != null) {
            MutableText nomExp = Text.literal(expediteur)
                .styled(s -> s
                    .withColor(Formatting.WHITE)
                    .withBold(true)
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text.literal("§7Cliquer pour rembourser §f" + expediteur)))
                    .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND, "/payer " + expediteur + " ")));

            dest.sendMessage(Text.literal(SoldeCommand.SEP_GREEN));
            dest.sendMessage(Text.literal("    §a§l+ §f§lVirement reçu !"));
            dest.sendMessage(Text.literal("  §7De      §8» ").append(nomExp));
            dest.sendMessage(Text.literal("  §7Reçu    §8» §a§l+" + SoldeCommand.fmt(montant)   + " §6💎"));
            dest.sendMessage(Text.literal("  §7Solde   §8» §f§l"  + SoldeCommand.fmt(soldeDest) + " §6💎"));
            dest.sendMessage(Text.literal(SoldeCommand.SEP_GREEN));
        }

        return 1;
    }
}
