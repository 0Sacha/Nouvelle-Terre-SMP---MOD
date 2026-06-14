package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.QuestNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import io.netty.buffer.Unpooled;

import java.util.*;

@Environment(EnvType.CLIENT)
public class QuetesScreen extends Screen {

    // ── Data ─────────────────────────────────────────────────────────────────

    public record QuestData(int id, String type, String target, int quantity, int reward, String label) {}

    // ── Couleurs ─────────────────────────────────────────────────────────────

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int PW         = 420;
    private static final int PH         = 300;
    private static final int TOP_H      = 42;
    private static final int PAD        = 12;
    private static final int CARD_H     = 68;
    private static final int GAP        = 6;
    private static final int VISIBLE    = 3;
    private static final int SCROLL_W   = 4;
    private static final int BTN_W      = 80;
    private static final int BTN_H      = 18;

    private int px, py;

    // ── State ─────────────────────────────────────────────────────────────────

    private List<QuestData>        allQuests  = new ArrayList<>();
    private Map<Integer, Integer>  inProgress = new HashMap<>(); // questId → progress
    private Set<Integer>           completed  = new HashSet<>();

    private boolean tabMesQuetes = false;
    private int scrollOffset = 0;

    // ── Construction ──────────────────────────────────────────────────────────

    public QuetesScreen(List<QuestData> quests,
                        Map<Integer, Integer> inProgress,
                        Set<Integer> completed) {
        super(Text.literal("Quêtes"));
        this.allQuests  = quests;
        this.inProgress = inProgress;
        this.completed  = completed;
    }

    @Override
    protected void init() {
        px = (width  - PW) / 2;
        py = (height - PH) / 2;
    }

    @Override
    public boolean shouldPause() { return false; }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // fond
        ctx.fill(px, py, px + PW, py + PH, C_BG);
        ctx.fill(px, py, px + PW, py + 1, C_BORDER);
        ctx.fill(px, py + PH - 1, px + PW, py + PH, C_BORDER);
        ctx.fill(px, py, px + 1, py + PH, C_BORDER);
        ctx.fill(px + PW - 1, py, px + PW, py + PH, C_BORDER);

        // header
        ctx.fill(px, py, px + PW, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H, px + PW, py + TOP_H + 1, C_BORDER);
        ctx.drawText(textRenderer, "⚔  Quêtes", px + PAD, py + 14, C_GOLD, false);

        // tabs
        renderTabs(ctx, mx, my);

        // contenu
        List<QuestData> list = getFilteredList();
        int totalRows  = list.size();
        int visRows    = Math.min(VISIBLE, totalRows);
        int contentY   = py + TOP_H + 1;
        int contentH   = PH - TOP_H - 1;
        int startIdx   = Math.max(0, Math.min(scrollOffset, totalRows - VISIBLE));
        scrollOffset   = startIdx;

        for (int i = 0; i < visRows; i++) {
            int idx = startIdx + i;
            if (idx >= list.size()) break;
            QuestData q  = list.get(idx);
            int cardY    = contentY + PAD + i * (CARD_H + GAP);
            int cardX    = px + PAD;
            int cardW    = PW - PAD * 2 - SCROLL_W - 4;
            boolean hover = mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + CARD_H;
            renderCard(ctx, q, cardX, cardY, cardW, CARD_H, hover, mx, my);
        }

        // scrollbar
        if (totalRows > VISIBLE) {
            int trackX = px + PW - PAD - SCROLL_W + 2;
            int trackY = contentY + PAD;
            int trackH = VISIBLE * (CARD_H + GAP) - GAP;
            ctx.fill(trackX, trackY, trackX + SCROLL_W, trackY + trackH, C_BORDER);
            float ratio  = (float) VISIBLE / totalRows;
            int   thumbH = Math.max(16, (int)(trackH * ratio));
            int   thumbY = trackY + (int)((trackH - thumbH) * ((float) startIdx / (totalRows - VISIBLE)));
            ctx.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, C_GOLD);
        }

        // empty state
        if (list.isEmpty()) {
            String msg = tabMesQuetes ? "Aucune quête en cours." : "Aucune quête disponible.";
            ctx.drawText(textRenderer, msg, px + PW / 2 - textRenderer.getWidth(msg) / 2,
                    contentY + contentH / 2 - 4, C_DIM, false);
        }

        super.render(ctx, mx, my, delta);
    }

    private void renderTabs(DrawContext ctx, int mx, int my) {
        String[] labels = {"Disponibles", "Mes Quêtes"};
        boolean[] active = {!tabMesQuetes, tabMesQuetes};
        int tabW = 120;
        int tabY = py + 22;
        for (int i = 0; i < 2; i++) {
            int tabX = px + PW - tabW * 2 - PAD + i * (tabW + 2);
            boolean isActive = active[i];
            boolean hover = mx >= tabX && mx < tabX + tabW && my >= tabY && my < tabY + 16;
            ctx.fill(tabX, tabY, tabX + tabW, tabY + 16,
                    isActive ? C_SURFACE : (hover ? C_HOVER : C_PANEL));
            if (isActive) ctx.fill(tabX, tabY + 14, tabX + tabW, tabY + 16, C_GOLD);
            int textX = tabX + (tabW - textRenderer.getWidth(labels[i])) / 2;
            ctx.drawText(textRenderer, labels[i], textX, tabY + 4,
                    isActive ? C_GOLD : C_MID, false);
        }
    }

    private void renderCard(DrawContext ctx, QuestData q, int x, int y, int w, int h,
                            boolean hover, int mx, int my) {
        ctx.fill(x, y, x + w, y + h, hover ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 1, y + h, C_GOLD);
        ctx.fill(x, y + h - 1, x + w, y + h, C_BORDER);

        // badge type
        boolean isKill    = "KILL".equals(q.type());
        String  badge     = isKill ? "⚔" : "⛏";
        int     badgeCol  = isKill ? C_RED : C_GREEN;
        ctx.drawText(textRenderer, badge, x + 8, y + 6, badgeCol, false);

        // label
        ctx.drawText(textRenderer, q.label(), x + 22, y + 6, C_WHITE, false);

        // target + objectif
        String target = q.target().contains(":") ? q.target().split(":")[1] : q.target();
        String objStr = target.replace("_", " ") + " × " + q.quantity();
        ctx.drawText(textRenderer, objStr, x + 22, y + 18, C_MID, false);

        // récompense
        String rew = "+" + q.reward() + " ◆";
        ctx.drawText(textRenderer, rew, x + 22, y + 30, C_GOLD, false);

        boolean acceptedQuest = inProgress.containsKey(q.id());
        boolean doneQuest     = completed.contains(q.id());

        if (acceptedQuest) {
            int prog  = inProgress.getOrDefault(q.id(), 0);
            int total = q.quantity();
            float pct = Math.min(1f, (float) prog / total);

            // barre de progression
            int barX = x + 22;
            int barY = y + 44;
            int barW = w - 110;
            ctx.fill(barX, barY, barX + barW, barY + 4, C_BORDER);
            ctx.fill(barX, barY, barX + (int)(barW * pct), barY + 4, pct >= 1f ? C_GREEN : C_GOLD);

            String progStr = prog + "/" + total;
            ctx.drawText(textRenderer, progStr, barX + barW + 4, barY - 2, C_MID, false);

            if (pct >= 1f) {
                // bouton Réclamer
                int bx = x + w - BTN_W - 6;
                int by = y + h - BTN_H - 8;
                boolean bhover = mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H;
                ctx.fill(bx, by, bx + BTN_W, by + BTN_H, bhover ? C_GREEN : 0xFF1A6645);
                ctx.fill(bx, by, bx + BTN_W, by + 1, C_GREEN);
                int tw = textRenderer.getWidth("Réclamer");
                ctx.drawText(textRenderer, "Réclamer", bx + (BTN_W - tw) / 2, by + 5, C_WHITE, false);
            }
        } else if (doneQuest) {
            ctx.drawText(textRenderer, "✅ Terminée", x + w - 80, y + 6, C_GREEN, false);
        } else {
            // bouton Accepter
            int bx = x + w - BTN_W - 6;
            int by = y + h - BTN_H - 8;
            boolean bhover = mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H;
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, bhover ? C_GOLD : 0xFF5A3F10);
            ctx.fill(bx, by, bx + BTN_W, by + 1, C_GOLD);
            int tw = textRenderer.getWidth("Accepter");
            ctx.drawText(textRenderer, "Accepter", bx + (BTN_W - tw) / 2, by + 5, C_WHITE, false);
        }
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;

        // tabs
        int tabW = 120;
        int tabY = py + 22;
        for (int i = 0; i < 2; i++) {
            int tabX = px + PW - tabW * 2 - PAD + i * (tabW + 2);
            if (imx >= tabX && imx < tabX + tabW && imy >= tabY && imy < tabY + 16) {
                tabMesQuetes = (i == 1);
                scrollOffset = 0;
                return true;
            }
        }

        // cards
        List<QuestData> list = getFilteredList();
        int contentY = py + TOP_H + 1;
        int startIdx = Math.max(0, Math.min(scrollOffset, list.size() - VISIBLE));
        for (int i = 0; i < VISIBLE; i++) {
            int idx   = startIdx + i;
            if (idx >= list.size()) break;
            QuestData q   = list.get(idx);
            int cardX  = px + PAD;
            int cardY  = contentY + PAD + i * (CARD_H + GAP);
            int cardW  = PW - PAD * 2 - SCROLL_W - 4;
            if (imx < cardX || imx >= cardX + cardW || imy < cardY || imy >= cardY + CARD_H) continue;

            boolean accepted = inProgress.containsKey(q.id());
            boolean done     = completed.contains(q.id());
            int bx = cardX + cardW - BTN_W - 6;
            int by = cardY + CARD_H - BTN_H - 8;

            if (imx >= bx && imx < bx + BTN_W && imy >= by && imy < by + BTN_H) {
                if (accepted) {
                    int prog = inProgress.getOrDefault(q.id(), 0);
                    if (prog >= q.quantity()) sendAction(QuestNetworking.ACTION_CLAIM, q.id());
                } else if (!done) {
                    sendAction(QuestNetworking.ACTION_ACCEPT, q.id());
                }
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int total = getFilteredList().size();
        if (total > VISIBLE) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(amount), total - VISIBLE));
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<QuestData> getFilteredList() {
        List<QuestData> result = new ArrayList<>();
        for (QuestData q : allQuests) {
            boolean done     = completed.contains(q.id());
            boolean accepted = inProgress.containsKey(q.id());
            if (tabMesQuetes) {
                if (accepted && !done) result.add(q);
            } else {
                if (!done && !accepted) result.add(q);
            }
        }
        return result;
    }

    private void sendAction(int action, int questId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(action);
        buf.writeInt(questId);
        ClientPlayNetworking.send(QuestNetworking.QUEST_ACTION, buf);
    }

    // ── Update depuis réseau ──────────────────────────────────────────────────

    public void update(List<QuestData> quests, Map<Integer, Integer> progress, Set<Integer> completed) {
        this.allQuests  = quests;
        this.inProgress = progress;
        this.completed  = completed;
    }
}
