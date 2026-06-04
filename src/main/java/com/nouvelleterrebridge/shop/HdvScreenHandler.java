package com.nouvelleterrebridge.shop;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * ScreenHandler dédié à l'HDV.
 * Override onSlotClick pour empêcher toute manipulation vanilla et router vers HdvGui.
 * Override onClosed pour nettoyer le state uniquement quand c'est NOTRE handler qui ferme.
 */
public class HdvScreenHandler extends GenericContainerScreenHandler {

    private final UUID playerUuid;

    public HdvScreenHandler(int syncId, PlayerInventory playerInventory,
                            SimpleInventory inv, UUID playerUuid) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inv, 6);
        this.playerUuid = playerUuid;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        HdvGui.handleClick(serverPlayer, slotIndex, button, actionType);
        // Pas d'appel à super → aucune manipulation vanilla possible
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        HdvGui.removeState(playerUuid);
    }
}
