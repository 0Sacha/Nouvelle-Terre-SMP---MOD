package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.NouvelleTerreBridgeClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    // Reset the flag each frame before DebugHud potentially sets it
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DrawContext context, float tickDelta, CallbackInfo ci) {
        NouvelleTerreBridgeClient.debugHudActive = false;
    }
}
