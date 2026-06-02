package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.economy.EconomyManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Commande /payer <joueur> <montant> — vire des Shards à un autre joueur.
 */
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

        EconomyManager.transfer(expediteur, destinataire, montant, (success, message) -> {
            if (success) {
                joueur.sendMessage(Text.literal(
                    String.format("§a✅ Tu as envoyé §f§l%d 💎§a à §f%s§a.", montant, destinataire)
                ));
                // Notifie le destinataire s'il est en ligne
                ServerPlayerEntity dest = source.getServer().getPlayerManager().getPlayer(destinataire);
                if (dest != null) {
                    dest.sendMessage(Text.literal(
                        String.format("§a💰 Tu as reçu §f§l%d 💎§a de §f%s§a.", montant, expediteur)
                    ));
                }
            } else {
                joueur.sendMessage(Text.literal("§c❌ " + message));
            }
        });

        return 1;
    }
}
