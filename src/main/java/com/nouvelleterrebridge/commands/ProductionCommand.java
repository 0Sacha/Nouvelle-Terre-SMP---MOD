package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /production : ouvre le GUI Production naturelle (tous les joueurs).
 * Les actions admin (recheck / reload / reset) se font via les boutons du GUI (op only).
 */
public class ProductionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("production")
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) {
                    ctx.getSource().sendFeedback(() -> Text.literal("§cCommande joueur uniquement."), false);
                    return 0;
                }
                NouvelleTerreBridge.sendProductionOpen(player);
                return 1;
            })
        );
    }
}
