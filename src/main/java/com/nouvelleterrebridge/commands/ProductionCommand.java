package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import com.nouvelleterrebridge.economy.ProductionShopManager;
import com.nouvelleterrebridge.economy.ProductionTracker;
import com.nouvelleterrebridge.economy.ShopThresholds;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;

public class ProductionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("production")

            // /production sans argument : ouvre le GUI (tous les joueurs)
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) {
                    ctx.getSource().sendFeedback(() -> Text.literal("§cCommande joueur uniquement."), false);
                    return 0;
                }
                NouvelleTerreBridge.sendProductionOpen(player);
                return 1;
            })

            .then(CommandManager.literal("reset")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ProductionTracker.reset();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        EconomieCommand.SEP_RED + "\n" +
                        "§cCompteurs de production réinitialisés.\n" +
                        "§7Les annonces automatiques du shop ont été supprimées.\n" +
                        EconomieCommand.SEP_RED), false);
                    return 1;
                }))

            .then(CommandManager.literal("info")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    Map<String, ShopThresholds.Entry> thresholds = ShopThresholds.all();
                    if (thresholds.isEmpty()) {
                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "§eAucun seuil configuré dans seuils-shop.json"), false);
                        return 1;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(EconomieCommand.SEP_GOLD).append("\n");
                    sb.append("§6Production naturelle — seuils\n");
                    sb.append(EconomieCommand.SEP_GOLD).append("\n");
                    for (Map.Entry<String, ShopThresholds.Entry> entry : thresholds.entrySet()) {
                        long count    = ProductionTracker.get(entry.getKey());
                        long seuil    = entry.getValue().seuil;
                        boolean done  = count >= seuil;
                        String name   = entry.getKey().contains(":") ? entry.getKey().split(":")[1] : entry.getKey();
                        sb.append(done ? "§a✅" : "§7⬜");
                        sb.append(" §f").append(name);
                        sb.append(" §8» §f").append(fmt(count));
                        sb.append("§7/").append(fmt(seuil));
                        if (done) sb.append(" §a[DÉBLOQUÉ — ").append(entry.getValue().prix).append("◆/u]");
                        sb.append("\n");
                    }
                    String msg = sb.toString().trim();
                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return 1;
                }))

            .then(CommandManager.literal("recheck")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ProductionShopManager.checkAll();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aVérification des seuils effectuée."), false);
                    return 1;
                }))

            .then(CommandManager.literal("reload")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ShopThresholds.load();
                    ProductionShopManager.checkAll();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aseuils-shop.json rechargé et seuils re-vérifiés."), false);
                    return 1;
                }))
        );
    }

    private static String fmt(long n) {
        return String.format("%,d", n).replace(',', ' ');
    }
}
