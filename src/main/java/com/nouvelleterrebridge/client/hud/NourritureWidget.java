package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class NourritureWidget extends HudWidget {

    public NourritureWidget() { super("nourriture", "Nourriture", 0.01f, 0.11f, false); }

    @Override
    public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int food = mc.player.getFoodData().getFoodLevel(); // 0-20
        int accent = food <= 4 ? C_RED : food <= 10 ? C_GOLD : C_GREEN;
        String t = food + " / 20";
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), accent);
        ctx.drawString(mc.font, "Faim", x + 6, y + 3, C_MID, false);
        ctx.drawString(mc.font, t, x + 6 + mc.font.width("Faim "), y + 3, accent, false);
    }

    @Override public int getWidth(Minecraft mc) {
        return mc.font.width("Faim 20 / 20") + 12;
    }
    @Override public int getHeight(Minecraft mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.nourritureEnabled; anchorX = cfg.nourritureX; anchorY = cfg.nourritureY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.nourritureEnabled = enabled; cfg.nourritureX = anchorX; cfg.nourritureY = anchorY; }
}
