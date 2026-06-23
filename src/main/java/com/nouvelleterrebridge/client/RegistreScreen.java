package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.RegistreNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RegistreScreen extends Screen {

    public record PersonnageData(String nomRp, String pseudoMc, boolean enLigne) {}

    public record DetailData(
        String nomRp, String pseudoMc, boolean enLigne,
        String metier, int age, String origine, String specialite,
        String traits, String passe, String descPhysique,
        String descPerso, String objectifs, String citation) {}

    private enum ViewState { LIST, LOADING, DETAIL }

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_HOVER   = 0xFF282B34;

    private static final int PW_LIST   = 400;
    private static final int PH_LIST   = 300;
    private static final int PW_DETAIL = 520;
    private static final int PH_DETAIL = 400;
    private static final int ROW_H     = 22;
    private static final int HEADER    = 38;
    private static final int PAD       = 12;
    private static final int LINE_H    = 11;

    private final List<PersonnageData> personnages;
    private ViewState viewState = ViewState.LIST;
    private DetailData detail;
    private int listScrollY   = 0;
    private int detailScrollY = 0;

    // Updated each render, used by mouseClicked / mouseScrolled
    private int px, py, pw, ph;

    public RegistreScreen(List<PersonnageData> personnages) {
        super(Text.empty());
        this.personnages = new ArrayList<>(personnages);
        this.personnages.sort(Comparator
            .comparingInt((PersonnageData p) -> p.enLigne() ? 0 : 1)
            .thenComparing(p -> p.nomRp().toLowerCase()));
    }

    // ── Network callbacks ────────────────────────────────────────────────────

    public void sendDetailRequest(String pseudo) {
        viewState = ViewState.LOADING;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(pseudo);
        ClientPlayNetworking.send(RegistreNetworking.REGISTRE_DETAIL_REQUEST, buf);
    }

    public void onDetailReceived(DetailData d) {
        this.detail = d;
        this.viewState = ViewState.DETAIL;
        this.detailScrollY = 0;
    }

    public void onDetailError() {
        this.viewState = ViewState.LIST;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        pw = Math.min(viewState == ViewState.LIST ? PW_LIST : PW_DETAIL, width  - 16);
        ph = Math.min(viewState == ViewState.LIST ? PH_LIST : PH_DETAIL, height - 16);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        ctx.fill(0, 0, width, height, 0xAA000000);
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px,          py,          px + pw,     py + 1,      C_BORDER);
        ctx.fill(px,          py + ph - 1, px + pw,     py + ph,     C_BORDER);
        ctx.fill(px,          py,          px + 1,      py + ph,     C_BORDER);
        ctx.fill(px + pw - 1, py,          px + pw,     py + ph,     C_BORDER);

        switch (viewState) {
            case LIST    -> renderList(ctx, mouseX, mouseY);
            case LOADING -> renderLoading(ctx);
            case DETAIL  -> renderDetail(ctx, mouseX, mouseY);
        }
    }

    // ── List ─────────────────────────────────────────────────────────────────

    private void renderList(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(px, py, px + pw, py + HEADER, C_PANEL);
        ctx.fill(px, py + HEADER, px + pw, py + HEADER + 1, C_BORDER);
        ctx.drawText(textRenderer, "§lRegistre des personnages", px + PAD, py + 10, C_GOLD, false);
        long online = personnages.stream().filter(PersonnageData::enLigne).count();
        ctx.drawText(textRenderer, online + " en ligne · " + personnages.size() + " personnages",
            px + PAD, py + 24, C_DIM, false);

        boolean hClose = inBounds(mouseX, mouseY, px + pw - 20, py + 8, 14, 16);
        ctx.drawText(textRenderer, hClose ? "§c✕" : "§7✕", px + pw - 16, py + 12, C_WHITE, false);

        int listTop  = py + HEADER + 1;
        int listH    = ph - HEADER - 1;
        int contentH = personnages.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - listH + 4);
        listScrollY   = Math.max(0, Math.min(listScrollY, maxScroll));

        ctx.enableScissor(px + 1, listTop, px + pw - 1, py + ph - 1);
        for (int i = 0; i < personnages.size(); i++) {
            PersonnageData p = personnages.get(i);
            int ry = listTop + i * ROW_H - listScrollY;
            if (ry + ROW_H < listTop || ry > py + ph) continue;

            boolean hov = inBounds(mouseX, mouseY, px + 1, ry, pw - 2, ROW_H);
            if (hov) ctx.fill(px + 1, ry, px + pw - 1, ry + ROW_H, C_HOVER);

            int dot = p.enLigne() ? C_GREEN : C_DIM;
            ctx.fill(px + PAD, ry + 8, px + PAD + 6, ry + 14, dot);

            ctx.drawText(textRenderer, p.nomRp(), px + PAD + 10, ry + 7, C_WHITE, false);

            if (!p.pseudoMc().isEmpty()) {
                int nw = textRenderer.getWidth(p.nomRp());
                ctx.drawText(textRenderer, "§8— §7" + p.pseudoMc(),
                    px + PAD + 10 + nw + 4, ry + 7, C_MID, false);
            }

            if (p.enLigne()) {
                String badge = "● en ligne";
                ctx.drawText(textRenderer, "§a" + badge,
                    px + pw - PAD - textRenderer.getWidth(badge), ry + 7, C_GREEN, false);
            }

            if (hov) {
                ctx.drawText(textRenderer, "§8›", px + pw - PAD - 4, ry + 7, C_DIM, false);
            }
        }
        ctx.disableScissor();

        if (contentH > listH && maxScroll > 0) {
            int sbH = Math.max(16, listH * listH / contentH);
            int sbY = listTop + listScrollY * (listH - sbH) / maxScroll;
            ctx.fill(px + pw - 5, listTop, px + pw - 2, py + ph - 1, C_PANEL);
            ctx.fill(px + pw - 5, sbY,     px + pw - 2, sbY + sbH,   C_BORDER);
        }
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    private void renderLoading(DrawContext ctx) {
        ctx.fill(px, py, px + pw, py + HEADER, C_PANEL);
        ctx.fill(px, py + HEADER, px + pw, py + HEADER + 1, C_BORDER);
        ctx.drawText(textRenderer, "§lRegistre", px + PAD, py + 14, C_GOLD, false);

        String msg = "Chargement de la fiche...";
        ctx.drawText(textRenderer, "§7" + msg,
            px + (pw - textRenderer.getWidth(msg)) / 2,
            py + HEADER + (ph - HEADER) / 2 - 4, C_MID, false);
    }

    // ── Detail ───────────────────────────────────────────────────────────────

    private void renderDetail(DrawContext ctx, int mouseX, int mouseY) {
        if (detail == null) { onDetailError(); return; }

        // Header
        ctx.fill(px, py, px + pw, py + HEADER, C_PANEL);
        ctx.fill(px, py + HEADER, px + pw, py + HEADER + 1, C_BORDER);

        boolean hBack = inBounds(mouseX, mouseY, px + PAD - 2, py + 8, 56, 16);
        ctx.drawText(textRenderer, hBack ? "§f← Retour" : "§7← Retour", px + PAD, py + 14, C_MID, false);

        // Nom RP + dot statut
        int titleX = px + PAD + 62;
        ctx.drawText(textRenderer, "§l" + detail.nomRp(), titleX, py + 10, C_WHITE, false);
        int titleW = textRenderer.getWidth("§l" + detail.nomRp());
        ctx.fill(titleX + titleW + 6, py + 13, titleX + titleW + 12, py + 19,
            detail.enLigne() ? C_GREEN : C_DIM);

        boolean hClose = inBounds(mouseX, mouseY, px + pw - 20, py + 8, 14, 16);
        ctx.drawText(textRenderer, hClose ? "§c✕" : "§7✕", px + pw - 16, py + 12, C_WHITE, false);

        // Scrollable body
        int bodyTop  = py + HEADER + 1;
        int bodyH    = ph - HEADER - 1;
        int innerW   = pw - PAD * 2 - 6;
        int contentH = measureDetail(innerW);
        int maxScroll = Math.max(0, contentH - bodyH + 12);
        detailScrollY = Math.max(0, Math.min(detailScrollY, maxScroll));

        ctx.enableScissor(px + 1, bodyTop, px + pw - 1, py + ph - 1);
        renderDetailContent(ctx, px + PAD, bodyTop - detailScrollY + 6, innerW, false);
        ctx.disableScissor();

        if (contentH > bodyH && maxScroll > 0) {
            int sbH = Math.max(16, bodyH * bodyH / contentH);
            int sbY = bodyTop + detailScrollY * (bodyH - sbH) / maxScroll;
            ctx.fill(px + pw - 5, bodyTop, px + pw - 2, py + ph - 1, C_PANEL);
            ctx.fill(px + pw - 5, sbY,     px + pw - 2, sbY + sbH,   C_BORDER);
        }
    }

    private int measureDetail(int innerW) {
        return renderDetailContent(null, 0, 0, innerW, true);
    }

    /**
     * Rend ou mesure le contenu de la fiche détail.
     * dryRun=true : aucun rendu, retourne la hauteur totale.
     * dryRun=false : rend à (x, y).
     */
    private int renderDetailContent(DrawContext ctx, int x, int y, int innerW, boolean dryRun) {
        int cy = y;

        // ── Ligne info rapide ─────────────────────────────────────────────────
        List<String> chips = new ArrayList<>();
        if (!detail.pseudoMc().isEmpty()) chips.add("§7MC: §8" + detail.pseudoMc());
        if (!detail.metier().isEmpty())   chips.add("§7Métier: §f" + detail.metier());
        if (detail.age() > 0)             chips.add("§7Âge: §f" + detail.age() + " ans");
        if (!detail.origine().isEmpty())  chips.add("§7Origine: §f" + detail.origine());
        if (!chips.isEmpty()) {
            if (!dryRun) ctx.drawText(textRenderer, String.join("  §8·  ", chips), x, cy, C_MID, false);
            cy += LINE_H + 4;
        }

        // ── Spécialité ────────────────────────────────────────────────────────
        if (!detail.specialite().isEmpty()) {
            if (!dryRun)
                ctx.drawText(textRenderer, "§7Spécialité: §f" + detail.specialite(), x, cy, C_MID, false);
            cy += LINE_H + 6;
        }

        // ── Traits ────────────────────────────────────────────────────────────
        if (!detail.traits().isEmpty()) {
            if (!dryRun)
                ctx.drawText(textRenderer, "§7Traits: §8" + detail.traits(), x, cy, C_DIM, false);
            cy += LINE_H + 8;
        }

        // ── Sections longues ──────────────────────────────────────────────────
        cy = renderSection(ctx, x, cy, innerW, "Description physique", detail.descPhysique(), dryRun);
        cy = renderSection(ctx, x, cy, innerW, "Personnage",            detail.descPerso(),   dryRun);
        cy = renderSection(ctx, x, cy, innerW, "Passé",                 detail.passe(),       dryRun);
        cy = renderSection(ctx, x, cy, innerW, "Objectifs",             detail.objectifs(),   dryRun);

        // ── Citation ──────────────────────────────────────────────────────────
        if (!detail.citation().isEmpty()) {
            List<String> lines = wrapText("\"" + detail.citation() + "\"", innerW);
            for (String line : lines) {
                if (!dryRun)
                    ctx.drawText(textRenderer, "§7§o" + line, x, cy, C_DIM, false);
                cy += LINE_H;
            }
            cy += 4;
        }

        return cy - y;
    }

    private int renderSection(DrawContext ctx, int x, int cy, int innerW,
                              String label, String text, boolean dryRun) {
        if (text == null || text.isEmpty()) return cy;
        if (!dryRun)
            ctx.drawText(textRenderer, "§e" + label, x, cy, C_GOLD, false);
        cy += LINE_H + 2;
        for (String line : wrapText(text, innerW)) {
            if (!dryRun)
                ctx.drawText(textRenderer, line, x, cy, C_MID, false);
            cy += LINE_H;
        }
        return cy + 6;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        for (String para : text.split("\n")) {
            if (para.trim().isEmpty()) { result.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : para.split("\\s+")) {
                if (word.isEmpty()) continue;
                String test = line.length() > 0 ? line + " " + word : word;
                if (textRenderer.getWidth(test) > maxWidth && line.length() > 0) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    if (line.length() > 0) line.append(" ");
                    line.append(word);
                }
            }
            if (line.length() > 0) result.add(line.toString());
        }
        return result;
    }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (viewState == ViewState.LIST) {
            int listH    = ph - HEADER - 1;
            int contentH = personnages.size() * ROW_H;
            int maxScroll = Math.max(0, contentH - listH + 4);
            listScrollY = Math.max(0, Math.min(listScrollY - (int)(amount * 14), maxScroll));
        } else if (viewState == ViewState.DETAIL) {
            int bodyH    = ph - HEADER - 1;
            int contentH = measureDetail(pw - PAD * 2 - 6);
            int maxScroll = Math.max(0, contentH - bodyH + 12);
            detailScrollY = Math.max(0, Math.min(detailScrollY - (int)(amount * 14), maxScroll));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        // Clic hors du panneau → fermer
        if (!inBounds(mx, my, px, py, pw, ph)) { this.close(); return true; }

        // Bouton ✕ (toujours)
        if (inBounds(mx, my, px + pw - 20, py + 8, 14, 16)) { this.close(); return true; }

        if (viewState == ViewState.LIST) {
            int listTop = py + HEADER + 1;
            if (my < listTop) return super.mouseClicked(mouseX, mouseY, button);
            int idx = (my - listTop + listScrollY) / ROW_H;
            if (idx >= 0 && idx < personnages.size()) {
                sendDetailRequest(personnages.get(idx).pseudoMc());
                return true;
            }
        } else if (viewState == ViewState.DETAIL) {
            // Bouton ← Retour
            if (inBounds(mx, my, px + PAD - 2, py + 8, 56, 16)) {
                viewState = ViewState.LIST;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() { return false; }
}
