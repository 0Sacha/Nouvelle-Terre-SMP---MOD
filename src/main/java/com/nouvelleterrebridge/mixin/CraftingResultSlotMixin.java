package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.economy.ProductionTracker;
import com.nouvelleterrebridge.economy.QuestManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public class CraftingResultSlotMixin {

    @Shadow @Final private RecipeInputInventory input;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onCraftTaken(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player.getWorld().isClient()) return;

        // À HEAD, la grille contient encore les ingrédients : c'est le seul moment
        // où l'on peut juger de la provenance du résultat.
        Item decompacte = ingredientDecompacte(stack);
        if (decompacte != null) {
            // Décompactage (1 bloc → 9 lingots) : on ne crédite rien, et on retire
            // le bloc du compteur. Sans ça, compacter/décompacter en boucle gonflait
            // le compteur à l'infini et débloquait n'importe quoi au Shop Serveur
            // avec neuf lingots et de la patience. Le cycle complet est neutre.
            ProductionTracker.remove(Registries.ITEM.getId(decompacte).toString(), 1);
            return;
        }

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        ProductionTracker.add(itemId, stack.getCount());
        QuestManager.onItemHarvested(player.getName().getString(), itemId, stack.getCount(), player.getServer());
    }

    /**
     * Si la recette est un décompactage (un seul bloc en entrée, 4 ou 9 unités en
     * sortie), retourne l'item d'entrée ; sinon null.
     *
     * Le compactage (9 → 1), lui, reste compté : fabriquer réellement des blocs est
     * la façon prévue de les débloquer au Shop Serveur.
     */
    private Item ingredientDecompacte(ItemStack resultat) {
        Item ingredient = null;
        int  total      = 0;

        for (int i = 0; i < this.input.size(); i++) {
            ItemStack s = this.input.getStack(i);
            if (s.isEmpty()) continue;
            if (ingredient == null) ingredient = s.getItem();
            else if (ingredient != s.getItem()) return null;   // recette composite
            total += s.getCount();
        }
        if (ingredient == null || ingredient == resultat.getItem()) return null;
        if (total != 1) return null;

        int sortie = resultat.getCount();
        return (sortie == 9 || sortie == 4) ? ingredient : null;
    }
}
