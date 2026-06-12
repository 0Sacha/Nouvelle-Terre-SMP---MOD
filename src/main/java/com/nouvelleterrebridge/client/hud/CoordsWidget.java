package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class CoordsWidget extends HudWidget {

    public CoordsWidget() {
        super("coords", "Coordonnées", 0.01f, 0.06f, false);
    }

    private String coords(MinecraftClient mc) {
        if (mc.player == null) return "? / ? / ?";
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        return ClientConfig.get().coordsShowDecimals
            ? String.format("%.1f / %.1f / %.1f", x, y, z)
            : (int)Math.floor(x) + " / " + (int)Math.floor(y) + " / " + (int)Math.floor(z);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        String c = coords(mc);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int px = getPixelX(sw, mc), py = getPixelY(sh, mc);
        int w = getWidth(mc), h = getHeight(mc);
        int labelW = mc.textRenderer.getWidth("XYZ ");
        ctx.fill(px, py, px + w, py + h, C_PANEL);
        ctx.fill(px, py, px + 2, py + h, C_GOLD);
        ctx.drawText(mc.textRenderer, "XYZ", px + 8, py + 3, C_MID, false);
        ctx.drawText(mc.textRenderer, c,     px + 8 + labelW, py + 3, C_WHITE, false);
    }

    @Override
    public int getWidth(MinecraftClient mc)  { return mc.textRenderer.getWidth("XYZ " + coords(mc)) + 18; }
    @Override
    public int getHeight(MinecraftClient mc) { return 14; }

    @Override public boolean hasSettings()  { return true; }
    @Override public int     settingsHeight() { return 26; }

    @Override
    public void renderSettings(DrawContext ctx, MinecraftClient mc, int panelX, int sy, int panelW, int mx, int my) {
        renderCheckbox(ctx, mc.textRenderer, panelX + 10, sy + 7, "Décimales", ClientConfig.get().coordsShowDecimals, mx, my);
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
