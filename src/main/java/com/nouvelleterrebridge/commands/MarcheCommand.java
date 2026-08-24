package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.service.ServiceNetworkHandler;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /leboncube — les petites annonces de services entre joueurs. */
public final class MarcheCommand {

    private MarcheCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("leboncube")
            .executes(ctx -> ouvrir(ctx.getSource())));
    }

    private static int ouvrir(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs."));
            return 0;
        }
        ServiceNetworkHandler.ouvrir(joueur);
        return 1;
    }
}
