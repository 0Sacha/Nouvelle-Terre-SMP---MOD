package com.nouvelleterrebridge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.shop.FrenchItemNames;
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
 *  /vendre <qté> <prix>                 — vend l'item en main
 *  /marche [page]                       — liste les annonces
 *  /acheter <vendeur> <qté> <item>      — achète chez un vendeur
 *  /mesventes                           — voir ses annonces actives
 *  /annuler <item>                      — annuler sa propre annonce
 */
public class MarcheCommand {

    private static final int PAR_PAGE = 6;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        // /vendre <qté> <prix>
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
                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")
                    )))
        );

        // /acheter <vendeur> <qté> <item> — autocomplétion vendeur puis item
        dispatcher.register(
            CommandManager.literal("acheter")
                .then(CommandManager.argument("vendeur", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        String input = builder.getRemaining().toLowerCase();
                        MarketManager.getInstance().getAll().stream()
                            .map(l -> l.seller)
                            .distinct()
                            .filter(s -> s.toLowerCase().startsWith(input))
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .then(CommandManager.argument("quantite", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("item", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                String vendeur;
                                try { vendeur = StringArgumentType.getString(ctx, "vendeur"); }
                                catch (Exception e) { return builder.buildFuture(); }
                                String input = builder.getRemaining().toLowerCase();
                                for (MarketListing l : MarketManager.getInstance().getBySeller(vendeur)) {
                                    String nomFr = FrenchItemNames.toDisplay(l.item).toLowerCase();
                                    String mcId  = l.item.replace("minecraft:", "");
                                    String tip   = l.quantity + " dispo · " + l.pricePerUnit + " shard/u";
                                    if (nomFr.startsWith(input))
                                        builder.suggest(nomFr, Text.literal(tip));
                                    if (!mcId.equals(nomFr) && mcId.startsWith(input))
                                        builder.suggest(mcId, Text.literal(tip));
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executerAcheter(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "vendeur"),
                                IntegerArgumentType.getInteger(ctx, "quantite"),
                                StringArgumentType.getString(ctx, "item")
                            )))))
        );

        // /mesventes
        dispatcher.register(
            CommandManager.literal("mesventes")
                .executes(ctx -> executerMesVentes(ctx.getSource()))
        );

        // /annuler <item|id> — autocomplétion sur les propres annonces du joueur
        dispatcher.register(
            CommandManager.literal("annuler")
                .then(CommandManager.argument("item", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity joueur))
                            return builder.buildFuture();
                        String pseudo = joueur.getName().getString();
                        String input  = builder.getRemaining().toLowerCase();
                        for (MarketListing l : MarketManager.getInstance().getBySeller(pseudo)) {
                            String nomFr = FrenchItemNames.toDisplay(l.item).toLowerCase();
                            String mcId  = l.item.replace("minecraft:", "");
                            String idStr = String.valueOf(l.id);
                            String tip   = l.quantity + "x " + FrenchItemNames.toDisplay(l.item)
                                           + " · " + l.pricePerUnit + " shard/u";
                            if (idStr.startsWith(input))
                                builder.suggest(idStr, Text.literal(tip));
                            if (nomFr.startsWith(input))
                                builder.suggest(nomFr, Text.literal(tip));
                            if (!mcId.equals(nomFr) && mcId.startsWith(input))
                                builder.suggest(mcId, Text.literal(tip));
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> executerAnnuler(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "item")
                    )))
        );
    }

    // ── /vendre ──────────────────────────────────────────────────────────────

    private static int executerVendre(ServerCommandSource source, int quantite, int prix) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        ItemStack main = joueur.getMainHandStack();
        if (main.isEmpty()) {
            joueur.sendMessage(Text.literal("§cTiens l'item à vendre dans ta main !")); return 0;
        }
        if (main.getCount() < quantite) {
            joueur.sendMessage(Text.literal(
                String.format("§cTu n'as que §f%d§c exemplaire(s).", main.getCount()))); return 0;
        }

        String itemId  = Registries.ITEM.getId(main.getItem()).toString();
        String nomItem = FrenchItemNames.toDisplay(itemId);

        main.decrement(quantite);

        MarketListing annonce = MarketManager.getInstance().addListing(
                joueur.getName().getString(), itemId, quantite, prix);

        // Confirmation au vendeur
        joueur.sendMessage(Text.literal(String.format(
            "§a✅ Annonce §f#%d §acréée : §f%dx %s §apour §f%d💎/u §a(total §f%d💎§a).",
            annonce.id, quantite, nomItem, prix, annonce.getTotal()
        )));

        // Broadcast à tous les joueurs en ligne
        String broadcast = String.format(
            "§6[Marché] §e%s §7vend §f%dx %s §7· §f%d💎/u §7(total §f%d💎§7) — §f/acheter %s %d %s",
            joueur.getName().getString(), quantite, nomItem, prix, annonce.getTotal(),
            joueur.getName().getString(), quantite, nomItem.toLowerCase()
        );
        source.getServer().getPlayerManager().broadcast(Text.literal(broadcast), false);

        // Sync Discord (async, ne bloque pas)
        Map<String, Object> data = new HashMap<>();
        data.put("player", joueur.getName().getString());
        data.put("item", itemId);
        data.put("quantity", quantite);
        data.put("price", prix);
        data.put("id", annonce.id);
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

        int total = (int) Math.ceil(toutes.size() / (double) PAR_PAGE);
        int p = Math.min(Math.max(page, 1), total);
        List<MarketListing> slice = toutes.subList((p - 1) * PAR_PAGE, Math.min(p * PAR_PAGE, toutes.size()));

        source.sendFeedback(() -> Text.literal(
            String.format("§6═══ Marché (%d/%d) ══ §7/acheter <vendeur> <qté> <item>§6 ═══", p, total)
        ), false);

        for (MarketListing a : slice) {
            String nom = FrenchItemNames.toDisplay(a.item);
            source.sendFeedback(() -> Text.literal(String.format(
                "§e%s §7| §f%dx %s §7| §f%d 💎§7/u §7| total §f%d 💎",
                a.seller, a.quantity, nom, a.pricePerUnit, a.getTotal()
            )), false);
        }

        if (total > 1) {
            final int suivante = p + 1;
            if (p < total) source.sendFeedback(() -> Text.literal(
                "§7Page suivante : §f/marche " + suivante), false);
        }
        return 1;
    }

    // ── /acheter <vendeur> <qté> <item> ──────────────────────────────────────

    private static int executerAcheter(ServerCommandSource source, String vendeur, int quantite, String itemInput) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        String pseudo = joueur.getName().getString();

        // Résolution du nom d'item
        String itemId = FrenchItemNames.toMinecraftId(itemInput);
        if (itemId == null) {
            joueur.sendMessage(Text.literal(
                String.format("§cItem §f%s §cinconnu. Vérifie l'orthographe ou utilise §f/marche§c.", itemInput)));
            return 0;
        }

        String nomItem = FrenchItemNames.toDisplay(itemId);

        Optional<MarketListing> opt = MarketManager.getInstance().getBySellerAndItem(vendeur, itemId);
        if (opt.isEmpty()) {
            joueur.sendMessage(Text.literal(
                String.format("§cAucune annonce de §f%s§c pour §f%s§c. Vérifie avec §f/marche§c.", vendeur, nomItem)));
            return 0;
        }

        MarketListing annonce = opt.get();

        if (annonce.seller.equalsIgnoreCase(pseudo)) {
            joueur.sendMessage(Text.literal("§cTu ne peux pas acheter ta propre annonce. Utilise §f/annuler " + nomItem));
            return 0;
        }
        if (quantite > annonce.quantity) {
            joueur.sendMessage(Text.literal(
                String.format("§f%s§c n'en vend que §f%d§c.", vendeur, annonce.quantity)));
            return 0;
        }

        int total = quantite * annonce.pricePerUnit;
        LocalEconomy eco = LocalEconomy.getInstance();

        int soldeCourant = eco.getBalance(pseudo);
        if (soldeCourant < total) {
            joueur.sendMessage(Text.literal(String.format(
                "§c❌ Solde insuffisant — tu as §f%d💎§c, il te faut §f%d💎§c.",
                soldeCourant, total
            )));
            return 0;
        }

        // Transfert instantané (local, pas d'HTTP)
        eco.transfer(pseudo, annonce.seller, total);

        // Donne les items
        Item item = Registries.ITEM.get(Identifier.tryParse(annonce.item));
        int restant = quantite;
        while (restant > 0) {
            int sz = Math.min(restant, item.getMaxCount());
            ItemStack stack = new ItemStack(item, sz);
            if (!joueur.getInventory().insertStack(stack)) joueur.dropItem(stack, false);
            restant -= sz;
        }

        int nouvelleQte = annonce.quantity - quantite;
        MarketManager.getInstance().updateQuantity(annonce.id, nouvelleQte);

        // Confirmation à l'acheteur
        joueur.sendMessage(Text.literal(String.format(
            "§a✅ Tu as acheté §f%dx %s §aauprès de §f%s§a pour §f%d💎§a ! Solde restant : §f%d💎§a.",
            quantite, nomItem, vendeur, total, eco.getBalance(pseudo)
        )));

        // Notification au vendeur s'il est en ligne
        ServerPlayerEntity vend = source.getServer().getPlayerManager().getPlayer(annonce.seller);
        if (vend != null) vend.sendMessage(Text.literal(String.format(
            "§a💰 §f%s§a t'a acheté §f%dx %s§a pour §f%d💎§a !%s Solde : §f%d💎§a.",
            pseudo, quantite, nomItem, total,
            nouvelleQte > 0 ? " §7(§f" + nouvelleQte + " restants§7)" : " §7(stock épuisé)",
            eco.getBalance(annonce.seller)
        )));

        // Sync Discord (async)
        Map<String, Object> data = new HashMap<>();
        data.put("seller", annonce.seller);
        data.put("buyer", pseudo);
        data.put("item", annonce.item);
        data.put("quantity", quantite);
        data.put("total", total);
        data.put("id", annonce.id);
        EventDispatcher.envoyer("SALE_COMPLETED", data);
        return 1;
    }

    // ── /mesventes ────────────────────────────────────────────────────────────

    private static int executerMesVentes(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        List<MarketListing> miennes = MarketManager.getInstance().getBySeller(joueur.getName().getString());
        if (miennes.isEmpty()) {
            joueur.sendMessage(Text.literal("§eAucune annonce active. Utilise §f/vendre <qté> <prix>§e.")); return 1;
        }

        joueur.sendMessage(Text.literal("§6═══ Tes ventes ══ §7/annuler <item>§6 ═══"));
        for (MarketListing a : miennes) {
            String nom = FrenchItemNames.toDisplay(a.item);
            joueur.sendMessage(Text.literal(String.format(
                "§f%dx %s §7| §f%d 💎§7/u §7| total §f%d 💎",
                a.quantity, nom, a.pricePerUnit, a.getTotal()
            )));
        }
        return 1;
    }

    // ── /annuler <item|id> ────────────────────────────────────────────────────

    private static int executerAnnuler(ServerCommandSource source, String itemInput) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        String pseudo = joueur.getName().getString();
        Optional<MarketListing> opt = Optional.empty();

        // Support de l'ID numérique : /annuler 10
        try {
            int id = Integer.parseInt(itemInput.trim());
            opt = MarketManager.getInstance().getListing(id);
            if (opt.isEmpty()) {
                joueur.sendMessage(Text.literal("§cAucune annonce avec l'ID §f#" + id + "§c."));
                return 0;
            }
            MarketListing ann = opt.get();
            if (!ann.seller.equalsIgnoreCase(pseudo) && !source.hasPermissionLevel(2)) {
                joueur.sendMessage(Text.literal("§cCette annonce appartient à §f" + ann.seller + "§c."));
                return 0;
            }
        } catch (NumberFormatException ignored) {
            // Pas un nombre, on cherche par nom d'item
            String itemId = FrenchItemNames.toMinecraftId(itemInput);
            if (itemId == null) {
                joueur.sendMessage(Text.literal(
                    String.format("§cItem §f%s §cinconnu. Utilise §f/mesventes §cpour voir tes annonces.", itemInput)));
                return 0;
            }
            opt = MarketManager.getInstance().getBySellerAndItem(pseudo, itemId);
            if (opt.isEmpty()) {
                if (source.hasPermissionLevel(2)) {
                    Optional<MarketListing> adminOpt = MarketManager.getInstance().getAll().stream()
                            .filter(l -> l.item.equalsIgnoreCase(itemId)).findFirst();
                    if (adminOpt.isEmpty()) {
                        joueur.sendMessage(Text.literal("§cAucune annonce trouvée pour §f" + FrenchItemNames.toDisplay(itemId)));
                        return 0;
                    }
                    opt = adminOpt;
                } else {
                    joueur.sendMessage(Text.literal(
                        String.format("§cTu n'as pas d'annonce pour §f%s§c. Utilise §f/mesventes§c.", FrenchItemNames.toDisplay(itemId))));
                    return 0;
                }
            }
        }

        MarketListing annonce = opt.get();
        String nomItem = FrenchItemNames.toDisplay(annonce.item);

        // Rend les items
        Item item = Registries.ITEM.get(Identifier.tryParse(annonce.item));
        int restant = annonce.quantity;
        while (restant > 0) {
            int sz = Math.min(restant, item.getMaxCount());
            ItemStack stack = new ItemStack(item, sz);
            if (!joueur.getInventory().insertStack(stack)) joueur.dropItem(stack, false);
            restant -= sz;
        }

        MarketManager.getInstance().removeListing(annonce.id);

        joueur.sendMessage(Text.literal(String.format(
            "§a✅ Annonce §f#%d §aannulée. Tu as récupéré §f%dx %s§a.", annonce.id, annonce.quantity, nomItem)));

        // Broadcast à tous les joueurs
        source.getServer().getPlayerManager().broadcast(Text.literal(String.format(
            "§6[Marché] §e%s §7a retiré §f%dx %s §7du marché.", annonce.seller, annonce.quantity, nomItem
        )), false);

        // Sync Discord (async)
        Map<String, Object> data = new HashMap<>();
        data.put("seller", annonce.seller);
        data.put("item", annonce.item);
        data.put("quantity", annonce.quantity);
        data.put("id", annonce.id);
        EventDispatcher.envoyer("SALE_CANCELLED", data);
        return 1;
    }
}
