package com.nouvelleterrebridge;

import com.nouvelleterrebridge.commands.ConflitCommand;
import com.nouvelleterrebridge.commands.EconomieCommand;
import com.nouvelleterrebridge.commands.EventNarratifCommand;
import com.nouvelleterrebridge.commands.HdvCommand;
import com.nouvelleterrebridge.commands.LierCommand;
import com.nouvelleterrebridge.economy.LocalEconomy;
import com.nouvelleterrebridge.economy.KillRewards;
import com.nouvelleterrebridge.economy.PlaytimeTracker;
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
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

            // Copier les données avant de changer de thread
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
                default -> result = "§cAction inconnue.";
            }

            server.execute(() -> sendHdvResult(player, result));
        });
    }

    public static void sendHdvResult(ServerPlayerEntity player, String message) {
        boolean ok = !message.contains("§c");
        PacketByteBuf resp = PacketByteBufs.create();
        resp.writeBoolean(ok);
        resp.writeString(message);
        resp.writeInt(LocalEconomy.getInstance().getBalance(player.getName().getString()));
        writeListings(resp);
        ServerPlayNetworking.send(player, HdvNetworking.HDV_RESULT, resp);
    }

    public static PacketByteBuf buildHdvOpenPacket(ServerPlayerEntity player) {
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
}
