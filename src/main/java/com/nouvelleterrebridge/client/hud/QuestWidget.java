package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class QuestWidget extends HudWidget {

    // Mises à jour par NouvelleTerreBridgeClient sur QUEST_OPEN / QUEST_RESULT
    public static String  activeLabel    = null;
    public static String  activeProgress = null;
    public static boolean activeGroup    = false;

    private static final int C_BG = 0xAA14161A;

    public QuestWidget() {
        super("quest", "Quête active", 0.35f, 0.02f, false);
    }

    @Override
    public void loadFromConfig(ClientConfig cfg) {
        enabled = cfg.questEnabled;
        anchorX = cfg.questX;
        anchorY = cfg.questY;
    }

    @Override
    public void saveToConfig(ClientConfig cfg) {
        cfg.questEnabled = enabled;
        cfg.questX       = anchorX;
        cfg.questY       = anchorY;
    }

    @Override
    public int getWidth(MinecraftClient mc) {
        if (activeLabel == null) return 80;
        String full = buildText();
        return mc.textRenderer.getWidth(full) + 10;
    }

    @Override
    public int getHeight(MinecraftClient mc) { return 13; }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (activeLabel == null || activeLabel.isEmpty()) return;

        int x = getPixelX(mc.getWindow().getScaledWidth(), mc);
        int y = getPixelY(mc.getWindow().getScaledHeight(), mc);
        String full = buildText();
        int w = mc.textRenderer.getWidth(full) + 10;

        ctx.fill(x, y, x + w, y + 13, C_BG);
        ctx.fill(x, y, x + 2, y + 13, activeGroup ? 0xFF5BA8D4 : C_GOLD);
        ctx.drawText(mc.textRenderer, full, x + 6, y + 3, C_WHITE, false);
    }

    private static String buildText() {
        String prefix = activeGroup ? "§b👥 " : "§6⚔ ";
        String prog   = activeProgress != null ? " §7[" + activeProgress + "]" : "";
        return prefix + "§f" + activeLabel + prog;
    }
}
