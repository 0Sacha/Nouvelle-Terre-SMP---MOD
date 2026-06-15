package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.QuestNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;

import java.util.*;

@Environment(EnvType.CLIENT)
public class QuetesScreen extends Screen {

    // ── Data records ──────────────────────────────────────────────────────────

    public record QuestData(int id, String type, String target, int quantity,
                            int levelRequired, int maxPlayers, String rewardType,
                            int rewardShards, String rewardItem, int rewardItemQty,
                            int rewardXp, int costShards, String label, long expiresAt,
                            List<String> tags) {}

    public record ActiveQuestData(int questId, QuestData snapshot, int progress,
                                  boolean turnedIn, List<String> participants) {}

    public record PendingRewardData(String label, String itemId, int qty, long completedAt) {}

    public record LeaderboardEntry(String name, int value) {}

    // ── Couleurs ──────────────────────────────────────────────────────────────

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_BLUE    = 0xFF5BA8D4;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int MAX_PW = 540;
    private static final int MAX_PH = 380;
    private static final int TOP_H  = 44;
    private static final int PAD    = 12;
    private static final int COLS   = 3;
    private static final int GAP    = 8;
    private static final int CARD_H = 120;
    private static final int BTN_H  = 18;

    private int pw, ph, px, py, cardW;

    // ── State ─────────────────────────────────────────────────────────────────

    private int playerLevel = 0;
    private int playerXp    = 0;
    private int xpToNext    = 100;

    private List<QuestData>         available    = new ArrayList<>();
    private List<ActiveQuestData>   active       = new ArrayList<>();
    private List<PendingRewardData> pending      = new ArrayList<>();
    private Map<Integer, Integer>   groupPending = new HashMap<>();
    private List<LeaderboardEntry>  lbCompleted  = new ArrayList<>();
    private List<LeaderboardEntry>  lbLevel      = new ArrayList<>();

    private enum Tab { DISPONIBLES, EN_COURS, A_RECLAMER, CLASSEMENTS }
    private Tab tab       = Tab.DISPONIBLES;
    private int scrollRow = 0;

    private final List<int[]> cardBounds = new ArrayList<>();

    // ── Constructeur ──────────────────────────────────────────────────────────

    public QuetesScreen() { super(Text.literal("Quêtes")); }

    public QuetesScreen(int level, int xp, int xpNext,
                        List<QuestData> available, List<ActiveQuestData> active,
                        List<PendingRewardData> pending, Map<Integer, Integer> groupPending,
                        List<LeaderboardEntry> lbCompleted, List<LeaderboardEntry> lbLevel) {
        super(Text.literal("Quêtes"));
        update(level, xp, xpNext, available, active, pending, groupPending, lbCompleted, lbLevel);
    }

    @Override
    protected void init() {
        pw    = Math.min(MAX_PW, width  - 20);
        ph    = Math.min(MAX_PH, height - 20);
        px    = (width  - pw) / 2;
        py    = (height - ph) / 2;
        cardW = (pw - PAD * 2 - (COLS - 1) * GAP) / COLS;
    }

    @Override public boolean shouldPause() { return false; }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px, py, px + pw, py + 1, C_BORDER);
        ctx.fill(px, py + ph - 1, px + pw, py + ph, C_BORDER);
        ctx.fill(px, py, px + 1, py + ph, C_BORDER);
        ctx.fill(px + pw - 1, py, px + pw, py + ph, C_BORDER);

        // Header
        ctx.fill(px, py, px + pw, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H, px + pw, py + TOP_H + 1, C_BORDER);
        ctx.drawText(textRenderer, "⚔  Quêtes", px + PAD, py + 8, C_GOLD, false);
        ctx.drawText(textRenderer, "Niv. " + playerLevel + "  §8" + playerXp + "/" + xpToNext + " XP",
                px + PAD, py + 22, C_MID, false);

        int barX = px + PAD, barY = py + 36, barW2 = pw / 4;
        ctx.fill(barX, barY, barX + barW2, barY + 3, C_BORDER);
        int filled = xpToNext > 0 ? (int)((float) playerXp / xpToNext * barW2) : 0;
        ctx.fill(barX, barY, barX + filled, barY + 3, C_GOLD);

        renderTabs(ctx, mx, my);

        cardBounds.clear();
        int contentY = py + TOP_H + 1;
        switch (tab) {
            case DISPONIBLES  -> renderAvailable(ctx, mx, my, contentY);
            case EN_COURS     -> renderActive(ctx, mx, my, contentY);
            case A_RECLAMER   -> renderPending(ctx, mx, my, contentY);
            case CLASSEMENTS  -> renderClassements(ctx, mx, my, contentY);
        }

        super.render(ctx, mx, my, delta);
    }

    private void renderTabs(DrawContext ctx, int mx, int my) {
        Tab[]    tabs   = {Tab.DISPONIBLES, Tab.EN_COURS, Tab.A_RECLAMER, Tab.CLASSEMENTS};
        String[] labels = {
            "Disponibles",
            "En cours (" + active.size() + ")",
            "À Réclamer (" + pending.size() + ")",
            "Classements"
        };
        int tabW = (pw - PAD * 2) / 4;
        int tabY = py + TOP_H - 18;
        for (int i = 0; i < 4; i++) {
            int  tabX = px + PAD + i * (tabW + 1);
            boolean act = tab == tabs[i];
            boolean hov = !act && mx >= tabX && mx < tabX + tabW && my >= tabY && my < tabY + 16;
            ctx.fill(tabX, tabY, tabX + tabW, tabY + 16, act ? C_SURFACE : (hov ? C_HOVER : C_PANEL));
            if (act) ctx.fill(tabX, tabY + 14, tabX + tabW, tabY + 16, C_GOLD);
            int tw = textRenderer.getWidth(labels[i]);
            // Tronquer le texte si nécessaire
            String lbl = labels[i];
            while (textRenderer.getWidth(lbl) > tabW - 4 && lbl.length() > 3)
                lbl = lbl.substring(0, lbl.length() - 1);
            ctx.drawText(textRenderer, lbl, tabX + (tabW - textRenderer.getWidth(lbl)) / 2, tabY + 4,
                    act ? C_GOLD : C_MID, false);
        }
    }

    // ── Onglet Disponibles ────────────────────────────────────────────────────

    private void renderAvailable(DrawContext ctx, int mx, int my, int startY) {
        List<QuestData> list = getAvailableFiltered();
        int rows      = (list.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, rows - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scrollRow + row) * COLS + col;
                if (idx >= list.size()) continue;
                QuestData q = list.get(idx);
                int cx = px + PAD + col * (cardW + GAP);
                int cy = startY + PAD + row * (CARD_H + GAP);
                boolean hover = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H;
                renderAvailableCard(ctx, q, cx, cy, hover, mx, my);
            }
        }
        renderScrollbar(ctx, startY, rows, visibleRows(), scrollRow, maxScroll);
    }

    private void renderAvailableCard(DrawContext ctx, QuestData q, int x, int y, boolean hover, int mx, int my) {
        ctx.fill(x, y, x + cardW, y + CARD_H, hover ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + CARD_H, diffColor(q));
        ctx.fill(x, y + CARD_H - 1, x + cardW, y + CARD_H, C_BORDER);

        renderItemIcon(ctx, q.target(), x + cardW - 22, y + 7);

        List<String> filteredTags = q.tags().stream()
            .filter(t -> "SOLO".equals(t) || "GROUPE".equals(t)
                      || "KILL".equals(t) || "HARVEST".equals(t) || "DELIVERY".equals(t))
            .toList();
        int cy = renderTagsCompact(ctx, filteredTags, x + 6, y + 8);

        ctx.drawText(textRenderer, q.label(), x + 6, cy, C_WHITE, false);
        int labelW = textRenderer.getWidth(q.label());
        ctx.drawText(textRenderer, " ×" + q.quantity(), x + 6 + labelW, cy, C_MID, false);
        cy += 11;

        renderReward(ctx, q, x + 6, cy);

        if (q.costShards() > 0) {
            cy += 11;
            ctx.drawText(textRenderer, "§c−" + q.costShards() + " ◆", x + 6, cy, C_RED, false);
        }

        if (q.maxPlayers() > 1) {
            int accepted = groupPending.getOrDefault(q.id(), 0);
            ctx.drawText(textRenderer, "§b👥 " + accepted + "/" + q.maxPlayers(),
                    x + 6, y + CARD_H - BTN_H - 22, C_BLUE, false);
        }

        String btnLabel = q.maxPlayers() > 1 ? "Rejoindre" : "Accepter";
        int bw = textRenderer.getWidth(btnLabel) + 12;
        int bx = x + cardW - bw - 4;
        int by = y + CARD_H - BTN_H - 4;
        boolean bhov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H;
        ctx.fill(bx, by, bx + bw, by + BTN_H, bhov ? C_GOLD : 0xFF5A3F10);
        ctx.fill(bx, by, bx + bw, by + 1, C_GOLD);
        ctx.drawText(textRenderer, btnLabel, bx + (bw - textRenderer.getWidth(btnLabel)) / 2, by + 5, C_WHITE, false);
        cardBounds.add(new int[]{bx, by, bw, BTN_H, q.id(), QuestNetworking.ACTION_ACCEPT});
    }

    // ── Onglet En cours ───────────────────────────────────────────────────────

    private void renderActive(DrawContext ctx, int mx, int my, int startY) {
        if (active.isEmpty()) { drawCentered(ctx, "Aucune quête en cours.", startY + 60); return; }
        int rows = (active.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, rows - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scrollRow + row) * COLS + col;
                if (idx >= active.size()) continue;
                ActiveQuestData aq = active.get(idx);
                int cx = px + PAD + col * (cardW + GAP);
                int cy = startY + PAD + row * (CARD_H + GAP);
                boolean hover = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H;
                renderActiveCard(ctx, aq, cx, cy, hover, mx, my);
            }
        }
        renderScrollbar(ctx, startY, rows, visibleRows(), scrollRow, maxScroll);
    }

    private void renderActiveCard(DrawContext ctx, ActiveQuestData aq, int x, int y, boolean hover, int mx, int my) {
        QuestData q = aq.snapshot();
        if (q == null) return;

        ctx.fill(x, y, x + cardW, y + CARD_H, hover ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + CARD_H, diffColor(q));
        ctx.fill(x, y + CARD_H - 1, x + cardW, y + CARD_H, C_BORDER);

        renderItemIcon(ctx, q.target(), x + cardW - 22, y + 7);
        List<String> filteredTags = q.tags().stream()
            .filter(t -> "SOLO".equals(t) || "GROUPE".equals(t)
                      || "KILL".equals(t) || "HARVEST".equals(t) || "DELIVERY".equals(t))
            .toList();
        int cy = renderTagsCompact(ctx, filteredTags, x + 6, y + 8);
        ctx.drawText(textRenderer, q.label(), x + 6, cy, C_WHITE, false);
        int lw = textRenderer.getWidth(q.label());
        ctx.drawText(textRenderer, " ×" + q.quantity(), x + 6 + lw, cy, C_MID, false);
        cy += 11;

        int cancelBtnY = y + CARD_H - BTN_H - 4;
        int claimBtnY  = cancelBtnY - BTN_H - 4;

        if ("DELIVERY".equals(q.type())) {
            boolean hasItems = hasItemsInInventory(q.target(), q.quantity());
            ctx.drawText(textRenderer, (hasItems ? "§a✓ " : "§c✗ ") + fmtItem(q.target()), x + 6, cy,
                    hasItems ? C_GREEN : C_RED, false);
            if (hasItems && !aq.turnedIn()) {
                int bw = textRenderer.getWidth("Remettre") + 12;
                int bx = x + cardW - bw - 4;
                boolean bhov = mx >= bx && mx < bx + bw && my >= claimBtnY && my < claimBtnY + BTN_H;
                ctx.fill(bx, claimBtnY, bx + bw, claimBtnY + BTN_H, bhov ? C_GREEN : 0xFF1A6645);
                ctx.fill(bx, claimBtnY, bx + bw, claimBtnY + 1, C_GREEN);
                ctx.drawText(textRenderer, "Remettre", bx + 6, claimBtnY + 5, C_WHITE, false);
                cardBounds.add(new int[]{bx, claimBtnY, bw, BTN_H, aq.questId(), QuestNetworking.ACTION_CLAIM});
            } else if (aq.turnedIn()) {
                ctx.drawText(textRenderer, "→ À Réclamer", x + 6, cy + 11, C_GOLD, false);
            }
        } else {
            int prog = aq.progress(), total = q.quantity();
            float pct = total > 0 ? Math.min(1f, (float) prog / total) : 0f;
            int barW2 = cardW - 12;
            ctx.fill(x + 6, cy, x + 6 + barW2, cy + 4, C_BORDER);
            ctx.fill(x + 6, cy, x + 6 + (int)(barW2 * pct), cy + 4, pct >= 1f ? C_GREEN : C_GOLD);
            ctx.drawText(textRenderer, prog + "/" + total, x + 6, cy + 6, C_MID, false);
            if (pct >= 1f) {
                int bw = textRenderer.getWidth("Réclamer") + 12;
                int bx = x + cardW - bw - 4;
                boolean bhov = mx >= bx && mx < bx + bw && my >= claimBtnY && my < claimBtnY + BTN_H;
                ctx.fill(bx, claimBtnY, bx + bw, claimBtnY + BTN_H, bhov ? C_GREEN : 0xFF1A6645);
                ctx.fill(bx, claimBtnY, bx + bw, claimBtnY + 1, C_GREEN);
                ctx.drawText(textRenderer, "Réclamer", bx + 6, claimBtnY + 5, C_WHITE, false);
                cardBounds.add(new int[]{bx, claimBtnY, bw, BTN_H, aq.questId(), QuestNetworking.ACTION_CLAIM});
            }
        }

        int cw2 = textRenderer.getWidth("Annuler") + 10;
        boolean chov = mx >= x + 6 && mx < x + 6 + cw2 && my >= cancelBtnY && my < cancelBtnY + BTN_H;
        ctx.fill(x + 6, cancelBtnY, x + 6 + cw2, cancelBtnY + BTN_H, chov ? C_RED : 0xFF3D0A16);
        ctx.fill(x + 6, cancelBtnY, x + 6 + cw2, cancelBtnY + 1, C_RED);
        ctx.drawText(textRenderer, "Annuler", x + 11, cancelBtnY + 5, C_WHITE, false);
        cardBounds.add(new int[]{x + 6, cancelBtnY, cw2, BTN_H, aq.questId(), QuestNetworking.ACTION_CANCEL});
    }

    // ── Onglet À Réclamer ─────────────────────────────────────────────────────

    private void renderPending(DrawContext ctx, int mx, int my, int startY) {
        if (pending.isEmpty()) { drawCentered(ctx, "Aucune récompense en attente.", startY + 60); return; }
        int rows = (pending.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, rows - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scrollRow + row) * COLS + col;
                if (idx >= pending.size()) continue;
                PendingRewardData pr = pending.get(idx);
                int cx = px + PAD + col * (cardW + GAP);
                int cy = startY + PAD + row * (CARD_H + GAP);
                boolean hover = mx >= cx && mx < cx + cardW && my >= cy && my < cy + CARD_H;
                renderPendingCard(ctx, pr, cx, cy, hover, mx, my, idx);
            }
        }
        renderScrollbar(ctx, startY, rows, visibleRows(), scrollRow, maxScroll);
    }

    private void renderPendingCard(DrawContext ctx, PendingRewardData pr, int x, int y,
                                    boolean hover, int mx, int my, int idx) {
        ctx.fill(x, y, x + cardW, y + CARD_H, hover ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + CARD_H, C_GOLD);
        ctx.fill(x, y + CARD_H - 1, x + cardW, y + CARD_H, C_BORDER);

        ctx.drawText(textRenderer, "✨ Récompense", x + 6, y + 8, C_GOLD, false);
        ctx.drawText(textRenderer, pr.label(), x + 6, y + 20, C_WHITE, false);
        renderItemIcon(ctx, pr.itemId(), x + cardW / 2 - 8, y + 38);
        String itemStr = pr.qty() + "× " + fmtItem(pr.itemId());
        int iw = textRenderer.getWidth(itemStr);
        ctx.drawText(textRenderer, itemStr, x + (cardW - iw) / 2, y + 58, C_GREEN, false);

        int cancelBtnY  = y + CARD_H - BTN_H - 4;
        int collectBtnY = cancelBtnY - BTN_H - 4;

        int bw = textRenderer.getWidth("Récupérer") + 12;
        int bx = x + (cardW - bw) / 2;
        boolean bhov = mx >= bx && mx < bx + bw && my >= collectBtnY && my < collectBtnY + BTN_H;
        ctx.fill(bx, collectBtnY, bx + bw, collectBtnY + BTN_H, bhov ? C_GREEN : 0xFF1A6645);
        ctx.fill(bx, collectBtnY, bx + bw, collectBtnY + 1, C_GREEN);
        ctx.drawText(textRenderer, "Récupérer", bx + 6, collectBtnY + 5, C_WHITE, false);
        cardBounds.add(new int[]{bx, collectBtnY, bw, BTN_H, idx, QuestNetworking.ACTION_COLLECT});

        int cw2 = textRenderer.getWidth("Annuler") + 10;
        boolean chov = mx >= x + 6 && mx < x + 6 + cw2 && my >= cancelBtnY && my < cancelBtnY + BTN_H;
        ctx.fill(x + 6, cancelBtnY, x + 6 + cw2, cancelBtnY + BTN_H, chov ? C_RED : 0xFF3D0A16);
        ctx.fill(x + 6, cancelBtnY, x + 6 + cw2, cancelBtnY + 1, C_RED);
        ctx.drawText(textRenderer, "Annuler", x + 11, cancelBtnY + 5, C_WHITE, false);
        cardBounds.add(new int[]{x + 6, cancelBtnY, cw2, BTN_H, idx, QuestNetworking.ACTION_CANCEL_PENDING});
    }

    // ── Onglet Classements ────────────────────────────────────────────────────

    private void renderClassements(DrawContext ctx, int mx, int my, int startY) {
        int colW  = (pw - PAD * 2 - GAP) / 2;
        int col2X = px + PAD + colW + GAP;
        int y0    = startY + PAD;

        renderLeaderboard(ctx, lbCompleted, px + PAD, y0, colW, "⚔ Quêtes complétées", C_GOLD);
        renderLeaderboard(ctx, lbLevel,     col2X,    y0, colW, "✨ Niveau",             C_BLUE);
    }

    private void renderLeaderboard(DrawContext ctx, List<LeaderboardEntry> lb,
                                   int x, int y, int w, String title, int accentColor) {
        ctx.fill(x, y, x + w, y + 1, accentColor);
        ctx.drawText(textRenderer, title, x, y + 4, accentColor, false);
        y += 18;

        if (lb.isEmpty()) {
            ctx.drawText(textRenderer, "Aucune donnée", x + 4, y + 4, C_DIM, false);
            return;
        }
        for (int i = 0; i < lb.size(); i++) {
            LeaderboardEntry e = lb.get(i);
            boolean isTop3 = i < 3;
            int rowBg = (i % 2 == 0) ? C_SURFACE : 0;
            ctx.fill(x, y, x + w, y + 16, rowBg);

            // Médaille / rang
            String medal = switch (i) {
                case 0 -> "§6#1";
                case 1 -> "§7#2";
                case 2 -> "§c#3";
                default -> "§8#" + (i + 1);
            };
            ctx.drawText(textRenderer, medal, x + 4, y + 4, C_WHITE, false);

            // Nom (avec casse originale si possible)
            String name = e.name();
            int nameColor = isTop3 ? C_WHITE : C_MID;
            ctx.drawText(textRenderer, name, x + 24, y + 4, nameColor, false);

            // Valeur alignée à droite
            String val = String.valueOf(e.value());
            int valW = textRenderer.getWidth(val);
            ctx.drawText(textRenderer, val, x + w - valW - 4, y + 4, accentColor, false);

            y += 16;
        }
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;

        Tab[] tabs = {Tab.DISPONIBLES, Tab.EN_COURS, Tab.A_RECLAMER, Tab.CLASSEMENTS};
        int tabW2 = (pw - PAD * 2) / 4;
        int tabY  = py + TOP_H - 18;
        for (int i = 0; i < 4; i++) {
            int tabX = px + PAD + i * (tabW2 + 1);
            if (imx >= tabX && imx < tabX + tabW2 && imy >= tabY && imy < tabY + 16) {
                tab = tabs[i]; scrollRow = 0; return true;
            }
        }

        for (int[] b : cardBounds) {
            int bx = b[0], by = b[1], bw = b[2], bh = b[3], param = b[4], action = b[5];
            if (imx >= bx && imx < bx + bw && imy >= by && imy < by + bh) {
                sendAction(action, param); return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int maxScroll = Math.max(0, currentRows() - visibleRows());
        if (maxScroll > 0)
            scrollRow = Math.max(0, Math.min(scrollRow - (int) Math.signum(amount), maxScroll));
        return true;
    }

    // ── Helpers render ────────────────────────────────────────────────────────

    private int renderTagsCompact(DrawContext ctx, List<String> tags, int x, int y) {
        int tx = x;
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) { ctx.drawText(textRenderer, " · ", tx, y, C_DIM, false); tx += textRenderer.getWidth(" · "); }
            String tag = tags.get(i);
            ctx.drawText(textRenderer, tag, tx, y, tagColor(tag), false);
            tx += textRenderer.getWidth(tag);
            if (tx > x + cardW - 20) break;
        }
        return y + 10;
    }

    private int tagColor(String tag) {
        return switch (tag) {
            case "SOLO"     -> C_MID;
            case "GROUPE"   -> C_BLUE;
            case "KILL"     -> 0xFFBF4040;
            case "HARVEST"  -> 0xFF5EA85E;
            case "DELIVERY" -> 0xFF5BA8D4;
            default         -> C_DIM;
        };
    }

    private int diffColor(QuestData q) {
        for (String tag : q.tags()) {
            Integer c = switch (tag) {
                case "FACILE"     -> C_GREEN;
                case "MOYEN"      -> C_GOLD;
                case "DIFFICILE"  -> C_RED;
                case "LÉGENDAIRE" -> 0xFFB060FF;
                default           -> null;
            };
            if (c != null) return c;
        }
        return C_DIM;
    }

    private void renderReward(DrawContext ctx, QuestData q, int x, int y) {
        if ("SHARDS".equals(q.rewardType()))
            ctx.drawText(textRenderer, "§e+" + q.rewardShards() + " ◆  §7+" + q.rewardXp() + " XP", x, y, C_GOLD, false);
        else
            ctx.drawText(textRenderer, "§a+" + q.rewardItemQty() + "× " + fmtItem(q.rewardItem()) + "  §7+" + q.rewardXp() + " XP", x, y, C_GREEN, false);
    }

    private void renderItemIcon(DrawContext ctx, String itemId, int x, int y) {
        if (itemId == null || itemId.isEmpty()) return;
        try {
            Item item = Registries.ITEM.get(new Identifier(itemId));
            ctx.drawItem(new ItemStack(item), x, y);
        } catch (Exception ignored) {}
    }

    private void renderScrollbar(DrawContext ctx, int startY, int rows, int vis, int scroll, int maxScroll) {
        if (rows <= vis) return;
        int trackX = px + pw - 6;
        int trackY = startY + PAD;
        int trackH = vis * (CARD_H + GAP) - GAP;
        ctx.fill(trackX, trackY, trackX + 4, trackY + trackH, C_BORDER);
        float ratio  = (float) vis / rows;
        int   thumbH = Math.max(16, (int)(trackH * ratio));
        int   thumbY = maxScroll > 0 ? trackY + (int)((trackH - thumbH) * ((float) scroll / maxScroll)) : trackY;
        ctx.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, C_GOLD);
    }

    private void drawCentered(DrawContext ctx, String text, int y) {
        int tw = textRenderer.getWidth(text);
        ctx.drawText(textRenderer, text, px + (pw - tw) / 2, y, C_DIM, false);
    }

    // ── Helpers data ──────────────────────────────────────────────────────────

    private List<QuestData> getAvailableFiltered() {
        Set<Integer> acceptedIds = new HashSet<>();
        for (ActiveQuestData aq : active) acceptedIds.add(aq.questId());
        long now = System.currentTimeMillis();
        return available.stream()
            .filter(q -> !acceptedIds.contains(q.id()) && (q.expiresAt() <= 0 || q.expiresAt() > now))
            .toList();
    }

    private int visibleRows() {
        int contentH = ph - TOP_H - 1 - PAD * 2;
        return Math.max(1, contentH / (CARD_H + GAP));
    }

    private int currentRows() {
        if (tab == Tab.CLASSEMENTS) return 0;
        int count = switch (tab) {
            case DISPONIBLES -> getAvailableFiltered().size();
            case EN_COURS    -> active.size();
            case A_RECLAMER  -> pending.size();
            default          -> 0;
        };
        return (count + COLS - 1) / COLS;
    }

    private String fmtItem(String id) {
        if (id == null || id.isEmpty()) return "?";
        String raw = id.contains(":") ? id.split(":")[1] : id;
        return raw.replace("_", " ");
    }

    private boolean hasItemsInInventory(String itemId, int qty) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return false;
        int count = 0;
        for (var stack : mc.player.getInventory().main) {
            if (stack.isEmpty()) continue;
            if (net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString().equals(itemId))
                count += stack.getCount();
        }
        return count >= qty;
    }

    private void sendAction(int action, int param) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(action);
        buf.writeInt(param);
        ClientPlayNetworking.send(QuestNetworking.QUEST_ACTION, buf);
    }

    public void update(int level, int xp, int xpNext,
                       List<QuestData> available, List<ActiveQuestData> active,
                       List<PendingRewardData> pending, Map<Integer, Integer> groupPending,
                       List<LeaderboardEntry> lbCompleted, List<LeaderboardEntry> lbLevel) {
        this.playerLevel  = level;
        this.playerXp     = xp;
        this.xpToNext     = xpNext;
        this.available    = available;
        this.active       = active;
        this.pending      = pending;
        this.groupPending = groupPending;
        this.lbCompleted  = lbCompleted;
        this.lbLevel      = lbLevel;
    }
}
