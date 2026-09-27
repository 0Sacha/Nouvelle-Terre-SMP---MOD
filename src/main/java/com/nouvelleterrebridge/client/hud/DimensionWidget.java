package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

public class DimensionWidget extends HudWidget {

    public DimensionWidget() { super("dimension", "Dimension", 0.01f, 0.26f, false); }

    @Override
    public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.level == null) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        String dim   = getDimName(mc);
        int    accent = getDimColor(mc);
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), accent);
        ctx.drawString(mc.font, dim, x + 6, y + 3, accent, false);
    }

    private String getDimName(Minecraft mc) {
        var key = mc.level.dimension();
        if (key.equals(Level.OVERWORLD)) return "Monde";
        if (key.equals(Level.NETHER))    return "Nether";
        if (key.equals(Level.END))       return "End";
        String path = key.location().getPath();
        return path.substring(0, 1).toUpperCase() + path.substring(1).replace('_', ' ');
    }

    private int getDimColor(Minecraft mc) {
        var key = mc.level.dimension();
        if (key.equals(Level.NETHER)) return 0xFFFF4444;
        if (key.equals(Level.END))    return 0xFFCC88FF;
        return C_GREEN;
    }

    @Override public int getWidth(Minecraft mc) {
        String dim = (mc.level != null) ? getDimName(mc) : "Monde";
        return mc.font.width(dim) + 12;
    }
    @Override public int getHeight(Minecraft mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.dimensionEnabled; anchorX = cfg.dimensionX; anchorY = cfg.dimensionY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.dimensionEnabled = enabled; cfg.dimensionX = anchorX; cfg.dimensionY = anchorY; }
}
