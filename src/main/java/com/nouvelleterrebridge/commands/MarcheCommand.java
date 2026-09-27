package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.service.ServiceNetworkHandler;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** /leboncube — les petites annonces de services entre joueurs. */
public final class MarcheCommand {

    private MarcheCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("leboncube")
            .executes(ctx -> ouvrir(ctx.getSource())));
    }

    private static int ouvrir(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer joueur)) {
            source.sendFailure(Component.literal("Commande réservée aux joueurs."));
            return 0;
        }
        ServiceNetworkHandler.ouvrir(joueur);
        return 1;
    }
}
