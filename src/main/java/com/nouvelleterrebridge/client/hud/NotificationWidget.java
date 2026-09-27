package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class NotificationWidget extends HudWidget {

    // Dimensions correspondant à une notification réelle
    private static final int W = 194;
    private static final int H = 28;

    public NotificationWidget() { super("notif", "Notifications", 0.99f, 0.85f, true); }

    @Override public boolean isDragOnly() { return true; }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {}

    @Override public int getWidth(Minecraft mc)  { return W; }
    @Override public int getHeight(Minecraft mc) { return H; }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.notifEnabled; anchorX = cfg.notifX; anchorY = cfg.notifY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.notifEnabled = enabled; cfg.notifX = anchorX; cfg.notifY = anchorY; }
}
