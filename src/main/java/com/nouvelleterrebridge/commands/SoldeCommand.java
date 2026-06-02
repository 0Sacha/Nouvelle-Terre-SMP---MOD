package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.economy.EconomyManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class SoldeCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("solde")
                .executes(ctx -> executerSolde(ctx.getSource()))
        );
    }

    private static int executerSolde(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Cette commande est réservée aux joueurs."));
            return 0;
        }

        String pseudo = joueur.getName().getString();
        joueur.sendMessage(Text.literal("§e⏳ Récupération de ton solde..."));

        EconomyManager.getBalance(pseudo, source.getServer(), (solde) -> {
            if (solde < 0) {
                joueur.sendMessage(Text.literal("§cErreur : impossible de récupérer ton solde."));
            } else {
                joueur.sendMessage(Text.literal(
                    String.format("§6💰 Ton solde : §f§l%d 💎 §6Shards", solde)
                ));
            }
        });

        return 1;
    }
}
