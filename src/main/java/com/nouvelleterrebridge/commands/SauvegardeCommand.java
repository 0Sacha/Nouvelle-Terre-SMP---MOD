package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nouvelleterrebridge.economy.SauvegardeFichier;
import com.nouvelleterrebridge.economy.ServerShopPriceManager;
import com.nouvelleterrebridge.economy.ShopThresholds;
import com.nouvelleterrebridge.service.ServiceNetworkHandler;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * /nt-sauvegarde — consulter et restaurer les copies de l'économie.
 *
 * Le mod sauvegarde automatiquement les fichiers sensibles avant chaque opération
 * irréversible (migration des prix, purge du marché). Sans cette commande, revenir
 * en arrière obligeait à arrêter le serveur et à copier les fichiers à la main.
 *
 * Réservé au niveau op maximum : restaurer réécrit l'état économique du serveur.
 */
public final class SauvegardeCommand {

    /** Sauvegardes proposées à l'autocomplétion, la plus récente en tête. */
    private static final SuggestionProvider<CommandSourceStack> SAUVEGARDES =
        (ctx, builder) -> {
            for (String nom : SauvegardeFichier.lister()) builder.suggest(nom);
            return builder.buildFuture();
        };

    private SauvegardeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nt-sauvegarde")
            .requires(src -> src.hasPermission(ServiceNetworkHandler.NIVEAU_ADMIN))
            .executes(ctx -> lister(ctx.getSource()))
            .then(Commands.literal("restaurer")
                .then(Commands.argument("fichier", StringArgumentType.string())
                    .suggests(SAUVEGARDES)
                    .executes(ctx -> restaurer(ctx.getSource(),
                        StringArgumentType.getString(ctx, "fichier"))))));
    }

    private static int lister(CommandSourceStack source) {
        List<String> copies = SauvegardeFichier.lister();
        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_GOLD), false);
        if (copies.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  §7Aucune sauvegarde pour l'instant."), false);
            source.sendSuccess(() -> Component.literal(
                "  §8Elles sont créées automatiquement avant chaque migration de prix"), false);
            source.sendSuccess(() -> Component.literal("  §8ou purge du marché."), false);
            source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_GOLD), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("  §6§lSauvegardes disponibles §7("
            + copies.size() + ")"), false);
        source.sendSuccess(() -> Component.literal("  §8De la plus récente à la plus ancienne — clique pour restaurer"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // 12 suffisent à couvrir les manipulations récentes sans noyer le chat
        for (String nom : copies.stream().limit(12).toList()) {
            MutableComponent ligne = Component.literal("  §f· §e" + nom)
                .withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/nt-sauvegarde restaurer \"" + nom + "\""))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("§7Cliquer pour préparer la restauration"))));
            source.sendSuccess(() -> ligne, false);
        }
        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_GOLD), false);
        return 1;
    }

    private static int restaurer(CommandSourceStack source, String nom) {
        String cible = SauvegardeFichier.restaurer(nom);
        if (cible == null) {
            source.sendFailure(Component.literal("Sauvegarde introuvable ou illisible : " + nom));
            return 0;
        }

        // Recharger en mémoire : copier le fichier ne suffit pas, les gestionnaires
        // travaillent sur leur copie chargée au démarrage.
        String detail;
        if (cible.startsWith("seuils-shop")) {
            ShopThresholds.rechargerRestaure();
            detail = "Prix et seuils restaurés, migration neutralisée.";
        } else if (cible.startsWith("server-shop-prices")) {
            ServerShopPriceManager.load();
            detail = "État du marché restauré (flux et demande).";
        } else {
            detail = "§eFichier restauré, mais un redémarrage est nécessaire pour le prendre en compte.";
        }

        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_GREEN), false);
        source.sendSuccess(() -> Component.literal("  §a✅ Restauré §f" + cible), true);
        source.sendSuccess(() -> Component.literal("  §7" + detail), false);
        source.sendSuccess(() -> Component.literal("  §8L'état précédent a été sauvegardé avant d'être remplacé."), false);
        source.sendSuccess(() -> Component.literal(EconomieCommand.SEP_GREEN), false);
        return 1;
    }
}
