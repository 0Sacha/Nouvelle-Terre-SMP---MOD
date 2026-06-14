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

    private static final int PW       = 460;
    private static final int PH       = 360;
    private static final int TOP_H    = 44;
    private static final int PAD      = 12;
    private static final int COLS     = 2;
    private static final int GAP      = 8;
    private static final int CARD_W   = (PW - PAD * 3) / COLS;   // ~218
    private static final int CARD_H   = CARD_W;                   // carré
    private static final int SCROLL_W = 4;
    private static final int BTN_H    = 18;

    private int px, py;

    // ── State ─────────────────────────────────────────────────────────────────

    private int playerLevel = 0;
    private int playerXp    = 0;
    private int xpToNext    = 100;

    private List<QuestData>       available     = new ArrayList<>();
    private List<ActiveQuestData> active        = new ArrayList<>();
    private List<PendingRewardData> pending     = new ArrayList<>();
    private Map<Integer, Integer> groupPending  = new HashMap<>();

    private enum Tab { DISPONIBLES, EN_COURS, A_RECLAMER }
    private Tab tab         = Tab.DISPONIBLES;
    private int scrollRow   = 0;

    // ── Positions calculées dans render (relues dans mouseClicked) ────────────
    private final List<int[]> cardBounds = new ArrayList<>(); // [x, y, w, h, id_or_idx]

    // ── Constructeur ──────────────────────────────────────────────────────────

    public QuetesScreen() { super(Text.literal("Quêtes")); }

    public QuetesScreen(int level, int xp, int xpNext,
                        List<QuestData> available, List<ActiveQuestData> active,
                        List<PendingRewardData> pending, Map<Integer, Integer> groupPending) {
        super(Text.literal("Quêtes"));
        update(level, xp, xpNext, available, active, pending, groupPending);
    }

    @Override protected void init() { px = (width - PW) / 2; py = (height - PH) / 2; }
    @Override public boolean shouldPause() { return false; }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Fenêtre
        ctx.fill(px, py, px + PW, py + PH, C_BG);
        ctx.fill(px, py, px + PW, py + 1, C_BORDER);
        ctx.fill(px, py + PH - 1, px + PW, py + PH, C_BORDER);
        ctx.fill(px, py, px + 1, py + PH, C_BORDER);
        ctx.fill(px + PW - 1, py, px + PW, py + PH, C_BORDER);

        // Header
        ctx.fill(px, py, px + PW, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H, px + PW, py + TOP_H + 1, C_BORDER);
        ctx.drawText(textRenderer, "⚔  Quêtes", px + PAD, py + 8, C_GOLD, false);
        // Niveau joueur
        String lvlStr = "Niv. " + playerLevel + "  §8" + playerXp + "/" + xpToNext + " XP";
        ctx.drawText(textRenderer, lvlStr, px + PAD, py + 22, C_MID, false);

        // Barre XP
        int barX = px + PAD, barY = py + 36, barW = PW / 3;
        ctx.fill(barX, barY, barX + barW, barY + 3, C_BORDER);
        int filled = xpToNext > 0 ? (int)((float) playerXp / xpToNext * barW) : 0;
        ctx.fill(barX, barY, barX + filled, barY + 3, C_GOLD);

        // Tabs
        renderTabs(ctx, mx, my);

        // Contenu
        cardBounds.clear();
        int contentY = py + TOP_H + 1;
        switch (tab) {
            case DISPONIBLES -> renderAvailable(ctx, mx, my, contentY);
            case EN_COURS    -> renderActive(ctx, mx, my, contentY);
            case A_RECLAMER  -> renderPending(ctx, mx, my, contentY);
        }

        super.render(ctx, mx, my, delta);
    }

    private void renderTabs(DrawContext ctx, int mx, int my) {
        String[] labels = {"Disponibles", "En cours (" + active.size() + ")", "À Réclamer (" + pending.size() + ")"};
        Tab[]    tabs   = {Tab.DISPONIBLES, Tab.EN_COURS, Tab.A_RECLAMER};
        int      tabW   = (PW - PAD * 2) / 3;
        int      tabY   = py + TOP_H - 18;
        for (int i = 0; i < 3; i++) {
            int  tabX  = px + PAD + i * (tabW + 1);
            boolean act  = tab == tabs[i];
            boolean hov  = !act && mx >= tabX && mx < tabX + tabW && my >= tabY && my < tabY + 16;
            ctx.fill(tabX, tabY, tabX + tabW, tabY + 16, act ? C_SURFACE : (hov ? C_HOVER : C_PANEL));
            if (act) ctx.fill(tabX, tabY + 14, tabX + tabW, tabY + 16, C_GOLD);
            int tw = textRenderer.getWidth(labels[i]);
            ctx.drawText(textRenderer, labels[i], tabX + (tabW - tw) / 2, tabY + 4,
                    act ? C_GOLD : C_MID, false);
        }
    }

    // ── Onglet Disponibles ────────────────────────────────────────────────────

    private void renderAvailable(DrawContext ctx, int mx, int my, int startY) {
        List<QuestData> list = getAvailableFiltered();
        int rows = (list.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, rows - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scrollRow + row) * COLS + col;
                if (idx >= list.size()) continue;
                QuestData q = list.get(idx);
                int cx = px + PAD + col * (CARD_W + GAP);
                int cy = startY + PAD + row * (CARD_H + GAP);
                boolean hover = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;
                renderAvailableCard(ctx, q, cx, cy, hover, mx, my);
                cardBounds.add(new int[]{cx, cy, CARD_W, CARD_H, q.id(), 0}); // action 0 = accept
            }
        }
        renderScrollbar(ctx, startY, rows, visibleRows(), scrollRow, maxScroll);
    }

    private void renderAvailableCard(DrawContext ctx, QuestData q, int x, int y, boolean hover, int mx, int my) {
        ctx.fill(x, y, x + CARD_W, y + CARD_H, hover ? C_HOVER : C_SURFACE);
        // Bordure colorée gauche selon difficulté
        int accent = diffColor(q);
        ctx.fill(x, y, x + 3, y + CARD_H, accent);
        ctx.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, C_BORDER);

        // Tags
        int tagY = y + 6;
        tagY = renderTags(ctx, q.tags(), x + 6, tagY);

        // Icône item (target)
        renderItemIcon(ctx, q.target(), x + CARD_W - 26, y + 6);

        // Label
        ctx.drawText(textRenderer, q.label(), x + 6, tagY + 2, C_WHITE, false);

        // Objectif
        String obj = fmtTarget(q);
        ctx.drawText(textRenderer, obj, x + 6, tagY + 14, C_MID, false);

        // Récompense
        renderReward(ctx, q, x + 6, tagY + 26);

        // Coût
        if (q.costShards() > 0) {
            ctx.drawText(textRenderer, "§c⬡ " + q.costShards() + " ◆", x + 6, tagY + 38, C_RED, false);
        }

        // Groupe en attente
        if (q.maxPlayers() > 1) {
            int accepted = groupPending.getOrDefault(q.id(), 0);
            String gStr = "§b👥 " + accepted + "/" + q.maxPlayers() + " joueurs";
            ctx.drawText(textRenderer, gStr, x + 6, y + CARD_H - 32, C_BLUE, false);
        }

        // Bouton Accepter/Rejoindre
        String btnLabel = q.maxPlayers() > 1 ? "Rejoindre" : "Accepter";
        int bw = textRenderer.getWidth(btnLabel) + 12;
        int bx = x + CARD_W - bw - 6;
        int by = y + CARD_H - BTN_H - 6;
        boolean bhov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H;
        ctx.fill(bx, by, bx + bw, by + BTN_H, bhov ? C_GOLD : 0xFF5A3F10);
        ctx.fill(bx, by, bx + bw, by + 1, C_GOLD);
        ctx.drawText(textRenderer, btnLabel, bx + (bw - textRenderer.getWidth(btnLabel)) / 2, by + 5, C_WHITE, false);
    }

    // ── Onglet En cours ───────────────────────────────────────────────────────

    private void renderActive(DrawContext ctx, int mx, int my, int startY) {
        if (active.isEmpty()) {
            drawCentered(ctx, "Aucune quête en cours.", startY + 60);
            return;
        }
        int rows = (active.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, rows - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scrollRow + row) * COLS + col;
                if (idx >= active.size()) continue;
                ActiveQuestData aq = active.get(idx);
                int cx = px + PAD + col * (CARD_W + GAP);
                int cy = startY + PAD + row * (CARD_H + GAP);
                boolean hover = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;
                renderActiveCard(ctx, aq, cx, cy, hover, mx, my, idx);
            }
        }
        renderScrollbar(ctx, startY, rows, visibleRows(), scrollRow, maxScroll);
    }

    private void renderActiveCard(DrawContext ctx, ActiveQuestData aq, int x, int y,
                                   boolean hover, int mx, int my, int idx) {
        QuestData q = aq.snapshot();
        if (q == null) return;

        ctx.fill(x, y, x + CARD_W, y + CARD_H, hover ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + CARD_H, diffColor(q));
        ctx.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, C_BORDER);

        int tagY = y + 6;
        tagY = renderTags(ctx, q.tags(), x + 6, tagY);

        // Icône
        renderItemIcon(ctx, q.target(), x + CARD_W - 26, y + 6);

        ctx.drawText(textRenderer, q.label(), x + 6, tagY + 2, C_WHITE, false);
        ctx.drawText(textRenderer, fmtTarget(q), x + 6, tagY + 14, C_MID, false);

        int midY = y + CARD_H / 2 - 4;

        if ("DELIVERY".equals(q.type())) {
            // Slot visuel pour les objets à remettre
            boolean hasItems = hasItemsInInventory(q.target(), q.quantity());
            String slotLabel = (hasItems ? "§a✅" : "§c✗") + " " + q.quantity() + "× " + fmtItem(q.target());
            ctx.drawText(textRenderer, slotLabel, x + 6, midY, hasItems ? C_GREEN : C_RED, false);
            renderItemIcon(ctx, q.target(), x + 6, midY + 12);

            // Bouton Remettre
            if (hasItems && !aq.turnedIn()) {
                int bw = textRenderer.getWidth("Remettre") + 12;
                int bx = x + CARD_W - bw - 6;
                int by = y + CARD_H - BTN_H * 2 - 10;
                boolean bhov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H;
                ctx.fill(bx, by, bx + bw, by + BTN_H, bhov ? C_GREEN : 0xFF1A6645);
                ctx.fill(bx, by, bx + bw, by + 1, C_GREEN);
                ctx.drawText(textRenderer, "Remettre", bx + 6, by + 5, C_WHITE, false);
                cardBounds.add(new int[]{bx, by, bw, BTN_H, aq.questId(), QuestNetworking.ACTION_CLAIM});
            } else if (aq.turnedIn()) {
                ctx.drawText(textRenderer, "§eObjets remis — allez dans À Réclamer", x + 6, midY + 30, C_MID, false);
            }
        } else {
            // Barre de progression KILL/HARVEST
            int prog = aq.progress();
            int total = q.quantity();
            float pct = total > 0 ? Math.min(1f, (float) prog / total) : 0f;
            int barW = CARD_W - 12;
            ctx.fill(x + 6, midY, x + 6 + barW, midY + 5, C_BORDER);
            ctx.fill(x + 6, midY, x + 6 + (int)(barW * pct), midY + 5, pct >= 1f ? C_GREEN : C_GOLD);
            String progStr = prog + "/" + total;
            ctx.drawText(textRenderer, progStr, x + 6, midY + 8, C_MID, false);

            if (pct >= 1f) {
                int bw = textRenderer.getWidth("Réclamer") + 12;
                int bx = x + CARD_W - bw - 6;
                int by = y + CARD_H - BTN_H * 2 - 10;
                boolean bhov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H;
                ctx.fill(bx, by, bx + bw, by + BTN_H, bhov ? C_GREEN : 0xFF1A6645);
                ctx.fill(bx, by, bx + bw, by + 1, C_GREEN);
                ctx.drawText(textRenderer, "Réclamer", bx + 6, by + 5, C_WHITE, false);
                cardBounds.add(new int[]{bx, by, bw, BTN_H, aq.questId(), QuestNetworking.ACTION_CLAIM});
            }
        }

        // Récompense (mini)
        renderReward(ctx, q, x + 6, y + CARD_H - 36);

        // Bouton Annuler
        int cw = textRenderer.getWidth("Annuler") + 10;
        int cx2 = x + 6;
        int cy2 = y + CARD_H - BTN_H - 6;
        boolean chov = mx >= cx2 && mx < cx2 + cw && my >= cy2 && my < cy2 + BTN_H;
        ctx.fill(cx2, cy2, cx2 + cw, cy2 + BTN_H, chov ? C_RED : 0xFF3D0A16);
        ctx.fill(cx2, cy2, cx2 + cw, cy2 + 1, C_RED);
        ctx.drawText(textRenderer, "Annuler", cx2 + 5, cy2 + 5, C_WHITE, false);
        cardBounds.add(new int[]{cx2, cy2, cw, BTN_H, aq.questId(), QuestNetworking.ACTION_CANCEL});
    }

    // ── Onglet À Réclamer ─────────────────────────────────────────────────────

    private void renderPending(DrawContext ctx, int mx, int my, int startY) {
        if (pending.isEmpty()) {
            drawCentered(ctx, "Aucune récompense en attente.", startY + 60);
            return;
        }
        int rows = (pending.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, rows - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scrollRow + row) * COLS + col;
                if (idx >= pending.size()) continue;
                PendingRewardData pr = pending.get(idx);
                int cx = px + PAD + col * (CARD_W + GAP);
                int cy = startY + PAD + row * (CARD_H + GAP);
                boolean hover = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;
                renderPendingCard(ctx, pr, cx, cy, hover, mx, my, idx);
            }
        }
        renderScrollbar(ctx, startY, rows, visibleRows(), scrollRow, maxScroll);
    }

    private void renderPendingCard(DrawContext ctx, PendingRewardData pr, int x, int y,
                                    boolean hover, int mx, int my, int idx) {
        ctx.fill(x, y, x + CARD_W, y + CARD_H, hover ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + CARD_H, C_GOLD);
        ctx.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, C_BORDER);

        ctx.drawText(textRenderer, "§e✨ Récompense", x + 6, y + 8, C_GOLD, false);
        ctx.drawText(textRenderer, pr.label(), x + 6, y + 22, C_WHITE, false);

        // Icône de l'item récompense
        renderItemIcon(ctx, pr.itemId(), x + CARD_W / 2 - 8, y + CARD_H / 2 - 8);
        String itemStr = pr.qty() + "× " + fmtItem(pr.itemId());
        int iw = textRenderer.getWidth(itemStr);
        ctx.drawText(textRenderer, "§a" + itemStr, x + (CARD_W - iw) / 2, y + CARD_H / 2 + 10, C_GREEN, false);

        // Bouton Récupérer
        int bw = textRenderer.getWidth("Récupérer") + 12;
        int bx = x + (CARD_W - bw) / 2;
        int by = y + CARD_H - BTN_H * 2 - 10;
        boolean bhov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H;
        ctx.fill(bx, by, bx + bw, by + BTN_H, bhov ? C_GREEN : 0xFF1A6645);
        ctx.fill(bx, by, bx + bw, by + 1, C_GREEN);
        ctx.drawText(textRenderer, "Récupérer", bx + 6, by + 5, C_WHITE, false);
        cardBounds.add(new int[]{bx, by, bw, BTN_H, idx, QuestNetworking.ACTION_COLLECT});

        // Bouton Annuler
        int cw = textRenderer.getWidth("Annuler") + 10;
        int cx2 = x + 6;
        int cy2 = y + CARD_H - BTN_H - 6;
        boolean chov = mx >= cx2 && mx < cx2 + cw && my >= cy2 && my < cy2 + BTN_H;
        ctx.fill(cx2, cy2, cx2 + cw, cy2 + BTN_H, chov ? C_RED : 0xFF3D0A16);
        ctx.fill(cx2, cy2, cx2 + cw, cy2 + 1, C_RED);
        ctx.drawText(textRenderer, "Annuler", cx2 + 5, cy2 + 5, C_WHITE, false);
        cardBounds.add(new int[]{cx2, cy2, cw, BTN_H, idx, QuestNetworking.ACTION_CANCEL_PENDING});
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;

        // Tabs
        String[] labels = {"Disponibles", "En cours (" + active.size() + ")", "À Réclamer (" + pending.size() + ")"};
        Tab[]    tabs   = {Tab.DISPONIBLES, Tab.EN_COURS, Tab.A_RECLAMER};
        int tabW = (PW - PAD * 2) / 3;
        int tabY = py + TOP_H - 18;
        for (int i = 0; i < 3; i++) {
            int tabX = px + PAD + i * (tabW + 1);
            if (imx >= tabX && imx < tabX + tabW && imy >= tabY && imy < tabY + 16) {
                tab = tabs[i];
                scrollRow = 0;
                return true;
            }
        }

        // Boutons des cards (cardBounds rempli au render)
        for (int[] b : cardBounds) {
            int bx = b[0], by = b[1], bw = b[2], bh = b[3], param = b[4], action = b[5];
            if (imx >= bx && imx < bx + bw && imy >= by && imy < by + bh) {
                sendAction(action, param);
                return true;
            }
        }

        // Clic sur card en onglet Disponibles (zone entière = accepter)
        if (tab == Tab.DISPONIBLES) {
            List<QuestData> list = getAvailableFiltered();
            for (int row = 0; row < visibleRows(); row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = (scrollRow + row) * COLS + col;
                    if (idx >= list.size()) continue;
                    int cx = px + PAD + col * (CARD_W + GAP);
                    int cy = py + TOP_H + 1 + PAD + row * (CARD_H + GAP);
                    if (imx >= cx && imx < cx + CARD_W && imy >= cy && imy < cy + CARD_H) {
                        sendAction(QuestNetworking.ACTION_ACCEPT, list.get(idx).id());
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int rows = currentRows();
        int maxScroll = Math.max(0, rows - visibleRows());
        if (maxScroll > 0)
            scrollRow = Math.max(0, Math.min(scrollRow - (int) Math.signum(amount), maxScroll));
        return true;
    }

    // ── Helpers render ────────────────────────────────────────────────────────

    private int renderTags(DrawContext ctx, List<String> tags, int x, int y) {
        int tx = x;
        for (String tag : tags) {
            int col = tagColor(tag);
            String lbl = tag;
            int tw = textRenderer.getWidth(lbl) + 6;
            ctx.fill(tx, y, tx + tw, y + 10, col & 0x44FFFFFF);
            ctx.fill(tx, y, tx + tw, y + 1, col);
            ctx.fill(tx, y + 9, tx + tw, y + 10, col);
            ctx.drawText(textRenderer, lbl, tx + 3, y + 1, col, false);
            tx += tw + 3;
            if (tx > x + CARD_W - 12) { tx = x; y += 12; }
        }
        return y + 12;
    }

    private int tagColor(String tag) {
        return switch (tag) {
            case "SOLO"       -> C_MID;
            case "GROUPE"     -> C_BLUE;
            case "FACILE"     -> C_GREEN;
            case "MOYEN"      -> C_GOLD;
            case "DIFFICILE"  -> C_RED;
            case "LÉGENDAIRE" -> 0xFFB060FF;
            case "KILL"       -> 0xFFBF4040;
            case "HARVEST"    -> 0xFF5EA85E;
            case "DELIVERY"   -> 0xFF5BA8D4;
            case "SHARDS"     -> C_GOLD;
            case "ITEM"       -> 0xFFD4A85B;
            default           -> C_DIM;
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
        if ("SHARDS".equals(q.rewardType())) {
            ctx.drawText(textRenderer, "§e+" + q.rewardShards() + " ◆  §7+" + q.rewardXp() + " XP", x, y, C_GOLD, false);
        } else {
            ctx.drawText(textRenderer, "§a+" + q.rewardItemQty() + "× " + fmtItem(q.rewardItem()) + "  §7+" + q.rewardXp() + " XP", x, y, C_GREEN, false);
        }
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
        int trackX = px + PW - 6;
        int trackY = startY + PAD;
        int trackH = vis * (CARD_H + GAP) - GAP;
        ctx.fill(trackX, trackY, trackX + SCROLL_W, trackY + trackH, C_BORDER);
        float ratio  = (float) vis / rows;
        int   thumbH = Math.max(16, (int)(trackH * ratio));
        int   thumbY = maxScroll > 0 ? trackY + (int)((trackH - thumbH) * ((float) scroll / maxScroll)) : trackY;
        ctx.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, C_GOLD);
    }

    private void drawCentered(DrawContext ctx, String text, int y) {
        int tw = textRenderer.getWidth(text);
        ctx.drawText(textRenderer, text, px + (PW - tw) / 2, y, C_DIM, false);
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
        int contentH = PH - TOP_H - 1 - PAD * 2;
        return Math.max(1, contentH / (CARD_H + GAP));
    }

    private int currentRows() {
        int count = switch (tab) {
            case DISPONIBLES -> getAvailableFiltered().size();
            case EN_COURS    -> active.size();
            case A_RECLAMER  -> pending.size();
        };
        return (count + COLS - 1) / COLS;
    }

    private String fmtTarget(QuestData q) {
        String item = fmtItem(q.target());
        return q.quantity() + "× " + item;
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

    // ── Update depuis réseau ──────────────────────────────────────────────────

    public void update(int level, int xp, int xpNext,
                       List<QuestData> available, List<ActiveQuestData> active,
                       List<PendingRewardData> pending, Map<Integer, Integer> groupPending) {
        this.playerLevel  = level;
        this.playerXp     = xp;
        this.xpToNext     = xpNext;
        this.available    = available;
        this.active       = active;
        this.pending      = pending;
        this.groupPending = groupPending;
    }
}
