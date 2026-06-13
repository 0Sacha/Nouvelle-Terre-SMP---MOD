package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class DimensionWidget extends HudWidget {

    public DimensionWidget() { super("dimension", "Dimension", 0.01f, 0.26f, false); }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.world == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        String dim   = getDimName(mc);
        int    accent = getDimColor(mc);
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), accent);
        ctx.drawText(mc.textRenderer, dim, x + 6, y + 3, accent, false);
    }

    private String getDimName(MinecraftClient mc) {
        var key = mc.world.getRegistryKey();
        if (key.equals(World.OVERWORLD)) return "Monde";
        if (key.equals(World.NETHER))    return "Nether";
        if (key.equals(World.END))       return "End";
        String path = key.getValue().getPath();
        return path.substring(0, 1).toUpperCase() + path.substring(1).replace('_', ' ');
    }

    private int getDimColor(MinecraftClient mc) {
        var key = mc.world.getRegistryKey();
        if (key.equals(World.NETHER)) return 0xFFFF4444;
        if (key.equals(World.END))    return 0xFFCC88FF;
        return C_GREEN;
    }

    @Override public int getWidth(MinecraftClient mc) {
        String dim = (mc.world != null) ? getDimName(mc) : "Monde";
        return mc.textRenderer.getWidth(dim) + 12;
    }
    @Override public int getHeight(MinecraftClient mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.dimensionEnabled; anchorX = cfg.dimensionX; anchorY = cfg.dimensionY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.dimensionEnabled = enabled; cfg.dimensionX = anchorX; cfg.dimensionY = anchorY; }
}
