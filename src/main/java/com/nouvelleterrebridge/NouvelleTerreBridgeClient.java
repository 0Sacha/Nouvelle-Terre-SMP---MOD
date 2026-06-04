package com.nouvelleterrebridge;

import com.nouvelleterrebridge.client.HdvScreen;
import com.nouvelleterrebridge.network.HdvNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;

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
            client.execute(() -> client.setScreen(new HdvScreen(balance, listings)));
        });

        // S2C : résultat d'une action (achat, vente, retrait)
        ClientPlayNetworking.registerGlobalReceiver(HdvNetworking.HDV_RESULT, (client, handler, buf, responseSender) -> {
            boolean ok         = buf.readBoolean();
            String  message    = buf.readString();
            int     newBalance = buf.readInt();
            List<HdvScreen.ListingData> listings = readListings(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof HdvScreen screen) {
                    screen.handleResult(ok, message, newBalance, listings);
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
}
