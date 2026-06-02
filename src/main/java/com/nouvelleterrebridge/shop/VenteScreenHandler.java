package com.nouvelleterrebridge.shop;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * ScreenHandler côté serveur et client pour la mise en vente d'un item.
 */
public class VenteScreenHandler extends ScreenHandler {

    /** Enregistré dans NouvelleTerreBridge.onInitialize() */
    public static ScreenHandlerType<VenteScreenHandler> TYPE;

    private final SimpleInventory sellSlotInventory = new SimpleInventory(1);
    /** props[0] = quantity (1-64), props[1] = pricePerUnit (1+) */
    private final ArrayPropertyDelegate props = new ArrayPropertyDelegate(2);

    // ── Constructeur serveur ──────────────────────────────────────────────────

    public VenteScreenHandler(int syncId, PlayerInventory playerInventory, ItemStack itemToSell) {
        super(TYPE, syncId);

        // Initialise les propriétés
        props.set(0, 1);   // quantity par défaut : 1
        props.set(1, 1);   // price par défaut : 1

        // Place l'item dans le slot de vente
        sellSlotInventory.setStack(0, itemToSell.copy());

        // Slot 0 : slot de vente (non modifiable par le joueur)
        this.addSlot(new Slot(sellSlotInventory, 0, 26, 30) {
            @Override
            public boolean canInsert(ItemStack stack) { return false; }
            @Override
            public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
        });

        // Slots 1-27 : inventaire joueur (3 rangées × 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 145 + row * 18));
            }
        }

        // Slots 28-36 : barre de raccourcis
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 203));
        }

        this.addProperties(props);
    }

    // ── Constructeur client (depuis PacketByteBuf) ────────────────────────────

    public VenteScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, buf.readItemStack());
        props.set(0, buf.readInt());
        props.set(1, buf.readInt());
        sellSlotInventory.setStack(0, getItemToSell());
    }

    // ── Getters / ajustements ─────────────────────────────────────────────────

    public ItemStack getItemToSell() {
        return sellSlotInventory.getStack(0);
    }

    public int getQuantity() {
        return props.get(0);
    }

    public int getPrice() {
        return props.get(1);
    }

    public void adjustQuantity(int delta) {
        int max = getItemToSell().isEmpty() ? 64 : getItemToSell().getMaxCount();
        int newVal = Math.max(1, Math.min(max, props.get(0) + delta));
        props.set(0, newVal);
    }

    public void adjustPrice(int delta) {
        int newVal = Math.max(1, props.get(1) + delta);
        // Garde dans les bornes d'un int positif
        if (newVal < 1) newVal = 1;
        props.set(1, newVal);
    }

    // ── ScreenHandler ─────────────────────────────────────────────────────────

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        // Déplacement rapide désactivé pour le slot de vente
        return ItemStack.EMPTY;
    }
}
