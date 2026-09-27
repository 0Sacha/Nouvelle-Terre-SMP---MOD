package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.service.ServiceNetworkHandler;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * /server-admin — monitoring de l'économie, réservé aux administrateurs.
 *
 * Le niveau requis est le maximum ({@link ServiceNetworkHandler#NIVEAU_ADMIN}) :
 * l'écran expose la trésorerie du serveur, la masse monétaire et permet
 * d'arbitrer des litiges d'argent entre joueurs.
 */
public final class ServerAdminCommand {

    private ServerAdminCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("server-admin")
            .requires(src -> src.hasPermission(ServiceNetworkHandler.NIVEAU_ADMIN))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer joueur)) {
                    ctx.getSource().sendFailure(Component.literal("Commande réservée aux joueurs."));
                    return 0;
                }
                ServiceNetworkHandler.ouvrirAdmin(joueur, ctx.getSource().getServer());
                return 1;
            }));
    }
}
