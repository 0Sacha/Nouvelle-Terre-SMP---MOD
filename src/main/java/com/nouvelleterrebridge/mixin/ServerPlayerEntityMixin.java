package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    /** Remplace le nom dans la tab list et le nameplate par le nom RP s'il est connu. */
    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void overridePlayerListName(CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
        String nomRP = NouvelleTerreBridge.nomsRP.get(self.getUuidAsString());
        if (nomRP != null) {
            cir.setReturnValue(Text.literal("§f" + nomRP + " §8(§7" + self.getName().getString() + "§8)"));
        }
    }
}
