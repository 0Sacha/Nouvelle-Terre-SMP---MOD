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

    /** Demande de crédit en attente de l'accord du prêteur. */
    public static class LoanRequest {
        public int    id;
        public String lender;
        public String borrower;
        public int    principal;
        public int    durationDays;
        public int    penaltyBase;
        public int    penaltyIncrease;
        public long   createdAt;
    }

    private final Path fichier;
    private final List<Loan> loans = new ArrayList<>();
    private final List<LoanRequest> requests = new ArrayList<>();
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

    // ── Demandes de crédit ───────────────────────────────────────────────────

    /**
     * Le prêteur propose un crédit à l'emprunteur.
     * Aucun fonds n'est transféré tant que l'emprunteur n'a pas accepté.
     * Retourne null en cas de succès, un message d'erreur sinon.
     */
    public synchronized String request(String borrower, String lender, int principal,
                                       int durationDays, int penaltyBase, int penaltyIncrease) {
        boolean dejaEnAttente = requests.stream().anyMatch(r ->
            r.borrower.equalsIgnoreCase(borrower) && r.lender.equalsIgnoreCase(lender));
        if (dejaEnAttente) return "Vous avez deja une proposition en attente aupres de ce joueur.";
        LoanRequest r = new LoanRequest();
        r.id              = nextId++;
        r.lender          = lender;
        r.borrower        = borrower;
        r.principal       = principal;
        r.durationDays    = durationDays;
        r.penaltyBase     = penaltyBase;
        r.penaltyIncrease = penaltyIncrease;
        r.createdAt       = System.currentTimeMillis();
        requests.add(r);
        sauvegarder();
        return null;
    }

    /** L'emprunteur accepte une proposition : le crédit est créé et les fonds transférés. */
    public synchronized String acceptRequest(String borrower, int requestId) {
        LoanRequest r = findRequest(requestId);
        if (r == null || !r.borrower.equalsIgnoreCase(borrower)) return "Proposition introuvable.";
        String err = add(r.lender, r.borrower, r.principal, r.durationDays, r.penaltyBase, r.penaltyIncrease);
        if (err != null) return err;
        requests.remove(r);
        sauvegarder();
        return null;
    }

    /** L'emprunteur refuse, ou le prêteur annule sa propre proposition. */
    public synchronized String declineRequest(String who, int requestId) {
        LoanRequest r = findRequest(requestId);
        if (r == null || (!r.lender.equalsIgnoreCase(who) && !r.borrower.equalsIgnoreCase(who)))
            return "Proposition introuvable.";
        requests.remove(r);
        sauvegarder();
        return null;
    }

    public synchronized LoanRequest getRequest(int id) { return findRequest(id); }

    public synchronized List<LoanRequest> getRequestsAsLender(String lender) {
        return requests.stream().filter(r -> r.lender.equalsIgnoreCase(lender)).collect(Collectors.toList());
    }

    public synchronized List<LoanRequest> getRequestsAsBorrower(String borrower) {
        return requests.stream().filter(r -> r.borrower.equalsIgnoreCase(borrower)).collect(Collectors.toList());
    }

    private LoanRequest findRequest(int id) {
        return requests.stream().filter(r -> r.id == id).findFirst().orElse(null);
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
            if (root.has("requests")) {
                Type reqType = new TypeToken<List<LoanRequest>>() {}.getType();
                List<LoanRequest> loadedReqs = GSON.fromJson(root.getAsJsonArray("requests"), reqType);
                if (loadedReqs != null) requests.addAll(loadedReqs);
            }
            NouvelleTerreBridge.LOGGER.info("[LoanManager] {} credit(s) et {} demande(s) charge(s).",
                loans.size(), requests.size());
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LoanManager] Erreur chargement : {}", e.getMessage());
        }
    }

    private void sauvegarder() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fichier.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();
            root.addProperty("nextId", nextId);
            root.add("loans", GSON.toJsonTree(loans));
            root.add("requests", GSON.toJsonTree(requests));
            GSON.toJson(root, w);
        } catch (Exception e) {
            NouvelleTerreBridge.LOGGER.error("[LoanManager] Erreur sauvegarde : {}", e.getMessage());
        }
    }
}
