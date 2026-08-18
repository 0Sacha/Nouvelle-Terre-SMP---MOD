package com.nouvelleterrebridge;

import com.nouvelleterrebridge.commands.BankCommand;
import com.nouvelleterrebridge.commands.ConflitCommand;
import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.commands.EventNarratifCommand;
import com.nouvelleterrebridge.commands.HdvCommand;
import com.nouvelleterrebridge.commands.LierCommand;
import com.nouvelleterrebridge.commands.PayCommand;
import com.nouvelleterrebridge.commands.ProductionCommand;
import com.nouvelleterrebridge.commands.QuetesCommand;
import com.nouvelleterrebridge.commands.RegistreCommand;
import com.nouvelleterrebridge.commands.ShopCommand;
import com.nouvelleterrebridge.network.RegistreNetworking;
import com.nouvelleterrebridge.commands.WikiCommand;
import com.nouvelleterrebridge.economy.FirstJoinTracker;
import com.nouvelleterrebridge.economy.PlayerLevelManager;
import com.nouvelleterrebridge.economy.QuestManager;
import com.nouvelleterrebridge.network.QuestNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import com.nouvelleterrebridge.economy.Loan;
import com.nouvelleterrebridge.economy.LoanManager;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.KillRewards;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.economy.PlacedBlockTracker;
import com.nouvelleterrebridge.economy.ShardDenominations;
import com.nouvelleterrebridge.economy.ProductionShopManager;
import com.nouvelleterrebridge.economy.ProductionTracker;
import com.nouvelleterrebridge.economy.RecurringTransfer;
import com.nouvelleterrebridge.economy.RecurringTransferManager;
import com.nouvelleterrebridge.economy.ShopThresholds;
import com.nouvelleterrebridge.economy.ServerShopActions;
import com.nouvelleterrebridge.economy.ServerShopPriceManager;
import com.nouvelleterrebridge.economy.TransactionLog;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import com.nouvelleterrebridge.events.PlayerEvents;
import com.nouvelleterrebridge.events.ServerEvents;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.http.EventQueue;
import com.nouvelleterrebridge.network.BankNetworking;
import com.nouvelleterrebridge.network.ConflitNetworking;
import com.nouvelleterrebridge.network.HdvNetworking;
import com.nouvelleterrebridge.network.HubNetworking;
import com.nouvelleterrebridge.network.ShopNetworking;
import com.nouvelleterrebridge.network.ProductionNetworking;
import com.nouvelleterrebridge.market.FrenchItemNames;
import com.nouvelleterrebridge.market.MarketActions;
import com.nouvelleterrebridge.market.MarketListing;
import com.nouvelleterrebridge.market.MarketManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NouvelleTerreBridge implements ModInitializer {

    public static final String MOD_ID = "nouvelle-terre-bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ModConfig config;

    /** Cache uuid → nom RP, partagé entre PlayerEvents et le mixin de nommage. */
    public static final ConcurrentHashMap<String, String> nomsRP = new ConcurrentHashMap<>();

    /**
     * Serveur courant, pour les notifications émises depuis du code sans contexte
     * (compteurs de production, par exemple). Null tant que le serveur n'a pas démarré.
     */
    public static volatile MinecraftServer serveur;

    // ── Monnaie physique : coupures de 1 à 100 ◆ ──────────────────────────────
    // Purement du rangement : retirer 5 000 ◆ en pièces de 1 remplissait 78 piles.
    // `shard` garde son identifiant d'origine — le renommer aurait fait disparaître
    // tous les Shards déjà en circulation chez les joueurs.

    /** Shard ◆ — 1 ◆. Retrait via /bank, dépôt par clic droit. */
    public static final net.minecraft.item.Item SHARD     = coupure(1);
    public static final net.minecraft.item.Item SHARD_5   = coupure(5);
    public static final net.minecraft.item.Item SHARD_10  = coupure(10);
    public static final net.minecraft.item.Item SHARD_20  = coupure(20);
    public static final net.minecraft.item.Item SHARD_50  = coupure(50);
    public static final net.minecraft.item.Item SHARD_100 = coupure(100);

    private static net.minecraft.item.Item coupure(int valeur) {
        return new com.nouvelleterrebridge.item.ShardItem(
            new net.minecraft.item.Item.Settings().rarity(net.minecraft.util.Rarity.UNCOMMON), valeur);
    }

    /** Parchemin — terminal portatif ouvrant le hub des fenêtres du mod. */
    public static final net.minecraft.item.Item PARCHEMIN = new com.nouvelleterrebridge.item.ParcheminItem(
        new net.minecraft.item.Item.Settings()
            .maxCount(1)
            .fireproof()
            .rarity(net.minecraft.util.Rarity.RARE));

    @Override
    public void onInitialize() {
        LOGGER.info("[NouvelleTerreBridge] Initialisation du mod...");

        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier(MOD_ID, "shard"), SHARD);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier(MOD_ID, "shard_5"), SHARD_5);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier(MOD_ID, "shard_10"), SHARD_10);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier(MOD_ID, "shard_20"), SHARD_20);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier(MOD_ID, "shard_50"), SHARD_50);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier(MOD_ID, "shard_100"), SHARD_100);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier(MOD_ID, "parchemin"), PARCHEMIN);
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
            .modifyEntriesEvent(net.minecraft.item.ItemGroups.INGREDIENTS)
            .register(entries -> {
                // Une entrée par coupure : entries.add() n'a pas de variante varargs
                entries.add(SHARD);
                entries.add(SHARD_5);
                entries.add(SHARD_10);
                entries.add(SHARD_20);
                entries.add(SHARD_50);
                entries.add(SHARD_100);
            });
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
            .modifyEntriesEvent(net.minecraft.item.ItemGroups.TOOLS)
            .register(entries -> entries.add(PARCHEMIN));

        config = ModConfig.charger();
        LOGGER.info("[NouvelleTerreBridge] Configuration chargée : url={}", config.getBotUrl());

        EventQueue.getInstance().charger();
        EventDispatcher.init(config);

        // Référence serveur partagée — enregistrée ici et non dans ServerEvents,
        // qui se désactive entièrement si les événements bot sont coupés en config.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED
            .register(s -> serveur = s);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
            .register(s -> serveur = null);

        ServerEvents.register();
        PlayerEvents.register();
        KillRewards.register();
        PlaytimeTracker.register();
        RecurringTransferManager.register();
        LoanManager.register();

        ShopThresholds.load();
        ServerShopPriceManager.load();
        ProductionTracker.load();
        ProductionShopManager.purgerAnnoncesLegacy();
        PlayerLevelManager.load();
        QuestManager.load();
        FirstJoinTracker.getInstance().load();
        com.nouvelleterrebridge.economy.DailyBonusTracker.load();

        // Blocs cassés → drops réels (fortune/silk touch inclus)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld sw)) return;

            // Un bloc posé par un joueur puis recassé n'est pas de la production :
            // sans ce garde-fou, poser/casser le même bloc en boucle débloquait
            // n'importe quel item au Shop Serveur.
            if (PlacedBlockTracker.estPoseParJoueur(world, pos)) return;

            String pName = player.getName().getString();
            List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, blockEntity, player, player.getMainHandStack());
            for (ItemStack drop : drops) {
                String itemId = Registries.ITEM.getId(drop.getItem()).toString();
                ProductionTracker.add(itemId, drop.getCount());
                QuestManager.onItemHarvested(pName, itemId, drop.getCount(), sw.getServer());
            }
        });

        // Mobs tués par un joueur → quêtes KILL
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            String typeId = Registries.ENTITY_TYPE.getId(killedEntity.getType()).toString();
            QuestManager.onMobKilled(player.getName().getString(), typeId, player.getServer());
        });

        // Rollover des quêtes journalières (00h heure réelle, vérifié chaque minute)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(QuestManager::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HdvCommand.register(dispatcher);
            ShopCommand.register(dispatcher);
            BankCommand.register(dispatcher);
            EconomieCommand.register(dispatcher);
            PayCommand.register(dispatcher);
            LierCommand.register(dispatcher);
            ConflitCommand.register(dispatcher);
            EventNarratifCommand.register(dispatcher);
            ProductionCommand.register(dispatcher);
            QuetesCommand.register(dispatcher);
            RegistreCommand.register(dispatcher);
            WikiCommand.register(dispatcher);
        });

        registerHdvNetworking();
        registerBankNetworking();
        registerQuestNetworking();
        registerRegistreNetworking();
        registerProductionNetworking();
        registerConflitNetworking();
        registerHubNetworking();
        registerShopNetworking();

        // Envoie le solde au joueur dès qu'il est en jeu + refresh pool quêtes
        // + garantit qu'il possède son Parchemin
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                sendBalanceToPlayer(handler.getPlayer());
                QuestManager.refreshPlayerPool(handler.getPlayer().getName().getString(), server);
                donnerParcheminSiAbsent(handler.getPlayer());
            }));

        // Le Parchemin est rendu après une mort, même sans keepInventory
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) -> donnerParcheminSiAbsent(newPlayer));

        LOGGER.info("[NouvelleTerreBridge] Mod initialisé avec succès.");
    }

    private void registerHdvNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_ACTION, (server, player, handler, buf, responseSender) -> {
            // ── Lecture du paquet : obligatoirement ici ──
            // Le PacketByteBuf est libéré dès le retour de ce callback : tout doit être
            // extrait maintenant, et rien de ce qui suit ne doit retoucher au buffer.
            int type = buf.readInt();
            final String sItemId;
            final String sNbt;
            final int    sQty, sPrice, sListingId;

            switch (type) {
                case HdvNetworking.ACTION_BUY -> {
                    sItemId = buf.readString(); sQty = buf.readInt(); sNbt = buf.readString();
                    sPrice = 0; sListingId = 0;
                }
                case HdvNetworking.ACTION_SELL -> {
                    sItemId = buf.readString(); sQty = buf.readInt(); sPrice = buf.readInt(); sNbt = buf.readString();
                    sListingId = 0;
                }
                case HdvNetworking.ACTION_WITHDRAW -> {
                    sListingId = buf.readInt();
                    sItemId = ""; sNbt = ""; sQty = 0; sPrice = 0;
                }
                default -> {
                    sItemId = ""; sNbt = ""; sQty = 0; sPrice = 0; sListingId = 0;
                }
            }

            // ── Exécution : obligatoirement sur le thread serveur ──
            // buy/sell/withdraw touchent l'inventaire du joueur. Le faire depuis le thread
            // réseau court-circuite la synchronisation faite au tick : le serveur avait bien
            // l'item enchanté, mais le client se retrouvait avec une pile vierge.
            server.execute(() -> {
                final String result;
                switch (type) {
                    case HdvNetworking.ACTION_BUY -> result = MarketActions.buy(player, sItemId, sQty, sNbt);
                    case HdvNetworking.ACTION_SELL -> {
                        String err = MarketActions.sellByItemId(player, sItemId, sQty, sPrice, sNbt);
                        result = err != null ? err : "§a✅ Annonce publiée avec succès !";
                    }
                    case HdvNetworking.ACTION_WITHDRAW -> result = MarketActions.withdraw(player, sListingId);
                    default -> result = "§cAction inconnue.";
                }
                sendHdvResult(player, result, server);
            });
        });
    }

    public static void sendBalanceToPlayer(ServerPlayerEntity player) {
        int balance = LocalEconomy.getInstance().getBalance(player.getName().getString());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(balance);
        ServerPlayNetworking.send(player, HdvNetworking.NT_BALANCE, buf);
    }

    // Couleurs des toasts NT_TOAST — dupliquées de NotificationHud exprès : le code
    // serveur ne doit pas référencer une classe cliente, même pour des constantes
    // (l'inlining de javac masquait le problème, jusqu'au jour où il ne le fera plus).
    public static final int TOAST_VERT  = 0xFF2EAD6B;
    public static final int TOAST_OR    = 0xFFE8A838;
    public static final int TOAST_ROUGE = 0xFFBF2040;

    public static void sendToast(ServerPlayerEntity player, int color, String... lines) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(color);
        buf.writeInt(lines.length);
        for (String line : lines) buf.writeString(line);
        ServerPlayNetworking.send(player, HdvNetworking.NT_TOAST, buf);
    }

    public static void sendHdvResult(ServerPlayerEntity player, String message, MinecraftServer server) {
        boolean ok = !message.contains("§c");
        PacketByteBuf resp = PacketByteBufs.create();
        resp.writeBoolean(ok);
        resp.writeString(message);
        resp.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeListings(resp);
        ServerPlayNetworking.send(player, HdvNetworking.HDV_RESULT, resp);
    }

    public static PacketByteBuf buildHdvOpenPacket(ServerPlayerEntity player, MinecraftServer server) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeListings(buf);
        return buf;
    }

    private static void writeListings(PacketByteBuf buf) {
        List<MarketListing> listings = MarketManager.getInstance().getAll();
        buf.writeInt(listings.size());
        for (MarketListing l : listings) {
            buf.writeInt(l.id);
            buf.writeString(l.seller);
            buf.writeString(l.item);
            buf.writeInt(l.quantity);
            buf.writeInt(l.pricePerUnit);
            buf.writeString(l.itemNBT != null ? l.itemNBT : "");
        }
    }

    // ── Hub (Parchemin) ──────────────────────────────────────────────────────

    /** Donne le Parchemin au joueur s'il ne l'a pas déjà (connexion, respawn). */
    public static void donnerParcheminSiAbsent(ServerPlayerEntity player) {
        if (player == null) return;
        for (net.minecraft.item.ItemStack s : player.getInventory().main)
            if (s.isOf(PARCHEMIN)) return;
        if (player.getInventory().offHand.stream().anyMatch(s -> s.isOf(PARCHEMIN))) return;

        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(PARCHEMIN);
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }

    /** Catalogue du Shop Serveur : tous les items connus des seuils, avec prix d'achat et de rachat. */
    public static PacketByteBuf buildShopOpenPacket(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeShopEntries(buf);
        return buf;
    }

    private static void writeShopEntries(PacketByteBuf buf) {
        // Seuls les items dont la production naturelle a atteint le seuil sont
        // au catalogue : c'est ce qui rend le shop dépendant de l'activité du serveur.
        var debloques = ShopThresholds.all().entrySet().stream()
            .filter(e -> ServerShopActions.estDebloque(e.getKey()))
            .map(java.util.Map.Entry::getKey)
            .sorted()
            .toList();

        buf.writeInt(debloques.size());
        for (String itemId : debloques) {
            var pe = ServerShopPriceManager.getOrCreate(itemId);
            buf.writeString(itemId);
            buf.writeInt(ServerShopPriceManager.getPrice(itemId));
            buf.writeInt(ServerShopPriceManager.getBuybackPrice(itemId));
            buf.writeLong(pe.unitsSold - pe.unitsBought);
        }
    }

    private void registerShopNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(ShopNetworking.SHOP_ACTION, (server, player, handler, buf, responseSender) -> {
            int action    = buf.readInt();
            String itemId = buf.readString();
            int quantity  = buf.readInt();

            server.execute(() -> {
                String result = switch (action) {
                    case ShopNetworking.ACTION_BUY  -> ServerShopActions.buy(player, itemId, quantity);
                    case ShopNetworking.ACTION_SELL -> ServerShopActions.sell(player, itemId, quantity);
                    case ShopNetworking.ACTION_CLAIM_PARCHEMIN -> ServerShopActions.claimParchemin(player);
                    default -> "§cAction inconnue.";
                };

                PacketByteBuf resp = PacketByteBufs.create();
                resp.writeBoolean(!result.contains("§c"));
                resp.writeString(result);
                resp.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
                writeShopEntries(resp);
                ServerPlayNetworking.send(player, ShopNetworking.SHOP_RESULT, resp);
                sendBalanceToPlayer(player);
            });
        });
    }

    private void registerHubNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(HubNetworking.HUB_ACTION, (server, player, handler, buf, responseSender) -> {
            int action = buf.readInt();
            server.execute(() -> {
                switch (action) {
                    case HubNetworking.ACTION_HDV ->
                        ServerPlayNetworking.send(player, HdvNetworking.HDV_OPEN, buildHdvOpenPacket(player, server));
                    case HubNetworking.ACTION_BANK ->
                        ServerPlayNetworking.send(player, BankNetworking.BANK_OPEN, buildBankOpenPacket(player, server));
                    case HubNetworking.ACTION_SHOP ->
                        ServerPlayNetworking.send(player, ShopNetworking.SHOP_OPEN, buildShopOpenPacket(player));
                    case HubNetworking.ACTION_QUETES     -> sendQuestOpen(player);
                    case HubNetworking.ACTION_PRODUCTION -> sendProductionOpen(player);
                    case HubNetworking.ACTION_REGISTRE   -> RegistreCommand.open(player);
                    case HubNetworking.ACTION_CONFLIT    -> ConflitCommand.open(player);
                    case HubNetworking.ACTION_WIKI ->
                        ServerPlayNetworking.send(player, com.nouvelleterrebridge.network.WikiNetworking.WIKI_OPEN,
                                                  PacketByteBufs.empty());
                    default -> LOGGER.warn("[Hub] Action inconnue : {}", action);
                }
            });
        });
    }

    // ── Bank networking ──────────────────────────────────────────────────────

    private void registerBankNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(BankNetworking.BANK_REQUEST, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> ServerPlayNetworking.send(player, BankNetworking.BANK_OPEN, buildBankOpenPacket(player, server)));
        });

        ServerPlayNetworking.registerGlobalReceiver(BankNetworking.BANK_ACTION, (server, player, handler, buf, responseSender) -> {
            int type = buf.readInt();
            final String result;
            switch (type) {
                case BankNetworking.ACTION_LOAN_REQUEST -> {
                    String borrowerName = buf.readString();
                    int amount          = buf.readInt();
                    int durationDays    = buf.readInt();
                    int penaltyBase     = buf.readInt();
                    int penaltyIncrease = buf.readInt();
                    String lender = player.getName().getString();
                    if (lender.equalsIgnoreCase(borrowerName)) {
                        result = "§cVous ne pouvez pas vous preter a vous-meme.";
                    } else if (!LocalEconomy.getInstance().estConnu(borrowerName)) {
                        result = "§cJoueur inconnu.";
                    } else if (amount <= 0 || durationDays <= 0 || penaltyBase <= 0) {
                        result = "§cValeurs invalides.";
                    } else {
                        String err = LoanManager.getInstance().request(borrowerName, lender, amount, durationDays, penaltyBase, penaltyIncrease);
                        if (err != null) {
                            result = "§c" + err;
                        } else {
                            result = "§a✅ Proposition envoyee a §f" + borrowerName + "§a — en attente de son accord.";
                            server.execute(() -> {
                                ServerPlayerEntity bp = server.getPlayerManager().getPlayer(borrowerName);
                                if (bp != null) bp.sendMessage(Text.literal(
                                    "§e[Banque] §f" + lender + " §evous propose un credit de §f" + amount
                                    + " ◆§e (duree " + durationDays + " j, penalite " + penaltyBase
                                    + " ◆/j de retard). §7Ouvre /bank → Credits pour accepter ou refuser."));
                            });
                        }
                    }
                }
                case BankNetworking.ACTION_LOAN_ACCEPT -> {
                    int requestId = buf.readInt();
                    String borrowerName = player.getName().getString();
                    LoanManager.LoanRequest req = LoanManager.getInstance().getRequest(requestId);
                    String err = LoanManager.getInstance().acceptRequest(borrowerName, requestId);
                    if (err != null) {
                        result = "§c" + err;
                    } else {
                        result = "§a✅ Credit accepte — §f" + req.principal + " ◆§a recus de §f" + req.lender
                            + "§a ! A rembourser sous " + req.durationDays + " j.";
                        server.execute(() -> {
                            sendBalanceToPlayer(player);
                            ServerPlayerEntity lp = server.getPlayerManager().getPlayer(req.lender);
                            if (lp != null) {
                                lp.sendMessage(Text.literal(
                                    "§a[Banque] §f" + borrowerName + " §aa accepte votre credit — §f"
                                    + req.principal + " ◆§a transferes."));
                                sendBalanceToPlayer(lp);
                            }
                        });
                    }
                }
                case BankNetworking.ACTION_LOAN_DECLINE -> {
                    int requestId = buf.readInt();
                    String who = player.getName().getString();
                    LoanManager.LoanRequest req = LoanManager.getInstance().getRequest(requestId);
                    String err = LoanManager.getInstance().declineRequest(who, requestId);
                    if (err != null) {
                        result = "§c" + err;
                    } else {
                        boolean estPreteur = req.lender.equalsIgnoreCase(who);
                        result = estPreteur ? "§a✅ Proposition annulee." : "§a✅ Proposition refusee.";
                        String autre = estPreteur ? req.borrower : req.lender;
                        String msg = estPreteur
                            ? "§e[Banque] §f" + who + " §ea annule sa proposition de credit (" + req.principal + " ◆)."
                            : "§c[Banque] §f" + who + " §ca refuse votre proposition de credit (" + req.principal + " ◆).";
                        server.execute(() -> {
                            ServerPlayerEntity op = server.getPlayerManager().getPlayer(autre);
                            if (op != null) op.sendMessage(Text.literal(msg));
                        });
                    }
                }
                case BankNetworking.ACTION_LOAN_REPAY -> {
                    int loanId = buf.readInt();
                    String borrowerName = player.getName().getString();
                    Loan loan = LoanManager.getInstance().getLoan(loanId);
                    String err = LoanManager.getInstance().repay(borrowerName, loanId);
                    if (err != null) {
                        result = "§c" + err;
                    } else {
                        result = "§a✅ Credit rembourse !";
                        if (loan != null) {
                            server.execute(() -> {
                                ServerPlayerEntity lp = server.getPlayerManager().getPlayer(loan.lender);
                                if (lp != null) lp.sendMessage(Text.literal(
                                    "§a[Nouvelle Terre] §f" + borrowerName + " §aa rembourse son credit de §f" + loan.principal + " ◆§a !"));
                            });
                        }
                    }
                }
                case BankNetworking.ACTION_LOAN_FORGIVE -> {
                    int loanId = buf.readInt();
                    String err = LoanManager.getInstance().forgive(player.getName().getString(), loanId);
                    result = err != null ? "§c" + err : "§a✅ Credit pardonne.";
                }
                case BankNetworking.ACTION_TRANSFER -> {
                    String target = buf.readString();
                    int amount = buf.readInt();
                    String sender = player.getName().getString();
                    if (sender.equalsIgnoreCase(target)) {
                        result = "§cVous ne pouvez pas vous envoyer des fonds.";
                    } else {
                        boolean ok = LocalEconomy.getInstance().transfer(sender, target, amount);
                        if (ok) {
                            result = "§a✅ " + amount + " ◆ envoyés à §f" + target + "§a.";
                            server.execute(() -> {
                                ServerPlayerEntity t = server.getPlayerManager().getPlayer(target);
                                if (t != null) t.sendMessage(Text.literal(
                                    "§a[Nouvelle Terre] §f" + sender + " §avous a envoyé §f" + amount + " ◆§a !"));
                            });
                        } else {
                            result = "§cSolde insuffisant ou joueur inconnu.";
                        }
                    }
                }
                case BankNetworking.ACTION_RECURRING_CREATE -> {
                    String to = buf.readString();
                    int amount = buf.readInt();
                    int intervalTicks = buf.readInt();
                    String from = player.getName().getString();
                    if (from.equalsIgnoreCase(to)) {
                        result = "§cVous ne pouvez pas vous faire de virement récurrent.";
                    } else if (!LocalEconomy.getInstance().estConnu(to)) {
                        result = "§cJoueur inconnu.";
                    } else if (amount <= 0) {
                        result = "§cMontant invalide.";
                    } else if (intervalTicks < 1200) {
                        result = "§cIntervalle minimum : 1 minute.";
                    } else {
                        RecurringTransferManager.getInstance().add(from, to, amount, intervalTicks);
                        result = "§a✅ Virement récurrent créé vers §f" + to + "§a !";
                    }
                }
                case BankNetworking.ACTION_RECURRING_CANCEL -> {
                    int id = buf.readInt();
                    boolean ok = RecurringTransferManager.getInstance().cancel(id, player.getName().getString());
                    result = ok ? "§a✅ Virement récurrent annulé." : "§cVirement introuvable.";
                }
                case BankNetworking.ACTION_WITHDRAW_SHARDS -> {
                    int amount = buf.readInt();
                    String name = player.getName().getString();
                    if (amount <= 0) {
                        result = "§cMontant invalide.";
                    } else if (LocalEconomy.getInstance().getBalance(name) < amount) {
                        result = "§cSolde insuffisant.";
                    } else {
                        LocalEconomy.getInstance().removeShards(name, amount);
                        TransactionLog.log(name, TransactionLog.TYPE_TRANSFER_OUT, "Retrait en Shards physiques", amount);
                        result = "§a✅ " + amount + " ◆ retirés en coupures — clic droit dessus pour les redéposer.";
                        // Rendu en grosses coupures d'abord : 5 000 ◆ en pièces de 1
                        // remplissaient 78 piles d'inventaire.
                        server.execute(() -> {
                            ShardDenominations.donner(player, amount);
                            sendBalanceToPlayer(player);
                        });
                    }
                }
                case BankNetworking.ACTION_DEPOSIT_SHARDS -> {
                    int demande = buf.readInt();   // 0 = tout ce qu'il y a dans l'inventaire
                    // Comptage et retrait sur le thread serveur : l'inventaire n'est pas
                    // thread-safe, et le montant réellement déposable en dépend.
                    server.execute(() -> {
                        String name = player.getName().getString();

                        // Valeur réelle de la monnaie portée, toutes coupures confondues
                        int dispo = ShardDenominations.totalEnPoche(player);

                        if (dispo <= 0) {
                            sendBankResult(player, "§cAucun Shard dans votre inventaire.", server);
                            return;
                        }
                        if (demande < 0) {
                            sendBankResult(player, "§cMontant invalide.", server);
                            return;
                        }
                        // Le client borne déjà la saisie, mais il ne fait pas autorité :
                        // on re-plafonne à ce qui est réellement en poche.
                        int aDeposer = demande == 0 ? dispo : Math.min(demande, dispo);

                        // Une coupure ne se coupe pas en deux : si le prélèvement dépasse
                        // le montant voulu, la différence est rendue en petite monnaie.
                        int prelevé = ShardDenominations.retirer(player, aDeposer);
                        int appoint = prelevé - aDeposer;
                        if (appoint > 0) ShardDenominations.donner(player, appoint);

                        LocalEconomy.getInstance().depositShards(name, aDeposer);
                        TransactionLog.log(name, TransactionLog.TYPE_TRANSFER_IN, "Dépôt de Shards physiques", aDeposer);
                        sendBalanceToPlayer(player);
                        sendBankResult(player, "§a✅ " + aDeposer + " ◆ déposés"
                            + (appoint > 0 ? " §7(" + appoint + " ◆ rendus en monnaie)" : "")
                            + " §a— solde : §e" + LocalEconomy.getInstance().getBalance(name) + " ◆", server);
                    });
                    return;
                }
                default -> result = "§cAction inconnue.";
            }
            server.execute(() -> sendBankResult(player, result, server));
        });
    }

    public static void sendBankResult(ServerPlayerEntity player, String message, MinecraftServer server) {
        boolean ok = !message.contains("§c");
        PacketByteBuf resp = PacketByteBufs.create();
        resp.writeBoolean(ok);
        resp.writeString(message);
        writeBankData(resp, player, server);
        ServerPlayNetworking.send(player, BankNetworking.BANK_RESULT, resp);
    }

    public static PacketByteBuf buildBankOpenPacket(ServerPlayerEntity player, MinecraftServer server) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeBankData(buf, player, server);
        return buf;
    }

    private static void writeBankData(PacketByteBuf buf, ServerPlayerEntity player, MinecraftServer server) {
        String name = player.getName().getString();
        LocalEconomy eco = LocalEconomy.getInstance();

        buf.writeInt(eco.getBalance(name));
        buf.writeInt(PlaytimeTracker.getTicksUntilReward(player.getUuid()));

        // Transactions
        List<TransactionLog.Entry> txs = TransactionLog.getLast(name, 20);
        buf.writeInt(txs.size());
        for (TransactionLog.Entry e : txs) {
            buf.writeInt(e.type()); buf.writeString(e.label()); buf.writeInt(e.amount()); buf.writeLong(e.timestamp());
        }

        // Stats économiques — les comptes système ($Serveur) sont exclus
        Map<String, Integer> allBalances = eco.getAllBalances();
        int totalShards = allBalances.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("$"))
            .mapToInt(Map.Entry::getValue).filter(v -> v > 0).sum();
        buf.writeInt(totalShards);
        buf.writeInt((int) allBalances.keySet().stream().filter(k -> !k.startsWith("$")).count());

        // Classement top 10 (hors comptes système)
        Map<String, String> casing = buildCasingMap(server, eco);
        List<Map.Entry<String, Integer>> top = allBalances.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("$"))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        buf.writeInt(top.size());
        for (Map.Entry<String, Integer> e : top) {
            buf.writeString(casing.getOrDefault(e.getKey(), e.getKey()));
            buf.writeInt(e.getValue());
        }

        // Crédits en tant que prêteur
        List<Loan> asLender = LoanManager.getInstance().getLoansAsLender(name);
        buf.writeInt(asLender.size());
        for (Loan l : asLender) writeLoanData(buf, l.borrower, l);

        // Crédits en tant qu'emprunteur
        List<Loan> asBorrower = LoanManager.getInstance().getLoansAsBorrower(name);
        buf.writeInt(asBorrower.size());
        for (Loan l : asBorrower) writeLoanData(buf, l.lender, l);

        // Demandes de crédit reçues (en tant que prêteur, à accepter/refuser)
        List<LoanManager.LoanRequest> reqAsLender = LoanManager.getInstance().getRequestsAsLender(name);
        buf.writeInt(reqAsLender.size());
        for (LoanManager.LoanRequest r : reqAsLender) writeLoanRequest(buf, r.borrower, r);

        // Demandes de crédit envoyées (en tant qu'emprunteur, en attente)
        List<LoanManager.LoanRequest> reqAsBorrower = LoanManager.getInstance().getRequestsAsBorrower(name);
        buf.writeInt(reqAsBorrower.size());
        for (LoanManager.LoanRequest r : reqAsBorrower) writeLoanRequest(buf, r.lender, r);

        // Joueurs connus (dropdown) — comptes système exclus
        List<String> known = eco.getSoldesKeys().stream()
            .filter(k -> !k.equalsIgnoreCase(name) && !k.startsWith("$"))
            .map(k -> casing.getOrDefault(k, k))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        buf.writeInt(known.size());
        for (String p : known) buf.writeString(p);

        // Virements récurrents du joueur
        List<RecurringTransfer> recurring = RecurringTransferManager.getInstance().getForPlayer(name);
        buf.writeInt(recurring.size());
        for (RecurringTransfer rt : recurring) {
            buf.writeInt(rt.id);
            buf.writeString(rt.to);
            buf.writeInt(rt.amount);
            buf.writeInt(rt.intervalTicks);
            buf.writeInt(rt.intervalTicks - rt.ticksSince);
        }
    }

    private static void writeLoanData(PacketByteBuf buf, String other, Loan l) {
        buf.writeInt(l.id);
        buf.writeString(other);
        buf.writeInt(l.principal);
        buf.writeLong(l.dueTimestamp);
        buf.writeInt(l.daysOverdue);
        buf.writeInt(l.totalPenalty);
        buf.writeInt(l.nextPenalty());
        buf.writeBoolean(l.repaid);
    }

    private static void writeLoanRequest(PacketByteBuf buf, String other, LoanManager.LoanRequest r) {
        buf.writeInt(r.id);
        buf.writeString(other);
        buf.writeInt(r.principal);
        buf.writeInt(r.durationDays);
        buf.writeInt(r.penaltyBase);
    }

    private static Map<String, String> buildCasingMap(MinecraftServer server, LocalEconomy eco) {
        Map<String, String> casing = new HashMap<>();
        server.getPlayerManager().getPlayerList().forEach(p ->
            casing.putIfAbsent(p.getName().getString().toLowerCase(), p.getName().getString()));
        MarketManager.getInstance().getAll().forEach(l ->
            casing.putIfAbsent(l.seller.toLowerCase(), l.seller));
        return casing;
    }

    // ── Quest networking ─────────────────────────────────────────────────────

    public static void sendQuestOpen(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeFullQuestData(buf, player.getName().getString());
        ServerPlayNetworking.send(player, QuestNetworking.QUEST_OPEN, buf);
    }

    private void registerQuestNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(QuestNetworking.QUEST_ACTION, (server, player, handler, buf, responseSender) -> {
            int action = buf.readInt();
            int param  = buf.readInt();   // questId or index depending on action
            String pName = player.getName().getString();
            server.execute(() -> {
                String err = switch (action) {
                    case QuestNetworking.ACTION_ACCEPT         -> QuestManager.accept(pName, param, server);
                    case QuestNetworking.ACTION_CLAIM          -> QuestManager.claim(pName, param, player, server);
                    case QuestNetworking.ACTION_CANCEL         -> QuestManager.cancel(pName, param);
                    case QuestNetworking.ACTION_COLLECT        -> QuestManager.collectReward(pName, param, player);
                    case QuestNetworking.ACTION_CANCEL_PENDING -> QuestManager.cancelPending(pName, param);
                    default                                    -> "Action inconnue.";
                };
                boolean ok = err == null;
                sendQuestResult(player, ok, ok ? "§a✅ Mis à jour !" : "§c" + err, server);
            });
        });
    }

    public static void sendQuestResult(ServerPlayerEntity player, boolean ok, String message, MinecraftServer server) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(ok);
        buf.writeString(message);
        writeFullQuestData(buf, player.getName().getString());
        ServerPlayNetworking.send(player, QuestNetworking.QUEST_RESULT, buf);
    }

    private static void writeFullQuestData(PacketByteBuf buf, String playerName) {
        int level = PlayerLevelManager.getLevel(playerName);
        int xp    = PlayerLevelManager.getXp(playerName);
        buf.writeInt(level);
        buf.writeInt(xp);
        buf.writeInt(PlayerLevelManager.xpToNextLevel(level));

        // Quêtes disponibles
        List<com.nouvelleterrebridge.economy.Quest> available = QuestManager.getAvailable(playerName);
        buf.writeInt(available.size());
        for (var q : available) writeQuest(buf, q);

        // Quêtes actives
        List<QuestManager.ActiveQuest> active = QuestManager.getActive(playerName);
        buf.writeInt(active.size());
        for (QuestManager.ActiveQuest aq : active) {
            buf.writeInt(aq.questId);
            writeQuest(buf, aq.snapshot != null ? aq.snapshot : new com.nouvelleterrebridge.economy.Quest());
            buf.writeInt(aq.progress);
            buf.writeBoolean(aq.turnedIn);
            buf.writeInt(aq.groupParticipants.size());
            for (String p : aq.groupParticipants) buf.writeString(p);
        }

        // Récompenses en attente (items à récupérer)
        List<QuestManager.PendingReward> pending = QuestManager.getPending(playerName);
        buf.writeInt(pending.size());
        for (QuestManager.PendingReward pr : pending) {
            buf.writeString(pr.questLabel);
            buf.writeString(pr.rewardItem != null ? pr.rewardItem : "");
            buf.writeInt(pr.rewardItemQty);
            buf.writeLong(pr.completedAt);
        }

        // Acceptations en attente pour les quêtes groupe
        Map<Integer, Integer> gpc = QuestManager.getGroupPendingCounts();
        buf.writeInt(gpc.size());
        for (var e : gpc.entrySet()) {
            buf.writeInt(e.getKey());
            buf.writeInt(e.getValue());
        }

        // Classements
        var topCompleted = QuestManager.getLeaderboardByCompleted(10);
        buf.writeInt(topCompleted.size());
        for (var e : topCompleted) { buf.writeString(e.getKey()); buf.writeInt(e.getValue()); }

        var topLevel = PlayerLevelManager.getLeaderboardByLevel(10);
        buf.writeInt(topLevel.size());
        for (var e : topLevel) { buf.writeString(e.getKey()); buf.writeInt(e.getValue()); }

        // Quête communautaire du jour
        QuestManager.CommunityState cs = QuestManager.getCommunity();
        boolean hasCommunity = cs != null && cs.quest != null;
        buf.writeBoolean(hasCommunity);
        if (hasCommunity) {
            buf.writeString(cs.quest.label);
            buf.writeString(cs.quest.type != null ? cs.quest.type : "");
            buf.writeString(cs.quest.target != null ? cs.quest.target : "");
            buf.writeInt(cs.quest.quantity);
            buf.writeInt(cs.progress);
            buf.writeInt(cs.quest.rewardShards);
            buf.writeBoolean(cs.completed);
            buf.writeInt(QuestManager.getCommunityContribution(playerName));
        }
    }

    // ── Production networking ────────────────────────────────────────────────

    /** Envoie l'état de la production au joueur (ouvre le GUI côté client). */
    public static void sendProductionOpen(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        writeProductionData(buf, player);
        ServerPlayNetworking.send(player, ProductionNetworking.PROD_OPEN, buf);
    }

    private static void writeProductionData(PacketByteBuf buf, ServerPlayerEntity player) {
        buf.writeBoolean(player.hasPermissionLevel(2));
        Map<String, ShopThresholds.Entry> all = ShopThresholds.all();
        buf.writeInt(all.size());
        for (Map.Entry<String, ShopThresholds.Entry> e : all.entrySet()) {
            buf.writeString(e.getKey());
            buf.writeLong(ProductionTracker.get(e.getKey()));
            buf.writeLong(e.getValue().seuil);
            buf.writeInt(e.getValue().prix);
            buf.writeInt(e.getValue().quantite);
            // Le shop ne passe plus par des annonces HDV depuis la 1.3.0 : la mise en
            // vente effective est celle du Shop Serveur, seuil et désactivation compris.
            buf.writeBoolean(ServerShopActions.estDebloque(e.getKey()));
            buf.writeBoolean(e.getValue().desactive);
        }
    }

    private void registerProductionNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(ProductionNetworking.PROD_ACTION, (server, player, handler, buf, responseSender) -> {
            int action    = buf.readInt();
            String itemId = buf.readString();
            int valeur    = buf.readInt();
            server.execute(() -> {
                boolean ok;
                String msg;
                String nomItem = itemId.isEmpty() ? "" : FrenchItemNames.toDisplay(itemId);
                if (!player.hasPermissionLevel(2)) {
                    ok = false; msg = "§cRéservé aux opérateurs.";
                } else if (action == ProductionNetworking.ACTION_RESET) {
                    ProductionTracker.reset();
                    ShopThresholds.resetAll();
                    ok = true; msg = "§a✅ Production remise à zéro : compteurs, seuils et annonces auto.";
                } else if (action == ProductionNetworking.ACTION_RECHECK) {
                    // Le shop lit les seuils en direct depuis la 1.3.0 : il n'y a plus
                    // rien à « re-vérifier », l'action ne sert qu'à renvoyer un état frais.
                    ProductionShopManager.purgerAnnoncesLegacy();
                    ok = true; msg = "§a✅ Données rafraîchies.";
                } else if (action == ProductionNetworking.ACTION_RELOAD) {
                    ShopThresholds.load();
                    ProductionShopManager.purgerAnnoncesLegacy();
                    ok = true; msg = "§a✅ seuils-shop.json rechargé.";
                } else if (action == ProductionNetworking.ACTION_SET_PRICE) {
                    if (valeur <= 0) {
                        ok = false; msg = "§cPrix invalide.";
                    } else if (ShopThresholds.setPrix(itemId, valeur)) {
                        // Le prix de base du shop est une copie figée : sans resync,
                        // la correction resterait sans effet sur un item déjà échangé.
                        ServerShopPriceManager.resyncBasePrices();
                        ok = true; msg = "§a✅ " + nomItem + " : prix fixé à " + valeur + " ◆.";
                    } else {
                        ok = false; msg = "§cItem absent du catalogue.";
                    }
                } else if (action == ProductionNetworking.ACTION_TOGGLE) {
                    Boolean desactive = ShopThresholds.toggleDesactive(itemId);
                    if (desactive == null) {
                        ok = false; msg = "§cItem absent du catalogue.";
                    } else {
                        ok = true;
                        msg = desactive ? "§e⏸ " + nomItem + " retiré de la vente."
                                        : "§a✅ " + nomItem + " remis en vente.";
                    }
                } else if (action == ProductionNetworking.ACTION_DELETE) {
                    if (ShopThresholds.supprimer(itemId)) {
                        ok = true; msg = "§a✅ " + nomItem + " supprimé du catalogue.";
                    } else {
                        ok = false; msg = "§cItem absent du catalogue.";
                    }
                } else {
                    ok = false; msg = "§cAction inconnue.";
                }
                PacketByteBuf resp = PacketByteBufs.create();
                resp.writeBoolean(ok);
                resp.writeString(msg);
                writeProductionData(resp, player);
                ServerPlayNetworking.send(player, ProductionNetworking.PROD_RESULT, resp);
            });
        });
    }

    // ── Conflit networking ───────────────────────────────────────────────────

    private void registerConflitNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(ConflitNetworking.CONFLIT_ACTION, (server, player, handler, buf, responseSender) -> {
            String cible  = buf.readString();
            String raison = buf.readString();
            server.execute(() -> {
                String pseudo = player.getName().getString();
                boolean ok;
                String msg;
                if (pseudo.equalsIgnoreCase(cible)) {
                    ok = false; msg = "Vous ne pouvez pas vous déclarer conflit à vous-même.";
                } else if (raison.trim().length() < 3) {
                    ok = false; msg = "Raison trop courte.";
                } else {
                    ok = true;
                    msg = "⚔ Conflit déclaré contre " + cible + " — le Conseil des Fondateurs est alerté.";
                    Map<String, Object> data = new HashMap<>();
                    data.put("player", pseudo);
                    data.put("target", cible);
                    data.put("reason", raison.trim());
                    EventDispatcher.envoyer("CONFLICT_DECLARED", data);
                }
                PacketByteBuf resp = PacketByteBufs.create();
                resp.writeBoolean(ok);
                resp.writeString(msg);
                ServerPlayNetworking.send(player, ConflitNetworking.CONFLIT_RESULT, resp);
            });
        });
    }

    // ── Registre networking ──────────────────────────────────────────────────

    private void registerRegistreNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(RegistreNetworking.REGISTRE_DETAIL_REQUEST,
            (server, player, handler, buf, responseSender) -> {
                String pseudo = buf.readString();
                EventDispatcher.fetchPersonnageDetail(pseudo, server, detail -> {
                    PacketByteBuf resp = PacketByteBufs.create();
                    if (detail == null) {
                        resp.writeBoolean(false);
                        ServerPlayNetworking.send(player, RegistreNetworking.REGISTRE_DETAIL, resp);
                        return;
                    }
                    resp.writeBoolean(true);
                    resp.writeString(sVal(detail, "nom_rp"));
                    resp.writeString(sVal(detail, "pseudo_mc"));
                    resp.writeBoolean(bVal(detail, "en_ligne"));
                    resp.writeString(sVal(detail, "metier"));
                    resp.writeInt(iVal(detail, "age"));
                    resp.writeString(sVal(detail, "origine"));
                    resp.writeString(sVal(detail, "specialite"));
                    resp.writeString(sVal(detail, "traits"));
                    resp.writeString(sVal(detail, "passe"));
                    resp.writeString(sVal(detail, "description_physique"));
                    resp.writeString(sVal(detail, "description_personnage"));
                    resp.writeString(sVal(detail, "objectifs"));
                    resp.writeString(sVal(detail, "citation"));
                    ServerPlayNetworking.send(player, RegistreNetworking.REGISTRE_DETAIL, resp);
                });
            });
    }

    private static String sVal(Map<String, Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : "";
    }
    private static boolean bVal(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Boolean b && b;
    }
    private static int iVal(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Number n ? n.intValue() : 0;
    }

    private static void writeQuest(PacketByteBuf buf, com.nouvelleterrebridge.economy.Quest q) {
        buf.writeInt(q.id);
        buf.writeString(q.type       != null ? q.type       : "");
        buf.writeString(q.target     != null ? q.target     : "");
        buf.writeInt(q.quantity);
        buf.writeInt(q.levelRequired);
        buf.writeInt(q.maxPlayers);
        buf.writeString(q.rewardType != null ? q.rewardType : "SHARDS");
        buf.writeInt(q.rewardShards);
        buf.writeString(q.rewardItem != null ? q.rewardItem : "");
        buf.writeInt(q.rewardItemQty);
        buf.writeInt(q.rewardXp);
        buf.writeInt(q.costShards);
        buf.writeString(q.label      != null ? q.label      : "");
        buf.writeLong(q.expiresAt);
        List<String> tags = q.tags != null ? q.tags : List.of();
        buf.writeInt(tags.size());
        for (String t : tags) buf.writeString(t);
    }

}
