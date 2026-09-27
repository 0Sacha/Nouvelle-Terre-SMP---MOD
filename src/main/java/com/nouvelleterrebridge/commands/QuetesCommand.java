package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.PlayerLevelManager;
import com.nouvelleterrebridge.economy.QuestManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public class QuetesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quetes")
            .requires(src -> src.getPlayer() != null)

            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                NouvelleTerreBridge.sendQuestOpen(player);
                return 1;
            })

            .then(Commands.literal("refresh")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) {
                        QuestManager.forceRefresh(player.getName().getString(), ctx.getSource().getServer());
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§aPool de quêtes régénéré."), false);
                    return 1;
                }))

            .then(Commands.literal("reset")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    QuestManager.reset();
                    PlayerLevelManager.reset();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        EconomieCommand.SEP_RED + "\n" +
                        "§cProgression des quêtes et niveaux réinitialisés.\n" +
                        EconomieCommand.SEP_RED), false);
                    return 1;
                }))
        );
    }
}
