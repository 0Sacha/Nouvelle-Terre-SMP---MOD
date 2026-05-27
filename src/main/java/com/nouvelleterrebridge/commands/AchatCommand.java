package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.nouvelleterrebridge.events.EconomyEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Commande /achat — achète une vente active sur le marché.
 * Syntaxe : /achat <id_vente>
 *
 * Note : la logique de validation (fonds suffisants, vente existante) doit être
 * implémentée par votre système économique. Ce mod envoie seulement les événements.
 */
public class AchatCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("achat")
                .then(CommandManager.argument("id_vente", IntegerArgumentType.integer(1))
                    .executes(ctx -> executerAchat(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "id_vente"))))
        );
    }

    private static int executerAchat(ServerCommandSource source, int idVente) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Cette commande est réservée aux joueurs."));
            return 0;
        }

        String pseudo = joueur.getName().getString();

        // Avertissement : l'achat réel (transfert d'items/monnaie) doit être géré
        // par votre plugin économique (ex: Haiku Economy, EssentialsX, etc.)
        // Ce mod ne fait que notifier Discord de la transaction.

        joueur.sendMessage(Text.literal(
            String.format("§e⚠ Achat de la vente §f#%d §einitiée. Vérifiez avec le vendeur pour l'échange physique des items et Shards.", idVente)
        ));

        // L'événement SALE_COMPLETED sera déclenché par le vendeur ou un admin
        // quand la transaction sera effectivement conclue
        source.sendFeedback(() -> Text.literal(
            String.format("[NouvelleTerreBridge] %s souhaite acheter la vente #%d", pseudo, idVente)
        ), true);

        return 1;
    }
}
