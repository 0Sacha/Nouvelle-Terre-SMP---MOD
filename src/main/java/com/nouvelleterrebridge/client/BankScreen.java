package com.nouvelleterrebridge.client;

import com.nouvelleterrebridge.network.BankNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.*;

@Environment(EnvType.CLIENT)
public class BankScreen extends Screen {

    // ── Records ────────────────────────────────────────────────────────────────

    public record TxData(int type, String label, int amount, long timestamp) {}
    public record LoanData(int id, String other, int principal, long dueMs,
                           int daysOverdue, int totalPenalty, int nextPenalty, boolean repaid) {}
    public record LeaderboardEntry(String name, int balance) {}

    // ── Tabs ───────────────────────────────────────────────────────────────────

    private enum Tab {
        ACCOUNT("Compte"), ECONOMY("Economie"), LEADERBOARD("Classement"), CREDITS("Credits");
        final String label;
        Tab(String l) { this.label = l; }
    }

    // ── Couleurs ───────────────────────────────────────────────────────────────

    private static final int C_BG      = 0xFF14161A;
    private static final int C_PANEL   = 0xFF1B1D22;
    private static final int C_SURFACE = 0xFF21242C;
    private static final int C_HOVER   = 0xFF282B34;
    private static final int C_STRIP   = 0xFF1E2128;
    private static final int C_BORDER  = 0xFF2A2D38;
    private static final int C_GOLD    = 0xFFE8A838;
    private static final int C_RED     = 0xFFBF2040;
    private static final int C_GREEN   = 0xFF2EAD6B;
    private static final int C_BLUE    = 0xFF3B82F6;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_MID     = 0xFF9096A3;
    private static final int C_DIM     = 0xFF565C6A;
    private static final int C_DARK    = 0xFF353840;

    // ── Layout ─────────────────────────────────────────────────────────────────

    private static final int TOP_H     = 44;
    private static final int PAD       = 12;
    private static final int GAP       = 8;
    private static final int WIN_MAX_W = 920;
    private static final int WIN_MAX_H = 560;
    private int winX, winY, winW, winH;

    // ── Sélecteurs modal ───────────────────────────────────────────────────────

    private static final int[] DURATIONS = {1, 2, 3, 7, 14, 30};
    private static final int[] PENALTIES = {5, 10, 15, 20, 25, 50};

    // ── Data ───────────────────────────────────────────────────────────────────

    private int balance;
    private int ticksReward;
    private List<TxData> transactions;
    private int totalShards;
    private int playerCount;
    private List<LeaderboardEntry> leaderboard;
    private List<LoanData> loansAsLender;
    private List<LoanData> loansAsBorrower;
    private List<String> knownPlayers;

    // ── UI State ───────────────────────────────────────────────────────────────

    private Tab activeTab = Tab.ACCOUNT;
    private int txScroll = 0;
    private long screenOpenTime;

    private String toastMsg;
    private boolean toastOk;
    private long toastEnd;

    // ── Modal — nouveau crédit ──────────────────────────────────────────────────

    private boolean modalOpen = false;
    private int     modalBorrowerIdx   = 0;
    private boolean borrowerDropOpen   = false;
    private int     borrowerDropScroll = 0;
    private int     modalDurationIdx   = 3; // 7 jours par défaut
    private int     modalPenaltyIdx    = 2; // 15 ◆/j par défaut
    private TextFieldWidget modalAmountField;

    // Positions cachées pour le mouseClicked
    private int modalX, modalY, modalW, modalH;
    private int modalBorrowDropX, modalBorrowDropY, modalBorrowDropW;
    private int modalDurationBtnX, modalDurationBtnW;
    private int modalPenaltyBtnX, modalPenaltyBtnW;
    private int modalSelectorY;
    private int modalCreateBtnX, modalCreateBtnY, modalCreateBtnW;
    private int newLoanBtnX, newLoanBtnY, newLoanBtnW;

    // ── Constructeur ───────────────────────────────────────────────────────────

    public BankScreen(int balance, int ticksReward, List<TxData> transactions,
                      int totalShards, int playerCount, List<LeaderboardEntry> leaderboard,
                      List<LoanData> loansAsLender, List<LoanData> loansAsBorrower,
                      List<String> knownPlayers) {
        super(Text.literal("Banque — Nouvelle Terre"));
        this.balance        = balance;
        this.ticksReward    = ticksReward;
        this.transactions   = new ArrayList<>(transactions);
        this.totalShards    = totalShards;
        this.playerCount    = playerCount;
        this.leaderboard    = new ArrayList<>(leaderboard);
        this.loansAsLender  = new ArrayList<>(loansAsLender);
        this.loansAsBorrower = new ArrayList<>(loansAsBorrower);
        this.knownPlayers   = new ArrayList<>(knownPlayers);
        this.screenOpenTime = System.currentTimeMillis();
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        recomputeWin();
        modalAmountField = new TextFieldWidget(textRenderer, 0, -200, 160, 18, Text.empty());
        modalAmountField.setMaxLength(8);
        modalAmountField.setPlaceholder(Text.literal("Montant..."));
        addSelectableChild(modalAmountField);
    }

    private void recomputeWin() {
        winW = Math.min(WIN_MAX_W, width - 40);
        winH = Math.min(WIN_MAX_H, height - 40);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
    }

    // ── handleResult ───────────────────────────────────────────────────────────

    public void handleResult(boolean ok, String message, int balance, int ticksReward,
                             List<TxData> transactions, int totalShards, int playerCount,
                             List<LeaderboardEntry> leaderboard,
                             List<LoanData> loansAsLender, List<LoanData> loansAsBorrower,
                             List<String> knownPlayers) {
        this.balance        = balance;
        this.ticksReward    = ticksReward;
        this.transactions   = new ArrayList<>(transactions);
        this.totalShards    = totalShards;
        this.playerCount    = playerCount;
        this.leaderboard    = new ArrayList<>(leaderboard);
        this.loansAsLender  = new ArrayList<>(loansAsLender);
        this.loansAsBorrower = new ArrayList<>(loansAsBorrower);
        this.knownPlayers   = new ArrayList<>(knownPlayers);
        this.screenOpenTime = System.currentTimeMillis();
        modalOpen       = false;
        borrowerDropOpen = false;
        txScroll        = 0;
        toast(message, ok);
    }

    private void toast(String msg, boolean ok) {
        toastMsg = msg.replaceAll("§[0-9a-fA-Fklmnor]", "");
        toastOk  = ok;
        toastEnd = System.currentTimeMillis() + 3200;
    }

    // ── Render principal ────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x78000000);
        recomputeWin();
        ctx.fill(winX + 3, winY + 3, winX + winW + 3, winY + winH + 3, 0x40000000);
        ctx.fill(winX, winY, winX + winW, winY + winH, 0xCC14161A);

        renderTopBar(ctx, mx, my);

        int cy = winY + TOP_H + PAD;
        int ch = winH - TOP_H - PAD * 2;
        switch (activeTab) {
            case ACCOUNT     -> renderAccountTab(ctx, mx, my, cy, ch);
            case ECONOMY     -> renderEconomyTab(ctx, mx, my, cy, ch);
            case LEADERBOARD -> renderLeaderboardTab(ctx, mx, my, cy, ch);
            case CREDITS     -> renderCreditsTab(ctx, mx, my, cy, ch);
        }

        renderToast(ctx);

        // Overlay modal — AVANT super.render() pour que le champ passe dessus
        if (modalOpen) {
            ctx.fill(winX, winY + TOP_H, winX + winW, winY + winH, 0x88000000);
            renderLoanModal(ctx, mx, my);
            if (borrowerDropOpen) renderBorrowerDrop(ctx, mx, my);
            if (!borrowerDropOpen && modalAmountField != null) {
                modalAmountField.setX(modalX + PAD);
                modalAmountField.setY(modalY + 92);
                modalAmountField.setWidth(modalW - PAD * 2);
                modalAmountField.render(ctx, mx, my, delta);
            } else if (modalAmountField != null) {
                modalAmountField.setY(-200);
            }
        } else if (modalAmountField != null) {
            modalAmountField.setY(-200);
        }

        super.render(ctx, mx, my, delta);
    }

    // ── Top bar ────────────────────────────────────────────────────────────────

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(winX, winY, winX + winW, winY + TOP_H, 0xE01B1D22);
        ctx.fill(winX, winY + TOP_H - 1, winX + winW, winY + TOP_H, C_BORDER);

        int ty = winY + (TOP_H - textRenderer.fontHeight) / 2;
        int tx = winX + PAD;

        ctx.drawText(textRenderer, "◆", tx, ty, C_GOLD, false);
        tx += textRenderer.getWidth("◆") + 6;
        ctx.drawText(textRenderer, "Banque", tx, ty, C_WHITE, false);
        tx += textRenderer.getWidth("Banque") + 8;
        ctx.fill(tx, winY + (TOP_H - 16) / 2, tx + 1, winY + (TOP_H + 16) / 2, C_BORDER);
        tx += 9;
        ctx.drawText(textRenderer, "Nouvelle Terre", tx, ty, C_MID, false);
        tx += textRenderer.getWidth("Nouvelle Terre") + 20;

        for (Tab tab : Tab.values()) {
            boolean active = activeTab == tab;
            int tw = textRenderer.getWidth(tab.label) + 18;
            boolean hov = mx >= tx && mx <= tx + tw && my >= winY && my <= winY + TOP_H - 1;
            int tabY = winY + (TOP_H - 22) / 2;
            if (active) {
                ctx.fill(tx, tabY, tx + tw, tabY + 22, C_GOLD);
                ctx.drawText(textRenderer, tab.label,
                    tx + tw / 2 - textRenderer.getWidth(tab.label) / 2, tabY + 7, C_BG, false);
            } else if (hov) {
                ctx.fill(tx, tabY, tx + tw, tabY + 22, C_HOVER);
                ctx.drawCenteredTextWithShadow(textRenderer, tab.label, tx + tw / 2, tabY + 7, C_WHITE);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer, tab.label, tx + tw / 2, tabY + 7, C_DIM);
            }
            tx += tw + 4;
        }

        // Chip solde
        String bal = fmt(balance) + " ◆";
        int bw = textRenderer.getWidth(bal) + 18;
        int bx = winX + winW - bw - PAD;
        int by = winY + (TOP_H - 20) / 2;
        boolean balHov = mx >= bx && mx < bx + bw && my >= winY && my <= winY + TOP_H - 1;
        ctx.fill(bx, by, bx + bw, by + 20, balHov ? C_HOVER : C_STRIP);
        ctx.fill(bx, by, bx + 2, by + 20, balance < 0 ? C_RED : C_GOLD);
        ctx.drawText(textRenderer, bal, bx + 10, by + 6, balance < 0 ? C_RED : C_GOLD, false);
    }

    // ── Onglet Compte ──────────────────────────────────────────────────────────

    private void renderAccountTab(DrawContext ctx, int mx, int my, int cy, int ch) {
        int px = winX + PAD, pw = winW - PAD * 2;

        // Solde large
        int headerH = 60;
        ctx.fill(px, cy, px + pw, cy + headerH, C_PANEL);
        ctx.fill(px, cy, px + 3, cy + headerH, balance < 0 ? C_RED : C_GOLD);
        ctx.fill(px, cy + headerH - 1, px + pw, cy + headerH, C_BORDER);
        String balStr = fmt(balance) + " ◆";
        ctx.drawText(textRenderer, balStr,
            px + (pw - textRenderer.getWidth(balStr)) / 2,
            cy + (headerH - textRenderer.fontHeight) / 2,
            balance < 0 ? C_RED : C_GOLD, false);
        cy += headerH + GAP;

        // Carte récompense
        long elapsed = System.currentTimeMillis() - screenOpenTime;
        int rewardTicks = Math.max(0, ticksReward - (int)(elapsed / 50));
        int cardH = 52;
        ctx.fill(px, cy, px + pw, cy + cardH, C_PANEL);
        ctx.fill(px, cy, px + 3, cy + cardH, C_GOLD);
        ctx.fill(px, cy + cardH - 1, px + pw, cy + cardH, C_BORDER);
        ctx.drawText(textRenderer, "RECOMPENSE — +5 ◆ par 30 min de jeu", px + 12, cy + 8, C_DIM, false);
        int barW = pw - 30;
        ctx.fill(px + 12, cy + 26, px + 12 + barW, cy + 31, C_BORDER);
        int prog = (int)(barW * Math.max(0f, Math.min(1f, 1f - rewardTicks / 36000f)));
        ctx.fill(px + 12, cy + 26, px + 12 + prog, cy + 31, C_GOLD);
        boolean ready = rewardTicks == 0;
        ctx.drawText(textRenderer, ready ? "Recompense disponible !" : "Dans " + ticksToTime(rewardTicks),
            px + 12, cy + 38, ready ? C_GOLD : C_MID, false);
        cy += cardH + GAP;

        // Liste transactions
        int txAreaH = ch - (headerH + GAP + cardH + GAP);
        ctx.fill(px, cy, px + pw, cy + txAreaH, C_PANEL);
        ctx.fill(px, cy, px + 3, cy + txAreaH, C_BORDER);
        ctx.fill(px, cy + txAreaH - 1, px + pw, cy + txAreaH, C_BORDER);
        ctx.drawText(textRenderer, "TRANSACTIONS", px + 12, cy + 8, C_DIM, false);
        int rowH = 20, listY = cy + 24;
        int visRows = (txAreaH - 24) / rowH;
        int start = Math.max(0, Math.min(txScroll, transactions.size() - visRows));
        for (int i = start; i < Math.min(start + visRows, transactions.size()); i++) {
            TxData tx = transactions.get(i);
            int ry = listY + (i - start) * rowH;
            boolean isIn = tx.type() == 1 || tx.type() == 2 || tx.type() == 4
                        || tx.type() == 6 || tx.type() == 8;
            int accent = (tx.type() == 9) ? C_RED : (isIn ? C_GREEN : C_RED);
            ctx.fill(px + 12, ry + 2, px + 15, ry + rowH - 2, accent);
            ctx.drawText(textRenderer, tx.label(), px + 20, ry + 4, C_MID, false);
            String amtStr = (isIn ? "+" : "-") + fmt(tx.amount()) + " ◆";
            ctx.drawText(textRenderer, amtStr,
                px + pw - textRenderer.getWidth(amtStr) - 12, ry + 4,
                isIn ? C_GREEN : C_RED, false);
        }
    }

    // ── Onglet Economie ────────────────────────────────────────────────────────

    private void renderEconomyTab(DrawContext ctx, int mx, int my, int cy, int ch) {
        int px = winX + PAD, pw = winW - PAD * 2;
        int cardH = 80, cardW = (pw - GAP) / 2;

        int avg = playerCount > 0 ? totalShards / playerCount : 0;
        String topName = leaderboard.isEmpty() ? "—" : leaderboard.get(0).name();
        int topBal = leaderboard.isEmpty() ? 0 : leaderboard.get(0).balance();

        renderStatCard(ctx, px, cy, cardW, cardH, C_GOLD,
            "SHARDS EN CIRCULATION", fmt(totalShards) + " ◆", C_GOLD);
        renderStatCard(ctx, px + cardW + GAP, cy, cardW, cardH, C_BLUE,
            "JOUEURS ENREGISTRES", String.valueOf(playerCount), C_BLUE);
        cy += cardH + GAP;
        renderStatCard(ctx, px, cy, cardW, cardH, C_GREEN,
            "SOLDE MOYEN", fmt(avg) + " ◆", C_GREEN);
        renderStatCard(ctx, px + cardW + GAP, cy, cardW, cardH, C_GOLD,
            "JOUEUR LE PLUS RICHE", topName + "  —  " + fmt(topBal) + " ◆", C_GOLD);
    }

    private void renderStatCard(DrawContext ctx, int x, int y, int w, int h,
                                int accent, String title, String value, int valueColor) {
        ctx.fill(x, y, x + w, y + h, C_PANEL);
        ctx.fill(x, y, x + 3, y + h, accent);
        ctx.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        ctx.drawText(textRenderer, title, x + 12, y + 14, C_DIM, false);
        ctx.drawText(textRenderer, value, x + 12, y + 40, valueColor, false);
    }

    // ── Onglet Classement ──────────────────────────────────────────────────────

    private void renderLeaderboardTab(DrawContext ctx, int mx, int my, int cy, int ch) {
        int px = winX + PAD, pw = winW - PAD * 2;
        String me = client != null && client.player != null ? client.player.getName().getString() : "";

        ctx.fill(px, cy, px + pw, cy + 24, C_PANEL);
        ctx.fill(px, cy + 23, px + pw, cy + 24, C_BORDER);
        ctx.drawText(textRenderer, "#",       px + 12,  cy + 7, C_DIM, false);
        ctx.drawText(textRenderer, "Joueur",  px + 52,  cy + 7, C_DIM, false);
        String hdSolde = "Solde";
        ctx.drawText(textRenderer, hdSolde, px + pw - textRenderer.getWidth(hdSolde) - 12, cy + 7, C_DIM, false);
        cy += 24;

        int rowH = 36;
        for (int i = 0; i < leaderboard.size(); i++) {
            LeaderboardEntry e = leaderboard.get(i);
            boolean isMe = e.name().equalsIgnoreCase(me);
            ctx.fill(px, cy, px + pw, cy + rowH, isMe ? 0x20E8A838 : (i % 2 == 0 ? C_PANEL : C_BG));
            if (isMe) ctx.fill(px, cy, px + 3, cy + rowH, C_GOLD);
            ctx.fill(px, cy + rowH - 1, px + pw, cy + rowH, C_BORDER);
            int rankColor = i == 0 ? C_GOLD : (i == 1 ? 0xFFC0C0C0 : (i == 2 ? 0xFFCD7F32 : C_DIM));
            ctx.drawText(textRenderer, "#" + (i + 1), px + 12, cy + (rowH - textRenderer.fontHeight) / 2, rankColor, false);
            ctx.drawText(textRenderer, e.name(), px + 52, cy + (rowH - textRenderer.fontHeight) / 2,
                isMe ? C_GOLD : C_WHITE, false);
            String balStr = fmt(e.balance()) + " ◆";
            ctx.drawText(textRenderer, balStr, px + pw - textRenderer.getWidth(balStr) - 12,
                cy + (rowH - textRenderer.fontHeight) / 2, C_GOLD, false);
            cy += rowH;
        }
    }

    // ── Onglet Credits ─────────────────────────────────────────────────────────

    private void renderCreditsTab(DrawContext ctx, int mx, int my, int cy, int ch) {
        int px = winX + PAD, pw = winW - PAD * 2;

        // Bouton "Nouveau credit"
        String btnLabel = "+ Nouveau credit";
        int btnW = textRenderer.getWidth(btnLabel) + 20;
        int btnX = winX + winW - btnW - PAD;
        boolean btnHov = mx >= btnX && mx < btnX + btnW && my >= cy && my < cy + 22;
        ctx.fill(btnX, cy, btnX + btnW, cy + 22, btnHov ? 0xFF1A8050 : C_GREEN);
        ctx.drawCenteredTextWithShadow(textRenderer, btnLabel, btnX + btnW / 2, cy + 7, C_WHITE);
        newLoanBtnX = btnX; newLoanBtnY = cy; newLoanBtnW = btnW;
        cy += 30;

        // Section prêts accordés
        ctx.drawText(textRenderer, "PRETS ACCORDES (" + loansAsLender.size() + ")", px, cy, C_DIM, false);
        cy += textRenderer.fontHeight + 6;
        if (loansAsLender.isEmpty()) {
            ctx.drawText(textRenderer, "Aucun pret accorde.", px + 8, cy, C_DIM, false);
            cy += 20;
        } else {
            for (LoanData loan : loansAsLender)
                cy = renderLoanRow(ctx, mx, my, px, pw, cy, loan, true);
        }
        cy += GAP;

        // Section emprunts
        ctx.drawText(textRenderer, "MES EMPRUNTS (" + loansAsBorrower.size() + ")", px, cy, C_DIM, false);
        cy += textRenderer.fontHeight + 6;
        if (loansAsBorrower.isEmpty()) {
            ctx.drawText(textRenderer, "Aucun emprunt en cours.", px + 8, cy, C_DIM, false);
        } else {
            for (LoanData loan : loansAsBorrower)
                cy = renderLoanRow(ctx, mx, my, px, pw, cy, loan, false);
        }
    }

    private int renderLoanRow(DrawContext ctx, int mx, int my,
                               int px, int pw, int y, LoanData loan, boolean asLender) {
        int rowH = 50;
        long now = System.currentTimeMillis();
        boolean overdue = !loan.repaid() && now > loan.dueMs();
        int accent = loan.repaid() ? C_DIM : (overdue ? C_RED : C_GREEN);

        ctx.fill(px, y, px + pw, y + rowH, C_PANEL);
        ctx.fill(px, y, px + 3, y + rowH, accent);
        ctx.fill(px, y + rowH - 1, px + pw, y + rowH, C_BORDER);

        // Infos gauche
        String role = asLender ? "Emprunteur: " : "Preteur: ";
        ctx.drawText(textRenderer, role + loan.other(), px + 12, y + 8, C_WHITE, false);
        ctx.drawText(textRenderer, fmt(loan.principal()) + " ◆ a rembourser",
            px + 12, y + 22, C_GOLD, false);

        // Infos centre
        String dateStr = new SimpleDateFormat("dd/MM/yyyy").format(new Date(loan.dueMs()));
        String dueLabel = overdue
            ? "En retard J+" + loan.daysOverdue() + "  (echeance: " + dateStr + ")"
            : "Echeance: " + dateStr;
        int mid = px + pw / 2;
        ctx.drawText(textRenderer, dueLabel, mid - textRenderer.getWidth(dueLabel) / 2, y + 8,
            overdue ? C_RED : C_MID, false);
        if (loan.totalPenalty() > 0) {
            String penStr = "Penalites deduites: " + fmt(loan.totalPenalty()) + " ◆"
                + (overdue ? "  (prochaine: " + fmt(loan.nextPenalty()) + " ◆/j)" : "");
            ctx.drawText(textRenderer, penStr, mid - textRenderer.getWidth(penStr) / 2, y + 22, C_RED, false);
        }

        // Bouton d'action droite
        int abW = 104, abX = px + pw - abW - 8;
        int abY = y + (rowH - 20) / 2;
        if (loan.repaid()) {
            ctx.fill(abX, abY, abX + abW, abY + 20, C_DARK);
            ctx.drawCenteredTextWithShadow(textRenderer, "Rembourse", abX + abW / 2, abY + 6, C_DIM);
        } else if (asLender) {
            boolean hov = mx >= abX && mx < abX + abW && my >= abY && my < abY + 20;
            ctx.fill(abX, abY, abX + abW, abY + 20, hov ? 0xFF8B1030 : C_RED);
            ctx.drawCenteredTextWithShadow(textRenderer, "Pardonner", abX + abW / 2, abY + 6, C_WHITE);
        } else {
            boolean canRepay = balance >= loan.principal();
            boolean hov = canRepay && mx >= abX && mx < abX + abW && my >= abY && my < abY + 20;
            ctx.fill(abX, abY, abX + abW, abY + 20, canRepay ? (hov ? 0xFF1A8050 : C_GREEN) : C_DARK);
            ctx.drawCenteredTextWithShadow(textRenderer, "Rembourser", abX + abW / 2, abY + 6,
                canRepay ? C_WHITE : C_DIM);
        }

        return y + rowH + GAP;
    }

    // ── Modal nouveau crédit ────────────────────────────────────────────────────

    private void renderLoanModal(DrawContext ctx, int mx, int my) {
        int mw = 360, mh = 272;
        int mx0 = winX + (winW - mw) / 2;
        int my0 = winY + (winH - mh) / 2;
        modalX = mx0; modalY = my0; modalW = mw; modalH = mh;

        // Fond + cadre
        ctx.fill(mx0, my0, mx0 + mw, my0 + mh, C_SURFACE);
        ctx.fill(mx0, my0, mx0 + mw, my0 + 1, C_GOLD);
        ctx.fill(mx0, my0 + mh - 1, mx0 + mw, my0 + mh, C_BORDER);
        ctx.fill(mx0, my0, mx0 + 1, my0 + mh, C_GOLD);
        ctx.fill(mx0 + mw - 1, my0, mx0 + mw, my0 + mh, C_BORDER);

        ctx.drawText(textRenderer, "NOUVEAU CREDIT", mx0 + PAD, my0 + 10, C_GOLD, false);
        ctx.fill(mx0, my0 + 28, mx0 + mw, my0 + 29, C_BORDER);

        int fy = my0 + 36;

        // Dropdown emprunteur
        ctx.drawText(textRenderer, "Emprunteur", mx0 + PAD, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        int dw = mw - PAD * 2;
        modalBorrowDropX = mx0 + PAD; modalBorrowDropY = fy; modalBorrowDropW = dw;
        String dropLbl = knownPlayers.isEmpty() ? "Aucun joueur connu"
            : (modalBorrowerIdx < knownPlayers.size() ? knownPlayers.get(modalBorrowerIdx) : "...");
        boolean dHov = !borrowerDropOpen && mx >= modalBorrowDropX
            && mx < modalBorrowDropX + dw && my >= fy && my < fy + 20;
        ctx.fill(modalBorrowDropX - 1, fy - 1, modalBorrowDropX + dw + 1, fy + 21, C_BORDER);
        ctx.fill(modalBorrowDropX, fy, modalBorrowDropX + dw, fy + 20,
            borrowerDropOpen || dHov ? C_HOVER : C_DARK);
        ctx.drawText(textRenderer, dropLbl, modalBorrowDropX + 8, fy + 6, C_WHITE, false);
        ctx.drawText(textRenderer, borrowerDropOpen ? "▲" : "▼",
            modalBorrowDropX + dw - 14, fy + 6, C_DIM, false);
        fy += 24;

        // Champ montant (positionné par render())
        ctx.drawText(textRenderer, "Montant", mx0 + PAD, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        fy += 22; // espace pour le TextFieldWidget

        // Sélecteurs durée + pénalité
        fy += 6;
        ctx.drawText(textRenderer, "Duree / Penalite de base", mx0 + PAD, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 4;
        int selW = (dw - GAP) / 2;
        modalDurationBtnX = mx0 + PAD; modalDurationBtnW = selW;
        modalPenaltyBtnX  = mx0 + PAD + selW + GAP; modalPenaltyBtnW = selW;
        modalSelectorY = fy;
        renderSelector(ctx, mx, my, modalDurationBtnX, fy, selW, DURATIONS[modalDurationIdx] + " jour(s)");
        renderSelector(ctx, mx, my, modalPenaltyBtnX,  fy, selW, PENALTIES[modalPenaltyIdx]  + " ◆/j");
        fy += 28;

        // Note augmentation
        ctx.drawText(textRenderer, "+5 ◆ supplementaires par jour de retard",
            mx0 + PAD, fy, C_DIM, false);
        fy += textRenderer.fontHeight + 6;

        // Avertissement montant
        int amount = parseAmount();
        if (amount > 0) {
            String warn = amount + " ◆ preleves de votre compte";
            ctx.drawText(textRenderer, warn, mx0 + mw / 2 - textRenderer.getWidth(warn) / 2, fy,
                amount > balance ? C_RED : C_MID, false);
        }
        fy += textRenderer.fontHeight + 6;

        // Bouton créer
        boolean canCreate = amount > 0 && amount <= balance && !knownPlayers.isEmpty();
        boolean cHov = canCreate && mx >= mx0 + PAD && mx < mx0 + mw - PAD
            && my >= fy && my < fy + 26;
        ctx.fill(mx0 + PAD, fy, mx0 + mw - PAD, fy + 26,
            canCreate ? (cHov ? 0xFF1A8050 : C_GREEN) : C_DARK);
        ctx.drawCenteredTextWithShadow(textRenderer, "Creer le credit",
            mx0 + mw / 2, fy + 9, canCreate ? C_WHITE : C_DIM);
        modalCreateBtnX = mx0 + PAD; modalCreateBtnY = fy; modalCreateBtnW = mw - PAD * 2;
    }

    private void renderSelector(DrawContext ctx, int mx, int my, int x, int y, int w, String label) {
        boolean hL = mx >= x && mx < x + 18 && my >= y && my < y + 22;
        boolean hR = mx >= x + w - 18 && mx < x + w && my >= y && my < y + 22;
        ctx.fill(x, y, x + w, y + 22, C_DARK);
        ctx.fill(x, y, x + 1, y + 22, C_BORDER);
        ctx.fill(x + w - 1, y, x + w, y + 22, C_BORDER);
        ctx.drawText(textRenderer, "<", x + 5, y + 7, hL ? C_WHITE : C_DIM, false);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 7, C_WHITE);
        ctx.drawText(textRenderer, ">", x + w - 12, y + 7, hR ? C_WHITE : C_DIM, false);
    }

    private void renderBorrowerDrop(DrawContext ctx, int mx, int my) {
        int vis = Math.min(knownPlayers.size(), 6);
        int dh  = vis * 20;
        int dy  = modalBorrowDropY + 21;
        ctx.fill(modalBorrowDropX - 1, dy, modalBorrowDropX + modalBorrowDropW + 1, dy + dh + 2, C_GOLD);
        ctx.fill(modalBorrowDropX, dy + 1, modalBorrowDropX + modalBorrowDropW, dy + dh + 1, C_SURFACE);
        for (int i = borrowerDropScroll; i < borrowerDropScroll + vis && i < knownPlayers.size(); i++) {
            int iy = dy + 1 + (i - borrowerDropScroll) * 20;
            boolean hov = mx >= modalBorrowDropX && mx < modalBorrowDropX + modalBorrowDropW
                && my >= iy && my < iy + 20;
            if (hov || i == modalBorrowerIdx)
                ctx.fill(modalBorrowDropX, iy, modalBorrowDropX + modalBorrowDropW, iy + 20, C_HOVER);
            ctx.drawText(textRenderer, knownPlayers.get(i),
                modalBorrowDropX + 8, iy + 6, i == modalBorrowerIdx ? C_GOLD : C_WHITE, false);
        }
    }

    // ── Toast ──────────────────────────────────────────────────────────────────

    private void renderToast(DrawContext ctx) {
        if (toastMsg == null) return;
        if (System.currentTimeMillis() > toastEnd) { toastMsg = null; return; }
        int tw = textRenderer.getWidth(toastMsg) + 28;
        int th = 26;
        int tx = winX + winW - tw - 12;
        int ty = winY + winH - th - 12;
        ctx.fill(tx, ty, tx + tw, ty + th, C_SURFACE);
        ctx.fill(tx, ty, tx + tw, ty + 1, C_BORDER);
        ctx.fill(tx, ty + th - 1, tx + tw, ty + th, C_BORDER);
        ctx.fill(tx + tw - 1, ty, tx + tw, ty + th, C_BORDER);
        ctx.fill(tx, ty, tx + 3, ty + th, toastOk ? C_GREEN : C_RED);
        ctx.drawText(textRenderer, toastMsg, tx + 11,
            ty + (th - textRenderer.fontHeight) / 2, C_WHITE, false);
    }

    // ── Mouse ──────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int x = (int) mx0, y = (int) my0;
        if (x < winX || x > winX + winW || y < winY || y > winY + winH) { close(); return true; }

        // ── Modal ouverte ──
        if (modalOpen) {
            if (borrowerDropOpen) {
                int dy = modalBorrowDropY + 21;
                if (x >= modalBorrowDropX && x < modalBorrowDropX + modalBorrowDropW
                        && y >= dy && y < dy + Math.min(knownPlayers.size(), 6) * 20 + 2) {
                    int idx = (y - dy - 1) / 20 + borrowerDropScroll;
                    if (idx >= 0 && idx < knownPlayers.size()) modalBorrowerIdx = idx;
                }
                borrowerDropOpen = false;
                return true;
            }
            // Fermer si clic hors modal
            if (x < modalX || x > modalX + modalW || y < modalY || y > modalY + modalH) {
                modalOpen = false;
                return true;
            }
            // Toggle dropdown
            if (x >= modalBorrowDropX && x < modalBorrowDropX + modalBorrowDropW
                    && y >= modalBorrowDropY && y < modalBorrowDropY + 20) {
                if (!knownPlayers.isEmpty()) borrowerDropOpen = !borrowerDropOpen;
                return true;
            }
            // Sélecteurs
            if (y >= modalSelectorY && y < modalSelectorY + 22) {
                if (x >= modalDurationBtnX && x < modalDurationBtnX + 18)
                    modalDurationIdx = (modalDurationIdx - 1 + DURATIONS.length) % DURATIONS.length;
                else if (x >= modalDurationBtnX + modalDurationBtnW - 18 && x < modalDurationBtnX + modalDurationBtnW)
                    modalDurationIdx = (modalDurationIdx + 1) % DURATIONS.length;
                else if (x >= modalPenaltyBtnX && x < modalPenaltyBtnX + 18)
                    modalPenaltyIdx = (modalPenaltyIdx - 1 + PENALTIES.length) % PENALTIES.length;
                else if (x >= modalPenaltyBtnX + modalPenaltyBtnW - 18 && x < modalPenaltyBtnX + modalPenaltyBtnW)
                    modalPenaltyIdx = (modalPenaltyIdx + 1) % PENALTIES.length;
                return true;
            }
            // Bouton créer
            if (y >= modalCreateBtnY && y < modalCreateBtnY + 26
                    && x >= modalCreateBtnX && x < modalCreateBtnX + modalCreateBtnW) {
                int amount = parseAmount();
                if (amount > 0 && amount <= balance && !knownPlayers.isEmpty())
                    sendLoanCreate(amount);
                return true;
            }
            return super.mouseClicked(mx0, my0, btn);
        }

        // ── Barre onglets ──
        if (y >= winY && y <= winY + TOP_H) {
            int tx = winX + PAD
                + textRenderer.getWidth("◆") + 6
                + textRenderer.getWidth("Banque") + 8 + 1 + 9
                + textRenderer.getWidth("Nouvelle Terre") + 20;
            for (Tab tab : Tab.values()) {
                int tw = textRenderer.getWidth(tab.label) + 18;
                if (x >= tx && x < tx + tw) { activeTab = tab; txScroll = 0; return true; }
                tx += tw + 4;
            }
        }

        // ── Onglet Crédits ──
        if (activeTab == Tab.CREDITS) {
            if (x >= newLoanBtnX && x < newLoanBtnX + newLoanBtnW
                    && y >= newLoanBtnY && y < newLoanBtnY + 22) {
                modalOpen = true;
                borrowerDropOpen = false;
                modalBorrowerIdx = 0;
                if (modalAmountField != null) modalAmountField.setText("");
                return true;
            }

            int px = winX + PAD, pw = winW - PAD * 2;
            int cy = winY + TOP_H + PAD + 30 + textRenderer.fontHeight + 6;
            if (loansAsLender.isEmpty()) cy += 20;
            for (LoanData loan : loansAsLender) {
                if (!loan.repaid() && isActionBtn(x, y, px, pw, cy)) {
                    sendLoanForgive(loan.id()); return true;
                }
                cy += 50 + GAP;
            }
            cy += GAP + textRenderer.fontHeight + 6;
            if (loansAsBorrower.isEmpty()) cy += 20;
            for (LoanData loan : loansAsBorrower) {
                if (!loan.repaid() && balance >= loan.principal() && isActionBtn(x, y, px, pw, cy)) {
                    sendLoanRepay(loan.id()); return true;
                }
                cy += 50 + GAP;
            }
        }

        return super.mouseClicked(mx0, my0, btn);
    }

    private boolean isActionBtn(int x, int y, int px, int pw, int rowY) {
        int abW = 104, abX = px + pw - abW - 8;
        int abY = rowY + (50 - 20) / 2;
        return x >= abX && x < abX + abW && y >= abY && y < abY + 20;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (activeTab == Tab.ACCOUNT) {
            txScroll = Math.max(0, txScroll - (int) amount);
            return true;
        }
        if (borrowerDropOpen) {
            borrowerDropScroll = Math.max(0,
                Math.min(Math.max(0, knownPlayers.size() - 6), borrowerDropScroll - (int) amount));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    // ── Réseau ─────────────────────────────────────────────────────────────────

    private void sendLoanCreate(int amount) {
        if (knownPlayers.isEmpty()) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(BankNetworking.ACTION_LOAN_CREATE);
        buf.writeString(knownPlayers.get(modalBorrowerIdx));
        buf.writeInt(amount);
        buf.writeInt(DURATIONS[modalDurationIdx]);
        buf.writeInt(PENALTIES[modalPenaltyIdx]);
        buf.writeInt(5); // penaltyIncrease fixe : +5 ◆ par jour supplémentaire
        ClientPlayNetworking.send(BankNetworking.BANK_ACTION, buf);
    }

    private void sendLoanRepay(int loanId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(BankNetworking.ACTION_LOAN_REPAY);
        buf.writeInt(loanId);
        ClientPlayNetworking.send(BankNetworking.BANK_ACTION, buf);
    }

    private void sendLoanForgive(int loanId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(BankNetworking.ACTION_LOAN_FORGIVE);
        buf.writeInt(loanId);
        ClientPlayNetworking.send(BankNetworking.BANK_ACTION, buf);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private int parseAmount() {
        if (modalAmountField == null) return 0;
        try { return Math.max(0, Integer.parseInt(modalAmountField.getText().trim())); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String fmt(int n) {
        if (n < 0) return "-" + fmt(-n);
        if (n < 1000) return String.valueOf(n);
        return fmt(n / 1000) + " " + String.format("%03d", n % 1000);
    }

    private static String ticksToTime(int ticks) {
        int s = ticks / 20, min = s / 60;
        return min > 0 ? min + "m " + (s % 60) + "s" : s + "s";
    }
}
