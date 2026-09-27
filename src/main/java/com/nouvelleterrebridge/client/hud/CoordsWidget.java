package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class CoordsWidget extends HudWidget {

    public CoordsWidget() {
        super("coords", "Coordonnées", 0.01f, 0.06f, false);
    }

    private String coords(Minecraft mc) {
        if (mc.player == null) return "? / ? / ?";
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        return ClientConfig.get().coordsShowDecimals
            ? String.format("%.1f / %.1f / %.1f", x, y, z)
            : (int)Math.floor(x) + " / " + (int)Math.floor(y) + " / " + (int)Math.floor(z);
    }

    @Override
    public void render(GuiGraphics ctx, Minecraft mc) {
        String c = coords(mc);
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int px = getPixelX(sw, mc), py = getPixelY(sh, mc);
        int w = getWidth(mc), h = getHeight(mc);
        int labelW = mc.font.width("XYZ ");
        ctx.fill(px, py, px + w, py + h, C_PANEL);
        ctx.fill(px, py, px + 2, py + h, C_GOLD);
        ctx.drawString(mc.font, "XYZ", px + 8, py + 3, C_MID, false);
        ctx.drawString(mc.font, c,     px + 8 + labelW, py + 3, C_WHITE, false);
    }

    @Override
    public int getWidth(Minecraft mc)  { return mc.font.width("XYZ " + coords(mc)) + 18; }
    @Override
    public int getHeight(Minecraft mc) { return 14; }

    @Override public boolean hasSettings()  { return true; }
    @Override public int     settingsHeight() { return 26; }

    @Override
    public void renderSettings(GuiGraphics ctx, Minecraft mc, int panelX, int sy, int panelW, int mx, int my) {
        renderCheckbox(ctx, mc.font, panelX + 10, sy + 7, "Décimales", ClientConfig.get().coordsShowDecimals, mx, my);
    }

    @Override
    public boolean handleSettingsClick(int mx, int my, int panelX, int sy, int panelW) {
        if (my >= sy + 7 && my < sy + 19 && mx >= panelX + 10 && mx < panelX + panelW - 10) {
            ClientConfig.get().coordsShowDecimals = !ClientConfig.get().coordsShowDecimals;
            return true;
        }
        return false;
    }

    @Override
    public void loadFromConfig(ClientConfig cfg) {
        enabled = cfg.coordsEnabled;
        anchorX = cfg.coordsX;
        anchorY = cfg.coordsY;
    }

    @Override
    public void saveToConfig(ClientConfig cfg) {
        cfg.coordsEnabled = enabled;
        cfg.coordsX       = anchorX;
        cfg.coordsY       = anchorY;
    }
}
