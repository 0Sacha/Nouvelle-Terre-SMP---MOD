package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class EffetsWidget extends HudWidget {

    private static final int MAX_EFFECTS = 5;
    private static final int LINE_H      = 12;
    private static final String[] ROMAN  = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};

    public EffetsWidget() { super("effets", "Effets actifs", 0.01f, 0.29f, false); }

    @Override
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = getPixelX(sw, mc), y = getPixelY(sh, mc);
        int w = getWidth(mc), h = getHeight(mc);
        List<StatusEffectInstance> effects = getEffects(mc);

        ctx.fill(x, y, x + w, y + h, C_PANEL);
        ctx.fill(x, y, x + 2, y + h, C_GOLD);

        if (effects.isEmpty()) {
            ctx.drawText(mc.textRenderer, "Aucun effet", x + 6, y + 3, C_DIM, false);
        } else {
            for (int i = 0; i < effects.size(); i++) {
                StatusEffectInstance e = effects.get(i);
                boolean good  = e.getEffectType().isBeneficial();
                int     color = good ? C_GREEN : C_RED;
                String  name  = e.getEffectType().getName().getString();
                if (name.length() > 14) name = name.substring(0, 13) + ".";
                int amp = e.getAmplifier();
                if (amp > 0) name += " " + (amp < ROMAN.length ? ROMAN[amp] : amp + 1);
                int secs = e.getDuration() / 20;
                if (secs < 999) name += " (" + secs + "s)";
                ctx.drawText(mc.textRenderer, name, x + 6, y + 3 + i * LINE_H, color, false);
            }
        }
    }

    private List<StatusEffectInstance> getEffects(MinecraftClient mc) {
        var all = new ArrayList<>(mc.player.getStatusEffects());
        if (all.size() > MAX_EFFECTS) all = new ArrayList<>(all.subList(0, MAX_EFFECTS));
        return all;
    }

    @Override public int getWidth(MinecraftClient mc) { return mc.textRenderer.getWidth("Fatigue min. VIII (999s)") + 12; }

    @Override public int getHeight(MinecraftClient mc) {
        if (mc.player == null) return 14;
        int count = Math.min(mc.player.getStatusEffects().size(), MAX_EFFECTS);
        return Math.max(14, count * LINE_H + 6);
    }

    @Override public void loadFromConfig(ClientConfig cfg) { enabled = cfg.effetsEnabled; anchorX = cfg.effetsX; anchorY = cfg.effetsY; }
    @Override public void saveToConfig(ClientConfig cfg)   { cfg.effetsEnabled = enabled; cfg.effetsX = anchorX; cfg.effetsY = anchorY; }
}
