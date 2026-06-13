package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public abstract class HudWidget {

    protected static final int C_PANEL  = 0xCC1B1D22;
    protected static final int C_BORDER = 0xFF2A2D38;
    protected static final int C_GOLD   = 0xFFE8A838;
    protected static final int C_GREEN  = 0xFF2EAD6B;
    protected static final int C_RED    = 0xFFBF2040;
    protected static final int C_WHITE  = 0xFFFFFFFF;
    protected static final int C_MID    = 0xFF9096A3;
    protected static final int C_DIM    = 0xFF565C6A;

    public final String  id;
    public final String  label;
    public float         anchorX, anchorY;
    public boolean       enabled;

    private final float   defaultX, defaultY;
    private final boolean defaultEnabled;

    protected HudWidget(String id, String label, float defaultX, float defaultY, boolean defaultEnabled) {
        this.id             = id;
        this.label          = label;
        this.anchorX        = defaultX;
        this.anchorY        = defaultY;
        this.enabled        = defaultEnabled;
        this.defaultX       = defaultX;
        this.defaultY       = defaultY;
        this.defaultEnabled = defaultEnabled;
    }

    public abstract void render(DrawContext ctx, MinecraftClient mc);
    public abstract int  getWidth(MinecraftClient mc);
    public abstract int  getHeight(MinecraftClient mc);

    public int getPixelX(int sw, MinecraftClient mc) {
        return Math.max(0, Math.min((int)(anchorX * sw), sw - getWidth(mc)));
    }
    public int getPixelY(int sh, MinecraftClient mc) {
        return Math.max(0, Math.min((int)(anchorY * sh), sh - getHeight(mc)));
    }

    public void resetToDefault() {
        anchorX = defaultX;
        anchorY = defaultY;
        enabled = defaultEnabled;
    }

    public boolean isDragOnly() { return false; }

    public boolean hasSettings() { return false; }
    public int     settingsHeight() { return 0; }
    public void    renderSettings(DrawContext ctx, MinecraftClient mc, int panelX, int settingsY, int panelW, int mx, int my) {}
    public boolean handleSettingsClick(int mx, int my, int panelX, int settingsY, int panelW) { return false; }

    public abstract void loadFromConfig(ClientConfig cfg);
    public abstract void saveToConfig(ClientConfig cfg);

    protected void renderCheckbox(DrawContext ctx, TextRenderer tr, int x, int y, String lbl, boolean value, int mx, int my) {
        boolean hov = mx >= x && mx < x + 12 && my >= y && my < y + 12;
        ctx.fill(x, y, x + 12, y + 12, C_BORDER);
        ctx.fill(x + 1, y + 1, x + 11, y + 11, value ? C_GREEN : (hov ? 0xFF282B34 : 0xFF14161A));
        ctx.drawText(tr, lbl, x + 16, y + 2, value ? C_WHITE : C_MID, false);
    }

    protected static String fmtBalance(int n) {
        return String.format("%,d", n).replace(',', ' ');
    }
}
