package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.ServiceNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * /server-admin — monitoring de l'économie du serveur.
 *
 * Regroupe ce qu'un administrateur doit pouvoir vérifier d'un coup d'œil : la
 * trésorerie du compte $Serveur, l'argent retenu en séquestre par LeBonCube, la
 * masse monétaire et l'inflation qu'elle provoque sur les prix du shop, et les
 * litiges en attente d'arbitrage.
 */
@Environment(EnvType.CLIENT)
public class ServerAdminScreen extends Screen {

    public record LitigeData(int id, String titre, String client, String prestataire,
                             int prix, int sequestre, String demandeur) {}

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_BLUE    = 0xFF3B82F6;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    private static final int MAX_PW = 620;
    private static final int MAX_PH = 440;
    private static final int TOP_H  = 40;
    private static final int PAD    = 12;
    private static final int GAP    = 8;

    private int pw, ph, px, py;

    private int soldeServeur, soldeSequestre, joueursConnus, medianJoueurs, enLigne;
    private int annoncesHdv, annoncesMarche;
    private long masseMonetaire;
    private double inflation;
    private List<LitigeData> litiges = new ArrayList<>();

    private int scroll = 0;
    private final List<int[]> bounds = new ArrayList<>();

    public ServerAdminScreen(int soldeServeur, int soldeSequestre, long masseMonetaire,
                             int joueursConnus, int medianJoueurs, int enLigne,
                             int annoncesHdv, int annoncesMarche, double inflation,
                             List<LitigeData> litiges) {
        super(Text.literal("Administration serveur"));
        maj(soldeServeur, soldeSequestre, masseMonetaire, joueursConnus, medianJoueurs,
            enLigne, annoncesHdv, annoncesMarche, inflation, litiges);
    }

    public void maj(int soldeServeur, int soldeSequestre, long masseMonetaire,
                    int joueursConnus, int medianJoueurs, int enLigne,
                    int annoncesHdv, int annoncesMarche, double inflation,
                    List<LitigeData> litiges) {
        this.soldeServeur   = soldeServeur;
        this.soldeSequestre = soldeSequestre;
        this.masseMonetaire = masseMonetaire;
        this.joueursConnus  = joueursConnus;
        this.medianJoueurs  = medianJoueurs;
        this.enLigne        = enLigne;
        this.annoncesHdv    = annoncesHdv;
        this.annoncesMarche = annoncesMarche;
        this.inflation      = inflation;
        this.litiges        = new ArrayList<>(litiges);
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_PW, width  - 20);
        ph = Math.min(MAX_PH, height - 20);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px, py, px + pw, py + 1, C_BORDER);
        ctx.fill(px, py + ph - 1, px + pw, py + ph, C_BORDER);

        bounds.clear();

        ctx.fill(px, py, px + pw, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H, px + pw, py + TOP_H + 1, C_BORDER);
        ctx.drawText(textRenderer, "⚙  Administration serveur", px + PAD, py + 9, C_RED, false);
        ctx.drawText(textRenderer, "§8Monitoring économique — réservé aux administrateurs",
            px + PAD, py + 23, C_DIM, false);

        int cy = py + TOP_H + PAD;
        int cardW = (pw - PAD * 2 - GAP * 2) / 3;
        int cardH = 54;

        carte(ctx, px + PAD, cy, cardW, cardH, C_GOLD, "TRÉSORERIE $Serveur", fmt(soldeServeur) + " ◆");
        carte(ctx, px + PAD + cardW + GAP, cy, cardW, cardH, C_BLUE, "EN SÉQUESTRE",
            fmt(soldeSequestre) + " ◆");
        carte(ctx, px + PAD + (cardW + GAP) * 2, cy, cardW, cardH, C_GREEN, "MASSE MONÉTAIRE",
            fmt((int) Math.min(Integer.MAX_VALUE, masseMonetaire)) + " ◆");
        cy += cardH + GAP;

        carte(ctx, px + PAD, cy, cardW, cardH, C_MID, "JOUEURS",
            enLigne + " en ligne / " + joueursConnus);
        carte(ctx, px + PAD + cardW + GAP, cy, cardW, cardH, C_MID, "SOLDE MÉDIAN",
            fmt(medianJoueurs) + " ◆");
        carte(ctx, px + PAD + (cardW + GAP) * 2, cy, cardW, cardH,
            inflation > 1.2 ? C_RED : C_MID, "INFLATION SHOP",
            String.format("×%.2f", inflation));
        cy += cardH + GAP;

        carte(ctx, px + PAD, cy, cardW, cardH, C_MID, "ANNONCES HDV", String.valueOf(annoncesHdv));
        carte(ctx, px + PAD + cardW + GAP, cy, cardW, cardH, C_MID, "ANNONCES LEBONCUBE",
            String.valueOf(annoncesMarche));
        carte(ctx, px + PAD + (cardW + GAP) * 2, cy, cardW, cardH,
            litiges.isEmpty() ? C_MID : C_RED, "LITIGES EN ATTENTE",
            String.valueOf(litiges.size()));
        cy += cardH + GAP;

        // Litiges à arbitrer
        int listH = ph - (cy - py) - PAD;
        ctx.fill(px + PAD, cy, px + pw - PAD, cy + listH, C_PANEL);
        ctx.fill(px + PAD, cy, px + PAD + 3, cy + listH, litiges.isEmpty() ? C_BORDER : C_RED);
        ctx.drawText(textRenderer, "LITIGES — ARBITRAGE", px + PAD + 12, cy + 8, C_DIM, false);

        if (litiges.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Aucun litige en cours.",
                px + pw / 2, cy + listH / 2 - 4, C_DIM);
            super.render(ctx, mx, my, delta);
            return;
        }

        int rowH = 44;
        int listY = cy + 24;
        int visRows = Math.max(1, (listH - 28) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, litiges.size() - visRows)));

        ctx.enableScissor(px + PAD, listY, px + pw - PAD, cy + listH);
        for (int i = scroll; i < Math.min(scroll + visRows + 1, litiges.size()); i++) {
            LitigeData l = litiges.get(i);
            int ry = listY + (i - scroll) * rowH;
            ctx.fill(px + PAD + 6, ry, px + pw - PAD - 6, ry + rowH - 3, C_SURFACE);
            ctx.drawText(textRenderer, l.titre(), px + PAD + 14, ry + 6, C_WHITE, false);
            ctx.drawText(textRenderer, "§7" + l.client() + " §8→ §7" + l.prestataire()
                + "  §8·  §6" + l.prix() + " ◆ §8· séquestre §6" + l.sequestre() + " ◆",
                px + PAD + 14, ry + 19, C_MID, false);
            ctx.drawText(textRenderer, "§cAnnulation demandée par " + l.demandeur(),
                px + PAD + 14, ry + 31, C_RED, false);

            int droite = px + pw - PAD - 14;
            droite = bouton(ctx, "Payer le presta", droite, ry + 12, mx, my, C_GREEN,
                ServiceNetworking.ADMIN_PAYER, l.id()) - 6;
            bouton(ctx, "Rembourser", droite, ry + 12, mx, my, C_RED,
                ServiceNetworking.ADMIN_REMBOURSER, l.id());
        }
        ctx.disableScissor();

        super.render(ctx, mx, my, delta);
    }

    private void carte(DrawContext ctx, int x, int y, int w, int h,
                       int accent, String titre, String valeur) {
        ctx.fill(x, y, x + w, y + h, C_PANEL);
        ctx.fill(x, y, x + 3, y + h, accent);
        ctx.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        ctx.drawText(textRenderer, titre, x + 10, y + 10, C_DIM, false);
        ctx.drawText(textRenderer, valeur, x + 10, y + 30, accent, false);
    }

    private int bouton(DrawContext ctx, String label, int droite, int y, int mx, int my,
                       int couleur, int action, int id) {
        int w = textRenderer.getWidth(label) + 14;
        int x = droite - w;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 18;
        ctx.fill(x, y, x + w, y + 18, hov ? couleur : C_SURFACE);
        ctx.fill(x, y, x + w, y + 1, couleur);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 5, hov ? C_WHITE : C_MID);
        bounds.add(new int[]{x, y, w, 18, action, id});
        return x;
    }

    private static String fmt(int n) {
        if (Math.abs(n) < 1000) return String.valueOf(n);
        return fmt(n / 1000) + " " + String.format("%03d", Math.abs(n % 1000));
    }

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;
        if (x < px || x > px + pw || y < py || y > py + ph) { close(); return true; }
        for (int[] b : bounds) {
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeInt(b[4]);
                buf.writeInt(b[5]);
                ClientPlayNetworking.send(ServiceNetworking.ADMIN_ACTION, buf);
                return true;
            }
        }
        return super.mouseClicked(mx0, my0, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount));
        return true;
    }
}
