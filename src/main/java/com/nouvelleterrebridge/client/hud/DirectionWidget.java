package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Direction regardée par le joueur. */
public class DirectionWidget extends HudWidget {

    // Lacet de Minecraft : 0 = sud, 90 = ouest, 180 = nord, 270 = est
    private static final String[] NOMS = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};

    public DirectionWidget() {
        super("direction", "Direction", 0.50f, 0.01f, false);
        option("degres", "Afficher les degrés", false);
    }

    private float lacet(Minecraft mc) {
        if (mc.player == null) return 180f;
        return ((mc.player.getYRot() % 360f) + 360f) % 360f;
    }

    private String nom(Minecraft mc) {
        return NOMS[(int) Math.floor((lacet(mc) + 22.5f) / 45f) % 8];
    }

    /** Cap de boussole : 0° au nord, 90° à l'est. */
    private String degres(Minecraft mc) {
        return opt("degres") ? " " + Math.round((lacet(mc) + 180f) % 360f) + "°" : "";
    }

    @Override protected int contentWidth(Minecraft mc)  { return mc.font.width(nom(mc) + degres(mc)); }
    @Override protected int contentHeight(Minecraft mc) { return 8; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        int x = texte(ctx, mc, nom(mc), 0, 0, C_ACCENT);
        texte(ctx, mc, degres(mc), x, 0, C_WHITE);
    }
}
