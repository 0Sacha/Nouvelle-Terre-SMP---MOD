package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.economy.ProductionTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDropMixin {

    @Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"))
    private void onDropStack(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
        if (!MobDropMixin.PLAYER_KILL.get() || stack.isEmpty()) return;
        ProductionTracker.add(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
    }
}
