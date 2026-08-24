package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.service.ServiceNetworkHandler;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /server-admin — monitoring de l'économie, réservé aux administrateurs.
 *
 * Le niveau requis est le maximum ({@link ServiceNetworkHandler#NIVEAU_ADMIN}) :
 * l'écran expose la trésorerie du serveur, la masse monétaire et permet
 * d'arbitrer des litiges d'argent entre joueurs.
 */
public final class ServerAdminCommand {

    private ServerAdminCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("server-admin")
            .requires(src -> src.hasPermissionLevel(ServiceNetworkHandler.NIVEAU_ADMIN))
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity joueur)) {
                    ctx.getSource().sendError(Text.literal("Commande réservée aux joueurs."));
                    return 0;
                }
                ServiceNetworkHandler.ouvrirAdmin(joueur, ctx.getSource().getServer());
                return 1;
            }));
    }
}
