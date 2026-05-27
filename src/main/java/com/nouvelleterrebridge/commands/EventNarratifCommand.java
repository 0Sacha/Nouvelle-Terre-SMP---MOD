package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Commande /evenement — déclenche un événement narratif (opérateurs uniquement).
 * Syntaxe : /evenement <message>
 * Diffuse l'événement dans le salon #annonces Discord.
 */
public class EventNarratifCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("evenement")
                // Réservé aux opérateurs (niveau 2)
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> executerEvenementNarratif(ctx.getSource(),
                        StringArgumentType.getString(ctx, "message"))))
        );
    }

    private static int executerEvenementNarratif(ServerCommandSource source, String message) {
        String auteur = source.getEntity() instanceof ServerPlayerEntity joueur
            ? joueur.getName().getString()
            : "Console";

        source.sendFeedback(() -> Text.literal(
            String.format("§d📜 Événement narratif envoyé à Discord : %s", message)
        ), true);

        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        data.put("author", auteur);
        EventDispatcher.envoyer("NARRATIVE_EVENT", data);

        return 1;
    }
}
