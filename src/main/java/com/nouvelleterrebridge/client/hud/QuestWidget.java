package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Quêtes en cours et leur progression. */
public class QuestWidget extends HudWidget {

    // Listes mises à jour par NouvelleTerreBridgeClient sur QUEST_OPEN / QUEST_RESULT
    public static final List<String>  activeLabels     = new ArrayList<>();
    public static final List<String>  activeProgresses = new ArrayList<>();
    public static final List<Boolean> activeGroups     = new ArrayList<>();

    public QuestWidget() {
        super("quest", "Quêtes actives", 0.35f, 0.02f, false);
    }

    private List<String> lignes() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < activeLabels.size(); i++) {
            String prog = i < activeProgresses.size() ? activeProgresses.get(i) : null;
            boolean groupe = i < activeGroups.size() && activeGroups.get(i);
            out.add((groupe ? "§b● §f" : "§6● §f") + activeLabels.get(i)
                + (prog != null ? " §7" + prog : ""));
        }
        if (out.isEmpty() && enEdition) {
            out.add("§6● §fTuer des zombies §73/10");
            out.add("§b● §fRécolter du blé §748/64");
        }
        return out;
    }

    @Override
    public boolean aAfficher(Minecraft mc) {
        return !activeLabels.isEmpty();
    }

    @Override
    protected int contentWidth(Minecraft mc) {
        int max = 0;
        for (String l : lignes()) max = Math.max(max, mc.font.width(l));
        return Math.max(max, 20);
    }

    @Override
    protected int contentHeight(Minecraft mc) {
        return Math.max(1, lignes().size()) * LIGNE - 2;
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        List<String> ls = lignes();
        for (int i = 0; i < ls.size(); i++) texte(ctx, mc, ls.get(i), 0, i * LIGNE, C_WHITE);
    }
}
