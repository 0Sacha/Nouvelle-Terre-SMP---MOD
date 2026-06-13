package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.economy.ProductionTracker;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MobDropMixin {

    // ThreadLocal flag : vrai uniquement pendant dropLoot d'un mob tué par un joueur
    private static final ThreadLocal<Boolean> PLAYER_KILL = ThreadLocal.withInitial(() -> false);

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeathHead(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Ne pas compter les morts de joueurs ni les morts sans attaquant joueur
        boolean killedByPlayer = !(self instanceof ServerPlayerEntity)
            && (source.getAttacker() instanceof ServerPlayerEntity);
        PLAYER_KILL.set(killedByPlayer);
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void onDeathTail(DamageSource source, CallbackInfo ci) {
        PLAYER_KILL.set(false);
    }

    @Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"))
    private void onDropStack(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
        if (!PLAYER_KILL.get() || stack.isEmpty()) return;
        ProductionTracker.add(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
    }
}
