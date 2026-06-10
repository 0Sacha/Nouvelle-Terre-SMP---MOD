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

/**
 * Toutes les commandes économie regroupées sous /economie.
 *
 *   /economie bourse                          → voir son solde
 *   /economie admin give  <joueur> <montant>  → [OP] créditer
 *   /economie admin take  <joueur> <montant>  → [OP] débiter
 *   /economie admin check <joueur>            → [OP] vérifier
 */
public class EconomieCommand {

    // ── Séparateurs UI ────────────────────────────────────────────────────────
    public static final String SEP_GOLD   = "§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    public static final String SEP_GREEN  = "§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    public static final String SEP_RED    = "§c§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    public static final String SEP_YELLOW = "§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    public static final String SEP_DARK   = "§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    private static final String ADMIN_TAG = "§4§l[ADMIN] §r";
    private static final String SHARD     = "§b◆§r";

    // ── Suggestions ───────────────────────────────────────────────────────────
    private static final SuggestionProvider<ServerCommandSource> TOUS_JOUEURS =
        (ctx, builder) -> {
            ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .stream()
                .map(p -> p.getName().getString())
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    // ── Enregistrement ────────────────────────────────────────────────────────
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("economie")
                .then(CommandManager.literal("bourse")
                    .executes(ctx -> executerBourse(ctx.getSource())))

                .then(CommandManager.literal("admin")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.literal("give")
                        .then(CommandManager.argument("joueur", StringArgumentType.word())
                            .suggests(TOUS_JOUEURS)
                            .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                                .executes(ctx -> executerAdminGive(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "joueur"),
                                    IntegerArgumentType.getInteger(ctx, "montant"))))))
                    .then(CommandManager.literal("take")
                        .then(CommandManager.argument("joueur", StringArgumentType.word())
                            .suggests(TOUS_JOUEURS)
                            .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                                .executes(ctx -> executerAdminTake(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "joueur"),
                                    IntegerArgumentType.getInteger(ctx, "montant"))))))
                    .then(CommandManager.literal("check")
                        .then(CommandManager.argument("joueur", StringArgumentType.word())
                            .suggests(TOUS_JOUEURS)
                            .executes(ctx -> executerAdminCheck(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "joueur"))))))
        );
    }

    // ── /economie bourse ──────────────────────────────────────────────────────
    private static int executerBourse(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }
        String pseudo = joueur.getName().getString();
        int solde = LocalEconomy.getInstance().getBalance(pseudo);
        joueur.sendMessage(Text.literal("§6[Nouvelle Terre] §7Solde §8» §f§l" + fmt(solde) + " " + SHARD));
        return 1;
    }

    // ── /economie admin give ──────────────────────────────────────────────────
    private static int executerAdminGive(ServerCommandSource source, String cible, int montant) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.addShards(cible, montant);
        int nouveau = eco.getBalance(cible);

        source.sendFeedback(() -> Text.literal(SEP_DARK), false);
        source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§a§l+ §f§lCrédit Shards"), true);
        source.sendFeedback(() -> Text.literal("  §7Joueur  §8» §f§l" + cible), false);
        source.sendFeedback(() -> Text.literal("  §7Crédit  §8» §a§l+" + fmt(montant) + " " + SHARD), false);
        source.sendFeedback(() -> Text.literal("  §7Nouveau §8» §f§l" + fmt(nouveau)  + " " + SHARD), false);
        source.sendFeedback(() -> Text.literal(SEP_DARK), false);

        ServerPlayerEntity joueurCible = source.getServer().getPlayerManager().getPlayer(cible);
        if (joueurCible != null) {
            joueurCible.sendMessage(Text.literal(SEP_GOLD));
            joueurCible.sendMessage(Text.literal("    §6§l✦ §f§lCrédit reçu !"));
            joueurCible.sendMessage(Text.literal("  §7Un administrateur t'a crédité."));
            joueurCible.sendMessage(Text.literal("  §7Montant §8» §a§l+" + fmt(montant) + " " + SHARD));
            joueurCible.sendMessage(Text.literal("  §7Solde   §8» §f§l"  + fmt(nouveau) + " " + SHARD));
            joueurCible.sendMessage(Text.literal(SEP_GOLD));
        }
        return 1;
    }

    // ── /economie admin take ──────────────────────────────────────────────────
    private static int executerAdminTake(ServerCommandSource source, String cible, int montant) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.removeShards(cible, montant);
        int restant = eco.getBalance(cible);

        source.sendFeedback(() -> Text.literal(SEP_DARK), false);
        source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§c§l- §f§lDébit Shards"), true);
        source.sendFeedback(() -> Text.literal("  §7Joueur  §8» §f§l" + cible), false);
        source.sendFeedback(() -> Text.literal("  §7Retiré  §8» §c§l-" + fmt(montant) + " " + SHARD), false);
        source.sendFeedback(() -> Text.literal("  §7Restant §8» §f§l" + fmt(restant)  + " " + SHARD), false);
        source.sendFeedback(() -> Text.literal(SEP_DARK), false);
        return 1;
    }

    // ── /economie admin check ─────────────────────────────────────────────────
    private static int executerAdminCheck(ServerCommandSource source, String cible) {
        LocalEconomy eco = LocalEconomy.getInstance();
        boolean connu = eco.estConnu(cible);
        int solde = eco.getBalance(cible);

        source.sendFeedback(() -> Text.literal(SEP_DARK), false);
        if (!connu) {
            source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§e§l⚠ §f§lJoueur inconnu"), false);
            source.sendFeedback(() -> Text.literal("  §7Pseudo §8» §f§l" + cible), false);
            source.sendFeedback(() -> Text.literal("  §7Aucun solde enregistré."), false);
        } else {
            MutableText nomCliquable = Text.literal(cible)
                .styled(s -> s.withColor(Formatting.WHITE).withBold(true)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("§7Cliquer pour créditer §f" + cible)))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/economie admin give " + cible + " ")));

            source.sendFeedback(() -> Text.literal("  " + ADMIN_TAG + "§b◆ §f§lVérification"), false);
            source.sendFeedback(() -> Text.literal("  §7Joueur §8» ").append(nomCliquable), false);
            source.sendFeedback(() -> Text.literal("  §7Solde  §8» §f§l" + fmt(solde) + " " + SHARD), false);
        }
        source.sendFeedback(() -> Text.literal(SEP_DARK), false);
        return 1;
    }

    // ── Utilitaire ────────────────────────────────────────────────────────────
    public static String fmt(int n) {
        String s = Integer.toString(n);
        if (s.length() <= 3) return s;
        StringBuilder sb = new StringBuilder();
        int debut = s.length() % 3;
        if (debut > 0) sb.append(s, 0, debut);
        for (int i = debut; i < s.length(); i += 3) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(s, i, i + 3);
        }
        return sb.toString();
    }
}
