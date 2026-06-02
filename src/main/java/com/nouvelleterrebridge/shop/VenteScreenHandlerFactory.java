package com.nouvelleterrebridge.shop;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Factory qui ouvre le VenteScreenHandler côté serveur et envoie les données initiales au client.
 */
public class VenteScreenHandlerFactory implements ExtendedScreenHandlerFactory {

    private final ItemStack item;
    private final int quantity;
    private final int price;

    public VenteScreenHandlerFactory(ItemStack item) {
        this(item, 1, 1);
    }

    public VenteScreenHandlerFactory(ItemStack item, int quantity, int price) {
        this.item = item.copy();
        this.quantity = quantity;
        this.price = price;
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeItemStack(item);
        buf.writeInt(quantity);
        buf.writeInt(price);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Mise en Vente");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new VenteScreenHandler(syncId, inv, item);
    }
}
