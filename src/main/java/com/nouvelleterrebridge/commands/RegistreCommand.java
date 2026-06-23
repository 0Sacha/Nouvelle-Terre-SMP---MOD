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
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(personnages.size());
                    for (Map<String, Object> p : personnages) {
                        buf.writeString((String) p.getOrDefault("nom_rp", "Inconnu"));
                        buf.writeString((String) p.getOrDefault("pseudo_mc", ""));
                        buf.writeBoolean(Boolean.TRUE.equals(p.get("en_ligne")));
                    }
                    ServerPlayNetworking.send(player, RegistreNetworking.REGISTRE_OPEN, buf);
                });

                return 1;
            }));
    }
}
