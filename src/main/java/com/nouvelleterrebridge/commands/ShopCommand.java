package com.nouvelleterrebridge.commands;

import com.nouvelleterrebridge.network.NtNet;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.network.ShopNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** /shop : ouvre le GUI du Shop Serveur (achat et revente). */
public class ShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
            .executes(ctx -> {
                CommandSourceStack src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayer player)) {
                    src.sendFailure(Component.literal("Commande réservée aux joueurs."));
                    return 0;
                }
                NtNet.versClient(player, ShopNetworking.SHOP_OPEN, NouvelleTerreBridge.buildShopOpenPacket(player));
                return 1;
            }));
    }
}
