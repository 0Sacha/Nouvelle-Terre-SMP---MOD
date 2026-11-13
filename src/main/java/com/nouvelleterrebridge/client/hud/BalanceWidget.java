package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.nouvelleterrebridge.client.BalanceHudOverlay;

/** Solde du compte en banque. */
public class BalanceWidget extends HudWidget {

    public BalanceWidget() {
        super("balance", "Solde ◆", 0.99f, 0.01f, true);
    }

    private String valeur() {
        return BalanceHudOverlay.cachedBalance < 0
            ? "? ◆"
            : fmtBalance(BalanceHudOverlay.cachedBalance) + " ◆";
    }

    @Override protected int contentWidth(Minecraft mc)  { return mc.font.width(valeur()); }
    @Override protected int contentHeight(Minecraft mc) { return 8; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        texte(ctx, mc, valeur(), 0, 0, C_ACCENT);
    }
}
