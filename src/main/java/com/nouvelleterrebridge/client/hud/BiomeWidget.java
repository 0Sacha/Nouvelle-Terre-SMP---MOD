package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Biome dans lequel se trouve le joueur. */
public class BiomeWidget extends HudWidget {

    public BiomeWidget() { super("biome", "Biome", 0.01f, 0.17f, false); }

    private String nom(Minecraft mc) {
        if (mc.player == null || mc.level == null) return "Plaine";
        var cle = mc.level.getBiome(mc.player.blockPosition()).getKey();
        if (cle == null) return "?";
        String s = cle.location().getPath().replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override protected int contentWidth(Minecraft mc)  { return mc.font.width("Biome " + nom(mc)); }
    @Override protected int contentHeight(Minecraft mc) { return 8; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        int x = texte(ctx, mc, "Biome ", 0, 0, C_ACCENT);
        texte(ctx, mc, nom(mc), x, 0, C_WHITE);
    }
}
