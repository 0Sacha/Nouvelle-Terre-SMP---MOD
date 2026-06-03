package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * /lier — génère un code à 6 chiffres à entrer dans Discord (/link <code>)
 * pour lier son compte Minecraft à son profil Discord.
 */
public class LierCommand {

    private static final Random RANDOM = new Random();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("lier")
                .executes(ctx -> executerLier(ctx.getSource()))
        );
    }

    private static int executerLier(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        String pseudo = joueur.getName().getString();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        Map<String, Object> data = new HashMap<>();
        data.put("pseudo", pseudo);
        data.put("code", code);
        EventDispatcher.envoyer("LINK_REQUEST", data);

        joueur.sendMessage(Text.literal(
            "§6🔗 Code de liaison : §f§l" + code + "\n" +
            "§7Tape §f/link " + code + " §7dans Discord. §eValide 10 minutes."
        ));
        return 1;
    }
}
