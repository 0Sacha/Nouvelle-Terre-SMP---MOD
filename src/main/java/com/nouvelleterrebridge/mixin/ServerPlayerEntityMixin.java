package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {

    /** Remplace le nom dans la tab list et le nameplate par le nom RP s'il est connu. */
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void overridePlayerListName(CallbackInfoReturnable<Component> cir) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        String nomRP = NouvelleTerreBridge.nomsRP.get(self.getStringUUID());
        if (nomRP != null) {
            cir.setReturnValue(Component.literal("§f" + nomRP + " §8(§7" + self.getName().getString() + "§8)"));
        }
    }
}
