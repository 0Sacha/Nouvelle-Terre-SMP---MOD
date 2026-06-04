package com.nouvelleterrebridge.shop;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Enclume détournée pour la saisie du prix lors d'une vente HDV.
 * Le joueur tape un entier → confirm → MarketActions.sell() est appelé.
 */
public class HdvSellPriceHandler extends AnvilScreenHandler {

    private final ServerPlayerEntity seller;
    private final int qty;
    private boolean confirmed = false;

    public HdvSellPriceHandler(int syncId, PlayerInventory playerInv,
                               ServerPlayerEntity seller, int qty) {
        super(syncId, playerInv, ScreenHandlerContext.EMPTY);
        this.seller = seller;
        this.qty    = qty;

        ItemStack prompt = new ItemStack(Items.EMERALD);
        prompt.setCustomName(Text.literal(""));
        getSlot(0).setStack(prompt);
        updateResult();
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex != 2) return; // bloquer tout sauf le slot de sortie
        ItemStack output = getSlot(2).getStack();
        String text = output.hasCustomName() ? output.getName().getString().trim() : "";

        int price;
        try {
            price = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            seller.sendMessage(Text.literal("§c❌ Saisis un nombre entier (ex: §f50§c)."));
            return;
        }
        if (price <= 0) {
            seller.sendMessage(Text.literal("§c❌ Le prix doit être supérieur à 0."));
            return;
        }

        confirmed = true;
        String result = MarketActions.sell(seller, qty, price);
        if (result != null) {
            seller.sendMessage(Text.literal(result)); // message d'erreur (main vide, etc.)
        }
        // Ouvre MY_SHOP au prochain tick
        seller.getServer().execute(() -> HdvGui.openHdvMyShop(seller));
    }

    @Override
    public void onClosed(PlayerEntity player) {
        getSlot(0).setStack(ItemStack.EMPTY);
        getSlot(1).setStack(ItemStack.EMPTY);
        getSlot(2).setStack(ItemStack.EMPTY);
        if (!confirmed) {
            seller.getServer().execute(() -> HdvGui.openHdvMyShop(seller));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }
}
