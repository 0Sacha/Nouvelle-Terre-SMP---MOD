package com.nouvelleterrebridge;

import com.nouvelleterrebridge.client.BankScreen;
import com.nouvelleterrebridge.client.HdvScreen;
import com.nouvelleterrebridge.network.BankNetworking;
import com.nouvelleterrebridge.network.HdvNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class NouvelleTerreBridgeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // S2C : serveur ouvre le HDV
        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_OPEN, (client, handler, buf, responseSender) -> {
            int balance = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            int ticksReward = buf.readInt();
            List<HdvScreen.TransactionData> transactions = readTransactions(buf);
            List<String> knownPlayers = readStringList(buf);
            List<HdvScreen.RecurringData> recurring = readRecurring(buf);
            client.execute(() -> client.setScreen(
                new HdvScreen(balance, listings, ticksReward, transactions, knownPlayers, recurring)));
        });

        // S2C : vérification de version
        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.NT_VERSION, (client, handler, buf, responseSender) -> {
            String serverVer = buf.readString();
            String clientVer = FabricLoader.getInstance()
                .getModContainer(NouvelleTerreBridge.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
            if (!serverVer.equals(clientVer)) {
                client.execute(() -> {
                    if (client.player == null) return;
                    String url = "https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD/releases/latest";
                    MutableText link = Text.literal("§9§n[Télécharger v" + serverVer + "]")
                        .styled(s -> s
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(url))));
                    MutableText msg = Text.literal("§e[Nouvelle Terre] §cMod obsolète §7— client §f" + clientVer
                            + " §7≠ serveur §f" + serverVer + " §7— ").append(link);
                    client.player.sendMessage(msg, false);
                });
            }
        });

        // ── Bank ──────────────────────────────────────────────────────────────

        ClientPlayNetworking.registerGlobalReceiver(BankNetworking.BANK_OPEN, (client, handler, buf, responseSender) -> {
            BankScreen screen = readBankPacket(buf);
            client.execute(() -> client.setScreen(screen));
        });

        ClientPlayNetworking.registerGlobalReceiver(BankNetworking.BANK_RESULT, (client, handler, buf, responseSender) -> {
            boolean ok      = buf.readBoolean();
            String  message = buf.readString();
            int balance     = buf.readInt();
            int ticksReward = buf.readInt();
            List<BankScreen.TxData>           txs      = readBankTxs(buf);
            int totalShards  = buf.readInt();
            int playerCount  = buf.readInt();
            List<BankScreen.LeaderboardEntry> lb       = readLeaderboard(buf);
            List<BankScreen.LoanData>         asLender = readLoans(buf);
            List<BankScreen.LoanData>         asBorrow = readLoans(buf);
            List<String>                      known    = readStringList(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof BankScreen screen) {
                    screen.handleResult(ok, message, balance, ticksReward, txs,
                        totalShards, playerCount, lb, asLender, asBorrow, known);
                }
            });
        });

        // ── HDV résultat ─────────────────────────────────────────────────────

        // S2C : résultat d'une action
        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_RESULT, (client, handler, buf, responseSender) -> {
            boolean ok      = buf.readBoolean();
            String  message = buf.readString();
            int balance     = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            int ticksReward = buf.readInt();
            List<HdvScreen.TransactionData> transactions = readTransactions(buf);
            List<String> knownPlayers = readStringList(buf);
            List<HdvScreen.RecurringData> recurring = readRecurring(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof HdvScreen screen) {
                    screen.handleResult(ok, message, balance, listings, ticksReward, transactions, knownPlayers, recurring);
                }
            });
        });
    }

    private static List<HdvScreen.ListingData> readListings(PacketByteBuf buf) {
        int count = buf.readInt();
        List<HdvScreen.ListingData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new HdvScreen.ListingData(buf.readInt(), buf.readString(), buf.readString(), buf.readInt(), buf.readInt()));
        return list;
    }

    private static List<HdvScreen.TransactionData> readTransactions(PacketByteBuf buf) {
        int count = buf.readInt();
        List<HdvScreen.TransactionData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new HdvScreen.TransactionData(buf.readInt(), buf.readString(), buf.readInt(), buf.readLong()));
        return list;
    }

    private static List<String> readStringList(PacketByteBuf buf) {
        int count = buf.readInt();
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(buf.readString());
        return list;
    }

    private static List<HdvScreen.RecurringData> readRecurring(PacketByteBuf buf) {
        int count = buf.readInt();
        List<HdvScreen.RecurringData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new HdvScreen.RecurringData(buf.readInt(), buf.readString(), buf.readInt(), buf.readInt(), buf.readInt()));
        return list;
    }

    // ── Lecture paquets Bank ──────────────────────────────────────────────────

    private static BankScreen readBankPacket(PacketByteBuf buf) {
        int balance     = buf.readInt();
        int ticksReward = buf.readInt();
        List<BankScreen.TxData>           txs      = readBankTxs(buf);
        int totalShards  = buf.readInt();
        int playerCount  = buf.readInt();
        List<BankScreen.LeaderboardEntry> lb       = readLeaderboard(buf);
        List<BankScreen.LoanData>         asLender = readLoans(buf);
        List<BankScreen.LoanData>         asBorrow = readLoans(buf);
        List<String>                      known    = readStringList(buf);
        return new BankScreen(balance, ticksReward, txs, totalShards, playerCount,
            lb, asLender, asBorrow, known);
    }

    private static List<BankScreen.TxData> readBankTxs(PacketByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.TxData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.TxData(buf.readInt(), buf.readString(), buf.readInt(), buf.readLong()));
        return list;
    }

    private static List<BankScreen.LeaderboardEntry> readLeaderboard(PacketByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.LeaderboardEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.LeaderboardEntry(buf.readString(), buf.readInt()));
        return list;
    }

    private static List<BankScreen.LoanData> readLoans(PacketByteBuf buf) {
        int count = buf.readInt();
        List<BankScreen.LoanData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(new BankScreen.LoanData(
                buf.readInt(), buf.readString(), buf.readInt(), buf.readLong(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));
        return list;
    }
}
