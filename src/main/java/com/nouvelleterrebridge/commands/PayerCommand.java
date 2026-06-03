package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.economy.LocalEconomy;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class PayerCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("payer")
                .then(CommandManager.argument("joueur", StringArgumentType.word())
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
            joueur.sendMessage(Text.literal("§cTu ne peux pas te payer toi-même."));
            return 0;
        }

        LocalEconomy eco = LocalEconomy.getInstance();
        int solde = eco.getBalance(expediteur);

        if (solde < montant) {
            joueur.sendMessage(Text.literal(
                String.format("§c❌ Solde insuffisant — tu as §f%d💎§c, il te faut §f%d💎§c.", solde, montant)
            ));
            return 0;
        }

        boolean ok = eco.transfer(expediteur, destinataire, montant);
        if (!ok) {
            joueur.sendMessage(Text.literal("§c❌ Virement impossible."));
            return 0;
        }

        joueur.sendMessage(Text.literal(
            String.format("§a✅ Tu as envoyé §f§l%d💎§a à §f%s§a. Solde restant : §f%d💎§a.",
                montant, destinataire, eco.getBalance(expediteur))
        ));

        ServerPlayerEntity dest = source.getServer().getPlayerManager().getPlayer(destinataire);
        if (dest != null) {
            dest.sendMessage(Text.literal(
                String.format("§a💰 §f%s§a t'a envoyé §f§l%d💎§a ! Solde : §f%d💎§a.",
                    expediteur, montant, eco.getBalance(destinataire))
            ));
        }

        return 1;
    }
}
