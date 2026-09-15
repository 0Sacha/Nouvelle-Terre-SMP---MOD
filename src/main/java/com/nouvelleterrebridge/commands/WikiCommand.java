package com.nouvelleterrebridge.commands;

import com.nouvelleterrebridge.network.NtNet;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.network.WikiNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class WikiCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("wiki")
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                        src.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
                    }
                    NtNet.versClient(player, WikiNetworking.WIKI_OPEN, PacketByteBufs.empty());
                    return 1;
                })
        );
    }
}
