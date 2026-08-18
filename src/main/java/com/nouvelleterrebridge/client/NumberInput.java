package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Champ numérique partagé par tous les écrans du mod (prix, quantités, montants).
 *
 * Deux façons de saisir, parce qu'aucune ne suffit seule : les paliers
 * (-Max/-64/-32/-1/+1/+32/+64/Max) pour ajuster à la souris sans viser un champ
 * texte, et la frappe directe au clavier — pavé numérique inclus — pour entrer
 * un gros montant sans cliquer cinquante fois.
 *
 * Les bornes sont mémorisées au rendu (`lastX/lastY/lastW`) et relues par
 * `mouseClicked` : recalculer les positions côté clic les désynchroniserait dès
 * que la mise en page bouge.
 */
@Environment(EnvType.CLIENT)
public class NumberInput {

    private static final int C_BG      = 0xFF14161A;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    /** Hauteur totale : boîte de valeur + rangée de paliers. */
    public static final int H = 42;

    private static final int BOX_H  = 20;
    private static final int STEP_H = 18;
    private static final String[] LABELS = {"Min", "-64", "-32", "-1", "+1", "+32", "+64", "Max"};
    private static final int[]    DELTAS = {Integer.MIN_VALUE, -64, -32, -1, 1, 32, 64, Integer.MAX_VALUE};

    private int value;
    private int min;
    private int max;
    private boolean focused = false;
    private String placeholder = "";

    private int lastX, lastY, lastW;

    public NumberInput(int value, int min, int max) {
        this.min = min;
        this.max = max;
        this.value = clamp(value);
    }

    public int  getValue()            { return value; }
    public void setValue(int v)       { value = clamp(v); }
    public boolean isFocused()        { return focused; }
    public void setFocused(boolean f) { focused = f; }
    public void setPlaceholder(String p) { placeholder = p; }

    /** Ajuste les bornes (ex. stock disponible qui change) en re-clampant la valeur. */
    public void setBounds(int min, int max) {
        this.min = min;
        this.max = Math.max(min, max);
        this.value = clamp(value);
    }

    private int clamp(int v) { return Math.max(min, Math.min(v, max)); }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
        lastX = x; lastY = y; lastW = w;

        // Boîte de valeur — bordure or quand le champ a le focus clavier
        int border = focused ? C_GOLD : C_BORDER;
        ctx.fill(x, y, x + w, y + BOX_H, C_BG);
        ctx.fill(x, y, x + w, y + 1, border);
        ctx.fill(x, y + BOX_H - 1, x + w, y + BOX_H, border);
        ctx.fill(x, y, x + 1, y + BOX_H, border);
        ctx.fill(x + w - 1, y, x + w, y + BOX_H, border);

        boolean empty = value == 0 && !placeholder.isEmpty() && !focused;
        String shown = empty ? placeholder : String.valueOf(value);
        if (focused) shown += "_";
        ctx.drawText(tr, shown, x + 8, y + (BOX_H - tr.fontHeight) / 2, empty ? C_DIM : C_WHITE, false);

        // Rangée de paliers
        int by = y + BOX_H + 4;
        int bw = w / LABELS.length;
        for (int i = 0; i < LABELS.length; i++) {
            int bx = x + i * bw;
            int bwEff = (i == LABELS.length - 1) ? (x + w - bx) : bw - 1;
            boolean hov = mx >= bx && mx < bx + bwEff && my >= by && my < by + STEP_H;
            boolean edge = DELTAS[i] == Integer.MIN_VALUE || DELTAS[i] == Integer.MAX_VALUE;
            ctx.fill(bx, by, bx + bwEff, by + STEP_H, hov ? C_HOVER : C_SURFACE);
            ctx.fill(bx, by, bx + bwEff, by + 1, hov ? C_GOLD : C_BORDER);
            ctx.drawCenteredTextWithShadow(tr, LABELS[i], bx + bwEff / 2, by + (STEP_H - tr.fontHeight) / 2,
                hov ? C_GOLD : (edge ? C_MID : C_DIM));
        }
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    /** @return true si le clic a été consommé par le champ ou un palier. */
    public boolean mouseClicked(int mx, int my) {
        if (mx >= lastX && mx < lastX + lastW && my >= lastY && my < lastY + BOX_H) {
            focused = true;
            return true;
        }

        int by = lastY + BOX_H + 4;
        if (my >= by && my < by + STEP_H && mx >= lastX && mx < lastX + lastW) {
            int bw  = lastW / LABELS.length;
            int idx = Math.min(LABELS.length - 1, (mx - lastX) / Math.max(1, bw));
            int d   = DELTAS[idx];
            if      (d == Integer.MIN_VALUE) value = min;
            else if (d == Integer.MAX_VALUE) value = max;
            else                             value = clamp(value + d);
            focused = true;
            return true;
        }

        focused = false;
        return false;
    }

    /** Frappe clavier : chiffres (pavé numérique inclus), retour arrière, effacement. */
    public boolean keyPressed(int key) {
        if (!focused) return false;
        // GLFW : 259 = backspace, 261 = suppr, 257/335 = entrée
        if (key == 259) {
            value = clamp(value / 10);
            return true;
        }
        if (key == 261) {
            value = clamp(0);
            return true;
        }
        if (key == 257 || key == 335) {
            focused = false;
            return true;
        }
        // Pavé numérique : GLFW KP_0..KP_9 = 320..329 (charTyped ne les remonte pas
        // toujours selon la disposition clavier, on les traite donc explicitement)
        if (key >= 320 && key <= 329) {
            append(key - 320);
            return true;
        }
        return false;
    }

    /** Chiffres de la rangée du haut, remontés en tant que caractères. */
    public boolean charTyped(char chr) {
        if (!focused) return false;
        if (chr >= '0' && chr <= '9') {
            append(chr - '0');
            return true;
        }
        return false;
    }

    private void append(int digit) {
        long next = (long) value * 10 + digit;
        value = clamp((int) Math.min(next, Integer.MAX_VALUE));
    }
}
