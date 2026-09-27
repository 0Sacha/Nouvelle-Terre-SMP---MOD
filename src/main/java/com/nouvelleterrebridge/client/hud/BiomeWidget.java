package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public class BiomeWidget extends HudWidget {

    public BiomeWidget() { super("biome", "Biome", 0.01f, 0.17f, false); }

    @Override
    public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        String name = getBiomeName(mc);
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), C_GREEN);
        ctx.drawString(mc.font, "Biome", x + 6, y + 3, C_MID, false);
        ctx.drawString(mc.font, name, x + 6 + mc.font.width("Biome "), y + 3, C_WHITE, false);
    }

    private String getBiomeName(Minecraft mc) {
        Holder<Biome> biome = mc.level.getBiome(mc.player.blockPosition());
        var key = biome.getKey();
        return key != null ? capitalize(key.location().getPath().replace('_', ' ')) : "?";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override public int getWidth(Minecraft mc) {
        String name = (mc.player != null && mc.level != null) ? getBiomeName(mc) : "Plaine";
        return mc.font.width("Biome " + name) + 12;
    }
    @Override public int getHeight(Minecraft mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.biomeEnabled; anchorX = cfg.biomeX; anchorY = cfg.biomeY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.biomeEnabled = enabled; cfg.biomeX = anchorX; cfg.biomeY = anchorY; }
}
