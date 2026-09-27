package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.NtNet;

import com.nouvelleterrebridge.network.ConflitNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI /conflit : déclarer un conflit RP contre un joueur en ligne.
 * Liste des joueurs (clic pour sélectionner) + champ raison + bouton Déclarer.
 * Le Conseil des Fondateurs est alerté sur Discord (event CONFLICT_DECLARED).
 */
public class ConflitScreen extends Screen {

    // ── Couleurs (palette commune) ─────────────────────────────────────────────

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;
    private static final int C_DARK    = 0xFF353840;

    // ── Layout ─────────────────────────────────────────────────────────────────

    private static final int MAX_PW = 340;
    private static final int MAX_PH = 320;
    private static final int TOP_H  = 40;
    private static final int PAD    = 12;
    private static final int ROW_H  = 20;

    private int pw, ph, px, py;

    // ── State ──────────────────────────────────────────────────────────────────

    private final List<String> players;
    private int selectedIdx = -1;
    private int scroll = 0;

    private EditBox reasonField;
    private int declareBtnY = -1;
    private int listY, listH;

    public ConflitScreen(List<String> players) {
        super(Component.literal("Déclarer un conflit"));
        this.players = new ArrayList<>(players);
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_PW, width  - 20);
        ph = Math.min(MAX_PH, height - 20);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        reasonField = new EditBox(font, px + PAD, 0, pw - PAD * 2, 18, Component.empty());
        reasonField.setMaxLength(120);
        reasonField.setHint(Component.literal("Raison du conflit..."));
        addRenderableWidget(reasonField);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        // No-op — cet écran dessine son propre fond ; super.render() (appelé en dernier
        // pour les widgets vanilla) réappliquerait sinon flou + texture menu par-dessus.
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px, py, px + pw, py + 1, C_BORDER);
        ctx.fill(px, py + ph - 1, px + pw, py + ph, C_BORDER);
        ctx.fill(px, py, px + 1, py + ph, C_BORDER);
        ctx.fill(px + pw - 1, py, px + pw, py + ph, C_BORDER);

        // Header
        ctx.fill(px, py, px + pw, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H, px + pw, py + TOP_H + 1, C_BORDER);
        HubBackButton.render(ctx, font, px + PAD, py + (TOP_H - HubBackButton.H) / 2, mx, my);
        int titleX = px + PAD + HubBackButton.W + 8;
        ctx.drawString(font, "⚔  Déclarer un conflit RP", titleX, py + 9, C_RED, false);
        ctx.drawString(font, "Le Conseil des Fondateurs sera alerté", titleX, py + 23, C_DIM, false);

        // Zone basse : label + champ raison + bouton
        int bottomH = 14 + 22 + 26 + PAD;
        int by = py + ph - bottomH;

        // Liste des joueurs
        ctx.drawString(font, "CONTRE QUI ?", px + PAD, py + TOP_H + 8, C_DIM, false);
        listY = py + TOP_H + 20;
        listH = by - listY - 6;
        int visRows = Math.max(1, listH / ROW_H);

        if (players.isEmpty()) {
            ctx.drawString(font, "Aucun autre joueur en ligne.", px + PAD, listY + 8, C_DIM, false);
        } else {
            int maxScroll = Math.max(0, players.size() - visRows);
            scroll = Math.min(scroll, maxScroll);
            for (int i = scroll; i < Math.min(scroll + visRows, players.size()); i++) {
                int ry = listY + (i - scroll) * ROW_H;
                boolean sel = i == selectedIdx;
                boolean hov = mx >= px + PAD && mx < px + pw - PAD && my >= ry && my < ry + ROW_H;
                ctx.fill(px + PAD, ry, px + pw - PAD, ry + ROW_H - 2, sel ? C_HOVER : (hov ? C_HOVER : C_PANEL));
                if (sel) ctx.fill(px + PAD, ry, px + PAD + 3, ry + ROW_H - 2, C_RED);
                ctx.drawString(font, players.get(i), px + PAD + 8, ry + 5, sel ? C_WHITE : C_MID, false);
                if (sel) {
                    String mark = "⚔";
                    ctx.drawString(font, mark, px + pw - PAD - font.width(mark) - 6, ry + 5, C_RED, false);
                }
            }
            // Scrollbar
            if (players.size() > visRows) {
                int trackX = px + pw - PAD + 4;
                ctx.fill(trackX, listY, trackX + 3, listY + listH, C_BORDER);
                float ratio  = (float) visRows / players.size();
                int   thumbH = Math.max(12, (int)(listH * ratio));
                int   thumbY = maxScroll > 0 ? listY + (int)((listH - thumbH) * ((float) scroll / maxScroll)) : listY;
                ctx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, C_GOLD);
            }
        }

        // Champ raison
        ctx.drawString(font, "POURQUOI ?", px + PAD, by, C_DIM, false);
        reasonField.setX(px + PAD);
        reasonField.setY(by + 12);
        reasonField.setWidth(pw - PAD * 2);
        reasonField.render(ctx, mx, my, delta);

        // Bouton Déclarer
        boolean canDeclare = selectedIdx >= 0 && reasonField.getValue().trim().length() >= 3;
        declareBtnY = by + 12 + 24;
        boolean bhov = canDeclare && mx >= px + PAD && mx < px + pw - PAD
            && my >= declareBtnY && my < declareBtnY + 24;
        ctx.fill(px + PAD, declareBtnY, px + pw - PAD, declareBtnY + 24,
            canDeclare ? (bhov ? 0xFF8B1030 : C_RED) : C_DARK);
        String lbl = selectedIdx >= 0
            ? "⚔ Déclarer le conflit contre " + players.get(selectedIdx)
            : "Sélectionne un joueur";
        lbl = truncate(lbl, pw - PAD * 2 - 12);
        ctx.drawCenteredString(font, lbl, px + pw / 2, declareBtnY + 8,
            canDeclare ? C_WHITE : C_DIM);

        super.render(ctx, mx, my, delta);
    }

    // ── Interactions ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;
        if (x < px || x > px + pw || y < py || y > py + ph) { onClose(); return true; }

        if (HubBackButton.clicked(px + PAD, py + (TOP_H - HubBackButton.H) / 2, x, y)) return true;

        // D'abord le champ raison (focus clavier)
        if (super.mouseClicked(mx0, my0, btn)) return true;

        // Sélection joueur
        if (!players.isEmpty() && x >= px + PAD && x < px + pw - PAD && y >= listY && y < listY + listH) {
            int idx = scroll + (y - listY) / ROW_H;
            if (idx >= 0 && idx < players.size()) { selectedIdx = idx; return true; }
        }

        // Bouton Déclarer
        if (declareBtnY >= 0 && x >= px + PAD && x < px + pw - PAD
                && y >= declareBtnY && y < declareBtnY + 24) {
            String reason = reasonField.getValue().trim();
            if (selectedIdx >= 0 && reason.length() >= 3) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(players.get(selectedIdx));
                buf.writeUtf(reason);
                NtNet.versServeur(ConflitNetworking.CONFLIT_ACTION, buf);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double amount) {
        int visRows = Math.max(1, listH / ROW_H);
        int maxScroll = Math.max(0, players.size() - visRows);
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount), maxScroll));
        return true;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String truncate(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 1 && font.width(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
