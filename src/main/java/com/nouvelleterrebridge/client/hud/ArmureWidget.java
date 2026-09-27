package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class ArmureWidget extends HudWidget {

    public ArmureWidget() { super("armure", "Armure", 0.01f, 0.20f, false); }

    @Override
    public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int armor  = mc.player.getArmorValue(); // 0-20 points
        int accent = armor <= 4 ? C_RED : armor <= 12 ? C_GOLD : C_GREEN;
        String t   = "Arm. " + armor + " / 20";
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), accent);
        ctx.drawString(mc.font, t, x + 6, y + 3, accent, false);
    }

    @Override public int getWidth(Minecraft mc)  { return mc.font.width("Arm. 20 / 20") + 12; }
    @Override public int getHeight(Minecraft mc) { return 14; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.armureEnabled; anchorX = cfg.armureX; anchorY = cfg.armureY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.armureEnabled = enabled; cfg.armureX = anchorX; cfg.armureY = anchorY; }
}
