package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Coordonnées du joueur. */
public class CoordsWidget extends HudWidget {

    public CoordsWidget() {
        super("coords", "Coordonnées", 0.01f, 0.06f, false);
        option("decimales", "Décimales", false);
    }

    private String coords(Minecraft mc) {
        if (mc.player == null) return "0 / 64 / 0";
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        return opt("decimales")
            ? String.format("%.1f / %.1f / %.1f", x, y, z)
            : (int) Math.floor(x) + " / " + (int) Math.floor(y) + " / " + (int) Math.floor(z);
    }

    @Override protected int contentWidth(Minecraft mc)  { return mc.font.width("XYZ " + coords(mc)); }
    @Override protected int contentHeight(Minecraft mc) { return 8; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        int x = texte(ctx, mc, "XYZ ", 0, 0, C_ACCENT);
        texte(ctx, mc, coords(mc), x, 0, C_WHITE);
    }
}
