package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.network.ConflitNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * /conflit : ouvre le GUI de déclaration de conflit RP (screen client).
 * La déclaration elle-même passe par CONFLIT_ACTION (voir NouvelleTerreBridge).
 */
public class ConflitCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("conflit")
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendError(Text.literal("Cette commande est réservée aux joueurs."));
                    return 0;
                }
                String moi = player.getName().getString();
                List<String> enLigne = src.getServer().getPlayerManager().getPlayerList().stream()
                    .map(p -> p.getName().getString())
                    .filter(name -> !name.equalsIgnoreCase(moi))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(enLigne.size());
                for (String name : enLigne) buf.writeString(name);
                ServerPlayNetworking.send(player, ConflitNetworking.CONFLIT_OPEN, buf);
                return 1;
            })
        );
    }
}
