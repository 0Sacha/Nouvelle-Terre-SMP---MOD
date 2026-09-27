package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.FirstJoinTracker;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;

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
    public static final String SEP_DARK   = "§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    private static final String ADMIN_TAG = "§4§l[ADMIN] §r";
    private static final String SHARD     = "§b◆§r";

    // ── Suggestions ───────────────────────────────────────────────────────────
    private static final SuggestionProvider<CommandSourceStack> TOUS_JOUEURS =
        (ctx, builder) -> {
            ctx.getSource().getServer().getPlayerList().getPlayers()
                .stream()
                .map(p -> p.getName().getString())
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    // ── Enregistrement ────────────────────────────────────────────────────────
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("economie")
                .then(Commands.literal("bourse")
                    .executes(ctx -> executerBourse(ctx.getSource())))

                .then(Commands.literal("admin")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("give")
                        .then(Commands.argument("joueur", StringArgumentType.word())
                            .suggests(TOUS_JOUEURS)
                            .then(Commands.argument("montant", IntegerArgumentType.integer(1))
                                .executes(ctx -> executerAdminGive(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "joueur"),
                                    IntegerArgumentType.getInteger(ctx, "montant"))))))
                    .then(Commands.literal("take")
                        .then(Commands.argument("joueur", StringArgumentType.word())
                            .suggests(TOUS_JOUEURS)
                            .then(Commands.argument("montant", IntegerArgumentType.integer(1))
                                .executes(ctx -> executerAdminTake(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "joueur"),
                                    IntegerArgumentType.getInteger(ctx, "montant"))))))
                    .then(Commands.literal("check")
                        .then(Commands.argument("joueur", StringArgumentType.word())
                            .suggests(TOUS_JOUEURS)
                            .executes(ctx -> executerAdminCheck(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "joueur")))))
                    .then(Commands.literal("reset-economy")
                        .then(Commands.literal("confirmer")
                            .executes(ctx -> executerResetEconomy(ctx.getSource())))))
        );
    }

    // ── /economie bourse ──────────────────────────────────────────────────────
    private static int executerBourse(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer joueur)) {
            source.sendFailure(Component.literal("Commande réservée aux joueurs.")); return 0;
        }
        String pseudo = joueur.getName().getString();
        int solde = LocalEconomy.getInstance().getBalance(pseudo);
        joueur.sendSystemMessage(Component.literal("§6[Nouvelle Terre] §7Solde §8» §f§l" + fmt(solde) + " " + SHARD));
        return 1;
    }

    // ── /economie admin give ──────────────────────────────────────────────────
    private static int executerAdminGive(CommandSourceStack source, String cible, int montant) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.addShards(cible, montant, "Don administrateur");
        int nouveau = eco.getBalance(cible);

        source.sendSuccess(() -> Component.literal(SEP_DARK), false);
        source.sendSuccess(() -> Component.literal("  " + ADMIN_TAG + "§a§l+ §f§lCrédit Shards"), true);
        source.sendSuccess(() -> Component.literal("  §7Joueur  §8» §f§l" + cible), false);
        source.sendSuccess(() -> Component.literal("  §7Crédit  §8» §a§l+" + fmt(montant) + " " + SHARD), false);
        source.sendSuccess(() -> Component.literal("  §7Nouveau §8» §f§l" + fmt(nouveau)  + " " + SHARD), false);
        source.sendSuccess(() -> Component.literal(SEP_DARK), false);

        ServerPlayer joueurCible = source.getServer().getPlayerList().getPlayerByName(cible);
        if (joueurCible != null) {
            joueurCible.sendSystemMessage(Component.literal(SEP_GOLD));
            joueurCible.sendSystemMessage(Component.literal("    §6§l✦ §f§lCrédit reçu !"));
            joueurCible.sendSystemMessage(Component.literal("  §7Un administrateur t'a crédité."));
            joueurCible.sendSystemMessage(Component.literal("  §7Montant §8» §a§l+" + fmt(montant) + " " + SHARD));
            joueurCible.sendSystemMessage(Component.literal("  §7Solde   §8» §f§l"  + fmt(nouveau) + " " + SHARD));
            joueurCible.sendSystemMessage(Component.literal(SEP_GOLD));
        }
        return 1;
    }

    // ── /economie admin take ──────────────────────────────────────────────────
    private static int executerAdminTake(CommandSourceStack source, String cible, int montant) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.removeShards(cible, montant);
        int restant = eco.getBalance(cible);

        source.sendSuccess(() -> Component.literal(SEP_DARK), false);
        source.sendSuccess(() -> Component.literal("  " + ADMIN_TAG + "§c§l- §f§lDébit Shards"), true);
        source.sendSuccess(() -> Component.literal("  §7Joueur  §8» §f§l" + cible), false);
        source.sendSuccess(() -> Component.literal("  §7Retiré  §8» §c§l-" + fmt(montant) + " " + SHARD), false);
        source.sendSuccess(() -> Component.literal("  §7Restant §8» §f§l" + fmt(restant)  + " " + SHARD), false);
        source.sendSuccess(() -> Component.literal(SEP_DARK), false);
        return 1;
    }

    // ── /economie admin check ─────────────────────────────────────────────────
    private static int executerAdminCheck(CommandSourceStack source, String cible) {
        LocalEconomy eco = LocalEconomy.getInstance();
        boolean connu = eco.estConnu(cible);
        int solde = eco.getBalance(cible);

        source.sendSuccess(() -> Component.literal(SEP_DARK), false);
        if (!connu) {
            source.sendSuccess(() -> Component.literal("  " + ADMIN_TAG + "§e§l⚠ §f§lJoueur inconnu"), false);
            source.sendSuccess(() -> Component.literal("  §7Pseudo §8» §f§l" + cible), false);
            source.sendSuccess(() -> Component.literal("  §7Aucun solde enregistré."), false);
        } else {
            MutableComponent nomCliquable = Component.literal(cible)
                .withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("§7Cliquer pour créditer §f" + cible)))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/economie admin give " + cible + " ")));

            source.sendSuccess(() -> Component.literal("  " + ADMIN_TAG + "§b◆ §f§lVérification"), false);
            source.sendSuccess(() -> Component.literal("  §7Joueur §8» ").append(nomCliquable), false);
            source.sendSuccess(() -> Component.literal("  §7Solde  §8» §f§l" + fmt(solde) + " " + SHARD), false);
        }
        source.sendSuccess(() -> Component.literal(SEP_DARK), false);
        return 1;
    }

    // ── /economie admin reset-economy confirmer ───────────────────────────────
    private static int executerResetEconomy(CommandSourceStack source) {
        LocalEconomy eco = LocalEconomy.getInstance();
        eco.resetAll();
        FirstJoinTracker.getInstance().resetAll();

        // Donner 500 ◆ immédiatement à tous les joueurs en ligne
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            String name = p.getName().getString();
            eco.addShards(name, 500, "Pécule de départ");
            FirstJoinTracker.getInstance().markReceived(name);
            p.sendSystemMessage(Component.literal(
                "§6[Admin] §fL'économie a été réinitialisée. Tu reçois §e§l500 ◆§f de départ !"));
            NouvelleTerreBridge.sendBalanceToPlayer(p);
        }

        source.sendSuccess(() -> Component.literal(SEP_RED), false);
        source.sendSuccess(() -> Component.literal("  " + ADMIN_TAG + "§c§l⚠ Économie réinitialisée"), true);
        source.sendSuccess(() -> Component.literal("  §7Soldes remis à §f§l0 ◆§7, 500 ◆ distribués aux joueurs en ligne."), false);
        source.sendSuccess(() -> Component.literal("  §7Les joueurs hors ligne recevront §a§l500 ◆ §7à leur prochaine connexion."), false);
        source.sendSuccess(() -> Component.literal(SEP_RED), false);
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
