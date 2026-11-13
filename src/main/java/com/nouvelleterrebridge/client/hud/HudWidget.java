package com.nouvelleterrebridge.client.hud;

import com.nouvelleterrebridge.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un élément du HUD.
 *
 * Un widget ne décrit que son <b>contenu</b> ({@link #contentWidth},
 * {@link #contentHeight}, {@link #renderContent}), dessiné à partir de l'origine.
 * Tout le reste est commun et géré ici : position, taille, fond, ombre du texte
 * et options propres. C'est ce qui donne à tous les widgets la même allure et les
 * mêmes réglages dans l'éditeur, sans rien réécrire dans chacun.
 */
public abstract class HudWidget {

    public static final int C_ACCENT = 0xFFE8A838;
    public static final int C_GREEN  = 0xFF4ADE80;
    public static final int C_RED    = 0xFFF87171;
    public static final int C_WHITE  = 0xFFFFFFFF;
    public static final int C_MID    = 0xFFB4B9C4;
    public static final int C_DIM    = 0xFF7A808C;

    /** Fond translucide commun à tous les widgets. */
    private static final int C_FOND = 0x78000000;

    protected static final int PAD_X = 5;
    protected static final int PAD_Y = 4;
    /** Hauteur d'une ligne de texte, interligne compris. */
    protected static final int LIGNE = 10;

    public static final float TAILLE_MIN = 0.5f;
    public static final float TAILLE_MAX = 2.0f;

    /**
     * Vrai pendant le rendu de l'éditeur. Un widget dont le contenu dépend du jeu
     * (effets actifs, quêtes, armure) affiche alors un exemple au lieu de rien :
     * on ne peut pas placer ni régler un widget invisible.
     */
    public static boolean enEdition = false;

    /** Réglage oui/non propre à un widget, affiché tel quel dans l'éditeur. */
    public record Option(String cle, String label, boolean defaut) {}

    public final String id;
    public final String label;
    private final float   defautX, defautY;
    private final boolean defautActif;

    public float   anchorX, anchorY;
    public boolean enabled;
    public float   taille = 1.0f;
    public boolean fond   = true;
    public boolean ombre  = true;

    private final List<Option> options = new ArrayList<>();
    private final Map<String, Boolean> valeurs = new HashMap<>();

    protected HudWidget(String id, String label, float defautX, float defautY, boolean defautActif) {
        this.id          = id;
        this.label       = label;
        this.defautX     = defautX;
        this.defautY     = defautY;
        this.defautActif = defautActif;
        this.anchorX     = defautX;
        this.anchorY     = defautY;
        this.enabled     = defautActif;
    }

    // ── Contenu (à fournir par chaque widget) ─────────────────────────────────

    protected abstract int  contentWidth(Minecraft mc);
    protected abstract int  contentHeight(Minecraft mc);
    protected abstract void renderContent(GuiGraphics ctx, Minecraft mc);

    /** Faux quand le widget n'a rien à montrer en jeu (aucun effet, aucune quête…). */
    public boolean aAfficher(Minecraft mc) { return true; }

    /** Vrai pour un widget qui ne sert qu'à choisir une position (les notifications). */
    public boolean isDragOnly() { return false; }

    public boolean redimensionnable() { return !isDragOnly(); }

    // ── Options ───────────────────────────────────────────────────────────────

    protected void option(String cle, String label, boolean defaut) {
        options.add(new Option(cle, label, defaut));
        valeurs.put(cle, defaut);
    }

    public List<Option> options() { return options; }

    public boolean opt(String cle) { return valeurs.getOrDefault(cle, false); }

    public void setOpt(String cle, boolean valeur) { valeurs.put(cle, valeur); }

    // ── Géométrie ─────────────────────────────────────────────────────────────

    public int baseWidth(Minecraft mc)  { return contentWidth(mc)  + (isDragOnly() ? 0 : PAD_X * 2); }
    public int baseHeight(Minecraft mc) { return contentHeight(mc) + (isDragOnly() ? 0 : PAD_Y * 2); }

    public int getWidth(Minecraft mc)  { return Math.max(1, Math.round(baseWidth(mc)  * taille)); }
    public int getHeight(Minecraft mc) { return Math.max(1, Math.round(baseHeight(mc) * taille)); }

    /** Position à l'écran, toujours ramenée dans les bords. */
    public int getPixelX(int sw, Minecraft mc) {
        return Math.max(0, Math.min((int) (anchorX * sw), sw - getWidth(mc)));
    }

    public int getPixelY(int sh, Minecraft mc) {
        return Math.max(0, Math.min((int) (anchorY * sh), sh - getHeight(mc)));
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    /** Rendu en jeu, à la position enregistrée. */
    public void render(GuiGraphics ctx, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        renderAt(ctx, mc, getPixelX(sw, mc), getPixelY(sh, mc), taille);
    }

    public void renderAt(GuiGraphics ctx, Minecraft mc, float x, float y, float echelle) {
        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0);
        ctx.pose().scale(echelle, echelle, 1f);
        if (!isDragOnly()) {
            if (fond) fondArrondi(ctx, 0, 0, baseWidth(mc), baseHeight(mc), C_FOND);
            ctx.pose().translate(PAD_X, PAD_Y, 0);
        }
        renderContent(ctx, mc);
        ctx.pose().popPose();
    }

    /** Aperçu centré sur ({@code cx}, {@code cy}), réduit pour tenir dans le cadre donné. */
    public void renderApercu(GuiGraphics ctx, Minecraft mc, int cx, int cy, int maxW, int maxH) {
        int bw = baseWidth(mc), bh = baseHeight(mc);
        float echelle = Math.min(1.5f, Math.min(maxW / (float) bw, maxH / (float) bh));
        renderAt(ctx, mc, cx - bw * echelle / 2f, cy - bh * echelle / 2f, echelle);
    }

    /** Rectangle aux coins adoucis d'un pixel. */
    public static void fondArrondi(GuiGraphics ctx, int x, int y, int w, int h, int couleur) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, couleur);
        ctx.fill(x, y + 1, x + w, y + h - 1, couleur);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, couleur);
    }

    /** Écrit un texte avec le réglage d'ombre du widget. @return l'abscisse de fin */
    protected int texte(GuiGraphics ctx, Minecraft mc, String s, int x, int y, int couleur) {
        ctx.drawString(mc.font, s, x, y, couleur, ombre);
        return x + mc.font.width(s);
    }

    // ── Persistance ───────────────────────────────────────────────────────────

    public void reinitialiser() {
        anchorX = defautX;
        anchorY = defautY;
        taille  = 1.0f;
        fond    = true;
        ombre   = true;
        for (Option o : options) valeurs.put(o.cle(), o.defaut());
    }

    public void loadFromConfig(ClientConfig cfg) {
        ClientConfig.WidgetState s = cfg.widgets != null ? cfg.widgets.get(id) : null;
        // Premier lancement après la refonte : on reprend les anciens réglages
        if (s == null) s = cfg.ancienEtat(id);
        if (s == null) { enabled = defautActif; return; }

        enabled = s.enabled;
        anchorX = s.x;
        anchorY = s.y;
        taille  = Math.max(TAILLE_MIN, Math.min(s.taille <= 0 ? 1f : s.taille, TAILLE_MAX));
        fond    = s.fond;
        ombre   = s.ombre;
        if (s.options != null)
            for (Option o : options)
                if (s.options.containsKey(o.cle())) valeurs.put(o.cle(), s.options.get(o.cle()));
    }

    public void saveToConfig(ClientConfig cfg) {
        ClientConfig.WidgetState s = new ClientConfig.WidgetState();
        s.enabled = enabled;
        s.x       = anchorX;
        s.y       = anchorY;
        s.taille  = taille;
        s.fond    = fond;
        s.ombre   = ombre;
        s.options = new HashMap<>(valeurs);
        if (cfg.widgets == null) cfg.widgets = new HashMap<>();
        cfg.widgets.put(id, s);
    }

    protected static String fmtBalance(int n) {
        return String.format("%,d", n).replace(',', ' ');
    }
}
