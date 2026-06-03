package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Commande /conflit — déclare un conflit contre un autre joueur.
 * Syntaxe : /conflit <joueur_cible> <raison>
 * Alerte le Conseil des Fondateurs sur Discord.
 */
public class ConflitCommand {

    private static final SuggestionProvider<ServerCommandSource> JOUEURS_EN_LIGNE =
        (ctx, builder) -> {
            String moi = ctx.getSource().getName();
            ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .stream()
                .map(p -> p.getName().getString())
                .filter(name -> !name.equalsIgnoreCase(moi))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("conflit")
                .then(CommandManager.argument("cible", StringArgumentType.word())
                    .suggests(JOUEURS_EN_LIGNE)
                    .then(CommandManager.argument("raison", StringArgumentType.greedyString())
                        .executes(ctx -> executerConflit(ctx.getSource(),
                            StringArgumentType.getString(ctx, "cible"),
                            StringArgumentType.getString(ctx, "raison")))))
        );
    }

    private static int executerConflit(ServerCommandSource source, String cible, String raison) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Cette commande est réservée aux joueurs."));
            return 0;
        }

        String pseudo = joueur.getName().getString();

        // Empêche de se déclarer conflit à soi-même
        if (pseudo.equalsIgnoreCase(cible)) {
            source.sendError(Text.literal("Vous ne pouvez pas vous déclarer conflit à vous-même."));
            return 0;
        }

        joueur.sendMessage(Text.literal(
            String.format("§c⚔ Conflit déclaré contre §f%s§c. Le Conseil des Fondateurs a été alerté.", cible)
        ));

        Map<String, Object> data = new HashMap<>();
        data.put("player", pseudo);
        data.put("target", cible);
        data.put("reason", raison);
        EventDispatcher.envoyer("CONFLICT_DECLARED", data);

        return 1;
    }
}
