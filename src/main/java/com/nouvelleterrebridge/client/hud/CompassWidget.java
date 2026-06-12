package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class CompassWidget extends HudWidget {

    public CompassWidget() {
        super("compass", "Boussole", 0.50f, 0.02f, false);
    }

    private String text(MinecraftClient mc) {
        if (mc.player == null) return "?";
        float yaw = ((mc.player.getYaw() % 360) + 360) % 360;
        String dir = yawToDir(yaw);
        return ClientConfig.get().compassShowDegrees ? dir + "  " + (int)yaw + "°" : dir;
    }

    // Minecraft yaw : 0 = Sud, 90 = Ouest, 180 = Nord, 270 = Est
    private static String yawToDir(float y) {
        if (y < 22.5 || y >= 337.5) return "S";
        if (y < 67.5)  return "S-O";
        if (y < 112.5) return "O";
        if (y < 157.5) return "N-O";
        if (y < 202.5) return "N";
        if (y < 247.5) return "N-E";
        if (y < 292.5) return "E";
        return "S-E";
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        String t = text(mc);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int w = getWidth(mc), h = getHeight(mc);
        ctx.fill(x, y, x + w, y + h, C_PANEL);
        ctx.fill(x, y, x + 2, y + h, C_GOLD);
        ctx.drawText(mc.textRenderer, t, x + 8, y + 3, C_WHITE, false);
    }

    @Override
    public int getWidth(MinecraftClient mc)  { return mc.textRenderer.getWidth(text(mc)) + 18; }
    @Override
    public int getHeight(MinecraftClient mc) { return 14; }

    @Override public boolean hasSettings()    { return true; }
    @Override public int     settingsHeight() { return 26; }

    @Override
    public void renderSettings(DrawContext ctx, MinecraftClient mc, int panelX, int sy, int panelW, int mx, int my) {
        renderCheckbox(ctx, mc.textRenderer, panelX + 10, sy + 7, "Afficher les degrés", ClientConfig.get().compassShowDegrees, mx, my);
    }

    @Override
    public boolean handleSettingsClick(int mx, int my, int panelX, int sy, int panelW) {
        if (my >= sy + 7 && my < sy + 19 && mx >= panelX + 10 && mx < panelX + panelW - 10) {
            ClientConfig.get().compassShowDegrees = !ClientConfig.get().compassShowDegrees;
            return true;
        }
        return false;
    }

    @Override
    public void loadFromConfig(ClientConfig cfg) {
        enabled = cfg.compassEnabled;
        anchorX = cfg.compassX;
        anchorY = cfg.compassY;
    }

    @Override
    public void saveToConfig(ClientConfig cfg) {
        cfg.compassEnabled = enabled;
        cfg.compassX       = anchorX;
        cfg.compassY       = anchorY;
    }
}
