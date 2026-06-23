package com.nouvelleterrebridge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RegistreScreen extends Screen {

    public record PersonnageData(String nomRp, String pseudoMc, boolean enLigne) {}

    private static final int C_BG     = 0xFF14161A;
    private static final int C_PANEL  = 0xFF1B1D22;
    private static final int C_BORDER = 0xFF2A2D38;
    private static final int C_GOLD   = 0xFFE8A838;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_MID    = 0xFF9096A3;
    private static final int C_DIM    = 0xFF565C6A;
    private static final int C_GREEN  = 0xFF2EAD6B;
    private static final int C_HOVER  = 0xFF282B34;

    private static final int PW_MAX  = 400;
    private static final int PH_MAX  = 300;
    private static final int ROW_H   = 22;
    private static final int HEADER  = 38;
    private static final int PAD     = 12;

    private final List<PersonnageData> personnages;
    private int scrollY = 0;
    private int px, py, pw, ph;

    public RegistreScreen(List<PersonnageData> personnages) {
        super(Text.empty());
        this.personnages = new ArrayList<>(personnages);
        this.personnages.sort(Comparator
            .comparingInt((PersonnageData p) -> p.enLigne() ? 0 : 1)
            .thenComparing(p -> p.nomRp().toLowerCase()));
    }

    @Override
    protected void init() {
        pw = Math.min(PW_MAX, width  - 16);
        ph = Math.min(PH_MAX, height - 16);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xAA000000);

        // Panel
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px,          py,          px + pw,     py + 1,      C_BORDER);
        ctx.fill(px,          py + ph - 1, px + pw,     py + ph,     C_BORDER);
        ctx.fill(px,          py,          px + 1,       py + ph,    C_BORDER);
        ctx.fill(px + pw - 1, py,          px + pw,     py + ph,     C_BORDER);

        // Header
        ctx.fill(px, py, px + pw, py + HEADER, C_PANEL);
        ctx.fill(px, py + HEADER, px + pw, py + HEADER + 1, C_BORDER);
        ctx.drawText(textRenderer, "§lRegistre des personnages", px + PAD, py + 10, C_GOLD, false);
        long online = personnages.stream().filter(PersonnageData::enLigne).count();
        ctx.drawText(textRenderer, online + " en ligne · " + personnages.size() + " personnages",
            px + PAD, py + 24, C_DIM, false);

        // Close
        boolean hClose = mouseX >= px + pw - 20 && mouseX < px + pw - 6
                      && mouseY >= py + 8     && mouseY < py + 24;
        ctx.drawText(textRenderer, hClose ? "§c✕" : "§7✕", px + pw - 16, py + 12, C_WHITE, false);

        // List
        int listTop = py + HEADER + 1;
        int listH   = ph - HEADER - 1;
        int contentH = personnages.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - listH + 4);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));

        ctx.enableScissor(px + 1, listTop, px + pw - 1, py + ph - 1);

        for (int i = 0; i < personnages.size(); i++) {
            PersonnageData p = personnages.get(i);
            int ry = listTop + i * ROW_H - scrollY;
            if (ry + ROW_H < listTop || ry > py + ph) continue;

            boolean hov = mouseX >= px + 1 && mouseX < px + pw - 1
                       && mouseY >= ry      && mouseY < ry + ROW_H;
            if (hov) ctx.fill(px + 1, ry, px + pw - 1, ry + ROW_H, C_HOVER);

            // Dot 6×6
            int dot = p.enLigne() ? C_GREEN : C_DIM;
            ctx.fill(px + PAD, ry + 8, px + PAD + 6, ry + 14, dot);

            // Nom RP
            ctx.drawText(textRenderer, p.nomRp(), px + PAD + 10, ry + 7, C_WHITE, false);

            // Pseudo MC
            if (!p.pseudoMc().isEmpty()) {
                int nw = textRenderer.getWidth(p.nomRp());
                ctx.drawText(textRenderer, "§8— §7" + p.pseudoMc(),
                    px + PAD + 10 + nw + 4, ry + 7, C_MID, false);
            }

            // Badge "en ligne"
            if (p.enLigne()) {
                String badge = "● en ligne";
                ctx.drawText(textRenderer, "§a" + badge,
                    px + pw - PAD - textRenderer.getWidth(badge), ry + 7, C_GREEN, false);
            }
        }

        ctx.disableScissor();

        // Scrollbar
        if (contentH > listH && maxScroll > 0) {
            int sbH = Math.max(16, listH * listH / contentH);
            int sbY = listTop + scrollY * (listH - sbH) / maxScroll;
            ctx.fill(px + pw - 5, listTop, px + pw - 2, py + ph - 1, C_PANEL);
            ctx.fill(px + pw - 5, sbY, px + pw - 2, sbY + sbH, C_BORDER);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int listH    = ph - HEADER - 1;
        int contentH = personnages.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - listH + 4);
        scrollY = Math.max(0, Math.min(scrollY - (int)(amount * 14), maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (mx >= px + pw - 20 && mx < px + pw - 6 && my >= py + 8 && my < py + 24) {
            this.close(); return true;
        }
        if (mx < px || mx > px + pw || my < py || my > py + ph) {
            this.close(); return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() { return false; }
}
