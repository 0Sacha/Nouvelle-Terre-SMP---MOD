package com.nouvelleterrebridge;

import com.nouvelleterrebridge.client.HdvScreen;
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
            int ticksReward  = buf.readInt();
            int ticksSalary  = buf.readInt();
            List<HdvScreen.TransactionData> transactions = readTransactions(buf);
            client.execute(() -> client.setScreen(new HdvScreen(balance, listings, ticksReward, ticksSalary, transactions)));
        });

        // S2C : vérification de version — alerte si le client est en retard
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
                    MutableText msg = Text.literal("§e[Nouvelle Terre] §cMod obsolète §7— client §f" + clientVer + " §7≠ serveur §f" + serverVer + " §7— ")
                        .append(link);
                    client.player.sendMessage(msg, false);
                });
            }
        });

        // S2C : résultat d'une action (achat, vente, retrait, virement)
        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_RESULT, (client, handler, buf, responseSender) -> {
            boolean ok         = buf.readBoolean();
            String  message    = buf.readString();
            int     newBalance = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            int ticksReward  = buf.readInt();
            int ticksSalary  = buf.readInt();
            List<HdvScreen.TransactionData> transactions = readTransactions(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof HdvScreen screen) {
                    screen.handleResult(ok, message, newBalance, listings, ticksReward, ticksSalary, transactions);
                }
            });
        });
    }

    private static List<HdvScreen.ListingData> readListings(PacketByteBuf buf) {
        int count = buf.readInt();
        List<HdvScreen.ListingData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new HdvScreen.ListingData(
                buf.readInt(),    // id
                buf.readString(), // seller
                buf.readString(), // itemId
                buf.readInt(),    // quantity
                buf.readInt()     // pricePerUnit
            ));
        }
        return list;
    }

    private static List<HdvScreen.TransactionData> readTransactions(PacketByteBuf buf) {
        int count = buf.readInt();
        List<HdvScreen.TransactionData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new HdvScreen.TransactionData(
                buf.readInt(),    // type
                buf.readString(), // label
                buf.readInt(),    // amount
                buf.readLong()    // timestamp
            ));
        }
        return list;
    }
}
