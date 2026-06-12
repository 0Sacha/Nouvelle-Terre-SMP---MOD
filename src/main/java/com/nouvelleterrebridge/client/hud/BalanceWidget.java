package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.BalanceHudOverlay;
import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class BalanceWidget extends HudWidget {

    public BalanceWidget() {
        super("balance", "Solde ◆", 0.99f, 0.01f, true);
    }

    private String text(MinecraftClient mc) {
        return BalanceHudOverlay.cachedBalance < 0
            ? "? ◆"
            : fmtBalance(BalanceHudOverlay.cachedBalance) + " ◆";
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        String t = text(mc);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int w = getWidth(mc), h = getHeight(mc);
        ctx.fill(x, y, x + w, y + h, C_PANEL);
        ctx.fill(x, y, x + 2, y + h, C_GOLD);
        ctx.drawText(mc.textRenderer, t, x + 10, y + 3, C_GOLD, false);
    }

    @Override
    public int getWidth(MinecraftClient mc)  { return mc.textRenderer.getWidth(text(mc)) + 20; }
    @Override
    public int getHeight(MinecraftClient mc) { return 14; }

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
