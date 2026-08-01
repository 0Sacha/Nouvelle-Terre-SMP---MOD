package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Empêche de jeter le Parchemin avec la touche « lâcher » : c'est un outil
 * d'interface, pas une ressource.
 *
 * On intercepte {@code dropSelectedItem} (côté serveur, qui fait autorité) et
 * non {@code dropItem} : à ce stade la pile n'a pas encore été retirée de
 * l'inventaire, alors qu'annuler {@code dropItem} la supprimerait.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ParcheminDropMixin {

    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void nt$bloquerDropParchemin(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        ItemStack selected = self.getInventory().getMainHandStack();
        if (!selected.isEmpty() && selected.isOf(NouvelleTerreBridge.PARCHEMIN)) {
            cir.setReturnValue(false);
        }
    }
}
