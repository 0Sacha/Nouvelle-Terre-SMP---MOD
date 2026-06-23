package com.nouvelleterrebridge.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    /** Utilise le display name du PlayerListEntry (mis à jour par le serveur) pour le nameplate. */
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void useTabListDisplayName(CallbackInfoReturnable<Text> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity)(Object)this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) return;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(self.getUuid());
        if (entry == null || entry.getDisplayName() == null) return;
        cir.setReturnValue(entry.getDisplayName());
    }
}
