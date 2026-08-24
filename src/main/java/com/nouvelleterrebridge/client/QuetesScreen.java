package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.QuestNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

    public record CommunityData(String label, String type, String target, int quantity,
                                int progress, int reward, boolean completed, int myContribution) {}

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

    private static final int MAX_PW = 620;
    private static final int MAX_PH = 440;
    private static final int TOP_H  = 64;
    private static final int PAD    = 12;
    private static final int GAP    = 8;
    /** Une quête par ligne : le scroll se compte en quêtes, plus en rangées. */
    private static final int ROW_H   = 46;
    private static final int ROW_GAP = 4;
    private static final int BTN_H  = 18;

    private int pw, ph, px, py, rowW;

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
    private CommunityData           community    = null;

    private static final int BANNER_H = 44;

    private enum Tab { DISPONIBLES, EN_COURS, A_RECLAMER, CLASSEMENTS }
    private Tab tab       = Tab.DISPONIBLES;
    private int scrollRow = 0;

    private final List<int[]> cardBounds = new ArrayList<>();

    // ── Constructeur ──────────────────────────────────────────────────────────

    public QuetesScreen() { super(Text.literal("Quêtes")); }

    public QuetesScreen(int level, int xp, int xpNext,
                        List<QuestData> available, List<ActiveQuestData> active,
                        List<PendingRewardData> pending, Map<Integer, Integer> groupPending,
                        List<LeaderboardEntry> lbCompleted, List<LeaderboardEntry> lbLevel,
                        CommunityData community) {
        super(Text.literal("Quêtes"));
        update(level, xp, xpNext, available, active, pending, groupPending, lbCompleted, lbLevel, community);
    }

    @Override
    protected void init() {
        pw    = Math.min(MAX_PW, width  - 20);
        ph    = Math.min(MAX_PH, height - 20);
        px    = (width  - pw) / 2;
        py    = (height - ph) / 2;
        rowW  = pw - PAD * 2 - 8;   // 8 px réservés à la scrollbar
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

        // Header — TOP_H=64 : titre+niveau+XP above tabY=py+46
        ctx.fill(px, py, px + pw, py + TOP_H, C_PANEL);
        ctx.fill(px, py + TOP_H, px + pw, py + TOP_H + 1, C_BORDER);
        HubBackButton.render(ctx, textRenderer, px + PAD, py + 8, mx, my);
        int titleX = px + PAD + HubBackButton.W + 8;
        ctx.drawText(textRenderer, "⚔  Quêtes", titleX, py + 10, C_GOLD, false);

        String lvlText = "Niv. " + playerLevel;
        String xpText  = " · §8" + playerXp + "/" + xpToNext + " XP";
        ctx.drawText(textRenderer, lvlText, titleX, py + 24, C_MID, false);
        ctx.drawText(textRenderer, xpText,  titleX + textRenderer.getWidth(lvlText), py + 24, C_DIM, false);

        int barX = px + PAD, barY = py + 36, barW2 = pw * 3 / 8;
        ctx.fill(barX, barY, barX + barW2, barY + 4, C_BORDER);
        int filled = xpToNext > 0 ? (int)((float) playerXp / xpToNext * barW2) : 0;
        if (filled > 0) ctx.fill(barX, barY, barX + filled, barY + 4, C_GOLD);

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
            "En cours (" + getInProgress().size() + ")",
            "À Réclamer (" + (pending.size() + getCompleted().size()) + ")",
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
        int cardsY = startY;
        if (community != null) {
            renderCommunityBanner(ctx, px + PAD, startY + PAD, pw - PAD * 2);
            cardsY += BANNER_H + GAP;
        }

        List<QuestData> list = getAvailableFiltered();
        if (list.isEmpty()) { drawCentered(ctx, "Aucune quête disponible.", cardsY + 60); return; }
        int maxScroll = Math.max(0, list.size() - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            int idx = scrollRow + row;
            if (idx >= list.size()) break;
            int rx = px + PAD;
            int ry = cardsY + PAD + row * (ROW_H + ROW_GAP);
            boolean hover = mx >= rx && mx < rx + rowW && my >= ry && my < ry + ROW_H;
            renderAvailableRow(ctx, list.get(idx), rx, ry, hover, mx, my);
        }
        renderScrollbar(ctx, cardsY, list.size(), visibleRows(), scrollRow, maxScroll);
    }

    /** Bannière de la quête communautaire du serveur (progression globale). */
    private void renderCommunityBanner(DrawContext ctx, int x, int y, int w) {
        int violet = 0xFFB060FF;
        ctx.fill(x, y, x + w, y + BANNER_H, C_SURFACE);
        ctx.fill(x, y, x + 3, y + BANNER_H, violet);
        ctx.fill(x, y + BANNER_H - 1, x + w, y + BANNER_H, C_BORDER);

        String verbe = switch (community.type()) {
            case "KILL"     -> "Tuer";
            case "DELIVERY" -> "Livrer";
            default         -> "Récolter";
        };
        String objectif = verbe + " " + community.quantity() + " × "
            + targetName(community.type(), community.target());
        ctx.drawText(textRenderer, "QUÊTE DU SERVEUR — " + objectif, x + 8, y + 6, violet, false);
        String rw = "+" + community.reward() + " ◆ par participant";
        ctx.drawText(textRenderer, rw, x + w - textRenderer.getWidth(rw) - 8, y + 6, C_GOLD, false);

        float pct = community.quantity() > 0
            ? Math.min(1f, (float) community.progress() / community.quantity()) : 0f;
        int barW = w - 16;
        ctx.fill(x + 8, y + 18, x + 8 + barW, y + 23, C_BORDER);
        if (pct > 0) ctx.fill(x + 8, y + 18, x + 8 + (int)(barW * pct),
            y + 23, community.completed() ? C_GREEN : C_GOLD);

        String prog = community.completed()
            ? "Accomplie ! Récompense distribuée à tous les participants."
            : community.progress() + " / " + community.quantity()
              + (community.myContribution() > 0
                 ? "  ·  ma contribution : " + community.myContribution() : "  ·  tout le serveur y contribue");
        ctx.drawText(textRenderer, prog, x + 8, y + 28,
            community.completed() ? C_GREEN : C_MID, false);
    }

    /** Cadre commun a toutes les lignes : fond, accent de gauche, separation. */
    private void rowFrame(DrawContext ctx, int x, int y, boolean hover, int accent) {
        ctx.fill(x, y, x + rowW, y + ROW_H, hover ? C_HOVER : C_SURFACE);
        ctx.fill(x, y, x + 3, y + ROW_H, accent);
        ctx.fill(x, y + ROW_H - 1, x + rowW, y + ROW_H, C_BORDER);
    }

    /** Bouton aligne a droite d'une ligne ; renvoie son bord gauche. */
    private int rowButton(DrawContext ctx, String label, int right, int y, int mx, int my,
                          int couleur, int fondInactif, int id, int action) {
        int bw = textRenderer.getWidth(label) + 14;
        int bx = right - bw;
        int by = y + (ROW_H - BTN_H) / 2;
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H;
        ctx.fill(bx, by, bx + bw, by + BTN_H, hov ? couleur : fondInactif);
        ctx.fill(bx, by, bx + bw, by + 1, couleur);
        ctx.drawText(textRenderer, label, bx + 7, by + 5, C_WHITE, false);
        cardBounds.add(new int[]{bx, by, bw, BTN_H, id, action});
        return bx;
    }

    private void renderAvailableRow(DrawContext ctx, QuestData q, int x, int y, boolean hover, int mx, int my) {
        rowFrame(ctx, x, y, hover, diffColor(q));
        renderIcon(ctx, q.type(), q.target(), x + 12, y + (ROW_H - 16) / 2);

        int tx = x + 36;
        String objectif = targetName(q.type(), q.target()) + " \u00d7" + q.quantity();
        ctx.drawText(textRenderer, truncate(objectif, rowW - 220), tx, y + 8, C_WHITE, false);

        // Ligne secondaire : portee, recompense, cout, places de groupe
        StringBuilder sub = new StringBuilder();
        for (String t : q.tags())
            if ("SOLO".equals(t) || "GROUPE".equals(t) || "JOURNALI\u00c8RE".equals(t))
                sub.append(t.charAt(0)).append(t.substring(1).toLowerCase()).append(" \u00b7 ");
        sub.append(q.rewardShards() > 0 ? "+" + q.rewardShards() + " \u25c6" : "recompense objet");
        if (q.rewardXp() > 0)   sub.append("  +").append(q.rewardXp()).append(" XP");
        if (q.costShards() > 0) sub.append("  \u00a7c\u2212").append(q.costShards()).append(" \u25c6");
        if (q.maxPlayers() > 1) sub.append("  \u00a7b\ud83d\udc65 ")
            .append(groupPending.getOrDefault(q.id(), 0)).append("/").append(q.maxPlayers());
        ctx.drawText(textRenderer, truncate(sub.toString(), rowW - 220), tx, y + 24, C_MID, false);

        String btn = q.maxPlayers() > 1 ? "Rejoindre" : "Accepter";
        rowButton(ctx, btn, x + rowW - 8, y, mx, my, C_GOLD, 0xFF5A3F10,
                  q.id(), QuestNetworking.ACTION_ACCEPT);
    }

    // ── Onglet En cours ───────────────────────────────────────────────────────

    private void renderActive(DrawContext ctx, int mx, int my, int startY) {
        List<ActiveQuestData> inProgress = getInProgress();
        if (inProgress.isEmpty()) { drawCentered(ctx, "Aucune quête en cours.", startY + 60); return; }
        int maxScroll = Math.max(0, inProgress.size() - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            int idx = scrollRow + row;
            if (idx >= inProgress.size()) break;
            int rx = px + PAD;
            int ry = startY + PAD + row * (ROW_H + ROW_GAP);
            boolean hover = mx >= rx && mx < rx + rowW && my >= ry && my < ry + ROW_H;
            renderActiveRow(ctx, inProgress.get(idx), rx, ry, hover, mx, my);
        }
        renderScrollbar(ctx, startY, inProgress.size(), visibleRows(), scrollRow, maxScroll);
    }

    private void renderActiveRow(DrawContext ctx, ActiveQuestData aq, int x, int y, boolean hover, int mx, int my) {
        QuestData q = aq.snapshot();
        if (q == null) return;
        rowFrame(ctx, x, y, hover, diffColor(q));
        renderIcon(ctx, q.type(), q.target(), x + 12, y + (ROW_H - 16) / 2);

        int tx = x + 36;
        String objectif = targetName(q.type(), q.target()) + " \u00d7" + q.quantity();
        ctx.drawText(textRenderer, truncate(objectif, rowW - 230), tx, y + 7, C_WHITE, false);

        int droite = x + rowW - 8;
        droite = rowButton(ctx, "Annuler", droite, y, mx, my, C_RED, 0xFF3D0A16,
                           aq.questId(), QuestNetworking.ACTION_CANCEL) - 6;

        if ("DELIVERY".equals(q.type())) {
            boolean hasItems = hasItemsInInventory(q.target(), q.quantity());
            if (aq.turnedIn()) {
                ctx.drawText(textRenderer, "\u2192 \u00c0 R\u00e9clamer", tx, y + 24, C_GOLD, false);
            } else {
                ctx.drawText(textRenderer, (hasItems ? "\u00a7a\u2713 " : "\u00a7c\u2717 ") + fmtItem(q.target())
                    + " en inventaire", tx, y + 24, hasItems ? C_GREEN : C_RED, false);
                if (hasItems) rowButton(ctx, "Remettre", droite, y, mx, my, C_GREEN, 0xFF1A6645,
                                        aq.questId(), QuestNetworking.ACTION_CLAIM);
            }
        } else {
            // Barre de progression : occupe la largeur restante avant les boutons
            int prog = aq.progress(), total = q.quantity();
            float pct = total > 0 ? Math.min(1f, (float) prog / total) : 0f;
            String txt = prog + " / " + total;
            int txtW = textRenderer.getWidth(txt);
            int barW2 = Math.max(40, droite - tx - txtW - 12);
            ctx.fill(tx, y + 26, tx + barW2, y + 31, C_BORDER);
            if (pct > 0) ctx.fill(tx, y + 26, tx + (int) (barW2 * pct), y + 31,
                pct >= 1f ? C_GREEN : C_GOLD);
            ctx.drawText(textRenderer, txt, tx + barW2 + 8, y + 24, C_MID, false);
        }
    }

    // ── Onglet À Réclamer ─────────────────────────────────────────────────────

    private void renderPending(DrawContext ctx, int mx, int my, int startY) {
        List<ActiveQuestData> completed = getCompleted();
        int total = completed.size() + pending.size();
        if (total == 0) { drawCentered(ctx, "Aucune récompense en attente.", startY + 60); return; }
        int maxScroll = Math.max(0, total - visibleRows());
        scrollRow = Math.min(scrollRow, maxScroll);

        for (int row = 0; row < visibleRows(); row++) {
            int idx = scrollRow + row;
            if (idx >= total) break;
            int rx = px + PAD;
            int ry = startY + PAD + row * (ROW_H + ROW_GAP);
            boolean hover = mx >= rx && mx < rx + rowW && my >= ry && my < ry + ROW_H;
            if (idx < completed.size())
                renderClaimableRow(ctx, completed.get(idx), rx, ry, hover, mx, my);
            else
                renderPendingRow(ctx, pending.get(idx - completed.size()), rx, ry, hover, mx, my,
                                 idx - completed.size());
        }
        renderScrollbar(ctx, startY, total, visibleRows(), scrollRow, maxScroll);
    }

    private void renderClaimableRow(DrawContext ctx, ActiveQuestData aq, int x, int y, boolean hover, int mx, int my) {
        QuestData q = aq.snapshot();
        if (q == null) return;
        rowFrame(ctx, x, y, hover, C_GREEN);
        renderIcon(ctx, q.type(), q.target(), x + 12, y + (ROW_H - 16) / 2);

        int tx = x + 36;
        ctx.drawText(textRenderer, truncate("\u2705 " + targetName(q.type(), q.target())
            + " \u00d7" + q.quantity(), rowW - 230), tx, y + 8, C_WHITE, false);
        String rec = q.rewardShards() > 0 ? "+" + q.rewardShards() + " \u25c6" : "recompense objet";
        if (q.rewardXp() > 0) rec += "  +" + q.rewardXp() + " XP";
        ctx.drawText(textRenderer, rec, tx, y + 24, C_GOLD, false);

        int droite = x + rowW - 8;
        droite = rowButton(ctx, "Annuler", droite, y, mx, my, C_RED, 0xFF3D0A16,
                           aq.questId(), QuestNetworking.ACTION_CANCEL) - 6;
        rowButton(ctx, "R\u00e9clamer", droite, y, mx, my, C_GREEN, 0xFF1A6645,
                  aq.questId(), QuestNetworking.ACTION_CLAIM);
    }

    private void renderPendingRow(DrawContext ctx, PendingRewardData pr, int x, int y,
                                  boolean hover, int mx, int my, int idx) {
        rowFrame(ctx, x, y, hover, C_GOLD);
        renderItemIcon(ctx, pr.itemId(), x + 12, y + (ROW_H - 16) / 2);

        int tx = x + 36;
        ctx.drawText(textRenderer, truncate("\u2728 " + pr.label(), rowW - 230), tx, y + 8, C_WHITE, false);
        ctx.drawText(textRenderer, pr.qty() + "\u00d7 " + fmtItem(pr.itemId()), tx, y + 24, C_GREEN, false);

        int droite = x + rowW - 8;
        droite = rowButton(ctx, "Annuler", droite, y, mx, my, C_RED, 0xFF3D0A16,
                           idx, QuestNetworking.ACTION_CANCEL_PENDING) - 6;
        rowButton(ctx, "R\u00e9cup\u00e9rer", droite, y, mx, my, C_GREEN, 0xFF1A6645,
                  idx, QuestNetworking.ACTION_COLLECT);
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

        if (HubBackButton.clicked(px + PAD, py + 8, imx, imy)) return true;

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
            if (tx > x + rowW - 20) break;
        }
        return y + 10;
    }

    private int tagColor(String tag) {
        return switch (tag) {
            case "SOLO"        -> C_MID;
            case "GROUPE"      -> C_BLUE;
            case "JOURNALIÈRE" -> C_GOLD;
            case "KILL"        -> 0xFFBF4040;
            case "HARVEST"     -> 0xFF5EA85E;
            case "DELIVERY"    -> 0xFF5BA8D4;
            default            -> C_DIM;
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

    /**
     * Têtes de mob disponibles en vanilla. Plus lisible qu'un œuf d'apparition
     * quand elle existe — on la préfère donc systématiquement.
     */
    private static final Map<String, String> TETES_MOB = Map.of(
        "minecraft:zombie",          "minecraft:zombie_head",
        "minecraft:skeleton",        "minecraft:skeleton_skull",
        "minecraft:wither_skeleton", "minecraft:wither_skeleton_skull",
        "minecraft:creeper",         "minecraft:creeper_head",
        "minecraft:piglin",          "minecraft:piglin_head",
        "minecraft:ender_dragon",    "minecraft:dragon_head"
    );

    private void renderItemIcon(DrawContext ctx, String itemId, int x, int y) {
        renderIcon(ctx, null, itemId, x, y);
    }

    /**
     * Icône d'une cible de quête.
     *
     * Une cible KILL est un <b>type d'entité</b> ({@code minecraft:skeleton}), pas un
     * item : la chercher dans le registre des items renvoyait AIR et ne dessinait
     * rien du tout. On passe donc par la tête du mob, ou à défaut son œuf
     * d'apparition — tous les mobs vanilla en ont un.
     */
    private void renderIcon(DrawContext ctx, String type, String target, int x, int y) {
        if (target == null || target.isEmpty()) return;
        try {
            Item item = Registries.ITEM.get(new Identifier(target));
            if (item == Items.AIR && "KILL".equals(type)) {
                String tete = TETES_MOB.get(target);
                if (tete != null) item = Registries.ITEM.get(new Identifier(tete));
                if (item == Items.AIR) {
                    Identifier id = Identifier.tryParse(target);
                    if (id != null) item = Registries.ITEM.get(
                        new Identifier(id.getNamespace(), id.getPath() + "_spawn_egg"));
                }
            }
            if (item == Items.AIR) return;
            ctx.drawItem(new ItemStack(item), x, y);
        } catch (Exception ignored) {}
    }

    private void renderScrollbar(DrawContext ctx, int startY, int rows, int vis, int scroll, int maxScroll) {
        if (rows <= vis) return;
        int trackX = px + pw - 6;
        int trackY = startY + PAD;
        int trackH = vis * (ROW_H + ROW_GAP) - ROW_GAP;
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
        if (tab == Tab.DISPONIBLES && community != null) contentH -= BANNER_H + GAP;
        return Math.max(1, contentH / (ROW_H + ROW_GAP));
    }

    private int currentRows() {
        if (tab == Tab.CLASSEMENTS) return 0;
        int count = switch (tab) {
            case DISPONIBLES -> getAvailableFiltered().size();
            case EN_COURS    -> getInProgress().size();
            case A_RECLAMER  -> pending.size() + getCompleted().size();
            default          -> 0;
        };
        return count;
    }

    private String fmtItem(String id) {
        if (id == null || id.isEmpty()) return "?";
        try {
            Item item = Registries.ITEM.get(new Identifier(id));
            if (item != Items.AIR) return item.getName().getString();
        } catch (Exception ignored) {}
        String raw = id.contains(":") ? id.split(":")[1] : id;
        return raw.replace("_", " ");
    }

    /** Nom localisé de la cible d'une quête (mob pour KILL, item sinon). */
    public static String targetName(String type, String target) {
        try {
            Identifier id = Identifier.tryParse(target);
            if (id == null) return target;
            if ("KILL".equals(type))
                return Registries.ENTITY_TYPE.get(id).getName().getString();
            Item item = Registries.ITEM.get(id);
            if (item != Items.AIR) return item.getName().getString();
        } catch (Exception ignored) {}
        String raw = target.contains(":") ? target.split(":")[1] : target;
        return raw.replace("_", " ");
    }

    private String truncate(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private List<ActiveQuestData> getInProgress() {
        return active.stream()
            .filter(aq -> aq.snapshot() == null
                    || "DELIVERY".equals(aq.snapshot().type())
                    || aq.progress() < aq.snapshot().quantity())
            .toList();
    }

    private List<ActiveQuestData> getCompleted() {
        return active.stream()
            .filter(aq -> aq.snapshot() != null
                    && !"DELIVERY".equals(aq.snapshot().type())
                    && aq.progress() >= aq.snapshot().quantity())
            .toList();
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
                       List<LeaderboardEntry> lbCompleted, List<LeaderboardEntry> lbLevel,
                       CommunityData community) {
        this.playerLevel  = level;
        this.playerXp     = xp;
        this.xpToNext     = xpNext;
        this.available    = available;
        this.active       = active;
        this.pending      = pending;
        this.groupPending = groupPending;
        this.lbCompleted  = lbCompleted;
        this.lbLevel      = lbLevel;
        this.community    = community;
    }
}
