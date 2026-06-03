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
import java.util.stream.Collectors;

/**
 * Toutes les commandes du marché (HDV) regroupées sous /marche.
 *
 *   /marche [page]                           → liste les annonces
 *   /marche vendre <qte> <prix>              → vendre l'item en main
 *   /marche acheter <vendeur> <qte> <item>   → acheter
 *   /marche annonces                         → voir ses propres annonces
 *   /marche retirer <item|id>                → annuler et récupérer ses items
 */
public class MarcheCommand {

    private static final int PAR_PAGE = 6;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("marche")

                // /marche [page]
                .executes(ctx -> executerListe(ctx.getSource(), 1))
                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executerListe(
                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"))))

                // /marche vendre <qte> <prix>
                .then(CommandManager.literal("vendre")
                    .then(CommandManager.argument("quantite", IntegerArgumentType.integer(1, 64))
                        .then(CommandManager.argument("prix", IntegerArgumentType.integer(1))
                            .executes(ctx -> executerVendre(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "quantite"),
                                IntegerArgumentType.getInteger(ctx, "prix"))))))

                // /marche acheter <item> <qte>
                .then(CommandManager.literal("acheter")
                    .then(CommandManager.argument("item", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String pseudo = ctx.getSource().getName();
                            // Une suggestion par item distinct dispo sur le marché (pas ses propres annonces)
                            MarketManager.getInstance().getAll().stream()
                                .filter(l -> !l.seller.equalsIgnoreCase(pseudo))
                                .collect(java.util.stream.Collectors.toMap(
                                    l -> l.item,
                                    l -> l,
                                    (a, b) -> a.pricePerUnit <= b.pricePerUnit ? a : b // garde le moins cher
                                ))
                                .values().forEach(l -> {
                                    String nom = FrenchItemNames.toDisplay(l.item);
                                    boolean nomResout = l.item.equals(FrenchItemNames.toMinecraftId(nom));
                                    String valeur = nomResout ? nom.toLowerCase() : l.item;
                                    String tip = nom + " · " + l.pricePerUnit + "💎/u (meilleur prix)";
                                    builder.suggest(valeur, Text.literal(tip));
                                });
                            return builder.buildFuture();
                        })
                        .then(CommandManager.argument("quantite", IntegerArgumentType.integer(1))
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
                            // Une suggestion par annonce : l'ID numérique, tooltip = détails lisibles
                            for (MarketListing l : MarketManager.getInstance().getBySeller(pseudo)) {
                                String nom = FrenchItemNames.toDisplay(l.item);
                                String tip = "#" + l.id + " · " + l.quantity + "x " + nom
                                             + " · " + l.pricePerUnit + "💎/u";
                                builder.suggest(String.valueOf(l.id), Text.literal(tip));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executerRetirer(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "cible")))))
        );
    }

    // ── /marche [page] ────────────────────────────────────────────────────────

    private static int executerListe(ServerCommandSource source, int page) {
        List<MarketListing> toutes = MarketManager.getInstance().getAll();
        if (toutes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e📦 Le marché est vide pour le moment."), false);
            return 1;
        }

        int total = (int) Math.ceil(toutes.size() / (double) PAR_PAGE);
        int p = Math.min(Math.max(page, 1), total);
        List<MarketListing> slice = toutes.subList((p - 1) * PAR_PAGE, Math.min(p * PAR_PAGE, toutes.size()));

        source.sendFeedback(() -> Text.literal(String.format(
            "§6═══ Marché (%d/%d) ══ §7/marche acheter <vendeur> <qté> <item>§6 ═══", p, total
        )), false);

        for (MarketListing a : slice) {
            String nom = FrenchItemNames.toDisplay(a.item);
            source.sendFeedback(() -> Text.literal(String.format(
                "§e%s §7| §f%dx %s §7| §f%d 💎§7/u §7| total §f%d 💎",
                a.seller, a.quantity, nom, a.pricePerUnit, a.getTotal()
            )), false);
        }

        if (p < total) {
            final int suivante = p + 1;
            source.sendFeedback(() -> Text.literal("§7Page suivante : §f/marche " + suivante), false);
        }
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
            "§6[Marché] §e%s §7vend §f%dx %s §7· §f%d💎/u — §f/marche acheter %s %d %s",
            joueur.getName().getString(), quantite, nomItem, prix,
            joueur.getName().getString(), quantite, nomItem.toLowerCase()
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

    // ── /marche acheter <item> <qte> ─────────────────────────────────────────
    // Achète automatiquement chez le(s) vendeur(s) le moins cher.

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

        // Collecte toutes les annonces pour cet item (sauf les siennes), triées par prix croissant
        List<MarketListing> annonces = MarketManager.getInstance().getAll().stream()
            .filter(l -> l.item.equalsIgnoreCase(itemId) && !l.seller.equalsIgnoreCase(pseudo))
            .sorted(java.util.Comparator.comparingInt(l -> l.pricePerUnit))
            .collect(java.util.stream.Collectors.toList());

        if (annonces.isEmpty()) {
            joueur.sendMessage(Text.literal(String.format(
                "§cAucune annonce disponible pour §f%s§c.", nomItem)));
            return 0;
        }

        // Calcule le stock total dispo et le coût total au meilleur prix
        int stockTotal = annonces.stream().mapToInt(l -> l.quantity).sum();
        if (stockTotal < quantiteVoulue) {
            joueur.sendMessage(Text.literal(String.format(
                "§cStock insuffisant — seulement §f%d§c/%d dispo pour §f%s§c.",
                stockTotal, quantiteVoulue, nomItem)));
            return 0;
        }

        // Calcul du coût réel (peut traverser plusieurs vendeurs)
        int coutTotal = 0;
        int restantACalculer = quantiteVoulue;
        for (MarketListing l : annonces) {
            int pris = Math.min(restantACalculer, l.quantity);
            coutTotal += pris * l.pricePerUnit;
            restantACalculer -= pris;
            if (restantACalculer == 0) break;
        }

        LocalEconomy eco = LocalEconomy.getInstance();
        int solde = eco.getBalance(pseudo);
        if (solde < coutTotal) {
            joueur.sendMessage(Text.literal(String.format(
                "§c❌ Solde insuffisant — tu as §f%d💎§c, il te faut §f%d💎§c.",
                solde, coutTotal)));
            return 0;
        }

        // Exécute les achats vendeur par vendeur
        Item itemObj = Registries.ITEM.get(Identifier.tryParse(itemId));
        int restantADonner = quantiteVoulue;

        for (MarketListing annonce : annonces) {
            if (restantADonner <= 0) break;

            int pris = Math.min(restantADonner, annonce.quantity);
            int cout = pris * annonce.pricePerUnit;

            eco.transfer(pseudo, annonce.seller, cout);

            // Donne les items
            int aDistribuer = pris;
            while (aDistribuer > 0) {
                int sz = Math.min(aDistribuer, itemObj.getMaxCount());
                ItemStack stack = new ItemStack(itemObj, sz);
                if (!joueur.getInventory().insertStack(stack)) joueur.dropItem(stack, false);
                aDistribuer -= sz;
            }

            int nouvelleQte = annonce.quantity - pris;
            MarketManager.getInstance().updateQuantity(annonce.id, nouvelleQte);

            // Notifie le vendeur s'il est connecté
            final String vendeur = annonce.seller;
            final int prisFinal = pris;
            final int coutFinal = cout;
            ServerPlayerEntity vend = source.getServer().getPlayerManager().getPlayer(vendeur);
            if (vend != null) vend.sendMessage(Text.literal(String.format(
                "§a💰 §f%s§a t'a acheté §f%dx %s§a pour §f%d💎§a !%s Solde : §f%d💎§a.",
                pseudo, prisFinal, nomItem, coutFinal,
                nouvelleQte > 0 ? " §7(§f" + nouvelleQte + " restants§7)" : " §7(stock épuisé)",
                eco.getBalance(vendeur)
            )));

            // Sync Discord
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

        List<MarketListing> miennes = MarketManager.getInstance().getBySeller(joueur.getName().getString());
        if (miennes.isEmpty()) {
            joueur.sendMessage(Text.literal(
                "§eAucune annonce active. Utilise §f/marche vendre <qté> <prix>§e.")); return 1;
        }

        joueur.sendMessage(Text.literal("§6═══ Tes annonces ══ §7/marche retirer <id|item>§6 ═══"));
        for (MarketListing a : miennes) {
            String nom = FrenchItemNames.toDisplay(a.item);
            joueur.sendMessage(Text.literal(String.format(
                "§7#§f%d §7| §f%dx %s §7| §f%d💎§7/u §7| total §f%d💎",
                a.id, a.quantity, nom, a.pricePerUnit, a.getTotal()
            )));
        }
        return 1;
    }

    // ── /marche retirer <item|id> ─────────────────────────────────────────────

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
