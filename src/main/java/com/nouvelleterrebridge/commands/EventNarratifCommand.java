package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Commande /evenement — déclenche un événement narratif (opérateurs uniquement).
 * Syntaxe : /evenement <message>
 * Diffuse l'événement dans le salon #annonces Discord.
 */
public class EventNarratifCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("evenement")
                // Réservé aux opérateurs (niveau 2)
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> executerEvenementNarratif(ctx.getSource(),
                        StringArgumentType.getString(ctx, "message"))))
        );
    }

    private static int executerEvenementNarratif(CommandSourceStack source, String message) {
        String auteur = source.getEntity() instanceof ServerPlayer joueur
            ? joueur.getName().getString()
            : "Console";

        source.sendSuccess(() -> Component.literal(
            String.format("§d📜 Événement narratif envoyé à Discord : %s", message)
        ), true);

        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        data.put("author", auteur);
        EventDispatcher.envoyer("NARRATIVE_EVENT", data);

        return 1;
    }
}
