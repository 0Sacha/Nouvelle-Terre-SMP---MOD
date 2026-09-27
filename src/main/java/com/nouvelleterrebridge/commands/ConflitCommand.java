package com.nouvelleterrebridge.commands;

import com.nouvelleterrebridge.network.NtNet;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.network.ConflitNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * /conflit : ouvre le GUI de déclaration de conflit RP (screen client).
 * La déclaration elle-même passe par CONFLIT_ACTION (voir NouvelleTerreBridge).
 */
public class ConflitCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("conflit")
            .executes(ctx -> {
                CommandSourceStack src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayer player)) {
                    src.sendFailure(Component.literal("Cette commande est réservée aux joueurs."));
                    return 0;
                }
                open(player);
                return 1;
            })
        );
    }

    /** Envoie CONFLIT_OPEN avec la liste des joueurs en ligne (hors soi-même). */
    public static void open(ServerPlayer player) {
        String moi = player.getName().getString();
        List<String> enLigne = player.getServer().getPlayerList().getPlayers().stream()
            .map(p -> p.getName().getString())
            .filter(name -> !name.equalsIgnoreCase(moi))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

        FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
        buf.writeInt(enLigne.size());
        for (String name : enLigne) buf.writeUtf(name);
        NtNet.versClient(player, ConflitNetworking.CONFLIT_OPEN, buf);
    }
}
