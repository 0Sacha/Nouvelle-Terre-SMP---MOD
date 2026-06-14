package com.nouvelleterrebridge.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MobDropMixin {

    // ThreadLocal flag : vrai uniquement pendant dropLoot d'un mob tué par un joueur
    // Lue par EntityDropMixin (qui cible Entity.class où dropStack est réellement défini)
    public static final ThreadLocal<Boolean> PLAYER_KILL = ThreadLocal.withInitial(() -> false);

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeathHead(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        boolean killedByPlayer = !(self instanceof ServerPlayerEntity)
            && (source.getAttacker() instanceof ServerPlayerEntity);
        PLAYER_KILL.set(killedByPlayer);
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void onDeathTail(DamageSource source, CallbackInfo ci) {
        PLAYER_KILL.set(false);
    }
}
