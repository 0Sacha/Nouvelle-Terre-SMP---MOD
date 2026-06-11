package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class BalanceHudOverlay {

    private static final int C_PANEL  = 0xCC1B1D22;
    private static final int C_GOLD   = 0xFFE8A838;

    public static int cachedBalance = -1;

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!ClientConfig.get().hudEnabled) return;
            if (mc.player == null) return;
            if (mc.currentScreen instanceof HdvScreen) return;

            String text = cachedBalance < 0 ? "? ◆" : fmt(cachedBalance) + " ◆";
            int tw = mc.textRenderer.getWidth(text);
            int w = tw + 20;
            int h = 14;
            int x = mc.getWindow().getScaledWidth() - w - 8;
            int y = 8;

            ctx.fill(x, y, x + w, y + h, C_PANEL);
            ctx.fill(x, y, x + 2, y + h, C_GOLD);
            ctx.drawText(mc.textRenderer, text, x + 10, y + 3, C_GOLD, false);
        });
    }

    private static String fmt(int n) {
        return String.format("%,d", n).replace(',', ' ');
    }
}
