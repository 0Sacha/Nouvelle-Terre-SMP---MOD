package com.nouvelleterrebridge;

import com.nouvelleterrebridge.commands.BankCommand;
import com.nouvelleterrebridge.commands.ConflitCommand;
import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.commands.EventNarratifCommand;
import com.nouvelleterrebridge.commands.HdvCommand;
import com.nouvelleterrebridge.commands.LierCommand;
import com.nouvelleterrebridge.commands.PayCommand;
import com.nouvelleterrebridge.commands.ProductionCommand;
import com.nouvelleterrebridge.economy.Loan;
import com.nouvelleterrebridge.economy.LoanManager;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.KillRewards;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.economy.ProductionShopManager;
import com.nouvelleterrebridge.economy.ProductionTracker;
import com.nouvelleterrebridge.economy.RecurringTransfer;
import com.nouvelleterrebridge.economy.RecurringTransferManager;
import com.nouvelleterrebridge.economy.ShopThresholds;
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
import com.nouvelleterrebridge.network.HdvNetworking;
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
import java.util.stream.Collectors;

public class NouvelleTerreBridge implements ModInitializer {

    public static final String MOD_ID = "nouvelle-terre-bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ModConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("[NouvelleTerreBridge] Initialisation du mod...");

        config = ModConfig.charger();
        LOGGER.info("[NouvelleTerreBridge] Configuration chargée : url={}", config.getBotUrl());

        EventQueue.getInstance().charger();
        EventDispatcher.init(config);

        ServerEvents.register();
        PlayerEvents.register();
        KillRewards.register();
        PlaytimeTracker.register();
        RecurringTransferManager.register();
        LoanManager.register();

        ShopThresholds.load();
        ProductionTracker.load();
        ProductionShopManager.checkAll();

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld sw)) return;
            List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, blockEntity, player, player.getMainHandStack());
            for (ItemStack drop : drops) {
                String id = Registries.ITEM.getId(drop.getItem()).toString();
                ProductionTracker.add(id, drop.getCount());
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HdvCommand.register(dispatcher);
            BankCommand.register(dispatcher);
            EconomieCommand.register(dispatcher);
            PayCommand.register(dispatcher);
            LierCommand.register(dispatcher);
            ConflitCommand.register(dispatcher);
            EventNarratifCommand.register(dispatcher);
            ProductionCommand.register(dispatcher);
        });

        registerHdvNetworking();
        registerBankNetworking();

        // Envoie le solde au joueur dès qu'il est en jeu
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> sendBalanceToPlayer(handler.getPlayer())));

        LOGGER.info("[NouvelleTerreBridge] Mod initialisé avec succès.");
    }

    private void registerHdvNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_ACTION, (server, player, handler, buf, responseSender) -> {
            int type = buf.readInt();

            final String result;
            switch (type) {
                case HdvNetworking.ACTION_BUY -> {
                    String itemId = buf.readString();
                    int qty = buf.readInt();
                    result = MarketActions.buy(player, itemId, qty);
                }
                case HdvNetworking.ACTION_SELL -> {
                    String itemId = buf.readString();
                    int qty = buf.readInt();
                    int price = buf.readInt();
                    String err = MarketActions.sellByItemId(player, itemId, qty, price);
                    result = err != null ? err : "§a✅ Annonce publiée avec succès !";
                }
                case HdvNetworking.ACTION_WITHDRAW -> {
                    int listingId = buf.readInt();
                    result = MarketActions.withdraw(player, listingId);
                }
                case HdvNetworking.ACTION_TRANSFER -> {
                    String target = buf.readString();
                    int amount = buf.readInt();
                    String sender = player.getName().getString();
                    if (sender.equalsIgnoreCase(target)) {
                        result = "§cVous ne pouvez pas vous envoyer des fonds.";
                        break;
                    }
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
                case HdvNetworking.ACTION_RECURRING_CREATE -> {
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
                case HdvNetworking.ACTION_RECURRING_CANCEL -> {
                    int id = buf.readInt();
                    boolean ok = RecurringTransferManager.getInstance().cancel(id, player.getName().getString());
                    result = ok ? "§a✅ Virement récurrent annulé." : "§cVirement introuvable.";
                }
                default -> result = "§cAction inconnue.";
            }

            server.execute(() -> sendHdvResult(player, result, server));
        });
    }

    public static void sendBalanceToPlayer(ServerPlayerEntity player) {
        int balance = LocalEconomy.getInstance().getBalance(player.getName().getString());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(balance);
        ServerPlayNetworking.send(player, HdvNetworking.NT_BALANCE, buf);
    }

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
        }
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
                case BankNetworking.ACTION_LOAN_CREATE -> {
                    String borrower    = buf.readString();
                    int amount         = buf.readInt();
                    int durationDays   = buf.readInt();
                    int penaltyBase    = buf.readInt();
                    int penaltyIncrease = buf.readInt();
                    String lender = player.getName().getString();
                    if (lender.equalsIgnoreCase(borrower)) {
                        result = "§cVous ne pouvez pas vous accorder un credit.";
                    } else if (!LocalEconomy.getInstance().estConnu(borrower)) {
                        result = "§cJoueur inconnu.";
                    } else if (amount <= 0 || durationDays <= 0 || penaltyBase <= 0) {
                        result = "§cValeurs invalides.";
                    } else {
                        String err = LoanManager.getInstance().add(lender, borrower, amount, durationDays, penaltyBase, penaltyIncrease);
                        if (err != null) {
                            result = "§c" + err;
                        } else {
                            result = "§a✅ Credit de " + amount + " ◆ accorde a §f" + borrower + "§a !";
                            server.execute(() -> {
                                ServerPlayerEntity bp = server.getPlayerManager().getPlayer(borrower);
                                if (bp != null) bp.sendMessage(Text.literal(
                                    "§a[Nouvelle Terre] §f" + lender + " §avous a accorde un credit de §f" + amount + " ◆§a !"));
                            });
                        }
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

        // Stats économiques
        Map<String, Integer> allBalances = eco.getAllBalances();
        int totalShards = allBalances.values().stream().mapToInt(Integer::intValue).filter(v -> v > 0).sum();
        buf.writeInt(totalShards);
        buf.writeInt(allBalances.size());

        // Classement top 10
        Map<String, String> casing = buildCasingMap(server, eco);
        List<Map.Entry<String, Integer>> top = allBalances.entrySet().stream()
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

        // Joueurs connus (dropdown)
        List<String> known = eco.getSoldesKeys().stream()
            .filter(k -> !k.equalsIgnoreCase(name))
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

    private static Map<String, String> buildCasingMap(MinecraftServer server, LocalEconomy eco) {
        Map<String, String> casing = new HashMap<>();
        server.getPlayerManager().getPlayerList().forEach(p ->
            casing.putIfAbsent(p.getName().getString().toLowerCase(), p.getName().getString()));
        MarketManager.getInstance().getAll().forEach(l ->
            casing.putIfAbsent(l.seller.toLowerCase(), l.seller));
        return casing;
    }

}
