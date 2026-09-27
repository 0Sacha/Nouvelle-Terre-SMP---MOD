package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.NouvelleTerreBridgeClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// getDisplayName() est déclaré dans Entity, pas dans AbstractClientPlayer —
// on cible Entity et on filtre par instanceof côté client.
@Mixin(Entity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void useRpName(CallbackInfoReturnable<Component> cir) {
        if (!((Object)this instanceof AbstractClientPlayer)) return;
        AbstractClientPlayer self = (AbstractClientPlayer)(Object)this;
        String nomRP = NouvelleTerreBridgeClient.nomsRP.get(self.getUUID());
        if (nomRP == null) return;
        String pseudo = self.getName().getString();
        cir.setReturnValue(Component.literal("§f" + nomRP + " §8(§7" + pseudo + "§8)"));
    }
}
