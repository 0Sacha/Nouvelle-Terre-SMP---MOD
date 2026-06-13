package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class NourritureWidget extends HudWidget {

    public NourritureWidget() { super("nourriture", "Nourriture", 0.01f, 0.11f, false); }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int food = mc.player.getHungerManager().getFoodLevel(); // 0-20
        int accent = food <= 4 ? C_RED : food <= 10 ? C_GOLD : C_GREEN;
        String t = food + " / 20";
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), accent);
        ctx.drawText(mc.textRenderer, "Faim", x + 6, y + 3, C_MID, false);
        ctx.drawText(mc.textRenderer, t, x + 6 + mc.textRenderer.getWidth("Faim "), y + 3, accent, false);
    }

    @Override public int getWidth(MinecraftClient mc) {
        return mc.textRenderer.getWidth("Faim 20 / 20") + 12;
    }
    @Override public int getHeight(MinecraftClient mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.nourritureEnabled; anchorX = cfg.nourritureX; anchorY = cfg.nourritureY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.nourritureEnabled = enabled; cfg.nourritureX = anchorX; cfg.nourritureY = anchorY; }
}
