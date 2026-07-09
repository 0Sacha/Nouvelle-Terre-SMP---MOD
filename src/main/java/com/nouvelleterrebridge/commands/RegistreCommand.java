package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.network.RegistreNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;

public class RegistreCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("registre")
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) return 0;

                src.sendFeedback(() -> Text.literal("§8[Nouvelle Terre] §7Chargement du registre..."), false);

                EventDispatcher.fetchPersonnages(src.getServer(), personnages -> {
                    // Statut en ligne : le serveur fait foi (la DB du bot peut être désynchronisée)
                    var enLigneMC = new java.util.HashSet<String>();
                    for (ServerPlayerEntity sp : src.getServer().getPlayerManager().getPlayerList())
                        enLigneMC.add(sp.getName().getString().toLowerCase());

                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(personnages.size());
                    for (Map<String, Object> p : personnages) {
                        String pseudoMc = (String) p.getOrDefault("pseudo_mc", "");
                        buf.writeString((String) p.getOrDefault("nom_rp", "Inconnu"));
                        buf.writeString(pseudoMc);
                        buf.writeBoolean(enLigneMC.contains(pseudoMc.toLowerCase()));
                    }
                    ServerPlayNetworking.send(player, RegistreNetworking.REGISTRE_OPEN, buf);
                });

                return 1;
            }));
    }
}
