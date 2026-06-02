package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.nouvelleterrebridge.economy.EconomyManager;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.shop.MarketListing;
import com.nouvelleterrebridge.shop.MarketManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Commandes du marché (HDV) en jeu.
 *
 *  /vendre <quantité> <prix>   — vend l'item en main
 *  /marche [page]              — liste les annonces
 *  /acheter <id>               — achète une annonce
 *  /mesventes                  — voir ses propres annonces
 *  /annuler <id>               — annuler sa propre annonce
 */
public class MarcheCommand {

    private static final int PAR_PAGE = 6;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        // /vendre <quantité> <prix>
        dispatcher.register(
            CommandManager.literal("vendre")
                .then(CommandManager.argument("quantite", IntegerArgumentType.integer(1, 64))
                    .then(CommandManager.argument("prix", IntegerArgumentType.integer(1))
                        .executes(ctx -> executerVendre(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "quantite"),
                            IntegerArgumentType.getInteger(ctx, "prix")
                        ))))
        );

        // /marche [page]
        dispatcher.register(
            CommandManager.literal("marche")
                .executes(ctx -> executerMarche(ctx.getSource(), 1))
                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executerMarche(
                        ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "page")
                    )))
        );

        // /acheter <id>
        dispatcher.register(
            CommandManager.literal("acheter")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                    .executes(ctx -> executerAcheter(
                        ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "id")
                    )))
        );

        // /mesventes
        dispatcher.register(
            CommandManager.literal("mesventes")
                .executes(ctx -> executerMesVentes(ctx.getSource()))
        );

        // /annuler <id>
        dispatcher.register(
            CommandManager.literal("annuler")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                    .executes(ctx -> executerAnnuler(
                        ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "id")
                    )))
        );
    }

    // ── /vendre ──────────────────────────────────────────────────────────────

    private static int executerVendre(ServerCommandSource source, int quantite, int prix) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs."));
            return 0;
        }

        ItemStack main = joueur.getMainHandStack();
        if (main.isEmpty()) {
            joueur.sendMessage(Text.literal("§cTiens l'item à vendre dans ta main !"));
            return 0;
        }
        if (main.getCount() < quantite) {
            joueur.sendMessage(Text.literal(
                String.format("§cTu n'as que §f%d§c exemplaire(s) de cet item.", main.getCount())
            ));
            return 0;
        }

        String itemId = Registries.ITEM.getId(main.getItem()).toString();
        String nomItem = main.getName().getString();

        // Retire les items de l'inventaire
        main.decrement(quantite);

        // Crée l'annonce
        MarketListing annonce = MarketManager.getInstance().addListing(
            joueur.getName().getString(), itemId, quantite, prix
        );

        joueur.sendMessage(Text.literal(String.format(
            "§a✅ Annonce §f#%d §acréée : §f%dx %s §apour §f%d 💎/unité §a(total : §f%d 💎§a).",
            annonce.id, quantite, nomItem, prix, annonce.getTotal()
        )));

        // Notifie Discord
        Map<String, Object> data = new HashMap<>();
        data.put("player", joueur.getName().getString());
        data.put("item", itemId);
        data.put("quantity", quantite);
        data.put("price", prix);
        EventDispatcher.envoyer("SALE_POSTED", data);

        return 1;
    }

    // ── /marche ───────────────────────────────────────────────────────────────

    private static int executerMarche(ServerCommandSource source, int page) {
        List<MarketListing> toutes = MarketManager.getInstance().getAll();

        if (toutes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e📦 Le marché est vide pour le moment."), false);
            return 1;
        }

        int totalPages = (int) Math.ceil(toutes.size() / (double) PAR_PAGE);
        int pageClamped = Math.min(Math.max(page, 1), totalPages);
        int debut = (pageClamped - 1) * PAR_PAGE;
        List<MarketListing> slice = toutes.subList(debut, Math.min(debut + PAR_PAGE, toutes.size()));

        source.sendFeedback(() -> Text.literal(
            String.format("§6═══ Marché (%d/%d) ══ §7/acheter <id>§6 ═══", pageClamped, totalPages)
        ), false);

        for (MarketListing a : slice) {
            String nomItem = nomItemFormatted(a.item);
            source.sendFeedback(() -> Text.literal(String.format(
                "§f#%d §7| §f%dx %s §7| §f%d 💎§7/u §7| total §f%d 💎 §7| §e%s",
                a.id, a.quantity, nomItem, a.pricePerUnit, a.getTotal(), a.seller
            )), false);
        }

        if (totalPages > 1) {
            source.sendFeedback(() -> Text.literal(
                String.format("§7Page suivante : §f/marche %d", pageClamped + 1)
            ), false);
        }

        return 1;
    }

    // ── /acheter ──────────────────────────────────────────────────────────────

    private static int executerAcheter(ServerCommandSource source, int id) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs."));
            return 0;
        }

        String pseudo = joueur.getName().getString();
        Optional<MarketListing> opt = MarketManager.getInstance().getListing(id);

        if (opt.isEmpty()) {
            joueur.sendMessage(Text.literal(String.format("§cAnnonce §f#%d §cintrouvable.", id)));
            return 0;
        }

        MarketListing annonce = opt.get();

        if (annonce.seller.equalsIgnoreCase(pseudo)) {
            joueur.sendMessage(Text.literal("§cTu ne peux pas acheter ta propre annonce. Utilise §f/annuler " + id));
            return 0;
        }

        String nomItem = nomItemFormatted(annonce.item);
        joueur.sendMessage(Text.literal(String.format(
            "§e⏳ Achat de §f%dx %s §epour §f%d 💎§e...",
            annonce.quantity, nomItem, annonce.getTotal()
        )));

        EconomyManager.transfer(pseudo, annonce.seller, annonce.getTotal(), source.getServer(), (success, message) -> {
            if (!success) {
                joueur.sendMessage(Text.literal("§c❌ " + message));
                return;
            }

            // Donne les items à l'acheteur
            Item item = Registries.ITEM.get(Identifier.tryParse(annonce.item));
            int restant = annonce.quantity;
            while (restant > 0) {
                int stack = Math.min(restant, item.getMaxCount());
                ItemStack itemStack = new ItemStack(item, stack);
                if (!joueur.getInventory().insertStack(itemStack)) {
                    // Inventaire plein → drop au sol
                    joueur.dropItem(itemStack, false);
                }
                restant -= stack;
            }

            // Supprime l'annonce
            MarketManager.getInstance().removeListing(annonce.id);

            // Notifie le vendeur s'il est en ligne
            ServerPlayerEntity vendeur = source.getServer().getPlayerManager().getPlayer(annonce.seller);
            if (vendeur != null) {
                vendeur.sendMessage(Text.literal(String.format(
                    "§a💰 §f%s§a a acheté ton annonce §f#%d §a(§f%dx %s§a) pour §f%d 💎§a.",
                    pseudo, annonce.id, annonce.quantity, nomItem, annonce.getTotal()
                )));
            }

            joueur.sendMessage(Text.literal(String.format(
                "§a✅ Tu as acheté §f%dx %s §apour §f%d 💎§a. Bon usage !",
                annonce.quantity, nomItem, annonce.getTotal()
            )));

            // Notifie Discord
            Map<String, Object> data = new HashMap<>();
            data.put("seller", annonce.seller);
            data.put("buyer", pseudo);
            data.put("item", annonce.item);
            data.put("quantity", annonce.quantity);
            data.put("total", annonce.getTotal());
            EventDispatcher.envoyer("SALE_COMPLETED", data);
        });

        return 1;
    }

    // ── /mesventes ────────────────────────────────────────────────────────────

    private static int executerMesVentes(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs."));
            return 0;
        }

        List<MarketListing> miennes = MarketManager.getInstance().getBySeller(joueur.getName().getString());

        if (miennes.isEmpty()) {
            joueur.sendMessage(Text.literal("§eAucune annonce active. Utilise §f/vendre <qté> <prix>§e."));
            return 1;
        }

        joueur.sendMessage(Text.literal("§6═══ Tes ventes actives ══ §7/annuler <id>§6 ═══"));
        for (MarketListing a : miennes) {
            String nomItem = nomItemFormatted(a.item);
            joueur.sendMessage(Text.literal(String.format(
                "§f#%d §7| §f%dx %s §7| §f%d 💎§7/u §7| total §f%d 💎",
                a.id, a.quantity, nomItem, a.pricePerUnit, a.getTotal()
            )));
        }

        return 1;
    }

    // ── /annuler ──────────────────────────────────────────────────────────────

    private static int executerAnnuler(ServerCommandSource source, int id) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs."));
            return 0;
        }

        String pseudo = joueur.getName().getString();
        Optional<MarketListing> opt = MarketManager.getInstance().getListing(id);

        if (opt.isEmpty()) {
            joueur.sendMessage(Text.literal(String.format("§cAnnonce §f#%d §cintrouvable.", id)));
            return 0;
        }

        MarketListing annonce = opt.get();

        if (!annonce.seller.equalsIgnoreCase(pseudo)) {
            // Admin peut annuler n'importe quelle annonce
            if (!source.hasPermissionLevel(2)) {
                joueur.sendMessage(Text.literal("§cCe n'est pas ton annonce."));
                return 0;
            }
        }

        // Rend les items au vendeur
        String nomItem = nomItemFormatted(annonce.item);
        Item item = Registries.ITEM.get(Identifier.tryParse(annonce.item));
        int restant = annonce.quantity;
        while (restant > 0) {
            int stack = Math.min(restant, item.getMaxCount());
            ItemStack itemStack = new ItemStack(item, stack);
            if (!joueur.getInventory().insertStack(itemStack)) {
                joueur.dropItem(itemStack, false);
            }
            restant -= stack;
        }

        MarketManager.getInstance().removeListing(annonce.id);

        joueur.sendMessage(Text.literal(String.format(
            "§a✅ Annonce §f#%d §aannulée. Tu as récupéré §f%dx %s§a.",
            annonce.id, annonce.quantity, nomItem
        )));

        return 1;
    }

    // ── Utilitaire ────────────────────────────────────────────────────────────

    private static String nomItemFormatted(String itemId) {
        return itemId.replace("minecraft:", "").replace("_", " ");
    }
}
