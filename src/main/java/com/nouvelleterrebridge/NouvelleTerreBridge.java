package com.nouvelleterrebridge;

import com.nouvelleterrebridge.commands.ConflitCommand;
import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.commands.EventNarratifCommand;
import com.nouvelleterrebridge.commands.HdvCommand;
import com.nouvelleterrebridge.commands.LierCommand;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.KillRewards;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
import com.nouvelleterrebridge.economy.TransactionLog;
import com.nouvelleterrebridge.events.PlayerEvents;
import com.nouvelleterrebridge.events.ServerEvents;
import com.nouvelleterrebridge.events.TerritoryEvents;
import com.nouvelleterrebridge.http.EventDispatcher;
import com.nouvelleterrebridge.http.EventQueue;
import com.nouvelleterrebridge.network.HdvNetworking;
import com.nouvelleterrebridge.shop.MarketActions;
import com.nouvelleterrebridge.shop.MarketListing;
import com.nouvelleterrebridge.shop.MarketManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        TerritoryEvents.register();
        KillRewards.register();
        PlaytimeTracker.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HdvCommand.register(dispatcher);
            EconomieCommand.register(dispatcher);
            LierCommand.register(dispatcher);
            ConflitCommand.register(dispatcher);
            EventNarratifCommand.register(dispatcher);
        });

        registerHdvNetworking();

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
                case HdvNetworking.ACTION_ADMIN_SALARY -> {
                    int count = buf.readInt();
                    List<String> targets = new ArrayList<>();
                    for (int i = 0; i < count; i++) targets.add(buf.readString());
                    if (!player.hasPermissionLevel(2)) {
                        result = "§cPermission insuffisante.";
                    } else if (targets.isEmpty()) {
                        result = "§cAucun joueur sélectionné.";
                    } else {
                        for (String t : targets) {
                            LocalEconomy.getInstance().addShards(t, PlaytimeTracker.getSalaireBase());
                        }
                        result = "§a✅ Salaire versé à " + targets.size() + " joueur(s).";
                    }
                }
                default -> result = "§cAction inconnue.";
            }

            server.execute(() -> sendHdvResult(player, result, server));
        });
    }

    public static void sendHdvResult(ServerPlayerEntity player, String message, MinecraftServer server) {
        boolean ok = !message.contains("§c");
        PacketByteBuf resp = PacketByteBufs.create();
        resp.writeBoolean(ok);
        resp.writeString(message);
        resp.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeListings(resp);
        writeProfileData(resp, player, server);
        ServerPlayNetworking.send(player, HdvNetworking.HDV_RESULT, resp);
    }

    public static PacketByteBuf buildHdvOpenPacket(ServerPlayerEntity player, MinecraftServer server) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeListings(buf);
        writeProfileData(buf, player, server);
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

    private static void writeProfileData(PacketByteBuf buf, ServerPlayerEntity player, MinecraftServer server) {
        buf.writeInt(PlaytimeTracker.getTicksUntilReward(player.getUuid()));
        buf.writeInt(PlaytimeTracker.getTicksUntilSalary(player.getUuid()));
        List<TransactionLog.Entry> txs = TransactionLog.getLast(player.getName().getString(), 15);
        buf.writeInt(txs.size());
        for (TransactionLog.Entry e : txs) {
            buf.writeInt(e.type());
            buf.writeString(e.label());
            buf.writeInt(e.amount());
            buf.writeLong(e.timestamp());
        }

        // isOp
        buf.writeBoolean(player.hasPermissionLevel(2));

        // Known players with best-effort proper casing (online + market sellers override lowercase economy keys)
        Set<String> knownLower = LocalEconomy.getInstance().getSoldesKeys();
        Map<String, String> casing = server.getPlayerManager().getPlayerList().stream()
            .collect(Collectors.toMap(p -> p.getName().getString().toLowerCase(), p -> p.getName().getString(), (a, b) -> a));
        MarketManager.getInstance().getAll().forEach(l -> casing.putIfAbsent(l.seller.toLowerCase(), l.seller));
        List<String> known = knownLower.stream()
            .map(k -> casing.getOrDefault(k, k))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        buf.writeInt(known.size());
        for (String p : known) buf.writeString(p);

        // Online players (for admin salary selector)
        List<String> online = server.getPlayerManager().getPlayerList().stream()
            .map(p -> p.getName().getString())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        buf.writeInt(online.size());
        for (String p : online) buf.writeString(p);
    }
}
