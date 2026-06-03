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

public class ShardsCommand {

    /** Suggère tous les joueurs en ligne (l'admin peut cibler n'importe qui). */
    private static final SuggestionProvider<ServerCommandSource> JOUEURS_EN_LIGNE =
        (ctx, builder) -> {
            ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .stream()
                .map(p -> p.getName().getString())
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    private static final String ADMIN_TAG = "§4§l[ADMIN] §r";

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("shards")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("give")
                    .then(CommandManager.argument("joueur", StringArgumentType.word())
                        .suggests(JOUEURS_EN_LIGNE)
                        .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                            .executes(ctx -> executerGive(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "joueur"),
                                IntegerArgumentType.getInteger(ctx, "montant")
                            )))))
                .then(CommandManager.literal("take")
                    .then(CommandManager.argument("joueur", StringArgumentType.word())
                        .suggests(JOUEURS_EN_LIGNE)
                        .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                            .executes(ctx -> executerTake(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "joueur"),
                                IntegerArgumentType.getInteger(ctx, "montant")
                            )))))
                .then(CommandManager.literal("check")
                    .then(CommandManager.argument("joueur", StringArgumentType.word())
                        .suggests(JOUEURS_EN_LIGNE)
                        .executes(ctx -> executerCheck(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "joueur")
                        ))))
        );
    }

    private static int executerGive(ServerCommandSource source, String cible, int montant) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.addShards(cible, montant);
        int nouveau = eco.getBalance(cible);

        source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);
        source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§a§l+ §f§lCrédit Shards"), true);
        source.sendFeedback(() -> Text.literal("  §7Joueur  §8» §f§l" + cible), false);
        source.sendFeedback(() -> Text.literal("  §7Crédit  §8» §a§l+" + SoldeCommand.fmt(montant) + " §6💎"), false);
        source.sendFeedback(() -> Text.literal("  §7Nouveau §8» §f§l" + SoldeCommand.fmt(nouveau)  + " §6💎"), false);
        source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);

        // Notification au joueur ciblé
        ServerPlayerEntity joueurCible = source.getServer().getPlayerManager().getPlayer(cible);
        if (joueurCible != null) {
            joueurCible.sendMessage(Text.literal(SoldeCommand.SEP_GOLD));
            joueurCible.sendMessage(Text.literal("    §6§l✦ §f§lCrédit reçu !"));
            joueurCible.sendMessage(Text.literal("  §7Un administrateur t'a crédité."));
            joueurCible.sendMessage(Text.literal("  §7Montant §8» §a§l+" + SoldeCommand.fmt(montant) + " §6💎"));
            joueurCible.sendMessage(Text.literal("  §7Solde   §8» §f§l"  + SoldeCommand.fmt(nouveau) + " §6💎"));
            joueurCible.sendMessage(Text.literal(SoldeCommand.SEP_GOLD));
        }
        return 1;
    }

    private static int executerTake(ServerCommandSource source, String cible, int montant) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.removeShards(cible, montant);
        int restant = eco.getBalance(cible);

        source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);
        source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§c§l- §f§lDébit Shards"), true);
        source.sendFeedback(() -> Text.literal("  §7Joueur  §8» §f§l" + cible), false);
        source.sendFeedback(() -> Text.literal("  §7Retiré  §8» §c§l-" + SoldeCommand.fmt(montant) + " §6💎"), false);
        source.sendFeedback(() -> Text.literal("  §7Restant §8» §f§l" + SoldeCommand.fmt(restant)  + " §6💎"), false);
        source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);

        return 1;
    }

    private static int executerCheck(ServerCommandSource source, String cible) {
        LocalEconomy eco = LocalEconomy.getInstance();
        boolean connu = eco.estConnu(cible);
        int solde = eco.getBalance(cible);

        if (!connu) {
            source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);
            source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§e§l⚠ §f§lJoueur inconnu"), false);
            source.sendFeedback(() -> Text.literal("  §7Pseudo  §8» §f§l" + cible), false);
            source.sendFeedback(() -> Text.literal("  §7Aucun solde enregistré pour ce joueur."), false);
            source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);
        } else {
            // Nom cliquable → suggère /shards give <cible>
            MutableText nomCliquable = Text.literal(cible)
                .styled(s -> s
                    .withColor(Formatting.WHITE)
                    .withBold(true)
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text.literal("§7Cliquer pour créditer §f" + cible)))
                    .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND, "/shards give " + cible + " ")));

            source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);
            source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§6§l◆ §f§lVérification solde"), false);
            source.sendFeedback(() -> Text.literal("  §7Joueur §8» ").append(nomCliquable), false);
            source.sendFeedback(() -> Text.literal("  §7Solde  §8» §f§l" + SoldeCommand.fmt(solde) + " §6💎"), false);
            source.sendFeedback(() -> Text.literal(SoldeCommand.SEP_DARK), false);
        }
        return 1;
    }
}
