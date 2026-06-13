package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

@Environment(EnvType.CLIENT)
public class FpsWidget extends HudWidget {

    // Compteur de frames mis à jour depuis HudRenderCallback chaque frame
    public static int cachedFps = 0;
    private static int   frames  = 0;
    private static long  lastMs  = System.currentTimeMillis();

    public static void onFrame() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastMs >= 1000) {
            cachedFps = frames;
            frames    = 0;
            lastMs    = now;
        }
    }

    private boolean showPing = true;

    public FpsWidget() { super("fps", "FPS / Ping", 0.01f, 0.14f, false); }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int accent = cachedFps < 30 ? C_RED : cachedFps < 60 ? C_GOLD : C_GREEN;
        String text = cachedFps + " FPS";
        if (showPing && mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) text += "  " + entry.getLatency() + " ms";
        }
        ctx.fill(x, y, x + getWidth(mc), y + getHeight(mc), C_PANEL);
        ctx.fill(x, y, x + 2, y + getHeight(mc), accent);
        ctx.drawText(mc.textRenderer, text, x + 6, y + 3, C_MID, false);
    }

    @Override public int getWidth(MinecraftClient mc)  { return mc.textRenderer.getWidth("999 FPS  999 ms") + 12; }
    @Override public int getHeight(MinecraftClient mc) { return 14; }

    @Override public boolean hasSettings()    { return true; }
    @Override public int     settingsHeight() { return 14; }

    @Override
    public void renderSettings(DrawContext ctx, MinecraftClient mc, int px, int sy, int pw, int mx, int my) {
        renderCheckbox(ctx, mc.textRenderer, px + 8, sy, "Afficher le ping", showPing, mx, my);
    }

    @Override
    public boolean handleSettingsClick(int mx, int my, int px, int sy, int pw) {
        if (mx >= px + 8 && mx < px + 20 && my >= sy && my < sy + 12) {
            showPing = !showPing; return true;
        }
        return false;
    }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.fpsEnabled; anchorX = cfg.fpsX; anchorY = cfg.fpsY; showPing = cfg.fpsShowPing; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.fpsEnabled = enabled; cfg.fpsX = anchorX; cfg.fpsY = anchorY; cfg.fpsShowPing = showPing; }
}
