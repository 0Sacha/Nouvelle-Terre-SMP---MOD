package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Champ numérique partagé par tous les écrans du mod (prix, quantités, montants).
 *
 * Saisie au clavier, pavé numérique compris. Seuls restent deux raccourcis qui ne
 * se tapent pas : <b>Min</b> et <b>Max</b> — les paliers ±1/±32/±64 ont été retirés,
 * ils encombraient la ligne pour rien dès qu'on savait taper le montant voulu.
 *
 * Le champ démarre <b>vide</b> : le placeholder dit ce qu'on attend, au lieu d'une
 * valeur pré-remplie qu'il fallait effacer avant de saisir la sienne.
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

    /** Hauteur totale : boîte de saisie + rangée Min/Max. */
    public static final int H = 42;

    private static final int BOX_H  = 20;
    private static final int STEP_H = 18;

    /**
     * Saisie en cours, en texte : une chaîne vide est un champ vide, ce qu'un int
     * seul ne sait pas représenter (0 est une valeur légitime).
     */
    private String saisie = "";
    private int min;
    private int max;
    private boolean focused = false;
    private String placeholder = "";

    private int lastX, lastY, lastW;

    public NumberInput(int value, int min, int max) {
        this.min = min;
        this.max = Math.max(min, max);
        setValue(value);
    }

    /** Valeur courante ; {@code min} si le champ est vide. */
    public int getValue() {
        if (saisie.isEmpty()) return min;
        try {
            return clamp(Integer.parseInt(saisie));
        } catch (NumberFormatException e) {
            return min;   // dépassement de int : la saisie est bornée au rendu suivant
        }
    }

    public void setValue(int v)          { saisie = String.valueOf(clamp(v)); }
    /** Vide le champ pour laisser apparaître le placeholder. */
    public void clear()                  { saisie = ""; }
    public boolean isEmpty()             { return saisie.isEmpty(); }
    public boolean isFocused()           { return focused; }
    public void setFocused(boolean f)    { focused = f; }
    public void setPlaceholder(String p) { placeholder = p; }

    /** Ajuste les bornes (ex. stock disponible qui change) sans vider la saisie. */
    public void setBounds(int min, int max) {
        this.min = min;
        this.max = Math.max(min, max);
        if (!saisie.isEmpty()) setValue(getValue());
    }

    public int getMax() { return max; }

    private int clamp(int v) { return Math.max(min, Math.min(v, max)); }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
        lastX = x; lastY = y; lastW = w;

        // Boîte de saisie — bordure or quand le champ a le focus clavier
        int border = focused ? C_GOLD : C_BORDER;
        ctx.fill(x, y, x + w, y + BOX_H, C_BG);
        ctx.fill(x, y, x + w, y + 1, border);
        ctx.fill(x, y + BOX_H - 1, x + w, y + BOX_H, border);
        ctx.fill(x, y, x + 1, y + BOX_H, border);
        ctx.fill(x + w - 1, y, x + w, y + BOX_H, border);

        boolean vide = saisie.isEmpty();
        String shown = vide ? placeholder : saisie;
        if (focused) shown += "_";
        ctx.drawText(tr, shown, x + 8, y + (BOX_H - tr.fontHeight) / 2, vide ? C_DIM : C_WHITE, false);

        // Rangée Min / Max
        int by = y + BOX_H + 4;
        int half = w / 2;
        renderBouton(ctx, tr, x,        by, half - 1,      "Min", mx, my);
        renderBouton(ctx, tr, x + half, by, w - half,      "Max", mx, my);
    }

    private void renderBouton(DrawContext ctx, TextRenderer tr, int x, int y, int w, String label, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + STEP_H;
        ctx.fill(x, y, x + w, y + STEP_H, hov ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + w, y + 1, hov ? C_GOLD : C_BORDER);
        ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (STEP_H - tr.fontHeight) / 2,
            hov ? C_GOLD : C_MID);
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    /** @return true si le clic a été consommé par le champ ou un bouton. */
    public boolean mouseClicked(int mx, int my) {
        if (mx >= lastX && mx < lastX + lastW && my >= lastY && my < lastY + BOX_H) {
            focused = true;
            return true;
        }

        int by = lastY + BOX_H + 4;
        if (my >= by && my < by + STEP_H && mx >= lastX && mx < lastX + lastW) {
            setValue(mx < lastX + lastW / 2 ? min : max);
            focused = true;
            return true;
        }

        focused = false;
        return false;
    }

    /**
     * Touches de contrôle uniquement : retour arrière, effacement, validation.
     *
     * Les chiffres sont laissés à {@link #charTyped} — pavé numérique compris.
     * Les traiter ici <i>aussi</i> (touches GLFW 320-329) les comptait deux fois :
     * taper « 1 » au pavé numérique écrivait « 11 », rendant le champ inutilisable.
     */
    public boolean keyPressed(int key) {
        if (!focused) return false;
        // GLFW : 259 = backspace, 261 = suppr, 257/335 = entrée
        if (key == 259) {
            if (!saisie.isEmpty()) saisie = saisie.substring(0, saisie.length() - 1);
            return true;
        }
        if (key == 261) {
            saisie = "";
            return true;
        }
        if (key == 257 || key == 335) {
            focused = false;
            return true;
        }
        return false;
    }

    /** Chiffres — rangée du haut comme pavé numérique. */
    public boolean charTyped(char chr) {
        if (!focused) return false;
        if (chr < '0' || chr > '9') return false;
        if (saisie.equals("0")) saisie = "";          // pas de zéro en tête
        if (saisie.length() >= 9) return true;        // borne avant débordement de int
        saisie += chr;
        return true;
    }
}
