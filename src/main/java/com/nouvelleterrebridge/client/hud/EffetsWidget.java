package com.nouvelleterrebridge.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Effets de potion en cours, avec leur durée restante. */
public class EffetsWidget extends HudWidget {

    private static final int MAX = 6;
    private static final String[] ROMAIN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private record Ligne(String texte, int couleur) {}

    public EffetsWidget() { super("effets", "Effets actifs", 0.01f, 0.29f, false); }

    private List<Ligne> lignes(Minecraft mc) {
        List<Ligne> out = new ArrayList<>();
        if (mc.player != null) {
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                if (out.size() >= MAX) break;
                var type = e.getEffect().value();
                String nom = type.getDisplayName().getString();
                int amp = e.getAmplifier();
                if (amp > 0) nom += " " + (amp < ROMAIN.length ? ROMAIN[amp] : String.valueOf(amp + 1));
                // Une durée « infinie » (balise, effet de mod) ne s'affiche pas
                if (!e.isInfiniteDuration()) {
                    int s = e.getDuration() / 20;
                    nom += " §7" + (s / 60) + ":" + String.format("%02d", s % 60);
                }
                out.add(new Ligne(nom, type.isBeneficial() ? C_GREEN : C_RED));
            }
        }
        if (out.isEmpty() && enEdition) {
            out.add(new Ligne("Vitesse II §71:30", C_GREEN));
            out.add(new Ligne("Poison §70:12", C_RED));
        }
        return out;
    }

    @Override
    public boolean aAfficher(Minecraft mc) {
        return mc.player != null && !mc.player.getActiveEffects().isEmpty();
    }

    @Override
    protected int contentWidth(Minecraft mc) {
        int max = 0;
        for (Ligne l : lignes(mc)) max = Math.max(max, mc.font.width(l.texte()));
        return Math.max(max, 20);
    }

    @Override
    protected int contentHeight(Minecraft mc) {
        return Math.max(1, lignes(mc).size()) * LIGNE - 2;
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft mc) {
        List<Ligne> ls = lignes(mc);
        for (int i = 0; i < ls.size(); i++) texte(ctx, mc, ls.get(i).texte(), 0, i * LIGNE, ls.get(i).couleur());
    }
}
