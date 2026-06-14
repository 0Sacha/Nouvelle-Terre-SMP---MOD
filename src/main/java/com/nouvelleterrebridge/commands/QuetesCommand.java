package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.PlayerLevelManager;
import com.nouvelleterrebridge.economy.QuestManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class QuetesCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("quetes")
            .requires(src -> src.getPlayer() != null)

            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                NouvelleTerreBridge.sendQuestOpen(player);
                return 1;
            })

            .then(CommandManager.literal("refresh")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player != null) {
                        QuestManager.forceRefresh(player.getName().getString(), ctx.getSource().getServer());
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aPool de quêtes régénéré."), false);
                    return 1;
                }))

            .then(CommandManager.literal("reset")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    QuestManager.reset();
                    PlayerLevelManager.reset();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        EconomieCommand.SEP_RED + "\n" +
                        "§cProgression des quêtes et niveaux réinitialisés.\n" +
                        EconomieCommand.SEP_RED), false);
                    return 1;
                }))
        );
    }
}
