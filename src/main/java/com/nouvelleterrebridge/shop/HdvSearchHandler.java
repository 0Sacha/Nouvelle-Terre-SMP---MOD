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
 * Enclume détournée comme champ de saisie texte pour la recherche HDV.
 *
 * Flow :
 *  1. HDV ouvre cette enclume (pas de vraie enclume dans le monde)
 *  2. Le joueur tape sa recherche dans le field texte natif
 *  3. Clic sur le slot de sortie (2) → capture le texte → rouvre HDV filtré
 *  4. Escape → rouvre HDV sans filtre
 */
public class HdvSearchHandler extends AnvilScreenHandler {

    private final ServerPlayerEntity searcher;
    private boolean confirmed = false;

    public HdvSearchHandler(int syncId, PlayerInventory playerInv, ServerPlayerEntity searcher) {
        super(syncId, playerInv, ScreenHandlerContext.EMPTY);
        this.searcher = searcher;

        // Paper vide dans le slot d'entrée → le field texte de l'enclume est vide par défaut
        ItemStack prompt = new ItemStack(Items.PAPER);
        prompt.setCustomName(Text.literal(""));
        this.getSlot(0).setStack(prompt);
        this.updateResult();
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Seul le slot de sortie (2) est interactif : confirme la recherche
        if (slotIndex == 2) {
            ItemStack output = getSlot(2).getStack();
            String query = output.hasCustomName() ? output.getName().getString().trim() : "";
            confirmed = true;
            // Rouvre le HDV avec le filtre (sur le tick suivant pour éviter la récursion)
            searcher.getServer().execute(() -> HdvGui.openHdvWithSearch(searcher, query));
        }
        // Tous les autres clics bloqués (pas de prise d'items, pas de dépôt)
    }

    @Override
    public void onClosed(PlayerEntity player) {
        // Vider les slots sans rendre quoi que ce soit au joueur
        getSlot(0).setStack(ItemStack.EMPTY);
        getSlot(1).setStack(ItemStack.EMPTY);
        getSlot(2).setStack(ItemStack.EMPTY);

        if (!confirmed) {
            // ESC → retour au HDV principal sans filtre
            searcher.getServer().execute(() -> HdvGui.openHdv(searcher));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // Pas d'enclume réelle dans le monde → on autorise toujours
        return true;
    }
}
