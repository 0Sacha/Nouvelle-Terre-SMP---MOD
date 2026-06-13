package com.nouvelleterrebridge.mixin;

import com.nouvelleterrebridge.NouvelleTerreBridgeClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugHud.class)
public class DebugHudMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, CallbackInfo ci) {
        NouvelleTerreBridgeClient.debugHudActive = true;
    }
}
