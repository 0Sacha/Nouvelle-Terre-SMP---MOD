package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public class SoldeCommand {

    static final String SEP_GOLD = "§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    static final String SEP_GREEN = "§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    static final String SEP_RED   = "§c§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    static final String SEP_YELLOW= "§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";
    static final String SEP_DARK  = "§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r";

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("bourse")
                .executes(ctx -> executerSolde(ctx.getSource()))
        );
    }

    private static int executerSolde(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Cette commande est réservée aux joueurs."));
            return 0;
        }

        String pseudo = joueur.getName().getString();
        int solde = LocalEconomy.getInstance().getBalance(pseudo);

        // Montant cliquable → suggère /payer au clic, tooltip au survol
        MutableText montant = Text.literal(fmt(solde) + " 💎 Shards")
            .styled(s -> s
                .withColor(Formatting.GREEN)
                .withBold(true)
                .withHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Text.literal("§7Cliquez pour envoyer des Shards")))
                .withClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND, "/payer ")));

        joueur.sendMessage(Text.literal(SEP_GOLD));
        joueur.sendMessage(Text.literal("       §6§l✦ §f§lPortefeuille §6§l✦"));
        joueur.sendMessage(Text.literal("  §7Joueur §8» §f§l" + pseudo));
        joueur.sendMessage(Text.literal("  §7Solde  §8» ").append(montant));
        joueur.sendMessage(Text.literal(SEP_GOLD));

        return 1;
    }

    /** Formatte un entier avec espaces comme séparateurs de milliers (ex: 1 250). */
    static String fmt(int n) {
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
