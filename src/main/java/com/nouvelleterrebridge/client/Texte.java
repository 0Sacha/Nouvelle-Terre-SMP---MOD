package com.nouvelleterrebridge.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Texte centré <b>sans ombre</b>, pour tous les écrans du mod.
 *
 * ⚠ Ne pas revenir à {@code GuiGraphics.drawCenteredString} : il dessine toujours
 * une ombre portée, qui fait paraître les petits libellés gras et brouillés —
 * surtout sur un bouton coloré. Sacha l'a signalé plusieurs fois (LeBonCube en
 * 1.5.0, Shop en 2.3.0).
 */
public final class Texte {

    private Texte() {}

    public static void centre(GuiGraphics ctx, Font font, String texte, int cx, int y, int couleur) {
        ctx.drawString(font, texte, cx - font.width(texte) / 2, y, couleur, false);
    }

    public static void centre(GuiGraphics ctx, Font font, Component texte, int cx, int y, int couleur) {
        ctx.drawString(font, texte, cx - font.width(texte) / 2, y, couleur, false);
    }

    public static void centre(GuiGraphics ctx, Font font, FormattedCharSequence texte, int cx, int y, int couleur) {
        ctx.drawString(font, texte, cx - font.width(texte) / 2, y, couleur, false);
    }
}
