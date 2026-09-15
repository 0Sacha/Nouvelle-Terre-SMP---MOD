package com.nouvelleterrebridge.commands;

import com.nouvelleterrebridge.network.NtNet;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.network.BankNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BankCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("bank")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
                        ctx.getSource().sendError(Text.literal("Commande reservee aux joueurs."));
                        return 0;
                    }
                    NtNet.versClient(player, BankNetworking.BANK_OPEN, NouvelleTerreBridge.buildBankOpenPacket(player, ctx.getSource().getServer()));
                    return 1;
                })
        );
    }
}
