package com.nouvelleterrebridge.economy;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.nouvelleterrebridge.NouvelleTerreBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class LoanManager {

    private static final long DAY_MS = 86_400_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LoanManager instance;

    private final Path fichier;
    private final List<Loan> loans = new ArrayList<>();
    private int nextId = 1;
    private int tickCount = 0;

    private LoanManager() {
        fichier = FabricLoader.getInstance().getGameDir().resolve("nouvelle-terre-credits.json");
        charger();
    }

    public static synchronized LoanManager getInstance() {
        if (instance == null) instance = new LoanManager();
        return instance;
    }

    public static void register() {
        getInstance();
        ServerTickEvents.END_SERVER_TICK.register(server -> getInstance().tick(server));
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Crée un crédit : transfère les fonds du prêteur à l'emprunteur.
     * Retourne null en cas de succès, un message d'erreur sinon.
     */
    public synchronized String add(String lender, String borrower, int principal,
                                   int durationDays, int penaltyBase, int penaltyIncrease) {
        if (!LocalEconomy.getInstance().transfer(lender, borrower, principal))
            return "Solde insuffisant pour accorder ce credit.";
        long due = System.currentTimeMillis() + (long) durationDays * DAY_MS;
        Loan loan = new Loan(nextId++, lender, borrower, principal, due, penaltyBase, penaltyIncrease);
        loans.add(loan);
        sauvegarder();
        TransactionLog.log(lender,   TransactionLog.TYPE_LOAN_OUT, "Credit accorde a " + borrower, principal);
        TransactionLog.log(borrower, TransactionLog.TYPE_LOAN_IN,  "Credit recu de " + lender,     principal);
        return null;
    }

    /**
     * Rembourse un crédit : transfère le principal de l'emprunteur au prêteur.
     * Retourne null en cas de succès.
     */
    public synchronized String repay(String borrower, int loanId) {
        Loan loan = findById(loanId);
        if (loan == null || !loan.borrower.equalsIgnoreCase(borrower)) return "Credit introuvable.";
        if (loan.repaid) return "Ce credit est deja rembourse.";
        if (!LocalEconomy.getInstance().transfer(borrower, loan.lender, loan.principal))
            return "Solde insuffisant pour rembourser (" + loan.principal + " ◆ requis).";
        loan.repaid = true;
        sauvegarder();
        TransactionLog.log(borrower,    TransactionLog.TYPE_LOAN_REPAY_OUT, "Remboursement a " + loan.lender,  loan.principal);
        TransactionLog.log(loan.lender, TransactionLog.TYPE_LOAN_REPAY_IN,  "Remboursement de " + borrower,   loan.principal);
        return null;
    }

    /** Le prêteur pardonne/annule un crédit sans remboursement. */
    public synchronized String forgive(String lender, int loanId) {
        Loan loan = findById(loanId);
        if (loan == null || !loan.lender.equalsIgnoreCase(lender)) return "Credit introuvable.";
        if (loan.repaid) return "Ce credit est deja clos.";
        loan.repaid = true;
        sauvegarder();
        return null;
    }

    public synchronized Loan getLoan(int id) { return findById(id); }

    public synchronized List<Loan> getLoansAsLender(String lender) {
        return loans.stream().filter(l -> l.lender.equalsIgnoreCase(lender)).collect(Collectors.toList());
    }

    public synchronized List<Loan> getLoansAsBorrower(String borrower) {
        return loans.stream().filter(l -> l.borrower.equalsIgnoreCase(borrower)).collect(Collectors.toList());
    }

    // ── Tick ─────────────────────────────────────────────────────────────────
    // Vérifie toutes les minutes (1200 ticks) les crédits en retard.
    // Pour chaque jour de retard écoulé, déduit la pénalité (peut passer en négatif).

    private synchronized void tick(MinecraftServer server) {
        if (++tickCount % 1200 != 0) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Loan loan : loans) {
            if (loan.repaid || now <= loan.dueTimestamp) continue;
            while (now - loan.lastPenaltyMs >= DAY_MS) {
                int penalty = loan.nextPenalty();
                LocalEconomy.getInstance().forceDeduct(loan.borrower, penalty);
                TransactionLog.log(loan.borrower, TransactionLog.TYPE_LOAN_PENALTY,
                    "Penalite credit J+" + (loan.daysOverdue + 1), penalty);
                loan.daysOverdue++;
                loan.totalPenalty += penalty;
                loan.lastPenaltyMs += DAY_MS;
                changed = true;
                notifyPenalty(server, loan, penalty);
            }
        }
        if (changed) sauvegarder();
    }

    private void notifyPenalty(MinecraftServer server, Loan loan, int penalty) {
        ServerPlayerEntity b = server.getPlayerManager().getPlayer(loan.borrower);
        if (b != null) b.sendMessage(Text.literal(
            "§c[Nouvelle Terre] Credit non rembourse ! Penalite de §f" + penalty
            + " ◆§c appliquee. Total: §f" + loan.totalPenalty + " ◆"));
        ServerPlayerEntity l = server.getPlayerManager().getPlayer(loan.lender);
        if (l != null) l.sendMessage(Text.literal(
            "§e[Nouvelle Terre] §f" + loan.borrower
            + "§e : penalite de §f" + penalty + " ◆§e deduite (credit en retard)."));
    }

    // ── Persistance ───────────────────────────────────────────────────────────

    private Loan findById(int id) {
        return loans.stream().filter(l -> l.id == id).findFirst().orElse(null);
    }

    private void charger() {
        File f = fichier.toFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            nextId = root.has("nextId") ? root.get("nextId").getAsInt() : 1;
            Type type = new TypeToken<List<Loan>>() {}.getType();
            List<Loan> loaded = GSON.fromJson(root.getAsJsonArray("loans"), type);
            if (loaded != null) loans.addAll(loaded);
            NouvelleTerreBridge.LOGGER.info("[LoanManager] {} credit(s) charge(s).", loans.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LoanManager] Erreur chargement : {}", e.getMessage());
        }
    }

    private void sauvegarder() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fichier.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();
            root.addProperty("nextId", nextId);
            root.add("loans", GSON.toJsonTree(loans));
            GSON.toJson(root, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LoanManager] Erreur sauvegarde : {}", e.getMessage());
        }
    }
}
