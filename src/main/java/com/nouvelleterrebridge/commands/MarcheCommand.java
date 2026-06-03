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
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Toutes les commandes du marché (HDV) regroupées sous /marche.
 *
 *   /marche                       → liste des vendeurs actifs (cliquables)
 *   /marche <joueur>              → boutique d'un vendeur (items + boutons acheter)
 *   /marche vendre <qte> <prix>   → vendre l'item en main
 *   /marche acheter <qte> <item>  → acheter au meilleur prix
 *   /marche annonces              → voir ses propres annonces
 *   /marche retirer <id>          → retirer une annonce et récupérer ses items
 */
public class MarcheCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("marche")

                // /marche → accueil : liste des vendeurs
                .executes(ctx -> executerAccueil(ctx.getSource()))

                // /marche <joueur> → boutique du joueur (autocomplétion sur vendeurs actifs)
                .then(CommandManager.argument("joueur", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        MarketManager.getInstance().getAll().stream()
                            .map(l -> l.seller).distinct()
                            .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> executerBoutique(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "joueur"))))

                // /marche vendre <qte> <prix>
                .then(CommandManager.literal("vendre")
                    .then(CommandManager.argument("quantite", IntegerArgumentType.integer(1, 64))
                        .then(CommandManager.argument("prix", IntegerArgumentType.integer(1))
                            .executes(ctx -> executerVendre(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "quantite"),
                                IntegerArgumentType.getInteger(ctx, "prix"))))))

                // /marche acheter <qte> <item>
                .then(CommandManager.literal("acheter")
                    .then(CommandManager.argument("quantite", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("item", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                String pseudo = ctx.getSource().getName();
                                MarketManager.getInstance().getAll().stream()
                                    .filter(l -> !l.seller.equalsIgnoreCase(pseudo))
                                    .collect(Collectors.toMap(
                                        l -> l.item,
                                        l -> l,
                                        (a, b) -> a.pricePerUnit <= b.pricePerUnit ? a : b
                                    ))
                                    .values().forEach(l -> {
                                        String nom = FrenchItemNames.toDisplay(l.item);
                                        boolean nomResout = l.item.equals(FrenchItemNames.toMinecraftId(nom));
                                        String valeur = nomResout ? nom.toLowerCase() : l.item;
                                        builder.suggest(valeur, Text.literal(nom + " · " + l.pricePerUnit + "💎/u"));
                                    });
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executerAcheter(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "item"),
                                IntegerArgumentType.getInteger(ctx, "quantite"))))))

                // /marche annonces
                .then(CommandManager.literal("annonces")
                    .executes(ctx -> executerMesAnnonces(ctx.getSource())))

                // /marche retirer <id>
                .then(CommandManager.literal("retirer")
                    .then(CommandManager.argument("cible", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity joueur))
                                return builder.buildFuture();
                            String pseudo = joueur.getName().getString();
                            for (MarketListing l : MarketManager.getInstance().getBySeller(pseudo)) {
                                String nom = FrenchItemNames.toDisplay(l.item);
                                String tip = "#" + l.id + " · " + l.quantity + "x " + nom + " · " + l.pricePerUnit + "💎/u";
                                builder.suggest(String.valueOf(l.id), Text.literal(tip));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executerRetirer(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "cible")))))
        );
    }

    // ── /marche → accueil : liste des vendeurs ────────────────────────────────

    private static int executerAccueil(ServerCommandSource source) {
        List<MarketListing> toutes = MarketManager.getInstance().getAll();

        if (toutes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e📦 Le marché est vide pour le moment."), false);
            return 1;
        }

        // Grouper par vendeur
        Map<String, List<MarketListing>> parVendeur = toutes.stream()
            .collect(Collectors.groupingBy(l -> l.seller));

        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GOLD), false);
        source.sendFeedback(() -> Text.literal("      §6§l🏪 §f§lMarché de Nouvelle Terre"), false);
        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GOLD), false);

        parVendeur.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String vendeur = entry.getKey();
                int nbAnnonces = entry.getValue().size();
                int nbItems = entry.getValue().stream().mapToInt(l -> l.quantity).sum();

                MutableText nomCliquable = Text.literal("§f§l" + vendeur)
                    .styled(s -> s
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Text.literal("§7Cliquer pour voir la boutique de §f" + vendeur)))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/marche " + vendeur)));

                source.sendFeedback(() -> Text.literal("  §6🏪 ")
                    .append(nomCliquable)
                    .append(Text.literal(String.format("  §8— §7%d annonce%s · %d item%s",
                        nbAnnonces, nbAnnonces > 1 ? "s" : "",
                        nbItems, nbItems > 1 ? "s" : ""))), false);
            });

        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GOLD), false);
        return 1;
    }

    // ── /marche <joueur> → boutique d'un vendeur ──────────────────────────────

    private static int executerBoutique(ServerCommandSource source, String vendeur) {
        List<MarketListing> annonces = MarketManager.getInstance().getBySeller(vendeur);

        if (annonces.isEmpty()) {
            source.sendFeedback(() -> Text.literal(
                "§e" + vendeur + " n'a aucune annonce active."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GOLD), false);
        source.sendFeedback(() -> Text.literal("    §6§l🏪 §f§lBoutique de §6§l" + vendeur), false);
        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GOLD), false);

        String acheteur = source.getName();

        for (MarketListing l : annonces) {
            String nom = FrenchItemNames.toDisplay(l.item);
            boolean nomResout = l.item.equals(FrenchItemNames.toMinecraftId(nom));
            String valeurAchat = nomResout ? nom.toLowerCase() : l.item;

            // Bouton [ACHETER] cliquable → pré-remplit /marche acheter 1 <item>
            MutableText bouton = Text.literal(" §a[ACHETER]")
                .styled(s -> s
                    .withBold(true)
                    .withColor(Formatting.GREEN)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("§7Cliquer pour acheter §f" + nom + "\n§7Prix : §f" + l.pricePerUnit + "💎/u")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/marche acheter 1 " + valeurAchat)));

            boolean estMoi = vendeur.equalsIgnoreCase(acheteur);
            MutableText ligne = Text.literal(String.format(
                "  §8#%d §7%dx §f§l%s §7· §f%d💎§7/u §8(total §f%d💎§8)",
                l.id, l.quantity, nom, l.pricePerUnit, l.getTotal()
            ));

            if (!estMoi) ligne = ligne.append(bouton);

            final MutableText ligneFinal = ligne;
            source.sendFeedback(() -> ligneFinal, false);
        }

        source.sendFeedback(() -> Text.literal(EconomieCommand.SEP_GOLD), false);
        return 1;
    }

    // ── /marche vendre <qte> <prix> ───────────────────────────────────────────

    private static int executerVendre(ServerCommandSource source, int quantite, int prix) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        ItemStack main = joueur.getMainHandStack();
        if (main.isEmpty()) {
            joueur.sendMessage(Text.literal("§cTiens l'item à vendre dans ta main !")); return 0;
        }
        if (main.getCount() < quantite) {
            joueur.sendMessage(Text.literal(String.format(
                "§cTu n'as que §f%d§c exemplaire(s) en main.", main.getCount()))); return 0;
        }

        String itemId  = Registries.ITEM.getId(main.getItem()).toString();
        String nomItem = FrenchItemNames.toDisplay(itemId);

        main.decrement(quantite);
        MarketListing annonce = MarketManager.getInstance().addListing(
            joueur.getName().getString(), itemId, quantite, prix);

        joueur.sendMessage(Text.literal(String.format(
            "§a✅ Annonce §f#%d §acréée : §f%dx %s §apour §f%d💎/u §a(total §f%d💎§a).",
            annonce.id, quantite, nomItem, prix, annonce.getTotal()
        )));

        source.getServer().getPlayerManager().broadcast(Text.literal(String.format(
            "§6[Marché] §e%s §7vend §f%dx %s §7· §f%d💎/u — §f/marche %s",
            joueur.getName().getString(), quantite, nomItem, prix,
            joueur.getName().getString()
        )), false);

        Map<String, Object> data = new HashMap<>();
        data.put("player", joueur.getName().getString());
        data.put("item", itemId);
        data.put("quantity", quantite);
        data.put("price", prix);
        data.put("id", annonce.id);
        EventDispatcher.envoyer("SALE_POSTED", data);
        return 1;
    }

    // ── /marche acheter <qte> <item> ─────────────────────────────────────────

    private static int executerAcheter(ServerCommandSource source, String itemInput, int quantiteVoulue) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        String pseudo = joueur.getName().getString();
        String itemId = FrenchItemNames.toMinecraftId(itemInput);
        if (itemId == null) {
            joueur.sendMessage(Text.literal(String.format(
                "§cItem §f%s §cinconnu. Vérifie avec §f/marche§c.", itemInput)));
            return 0;
        }

        String nomItem = FrenchItemNames.toDisplay(itemId);

        List<MarketListing> annonces = MarketManager.getInstance().getAll().stream()
            .filter(l -> l.item.equalsIgnoreCase(itemId) && !l.seller.equalsIgnoreCase(pseudo))
            .sorted(Comparator.comparingInt(l -> l.pricePerUnit))
            .collect(Collectors.toList());

        if (annonces.isEmpty()) {
            joueur.sendMessage(Text.literal(String.format(
                "§cAucune annonce disponible pour §f%s§c.", nomItem)));
            return 0;
        }

        int stockTotal = annonces.stream().mapToInt(l -> l.quantity).sum();
        if (stockTotal < quantiteVoulue) {
            joueur.sendMessage(Text.literal(String.format(
                "§cStock insuffisant — seulement §f%d§c/%d dispo pour §f%s§c.",
                stockTotal, quantiteVoulue, nomItem)));
            return 0;
        }

        int coutTotal = 0;
        int restantCalc = quantiteVoulue;
        for (MarketListing l : annonces) {
            int pris = Math.min(restantCalc, l.quantity);
            coutTotal += pris * l.pricePerUnit;
            restantCalc -= pris;
            if (restantCalc == 0) break;
        }

        LocalEconomy eco = LocalEconomy.getInstance();
        if (eco.getBalance(pseudo) < coutTotal) {
            joueur.sendMessage(Text.literal(String.format(
                "§c❌ Solde insuffisant — tu as §f%d💎§c, il te faut §f%d💎§c.",
                eco.getBalance(pseudo), coutTotal)));
            return 0;
        }

        Item itemObj = Registries.ITEM.get(Identifier.tryParse(itemId));
        int restantADonner = quantiteVoulue;

        for (MarketListing annonce : annonces) {
            if (restantADonner <= 0) break;
            int pris = Math.min(restantADonner, annonce.quantity);
            int cout = pris * annonce.pricePerUnit;

            eco.transfer(pseudo, annonce.seller, cout);

            int aDistribuer = pris;
            while (aDistribuer > 0) {
                int sz = Math.min(aDistribuer, itemObj.getMaxCount());
                ItemStack stack = new ItemStack(itemObj, sz);
                if (!joueur.getInventory().insertStack(stack)) joueur.dropItem(stack, false);
                aDistribuer -= sz;
            }

            int nouvelleQte = annonce.quantity - pris;
            MarketManager.getInstance().updateQuantity(annonce.id, nouvelleQte);

            ServerPlayerEntity vend = source.getServer().getPlayerManager().getPlayer(annonce.seller);
            if (vend != null) vend.sendMessage(Text.literal(String.format(
                "§a💰 §f%s§a t'a acheté §f%dx %s§a pour §f%d💎§a !%s Solde : §f%d💎§a.",
                pseudo, pris, nomItem, cout,
                nouvelleQte > 0 ? " §7(§f" + nouvelleQte + " restants§7)" : " §7(stock épuisé)",
                eco.getBalance(annonce.seller)
            )));

            Map<String, Object> data = new HashMap<>();
            data.put("seller", annonce.seller);
            data.put("buyer", pseudo);
            data.put("item", annonce.item);
            data.put("quantity", pris);
            data.put("total", cout);
            data.put("id", annonce.id);
            EventDispatcher.envoyer("SALE_COMPLETED", data);

            restantADonner -= pris;
        }

        joueur.sendMessage(Text.literal(String.format(
            "§a✅ Tu as acheté §f%dx %s §apour §f%d💎§a au total. Solde restant : §f%d💎§a.",
            quantiteVoulue, nomItem, coutTotal, eco.getBalance(pseudo)
        )));
        return 1;
    }

    // ── /marche annonces ──────────────────────────────────────────────────────

    private static int executerMesAnnonces(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }
        // Redirige vers la boutique du joueur lui-même
        return executerBoutique(source, joueur.getName().getString());
    }

    // ── /marche retirer <id> ──────────────────────────────────────────────────

    private static int executerRetirer(ServerCommandSource source, String cibleInput) {
        if (!(source.getEntity() instanceof ServerPlayerEntity joueur)) {
            source.sendError(Text.literal("Commande réservée aux joueurs.")); return 0;
        }

        String pseudo = joueur.getName().getString();
        Optional<MarketListing> opt = Optional.empty();

        try {
            int id = Integer.parseInt(cibleInput.trim());
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
            String itemId = FrenchItemNames.toMinecraftId(cibleInput);
            if (itemId == null) {
                joueur.sendMessage(Text.literal(String.format(
                    "§cItem §f%s §cinconnu. Utilise §f/marche annonces §cpour voir tes annonces.", cibleInput)));
                return 0;
            }
            opt = MarketManager.getInstance().getBySellerAndItem(pseudo, itemId);
            if (opt.isEmpty()) {
                if (source.hasPermissionLevel(2)) {
                    opt = MarketManager.getInstance().getAll().stream()
                        .filter(l -> l.item.equalsIgnoreCase(itemId)).findFirst();
                    if (opt.isEmpty()) {
                        joueur.sendMessage(Text.literal(
                            "§cAucune annonce trouvée pour §f" + FrenchItemNames.toDisplay(itemId)));
                        return 0;
                    }
                } else {
                    joueur.sendMessage(Text.literal(String.format(
                        "§cTu n'as pas d'annonce pour §f%s§c. Utilise §f/marche annonces§c.",
                        FrenchItemNames.toDisplay(itemId))));
                    return 0;
                }
            }
        }

        MarketListing annonce = opt.get();
        String nomItem = FrenchItemNames.toDisplay(annonce.item);

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
            "§a✅ Annonce §f#%d §aretirée. Tu as récupéré §f%dx %s§a.", annonce.id, annonce.quantity, nomItem)));

        source.getServer().getPlayerManager().broadcast(Text.literal(String.format(
            "§6[Marché] §e%s §7a retiré §f%dx %s §7du marché.", annonce.seller, annonce.quantity, nomItem
        )), false);

        Map<String, Object> data = new HashMap<>();
        data.put("seller", annonce.seller);
        data.put("item", annonce.item);
        data.put("quantity", annonce.quantity);
        data.put("id", annonce.id);
        EventDispatcher.envoyer("SALE_CANCELLED", data);
        return 1;
    }
}
