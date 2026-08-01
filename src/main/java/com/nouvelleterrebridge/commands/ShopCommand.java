package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.network.ShopNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /shop : ouvre le GUI du Shop Serveur (achat et revente). */
public class ShopCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("shop")
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendError(Text.literal("Commande réservée aux joueurs."));
                    return 0;
                }
                ServerPlayNetworking.send(player, ShopNetworking.SHOP_OPEN,
                    NouvelleTerreBridge.buildShopOpenPacket(player));
                return 1;
            }));
    }
}
