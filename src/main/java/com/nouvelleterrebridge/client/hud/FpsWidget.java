package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Images par seconde, et latence vers le serveur. */
public class FpsWidget extends HudWidget {

    // Compteur mis à jour à chaque image depuis la couche HUD
    public static int cachedFps = 0;
    private static int  frames = 0;
    private static long lastMs = System.currentTimeMillis();

    public static void onFrame() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastMs >= 1000) {
            cachedFps = frames;
            frames    = 0;
            lastMs    = now;
        }
    }

    public FpsWidget() {
        super("fps", "FPS / Ping", 0.01f, 0.14f, false);
        option("ping", "Afficher le ping", true);
    }

    private String ping(Minecraft mc) {
        if (!opt("ping")) return "";
        if (mc.player != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) return "  " + info.getLatency() + " ms";
        }
        return enEdition ? "  0 ms" : "";
    }

    @Override protected int contentWidth(Minecraft mc)  { return mc.font.width(cachedFps + " FPS" + ping(mc)); }
    @Override protected int contentHeight(Minecraft mc) { return 8; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        int couleur = cachedFps < 30 ? C_RED : cachedFps < 60 ? C_ACCENT : C_GREEN;
        int x = texte(ctx, mc, String.valueOf(cachedFps), 0, 0, couleur);
        x = texte(ctx, mc, " FPS", x, 0, C_WHITE);
        texte(ctx, mc, ping(mc), x, 0, C_MID);
    }
}
