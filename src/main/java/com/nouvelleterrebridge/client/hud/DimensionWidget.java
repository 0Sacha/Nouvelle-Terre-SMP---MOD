package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

/** Dimension courante. */
public class DimensionWidget extends HudWidget {

    public DimensionWidget() { super("dimension", "Dimension", 0.01f, 0.26f, false); }

    private String nom(Minecraft mc) {
        if (mc.level == null) return "Monde";
        var cle = mc.level.dimension();
        if (cle.equals(Level.OVERWORLD)) return "Monde";
        if (cle.equals(Level.NETHER))    return "Nether";
        if (cle.equals(Level.END))       return "End";
        String s = cle.location().getPath();
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace('_', ' ');
    }

    private int couleur(Minecraft mc) {
        if (mc.level == null) return C_GREEN;
        var cle = mc.level.dimension();
        if (cle.equals(Level.NETHER)) return C_RED;
        if (cle.equals(Level.END))    return 0xFFC4A1FF;
        return C_GREEN;
    }

    @Override protected int contentWidth(Minecraft mc)  { return mc.font.width(nom(mc)); }
    @Override protected int contentHeight(Minecraft mc) { return 8; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        texte(ctx, mc, nom(mc), 0, 0, couleur(mc));
    }
}
