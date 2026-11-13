package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Heure du monde, au format 24 h. */
public class TimeWidget extends HudWidget {

    public TimeWidget() {
        super("time", "Heure", 0.01f, 0.01f, false);
        option("icone", "Icône ☀ / ☽", true);
    }

    private String valeur(Minecraft mc) {
        if (mc.level == null) return "12:00";
        long ticks = mc.level.getDayTime() % 24000;
        int heure = (int) ((ticks + 6000) / 1000 % 24);
        int min   = (int) ((ticks % 1000) * 60 / 1000);
        String t = String.format("%02d:%02d", heure, min);
        // Nuit : ticks 12500 à 23500 environ
        return opt("icone") ? (ticks >= 12500 && ticks < 23500 ? "☽ " : "☀ ") + t : t;
    }

    @Override protected int contentWidth(Minecraft mc)  { return mc.font.width(valeur(mc)); }
    @Override protected int contentHeight(Minecraft mc) { return 8; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        texte(ctx, mc, valeur(mc), 0, 0, C_WHITE);
    }
}
