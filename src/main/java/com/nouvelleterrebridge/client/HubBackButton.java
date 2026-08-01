package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Flèche « retour au Parchemin », partagée par tous les écrans du mod.
 *
 * Le hub ne dépend d'aucune donnée serveur : on peut le rouvrir directement
 * côté client, sans aller-retour réseau.
 */
@Environment(EnvType.CLIENT)
public final class HubBackButton {

    public static final int W = 22;
    public static final int H = 18;

    private static final int C_STRIP = 0xFF1E2128;
    private static final int C_HOVER = 0xFF282B34;
    private static final int C_GOLD  = 0xFFE8A838;
    private static final int C_MID   = 0xFF9096A3;

    private HubBackButton() {}

    public static boolean isHovered(int x, int y, int mx, int my) {
        return mx >= x && mx < x + W && my >= y && my < y + H;
    }

    public static void render(DrawContext ctx, TextRenderer tr, int x, int y, int mx, int my) {
        boolean hov = isHovered(x, y, mx, my);
        ctx.fill(x, y, x + W, y + H, hov ? C_HOVER : C_STRIP);
        ctx.fill(x, y, x + W, y + 1, hov ? C_GOLD : 0xFF2A2D38);
        ctx.fill(x, y + H - 1, x + W, y + H, hov ? C_GOLD : 0xFF2A2D38);
        ctx.fill(x, y, x + 1, y + H, hov ? C_GOLD : 0xFF2A2D38);
        ctx.fill(x + W - 1, y, x + W, y + H, hov ? C_GOLD : 0xFF2A2D38);
        ctx.drawCenteredTextWithShadow(tr, "←", x + W / 2, y + (H - tr.fontHeight) / 2, hov ? C_GOLD : C_MID);
    }

    /** Ouvre le hub si le clic tombe sur la flèche. @return true si le clic a été consommé. */
    public static boolean clicked(int x, int y, int mx, int my) {
        if (!isHovered(x, y, mx, my)) return false;
        MinecraftClient.getInstance().setScreen(new HubScreen());
        return true;
    }
}
