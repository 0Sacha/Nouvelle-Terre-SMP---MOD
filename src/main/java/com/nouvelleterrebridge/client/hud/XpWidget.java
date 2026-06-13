package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class XpWidget extends HudWidget {

    public XpWidget() { super("xp", "Expérience", 0.01f, 0.23f, false); }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int level    = mc.player.experienceLevel;
        float prog   = mc.player.experienceProgress; // 0.0-1.0
        int w        = getWidth(mc), h = getHeight(mc);
        ctx.fill(x, y, x + w, y + h, C_PANEL);
        ctx.fill(x, y, x + 2, y + h, C_GREEN);
        ctx.drawText(mc.textRenderer, "Niv. " + level, x + 6, y + 3, C_GREEN, false);
        // Barre de progression XP (4px en bas)
        int barW = w - 8;
        ctx.fill(x + 4, y + h - 5, x + 4 + barW, y + h - 2, 0xFF1B1D22);
        ctx.fill(x + 4, y + h - 5, x + 4 + (int)(barW * prog), y + h - 2, C_GREEN);
    }

    @Override public int getWidth(MinecraftClient mc)  { return mc.textRenderer.getWidth("Niv. 999") + 12; }
    @Override public int getHeight(MinecraftClient mc) { return 18; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.xpEnabled; anchorX = cfg.xpX; anchorY = cfg.xpY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.xpEnabled = enabled; cfg.xpX = anchorX; cfg.xpY = anchorY; }
}
