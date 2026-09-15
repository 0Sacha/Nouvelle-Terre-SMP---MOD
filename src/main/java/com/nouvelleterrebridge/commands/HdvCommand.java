package com.nouvelleterrebridge.commands;

import com.nouvelleterrebridge.network.NtNet;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.network.HdvNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class HdvCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("hdv")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
                        ctx.getSource().sendError(Text.literal("Commande réservée aux joueurs."));
                        return 0;
                    }
                    NtNet.versClient(player, HdvNetworking.HDV_OPEN, NouvelleTerreBridge.buildHdvOpenPacket(player, ctx.getSource().getServer()));
                    return 1;
                })
        );
    }
}
