package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.NouvelleTerreBridgeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// getDisplayName() est déclaré dans Entity, pas dans AbstractClientPlayerEntity —
// on cible Entity et on filtre par instanceof côté client.
@Mixin(Entity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void useRpName(CallbackInfoReturnable<Text> cir) {
        if (!((Object)this instanceof AbstractClientPlayerEntity)) return;
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity)(Object)this;
        String nomRP = NouvelleTerreBridgeClient.nomsRP.get(self.getUuid());
        if (nomRP == null) return;
        String pseudo = self.getName().getString();
        cir.setReturnValue(Text.literal("§f" + nomRP + " §8(§7" + pseudo + "§8)"));
    }
}
