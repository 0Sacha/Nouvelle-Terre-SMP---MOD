package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

@Environment(EnvType.CLIENT)
public class BiomeWidget extends HudWidget {

    public BiomeWidget() { super("biome", "Biome", 0.01f, 0.17f, false); }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        String name = getBiomeName(mc);
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), C_GREEN);
        ctx.drawText(mc.textRenderer, "Biome", x + 6, y + 3, C_MID, false);
        ctx.drawText(mc.textRenderer, name, x + 6 + mc.textRenderer.getWidth("Biome "), y + 3, C_WHITE, false);
    }

    private String getBiomeName(MinecraftClient mc) {
        RegistryEntry<Biome> biome = mc.world.getBiome(mc.player.getBlockPos());
        return biome.getKey()
            .map(k -> capitalize(k.getValue().getPath().replace('_', ' ')))
            .orElse("?");
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override public int getWidth(MinecraftClient mc) {
        String name = (mc.player != null && mc.world != null) ? getBiomeName(mc) : "Plaine";
        return mc.textRenderer.getWidth("Biome " + name) + 12;
    }
    @Override public int getHeight(MinecraftClient mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.biomeEnabled; anchorX = cfg.biomeX; anchorY = cfg.biomeY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.biomeEnabled = enabled; cfg.biomeX = anchorX; cfg.biomeY = anchorY; }
}
