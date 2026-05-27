package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.events.EconomyEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Commande /vente — propose un item à la vente sur le marché.
 * Syntaxe : /vente <item> <quantité> <prix_en_shards>
 */
public class VenteCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("vente")
                .then(CommandManager.argument("item", StringArgumentType.word())
                    .then(CommandManager.argument("quantite", IntegerArgumentType.integer(1, 64))
                        .then(CommandManager.argument("prix", IntegerArgumentType.integer(1))
                            .executes(ctx -> executerVente(ctx.getSource(),
                                StringArgumentType.getString(ctx, "item"),
                                IntegerArgumentType.getInteger(ctx, "quantite"),
                                IntegerArgumentType.getInteger(ctx, "prix"))))))
        );
    }

    private static int executerVente(ServerCommandSource source, String item, int quantite, int prix) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Cette commande est réservée aux joueurs."));
            return 0;
        }

        String pseudo = joueur.getName().getString();

        // Normalise le nom de l'item avec le préfixe minecraft: si absent
        String itemFinal = item.contains(":") ? item : "minecraft:" + item;

        // Notifie le joueur
        joueur.sendMessage(Text.literal(
            String.format("§a✅ Vente postée : §f%d× %s §apour §f%d Shard(s)§a. La communauté en sera informée !",
                quantite, itemFinal.replace("minecraft:", ""), prix)
        ));

        // Envoie l'événement au bot Discord
        EconomyEvents.surVentePostee(pseudo, itemFinal, quantite, prix);

        return 1;
    }
}
