package com.nouvelleterrebridge.commands;

import com.nouvelleterrebridge.network.NtNet;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.network.RegistreNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class RegistreCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("registre")
            .executes(ctx -> {
                CommandSourceStack src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayer player)) return 0;

                src.sendSuccess(() -> Component.literal("§8[Nouvelle Terre] §7Chargement du registre..."), false);
                open(player);
                return 1;
            }));
    }

    /** Récupère les personnages auprès du bot puis envoie REGISTRE_OPEN au joueur. */
    public static void open(ServerPlayer player) {
        var server = player.getServer();
        EventDispatcher.fetchPersonnages(server, personnages -> {
            // Statut en ligne : le serveur fait foi (la DB du bot peut être désynchronisée)
            var enLigneMC = new java.util.HashSet<String>();
            for (ServerPlayer sp : server.getPlayerList().getPlayers())
                enLigneMC.add(sp.getName().getString().toLowerCase());

            FriendlyByteBuf buf = com.nouvelleterrebridge.network.NtNet.buffer();
            buf.writeInt(personnages.size());
            for (Map<String, Object> p : personnages) {
                String pseudoMc = (String) p.getOrDefault("pseudo_mc", "");
                buf.writeUtf((String) p.getOrDefault("nom_rp", "Inconnu"));
                buf.writeUtf(pseudoMc);
                buf.writeBoolean(enLigneMC.contains(pseudoMc.toLowerCase()));
            }
            NtNet.versClient(player, RegistreNetworking.REGISTRE_OPEN, buf);
        });
    }
}
