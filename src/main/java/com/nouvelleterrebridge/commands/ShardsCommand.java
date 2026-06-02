package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.economy.EconomyManager;
import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ShardsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("shards")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("give")
                    .then(CommandManager.argument("joueur", StringArgumentType.word())
                        .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                            .executes(ctx -> executerGive(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "joueur"),
                                IntegerArgumentType.getInteger(ctx, "montant")
                            )))))
                .then(CommandManager.literal("take")
                    .then(CommandManager.argument("joueur", StringArgumentType.word())
                        .then(CommandManager.argument("montant", IntegerArgumentType.integer(1))
                            .executes(ctx -> executerTake(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "joueur"),
                                IntegerArgumentType.getInteger(ctx, "montant")
                            )))))
                .then(CommandManager.literal("check")
                    .then(CommandManager.argument("joueur", StringArgumentType.word())
                        .executes(ctx -> executerCheck(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "joueur")
                        ))))
        );
    }

    private static int executerGive(ServerCommandSource source, String cible, int montant) {
        EconomyManager.reward(cible, montant, "admin give");
        source.sendFeedback(() -> Text.literal(
            String.format("§a✅ +%d 💎 donnés à %s.", montant, cible)
        ), true);
        ServerPlayerEntity joueurCible = source.getServer().getPlayerManager().getPlayer(cible);
        if (joueurCible != null) {
            joueurCible.sendMessage(Text.literal(
                String.format("§6💰 Un administrateur t'a donné §f§l%d 💎§6.", montant)
            ));
        }
        return 1;
    }

    private static int executerTake(ServerCommandSource source, String cible, int montant) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("player", cible);
        data.put("amount", montant);
        data.put("description", "admin take");
        EventDispatcher.envoyer("ECONOMY_DEDUCT", data);
        source.sendFeedback(() -> Text.literal(
            String.format("§c✅ -%d 💎 retirés à %s.", montant, cible)
        ), true);
        return 1;
    }

    private static int executerCheck(ServerCommandSource source, String cible) {
        source.sendFeedback(() -> Text.literal("§e⏳ Récupération du solde de " + cible + "..."), false);
        EconomyManager.getBalance(cible, source.getServer(), (solde) -> {
            if (solde < 0) {
                source.sendFeedback(() -> Text.literal("§cImpossible de récupérer le solde de " + cible), false);
            } else {
                source.sendFeedback(() -> Text.literal(
                    String.format("§6💰 Solde de §f%s§6 : §f§l%d 💎", cible, solde)
                ), false);
            }
        });
        return 1;
    }
}
