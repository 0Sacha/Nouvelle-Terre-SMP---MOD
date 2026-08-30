package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.MaintenanceMode;
import com.nouvelleterrebridge.service.ServiceNetworkHandler;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("nt-maintenance")
            .requires(src -> src.hasPermissionLevel(ServiceNetworkHandler.NIVEAU_ADMIN))
            .executes(ctx -> etat(ctx.getSource()))
            .then(CommandManager.literal("on")
                .executes(ctx -> activer(ctx.getSource(), null))
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> activer(ctx.getSource(),
                        StringArgumentType.getString(ctx, "message")))))
            .then(CommandManager.literal("off")
                .executes(ctx -> desactiver(ctx.getSource()))));
    }

    private static int etat(ServerCommandSource source) {
        boolean actif = MaintenanceMode.estActif();
        source.sendFeedback(() -> Text.literal(actif ? EconomieCommand.SEP_RED : EconomieCommand.SEP_GREEN), false);
        if (actif) {
            source.sendFeedback(() -> Text.literal("  §c§lMAINTENANCE ACTIVE"), false);
            source.sendFeedback(() -> Text.literal("  §8Message §7» §f" + MaintenanceMode.getMessage()), false);
            source.sendFeedback(() -> Text.literal("  §8Seuls les op peuvent se connecter. §7/nt-maintenance off §8pour rouvrir."), false);
        } else {
            source.sendFeedback(() -> Text.literal("  §a§lSERVEUR OUVERT"), false);
            source.sendFeedback(() -> Text.literal("  §8/nt-maintenance on [message] §7pour fermer aux non-op."), false);
        }
        source.sendFeedback(() -> Text.literal(actif ? EconomieCommand.SEP_RED : EconomieCommand.SEP_GREEN), false);
        return 1;
    }

    private static int activer(ServerCommandSource source, String message) {
        MaintenanceMode.activer(message);

        // checkCanJoin ne concerne que l'entrée : sans expulsion, ceux qui sont
        // déjà là resteraient connectés.
        List<ServerPlayerEntity> expulses = new ArrayList<>();
        var pm = source.getServer().getPlayerManager();
        for (ServerPlayerEntity p : new ArrayList<>(pm.getPlayerList())) {
            if (!pm.isOperator(p.getGameProfile())) expulses.add(p);
        }
        for (ServerPlayerEntity p : expulses) {
            p.networkHandler.disconnect(Text.literal("§6⚠ " + MaintenanceMode.getMessage()));
        }

        int nb = expulses.size();
        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_RED), false);
        source.sendFeedback(() -> Text.literal("  §c§lMaintenance activée"), true);
        source.sendFeedback(() -> Text.literal("  §8Message §7» §f" + MaintenanceMode.getMessage()), false);
        source.sendFeedback(() -> Text.literal("  §7" + nb + " joueur(s) déconnecté(s), les op sont épargnés."), false);
        source.sendFeedback(() -> Text.literal("  §8L'état survit au redémarrage — pense à §7/nt-maintenance off§8."), false);
        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_RED), false);
        return 1;
    }

    private static int desactiver(ServerCommandSource source) {
        MaintenanceMode.desactiver();
        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GREEN), false);
        source.sendFeedback(() -> Text.literal("  §a§lServeur rouvert"), true);
        source.sendFeedback(() -> Text.literal("  §7Tout le monde peut de nouveau se connecter (whitelist inchangée)."), false);
        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GREEN), false);
        return 1;
    }
}
