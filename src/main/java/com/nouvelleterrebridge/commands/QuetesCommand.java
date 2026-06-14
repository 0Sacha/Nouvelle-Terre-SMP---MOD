package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.economy.QuestManager;
import com.nouvelleterrebridge.network.QuestNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuetesCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("quetes")
            .requires(src -> src.getPlayer() != null)

            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                sendQuestOpen(player);
                return 1;
            })

            .then(CommandManager.literal("refresh")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    QuestManager.reload();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aquetes-templates.json rechargé."), false);
                    return 1;
                }))

            .then(CommandManager.literal("reset")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    QuestManager.reset();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        EconomieCommand.SEP_RED + "\n" +
                        "§cProgression des quêtes réinitialisée.\n" +
                        EconomieCommand.SEP_RED), false);
                    return 1;
                }))
        );
    }

    public static void sendQuestOpen(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeQuestData(buf, player.getName().getString());
        ServerPlayNetworking.send(player, QuestNetworking.QUEST_OPEN, buf);
    }

    public static void writeQuestData(PacketByteBuf buf, String playerName) {
        List<com.nouvelleterrebridge.economy.Quest> all = QuestManager.getQuests();
        Map<Integer, Integer> progress  = QuestManager.getPlayerProgress(playerName);
        Set<Integer>          completed = QuestManager.getPlayerCompleted(playerName);

        buf.writeInt(all.size());
        for (com.nouvelleterrebridge.economy.Quest q : all) {
            buf.writeInt(q.id);
            buf.writeString(q.type);
            buf.writeString(q.target);
            buf.writeInt(q.quantity);
            buf.writeInt(q.reward);
            buf.writeString(q.label);
        }

        // progress des quêtes acceptées
        Map<Integer, Integer> accepted = new java.util.HashMap<>();
        for (int qid : progress.keySet()) {
            if (!completed.contains(qid)) accepted.put(qid, progress.get(qid));
        }
        buf.writeInt(accepted.size());
        for (Map.Entry<Integer, Integer> e : accepted.entrySet()) {
            buf.writeInt(e.getKey());
            buf.writeInt(e.getValue());
        }

        buf.writeInt(completed.size());
        for (int id : completed) buf.writeInt(id);
    }
}
