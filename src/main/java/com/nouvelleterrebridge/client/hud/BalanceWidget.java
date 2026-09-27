package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.BalanceHudOverlay;
import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class BalanceWidget extends HudWidget {

    public BalanceWidget() {
        super("balance", "Solde ◆", 0.99f, 0.01f, true);
    }

    private String text(Minecraft mc) {
        return BalanceHudOverlay.cachedBalance < 0
            ? "? ◆"
            : fmtBalance(BalanceHudOverlay.cachedBalance) + " ◆";
    }

    @Override
    public void render(GuiGraphics ctx, Minecraft mc) {
        String t = text(mc);
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int w = getWidth(mc), h = getHeight(mc);
        ctx.fill(x, y, x + w, y + h, C_PANEL);
        ctx.fill(x, y, x + 2, y + h, C_GOLD);
        ctx.drawString(mc.font, t, x + 10, y + 3, C_GOLD, false);
    }

    @Override
    public int getWidth(Minecraft mc)  { return mc.font.width(text(mc)) + 20; }
    @Override
    public int getHeight(Minecraft mc) { return 14; }

    @Override
    public void loadFromConfig(ClientConfig cfg) {
        enabled = cfg.hudEnabled;
        anchorX = cfg.balanceX;
        anchorY = cfg.balanceY;
    }

    @Override
    public void saveToConfig(ClientConfig cfg) {
        cfg.hudEnabled = enabled;
        cfg.balanceX   = anchorX;
        cfg.balanceY   = anchorY;
    }
}
