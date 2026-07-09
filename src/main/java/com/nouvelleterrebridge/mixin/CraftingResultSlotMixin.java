package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.economy.ProductionTracker;
import com.nouvelleterrebridge.economy.QuestManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public class CraftingResultSlotMixin {

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onCraftTaken(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player.getWorld().isClient()) return;
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        ProductionTracker.add(itemId, stack.getCount());
        QuestManager.onItemHarvested(player.getName().getString(), itemId, stack.getCount(), player.getServer());
    }
}
