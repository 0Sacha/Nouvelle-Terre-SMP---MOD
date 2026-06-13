package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class SanteWidget extends HudWidget {

    public SanteWidget() { super("sante", "Santé", 0.01f, 0.08f, false); }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        float hp = mc.player.getHealth(), max = mc.player.getMaxHealth();
        int accent = hp < max * 0.25f ? C_RED : hp < max * 0.6f ? C_GOLD : 0xFFFF4444;
        String t = fmt(hp) + " / " + fmt(max) + " ♥";
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), accent);
        ctx.drawText(mc.textRenderer, t, x + 6, y + 3, accent, false);
    }

    private String fmt(float v) { return v == (int) v ? String.valueOf((int) v) : String.format("%.1f", v); }

    @Override public int getWidth(MinecraftClient mc) {
        if (mc.player == null) return 60;
        float hp = mc.player.getHealth(), max = mc.player.getMaxHealth();
        return mc.textRenderer.getWidth(fmt(hp) + " / " + fmt(max) + " ♥") + 12;
    }
    @Override public int getHeight(MinecraftClient mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.santeEnabled; anchorX = cfg.santeX; anchorY = cfg.santeY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.santeEnabled = enabled; cfg.santeX = anchorX; cfg.santeY = anchorY; }
}
