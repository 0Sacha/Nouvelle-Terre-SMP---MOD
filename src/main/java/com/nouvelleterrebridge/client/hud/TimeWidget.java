package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class TimeWidget extends HudWidget {

    public TimeWidget() {
        super("time", "Heure", 0.01f, 0.01f, false);
    }

    private String text(MinecraftClient mc) {
        if (mc.world == null) return "??:??";
        long ticks = mc.world.getTimeOfDay() % 24000;
        int hour = (int)((ticks + 6000) / 1000 % 24);
        int min  = (int)((ticks % 1000) * 60 / 1000);
        String time = String.format("%02d:%02d", hour, min);
        if (ClientConfig.get().timeShowIcon) {
            // Nuit : ticks 12500–23500 (approximativement)
            return (ticks >= 12500 && ticks < 23500 ? "☽ " : "☀ ") + time;
        }
        return time;
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
        renderCheckbox(ctx, mc.textRenderer, panelX + 10, sy + 7, "Icône ☀ / ☽", ClientConfig.get().timeShowIcon, mx, my);
    }

    @Override
    public boolean handleSettingsClick(int mx, int my, int panelX, int sy, int panelW) {
        if (my >= sy + 7 && my < sy + 19 && mx >= panelX + 10 && mx < panelX + panelW - 10) {
            ClientConfig.get().timeShowIcon = !ClientConfig.get().timeShowIcon;
            return true;
        }
        return false;
    }

    @Override
    public void loadFromConfig(ClientConfig cfg) {
        enabled = cfg.timeEnabled;
        anchorX = cfg.timeX;
        anchorY = cfg.timeY;
    }

    @Override
    public void saveToConfig(ClientConfig cfg) {
        cfg.timeEnabled = enabled;
        cfg.timeX       = anchorX;
        cfg.timeY       = anchorY;
    }
}
