package com.nouvelleterrebridge.economy;

public class Loan {
    public int     id;
    public String  lender;
    public String  borrower;
    public int     principal;
    public long    dueTimestamp;
    public int     penaltyBase;
    public int     penaltyIncrease;

    /** Hausse quotidienne de la pénalité, en ◆ — imposée par le serveur, non négociable. */
    public static final int HAUSSE_PENALITE = 5;
    public int     daysOverdue;
    public int     totalPenalty;
    public boolean repaid;
    public long    createdAt;
    public long    lastPenaltyMs;

    public Loan() {}

    public Loan(int id, String lender, String borrower, int principal,
                long dueTimestamp, int penaltyBase, int penaltyIncrease) {
        this.id              = id;
        this.lender          = lender;
        this.borrower        = borrower;
        this.principal       = principal;
        this.dueTimestamp    = dueTimestamp;
        this.penaltyBase     = penaltyBase;
        this.penaltyIncrease = penaltyIncrease;
        this.daysOverdue     = 0;
        this.totalPenalty    = 0;
        this.repaid          = false;
        this.createdAt       = System.currentTimeMillis();
        this.lastPenaltyMs   = dueTimestamp;
    }

    /** Pénalité du prochain jour de retard (augmente chaque jour). */
    public int nextPenalty() {
        // Jamais négative : une hausse négative enregistrée avant le correctif
        // aurait fini par verser de l'argent à l'emprunteur en retard.
        return Math.max(0, penaltyBase + daysOverdue * Math.max(0, penaltyIncrease));
    }

    public boolean isOverdue() {
        return !repaid && System.currentTimeMillis() > dueTimestamp;
    }
}
