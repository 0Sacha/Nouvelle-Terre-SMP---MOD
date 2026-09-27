package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * /production : ouvre le GUI Production naturelle (tous les joueurs).
 * Les actions admin (recheck / reload / reset) se font via les boutons du GUI (op only).
 */
public class ProductionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("production")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayer();
                if (player == null) {
                    ctx.getSource().sendSuccess(() -> Component.literal("§cCommande joueur uniquement."), false);
                    return 0;
                }
                NouvelleTerreBridge.sendProductionOpen(player);
                return 1;
            })
        );
    }
}
