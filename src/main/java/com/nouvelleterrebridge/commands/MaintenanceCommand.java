package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.MaintenanceMode;
import com.nouvelleterrebridge.service.ServiceNetworkHandler;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * /nt-maintenance — ferme le serveur à tout le monde sauf aux op.
 *
 * Le refus lui-même est posé par {@code PlayerManagerMixin} ; cette commande ne
 * fait que basculer l'état et expulser ceux qui sont déjà connectés.
 */
public final class MaintenanceCommand {

    private MaintenanceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nt-maintenance")
            .requires(src -> src.hasPermission(ServiceNetworkHandler.NIVEAU_ADMIN))
            .executes(ctx -> etat(ctx.getSource()))
            .then(Commands.literal("on")
                .executes(ctx -> activer(ctx.getSource(), null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> activer(ctx.getSource(),
                        StringArgumentType.getString(ctx, "message")))))
            .then(Commands.literal("off")
                .executes(ctx -> desactiver(ctx.getSource()))));
    }

    private static int etat(CommandSourceStack source) {
        boolean actif = MaintenanceMode.estActif();
        source.sendSuccess(() -> Component.literal(actif ? EconomieCommand.SEP_RED : EconomieCommand.SEP_GREEN), false);
        if (actif) {
            source.sendSuccess(() -> Component.literal("  §c§lMAINTENANCE ACTIVE"), false);
            source.sendSuccess(() -> Component.literal("  §8Message §7» §f" + MaintenanceMode.getMessage()), false);
            source.sendSuccess(() -> Component.literal("  §8Seuls les op peuvent se connecter. §7/nt-maintenance off §8pour rouvrir."), false);
        } else {
            source.sendSuccess(() -> Component.literal("  §a§lSERVEUR OUVERT"), false);
            source.sendSuccess(() -> Component.literal("  §8/nt-maintenance on [message] §7pour fermer aux non-op."), false);
        }
        source.sendSuccess(() -> Component.literal(actif ? EconomieCommand.SEP_RED : EconomieCommand.SEP_GREEN), false);
        return 1;
    }

    private static int activer(CommandSourceStack source, String message) {
        MaintenanceMode.activer(message);

        // checkCanJoin ne concerne que l'entrée : sans expulsion, ceux qui sont
        // déjà là resteraient connectés.
        List<ServerPlayer> expulses = new ArrayList<>();
        var pm = source.getServer().getPlayerList();
        for (ServerPlayer p : new ArrayList<>(pm.getPlayers())) {
            if (!pm.isOp(p.getGameProfile())) expulses.add(p);
        }
        for (ServerPlayer p : expulses) {
            p.connection.disconnect(Component.literal("§6⚠ " + MaintenanceMode.getMessage()));
        }

        int nb = expulses.size();
        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_RED), false);
        source.sendSuccess(() -> Component.literal("  §c§lMaintenance activée"), true);
        source.sendSuccess(() -> Component.literal("  §8Message §7» §f" + MaintenanceMode.getMessage()), false);
        source.sendSuccess(() -> Component.literal("  §7" + nb + " joueur(s) déconnecté(s), les op sont épargnés."), false);
        source.sendSuccess(() -> Component.literal("  §8L'état survit au redémarrage — pense à §7/nt-maintenance off§8."), false);
        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_RED), false);
        return 1;
    }

    private static int desactiver(CommandSourceStack source) {
        MaintenanceMode.desactiver();
        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_GREEN), false);
        source.sendSuccess(() -> Component.literal("  §a§lServeur rouvert"), true);
        source.sendSuccess(() -> Component.literal("  §7Tout le monde peut de nouveau se connecter (whitelist inchangée)."), false);
        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_GREEN), false);
        return 1;
    }
}
