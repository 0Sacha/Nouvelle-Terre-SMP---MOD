package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.http.EventDispatcher;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin sur LivingEntity pour intercepter les morts de joueurs.
 * Seules les morts de ServerPlayerEntity (joueurs réels) sont transmises au bot.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void surMort(DamageSource source, CallbackInfo ci) {
        LivingEntity entite = (LivingEntity) (Object) this;

        // On ne traite que les joueurs côté serveur
        if (!(entite instanceof ServerPlayerEntity joueur)) return;

        String pseudo = joueur.getName().getString();
        String causeTexte = source.getDeathMessage(entite).getString();

        Map<String, Object> data = new HashMap<>();
        data.put("player", pseudo);
        data.put("message", causeTexte);
        data.put("cause", source.getName());

        EventDispatcher.envoyer("PLAYER_DEATH", data);
    }
}
