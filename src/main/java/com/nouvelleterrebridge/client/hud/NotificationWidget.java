package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class NotificationWidget extends HudWidget {

    // Dimensions correspondant à une notification réelle
    private static final int W = 194;
    private static final int H = 28;

    public NotificationWidget() { super("notif", "Notifications", 0.99f, 0.85f, true); }

    @Override public boolean isDragOnly() { return true; }

    @Override public void render(DrawContext ctx, MinecraftClient mc) {}

    @Override public int getWidth(MinecraftClient mc)  { return W; }
    @Override public int getHeight(MinecraftClient mc) { return H; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.notifEnabled; anchorX = cfg.notifX; anchorY = cfg.notifY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.notifEnabled = enabled; cfg.notifX = anchorX; cfg.notifY = anchorY; }
}
