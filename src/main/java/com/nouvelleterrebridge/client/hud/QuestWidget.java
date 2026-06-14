package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class QuestWidget extends HudWidget {

    // Listes mises à jour par NouvelleTerreBridgeClient sur QUEST_OPEN / QUEST_RESULT
    public static final List<String>  activeLabels     = new ArrayList<>();
    public static final List<String>  activeProgresses = new ArrayList<>();
    public static final List<Boolean> activeGroups     = new ArrayList<>();

    private static final int C_BG = 0xAA14161A;

    public QuestWidget() {
        super("quest", "Quêtes actives", 0.35f, 0.02f, false);
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
        if (activeLabels.isEmpty()) return 80;
        int max = 0;
        for (int i = 0; i < activeLabels.size(); i++)
            max = Math.max(max, mc.textRenderer.getWidth(buildLine(i)));
        return max + 12;
    }

    @Override
    public int getHeight(MinecraftClient mc) {
        int n = activeLabels.size();
        return n == 0 ? 13 : n * 13 + 2;
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (activeLabels.isEmpty()) return;

        int x = getPixelX(mc.getWindow().getScaledWidth(), mc);
        int y = getPixelY(mc.getWindow().getScaledHeight(), mc);
        int w = getWidth(mc);
        int h = getHeight(mc);

        ctx.fill(x, y, x + w, y + h, C_BG);
        // Barre d'accent gauche colorée selon le type de la première quête
        boolean firstGroup = !activeGroups.isEmpty() && activeGroups.get(0);
        ctx.fill(x, y, x + 2, y + h, firstGroup ? 0xFF5BA8D4 : C_GOLD);

        for (int i = 0; i < activeLabels.size(); i++) {
            ctx.drawText(mc.textRenderer, buildLine(i), x + 6, y + 2 + i * 13, C_WHITE, false);
        }
    }

    private static String buildLine(int i) {
        String lbl  = activeLabels.get(i);
        String prog = i < activeProgresses.size() ? activeProgresses.get(i) : null;
        boolean grp = i < activeGroups.size() && activeGroups.get(i);
        String prefix  = grp ? "§b● §f" : "§6● §f";
        String progStr = prog != null ? " §7[" + prog + "]" : "";
        return prefix + lbl + progStr;
    }
}
